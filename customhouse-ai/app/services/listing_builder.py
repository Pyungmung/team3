"""
[담당: 송귀성] 더미 매물 한 건을 외부 공공 API로 채우는 조립 모듈

더미 매물 CSV 생성 스크립트(scripts/gen_dummyhouses.py)와, 앞으로 만들 "신규 매물 생성하기" 탭이
같이 쓴다. 주소·좌표·참고 실거래를 CSV 컬럼(listing_schema.py)에 맞게 채우는 조각들이다.

- 행정안전부 도로명주소 검색 API(JUSO_API_KEY): 도로명주소·지번주소·우편번호·건물관리번호·행정구역코드·도로명코드
- 행정안전부 상세주소 API(JUSO_API_KEY_AV): 동이 있는 건물의 동명 (동이 딱 1개일 때만 채운다)
- 카카오 로컬 주소 검색(KAKAO_REST_APP_KEY): 위도/경도
- 국토교통부 전월세 실거래가 4종(DATA_GO_KR_API_KEY): 같은 지번의 실제 계약 -> "참고_국토부_*" 컬럼

서킷브레이커가 있는 juso_api.py / kakao_geocode.py는 추천 응답 경로(요청 1건당 소수 호출)용이고,
여기는 수만 건을 연달아 조회하는 생성 작업용이라 재시도/연속 실패 쿨다운/디스크 캐시를 따로 둔다.
"""
import json
import logging
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Callable

import requests

from app.core.config import settings
from app.services import api_collector

logger = logging.getLogger(__name__)

JUSO_SEARCH = "https://business.juso.go.kr/addrlink/addrLinkApi.do"
JUSO_DETAIL = "https://business.juso.go.kr/addrlink/addrDetailApi.do"
KAKAO_ADDR = "https://dapi.kakao.com/v2/local/search/address.json"

CONSECUTIVE_FAIL_COOLDOWN = 20  # 외부 API가 연속으로 이만큼 실패하면 잠깐 쉰다
COOLDOWN_SECONDS = 60


class Transient(Exception):
    """일시적 실패 - 캐시하지 않고 이번 건만 건너뛴다(다시 시도하면 될 수 있음)."""


# ---------------------------------------------------------------- 로그 / 캐시 설정
_log_hook: Callable[[str], None] | None = None
_cache_file: Path | None = None
_cache_lock = threading.Lock()
_cache: dict[str, dict] = {"juso": {}, "geo": {}}
_dirty = 0


def set_log_hook(hook: Callable[[str], None] | None) -> None:
    """생성 스크립트가 진행 로그를 자기 로그 파일에도 남기고 싶을 때 지정한다."""
    global _log_hook
    _log_hook = hook


def _log(msg: str) -> None:
    logger.info(msg)
    if _log_hook:
        _log_hook(msg)


def configure_cache(cache_file: Path | None) -> None:
    """디스크 캐시 파일을 지정하고 기존 내용을 읽는다. None이면 메모리 캐시만 쓴다(신규 등록처럼 소량일 때)."""
    global _cache_file, _cache
    _cache_file = cache_file
    if cache_file and cache_file.exists():
        _cache = json.loads(cache_file.read_text(encoding="utf-8"))
        _cache.setdefault("juso", {})
        _cache.setdefault("geo", {})


def save_cache(force: bool = False) -> None:
    global _dirty
    if _cache_file is None:
        return
    with _cache_lock:
        if not force and _dirty < 150:
            return
        tmp = _cache_file.with_suffix(".tmp")
        tmp.write_text(json.dumps(_cache, ensure_ascii=False), encoding="utf-8")
        tmp.replace(_cache_file)
        _dirty = 0


def _cache_put(kind: str, key: str, value) -> None:
    global _dirty
    with _cache_lock:
        _cache[kind][key] = value
        _dirty += 1
    save_cache()


# ---------------------------------------------------------------- HTTP (재시도 + 연속 실패 쿨다운)
_session = requests.Session()
_fail_lock = threading.Lock()
_consec_fail = 0


def _note_result(ok: bool) -> None:
    global _consec_fail
    with _fail_lock:
        _consec_fail = 0 if ok else _consec_fail + 1
        cool = _consec_fail >= CONSECUTIVE_FAIL_COOLDOWN
        if cool:
            _consec_fail = 0
    if cool:
        _log(f"외부 API 연속 실패 {CONSECUTIVE_FAIL_COOLDOWN}회 - {COOLDOWN_SECONDS}초 쿨다운")
        time.sleep(COOLDOWN_SECONDS)


