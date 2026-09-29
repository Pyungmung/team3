"""
[담당: 송귀성] RIR(소득 대비 주택임대료 비율) 산출(listing_recommender._resolve_rir) 테스트.
우선순위: 1) 관리자 수정 > 기준소득관리에 저장된 값(request.income_standard) 2) docs/RIR.csv(rir_stats, 레거시 폴백)
3) 둘 다 없으면 DEFAULT_RIR_PERCENT(20%). 2026-09-30: docs/RIR.csv는 이제 관리자 화면이 대체해서 저장소에 없을 수도
있으므로(실제로 없어도 정상 - 1)이 항상 채워지는 게 정상 운영이다), 2)번 경로는 실제 파일이 아니라 임시 CSV로
settings.rir_csv_file을 잠깐 바꿔서 검증한다(파일 존재 여부와 무관하게 항상 실행됨).
실행 (customhouse-ai 폴더에서):  python tests/test_listing_recommender_rir.py
"""
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.config import settings  # noqa: E402
from app.models.request_schema import DiagnosisRequest  # noqa: E402
from app.services import listing_recommender, rir_stats  # noqa: E402

INCOME = 300  # 월소득(만원) 예시

_FIXTURE_CSV = """\
통계표명:,지역 및 소득수준별 소득 대비 주택임대료 비율(RIR),
단위:,%,
,,2099
전체,,15.8
지역별 5),수도권,18.4
소득수준별 6),하위(1-4분위),18.3
,중위(5-8분위),16.2
,상위(9-10분위),19.4
출처:,"국토교통부,「주거실태조사」",
"""


class _fixture_rir_csv:
    """docs/RIR.csv 실존 여부와 무관하게 rir_stats가 이 임시 CSV를 읽도록 settings를 잠깐 바꾼다."""

    def __enter__(self):
        self._original = settings.rir_csv_file
        tmp = tempfile.NamedTemporaryFile(mode="w", suffix=".csv", delete=False, encoding="utf-8")
        tmp.write(_FIXTURE_CSV)
        tmp.close()
        settings.rir_csv_file = Path(tmp.name)
        rir_stats._cache = None  # 이전 결과(다른 경로 기준) 캐시를 지워 새 경로를 바로 읽게 한다
        return self

    def __exit__(self, *exc):
        Path(settings.rir_csv_file).unlink(missing_ok=True)
        settings.rir_csv_file = self._original
        rir_stats._cache = None


def req(income_standard=None):
    base = {"annualIncome": INCOME * 12, "deposit": 800, "workLocation": "강남구"}
    if income_standard is not None:
        base["incomeStandard"] = income_standard
    return DiagnosisRequest(**base)


def test_기준소득관리에_수도권_값이_있으면_그_값을_최우선으로_쓴다():
    r = req({
        "rirOverallPercent": 15.8, "rirMetroPercent": 20.0, "rirLowPercent": 18.0,
        "rirMidPercent": 16.0, "rirHighPercent": 22.0, "rirYear": 2099, "rirSource": "테스트 출처",
    })
    rir, affordable_rent, fields = listing_recommender._resolve_rir(r, INCOME)

    assert rir == 20.0
    assert affordable_rent == round(INCOME * 20.0 / 100, 1)
    assert fields["rir_year"] == 2099
    assert fields["rir_source"] == "테스트 출처"
    assert fields["rir_overall_percent"] == 15.8
    assert fields["rir_overall_affordable_rent"] == round(INCOME * 15.8 / 100, 1)
    labels = [lv["label"] for lv in fields["rir_by_income"]]
    assert labels == ["하위(1-4분위)", "중위(5-8분위)", "상위(9-10분위)"]


def test_소득수준별_값이_일부만_있으면_있는_것만_내려온다():
    r = req({"rirMetroPercent": 20.0, "rirMidPercent": 16.0})
    _, _, fields = listing_recommender._resolve_rir(r, INCOME)
    assert [lv["key"] for lv in fields["rir_by_income"]] == ["mid"]


def test_전국_값이_없으면_전국_적정월세도_None이다():
    r = req({"rirMetroPercent": 20.0})
    _, _, fields = listing_recommender._resolve_rir(r, INCOME)
    assert fields["rir_overall_percent"] is None
    assert fields["rir_overall_affordable_rent"] is None


def test_기준소득관리_값이_없으면_RIR_csv로_폴백한다():
    with _fixture_rir_csv():
        r = req(income_standard=None)
        rir_data = rir_stats.get_rir_stats()
        assert rir_data is not None

        rir, affordable_rent, fields = listing_recommender._resolve_rir(r, INCOME)

        assert rir == rir_data.metro_percent
        assert affordable_rent == round(INCOME * rir_data.metro_percent / 100, 1)
        assert fields["rir_year"] == rir_data.year


def test_수도권_값이_비어있는_기준소득관리_객체도_csv로_폴백한다():
    # income_standard는 있지만(예: RIR은 비우고 중위소득만 저장) 수도권 값이 없으면 여전히 CSV로 폴백한다
    with _fixture_rir_csv():
        r = req({"medianIncome100PercentMonthly": 2_000_000})
        rir_data = rir_stats.get_rir_stats()
        rir, _, _ = listing_recommender._resolve_rir(r, INCOME)
        assert rir == rir_data.metro_percent


def test_기준소득관리_값도_RIR_csv도_없으면_기본값_20퍼센트다():
    original = settings.rir_csv_file
    settings.rir_csv_file = Path(tempfile.gettempdir()) / "no-such-rir-file.csv"
    rir_stats._cache = None
    try:
        r = req(income_standard=None)
        rir, affordable_rent, fields = listing_recommender._resolve_rir(r, INCOME)
        assert rir == rir_stats.DEFAULT_RIR_PERCENT
        assert affordable_rent == round(INCOME * rir_stats.DEFAULT_RIR_PERCENT / 100, 1)
        assert fields == {}
    finally:
        settings.rir_csv_file = original
        rir_stats._cache = None


if __name__ == "__main__":
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("PASS", name)
            except AssertionError:
                failed += 1
                print("FAIL", name)
                raise
    print("failures:", failed)
    sys.exit(1 if failed else 0)