def http_json(url: str, params: dict, headers: dict | None = None, tries: int = 4) -> dict:
    last = None
    for attempt in range(tries):
        try:
            r = _session.get(url, params=params, headers=headers, timeout=8)
            if r.status_code == 200:
                _note_result(True)
                return r.json()
            last = f"HTTP {r.status_code} {r.text[:120]}"
        except (requests.RequestException, ValueError) as e:
            last = str(e)[:120]
        _note_result(False)
        time.sleep(1.5 * (attempt + 1))
    raise Transient(last)


# ---------------------------------------------------------------- 주소 / 좌표
def geocode(road_addr: str) -> tuple[float, float] | None:
    """도로명주소 -> (위도, 경도). 결과가 없으면 None(캐시됨), 일시 실패는 Transient."""
    with _cache_lock:
        if road_addr in _cache["geo"]:
            v = _cache["geo"][road_addr]
            return tuple(v) if v else None
    body = http_json(
        KAKAO_ADDR, {"query": road_addr}, headers={"Authorization": f"KakaoAK {settings.kakao_rest_app_key}"}
    )
    docs = body.get("documents") or []
    val = [float(docs[0]["y"]), float(docs[0]["x"])] if docs else None
    _cache_put("geo", road_addr, val)
    return tuple(val) if val else None


def resolve_keyword(keyword: str, cache_key: str | None = None) -> dict | None:
    """주소 검색어 하나(예: "서울특별시 서초구 방배동 123-4", 또는 사용자가 입력한 도로명주소) ->
    매물 CSV에 들어갈 주소 정보 dict. 행안부에 매칭이 없거나 좌표를 못 구하면 None(캐시됨), 일시 실패는 Transient.

    반환 키: roadAddr, jibunAddr, bdNm, zipNo, bdMgtSn, admCd, rnMgtSn, dongNm(동이 1개일 때만), lat, lon
    """
    key = cache_key or keyword
    with _cache_lock:
        if key in _cache["juso"]:
            return _cache["juso"][key]

    body = http_json(JUSO_SEARCH, {
        "confmKey": settings.juso_api_key, "currentPage": 1, "countPerPage": 1,
        "keyword": keyword, "resultType": "json"})
    common = body.get("results", {}).get("common", {})
    code = common.get("errorCode")
    if code == "4" or (code == "0" and not body["results"].get("juso")):
        _cache_put("juso", key, None)
        return None
    if code != "0":
        raise Transient(f"juso errorCode={code} {common.get('errorMessage')}")
    j = body["results"]["juso"][0]

    dong_name = ""
    if j.get("detBdNmList"):  # 동이 있는 건물만 상세주소 API로 동이 1개인지 확인 (호출 절약)
        try:
            d = http_json(JUSO_DETAIL, {
                "confmKey": settings.juso_api_key_av, "currentPage": 1, "countPerPage": 30,
                "admCd": j["admCd"], "rnMgtSn": j["rnMgtSn"], "udrtYn": j["udrtYn"],
                "buldMnnm": j["buldMnnm"], "buldSlno": j["buldSlno"], "resultType": "json"}, tries=2)
            lst = d.get("results", {}).get("juso") or []
            if len(lst) == 1:
                dong_name = lst[0].get("dongNm", "") or ""
        except Transient:
            dong_name = ""

    geo = geocode(j["roadAddr"])
    if geo is None:
        _cache_put("juso", key, None)  # 좌표를 못 구하는 주소는 지도에 못 찍으니 매물에서 제외
        return None
    info = {
        "roadAddr": j["roadAddr"], "jibunAddr": j["jibunAddr"], "bdNm": j.get("bdNm", ""), "zipNo": j["zipNo"],
        "bdMgtSn": j["bdMgtSn"], "admCd": j["admCd"], "rnMgtSn": j["rnMgtSn"], "dongNm": dong_name,
        "lat": geo[0], "lon": geo[1],
    }
    _cache_put("juso", key, info)
    return info


def resolve_address(gu: str, dong: str, jibun: str) -> dict | None:
    """(자치구, 법정동, 지번) -> 주소 정보 dict (resolve_keyword 참고). 국토부 실거래의 지번 기준 조회용."""
    return resolve_keyword(f"서울특별시 {gu} {dong} {jibun}", cache_key=f"{gu}|{dong}|{jibun}")


# ---------------------------------------------------------------- 국토부 실거래 (참고_국토부_* 컬럼)
def _num(s) -> float | None:
    try:
        return float(str(s).replace(",", "").strip())
    except ValueError:
        return None


def fetch_molit_records(gu: str, lawd: str, months: int) -> list[dict]:
    """자치구의 최근 N개월 전월세 실거래(4종)를 통합 dict 목록으로 돌려준다 (전량 수집, 중복 제거).

    dict 키: type, dong, jibun, name, houseType, deposit, rent, floor, area, buildYear, date(YYYY-MM-DD),
    contractType, contractTerm, useRR(Y/N), preDeposit, preRent. 월세(rent)=0이면 전세다.
    """
    ymds = api_collector._recent_deal_ymds(months)
    tasks = [(t, ym) for t in api_collector.PROPERTY_TYPES for ym in ymds]

    def one(task):
        t, ym = task
        for attempt in range(4):
            try:
                return t, api_collector._fetch_one(lawd, t, ym)
            except api_collector.MolitApiError as e:
                _log(f"국토부 재시도({t},{ym}): {str(e)[:100]}")
                time.sleep(2 * (attempt + 1))
        return t, []

    records, seen = [], set()
    with ThreadPoolExecutor(max_workers=6) as pool:
        for t, items in pool.map(one, tasks):
            cfg = api_collector.PROPERTY_TYPES[t]
            for it in items:
                dep, rent = _num(it.get("deposit")), _num(it.get("monthlyRent"))
                area = _num(it.get(cfg["area_field"]))
                if dep is None or rent is None or not area or area <= 0:
                    continue
                jibun = (it.get("jibun") or "").strip()
                if t != "단독다가구" and not jibun:
                    continue
                dong = (it.get("umdNm") or "").strip()
                if not dong:
                    continue
                rec = {
                    "type": t, "dong": dong, "jibun": jibun,
                    "name": (it.get(cfg["name_field"]) or "").strip() if cfg["name_field"] else "",
                    "houseType": it.get("houseType", ""), "deposit": int(dep), "rent": int(rent),
                    "floor": (it.get("floor") or "").strip(), "area": area, "buildYear": it.get("buildYear", ""),
                    "date": f"{it.get('dealYear')}-{int(it.get('dealMonth')):02d}-{int(it.get('dealDay')):02d}",
                    "contractType": it.get("contractType", ""), "contractTerm": it.get("contractTerm", ""),
                    "useRR": "Y" if it.get("useRRRight") == "사용" else "N",
                    "preDeposit": (it.get("preDeposit") or "").replace(",", ""),
                    "preRent": (it.get("preMonthlyRent") or "").replace(",", ""),
                }
                ident = (t, dong, jibun, rec["name"], rec["floor"], area, int(dep), int(rent), rec["date"])
                if ident in seen:
                    continue
                seen.add(ident)
                records.append(rec)
    _log(f"[{gu}] 국토부 실거래 수집 {months}개월: {len(records)}건 (유형별 " +
         ", ".join(f"{t} {sum(1 for r in records if r['type'] == t)}" for t in api_collector.PROPERTY_TYPES) + ")")
    return records


def reference_fields_from_record(rec: dict, info: dict | None = None) -> dict:
    """국토부 실거래 1건(+ 주소 정보)을 CSV의 "참고_*" 내부 키(ref_*) dict로 바꾼다 (listing_schema 키 기준)."""
    rent = rec["rent"]
    lease = "전세" if rent == 0 else "월세"
    fields = {
        "ref_contract_date": rec["date"], "ref_contract_type": rec["contractType"],
        "ref_contract_term": rec["contractTerm"], "ref_use_rr_right": rec["useRR"],
        "ref_pre_deposit": _to_int(rec["preDeposit"]), "ref_pre_monthly_rent": _to_int(rec["preRent"]),
        "ref_deposit": rec["deposit"], "ref_monthly_rent": rent,
        "ref_lease_kind": f"{lease} (monthlyRent={rent}, {'0이면 전세' if rent == 0 else '0보다 크면 월세'})",
        "ref_area": rec["area"], "ref_floor": rec["floor"], "ref_jibun": rec["jibun"],
    }
    if info:
        fields.update({
            "ref_bd_mgt_sn": info["bdMgtSn"], "ref_adm_cd": info["admCd"],
            "ref_rn_mgt_sn": info["rnMgtSn"], "ref_detail_dong": info["dongNm"],
        })
    return fields


def find_reference_transaction(records: list[dict], dong: str, jibun: str) -> dict | None:
    """수집한 실거래(fetch_molit_records 결과)에서 같은 법정동+지번의 가장 최근 계약 1건.
    신규 매물 등록 시 "참고 실거래"를 자동으로 붙이는 데 쓴다."""
    same = [r for r in records if r["dong"] == dong and r["jibun"] == jibun]
    return max(same, key=lambda r: r["date"]) if same else None


def _to_int(text) -> int | None:
    v = _num(text)
    return int(v) if v is not None else None
