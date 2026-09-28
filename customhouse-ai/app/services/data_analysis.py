"""
[담당: 송귀성] Pandas 기반 비교 분석 및 시세 매칭
기획서의 "다중 API 연결 및 데이터 비교 분석기능 실현 (pandas 라이브러리 활용)"에 해당.
현재는 calculator.py가 계산한 지역별 결과 리스트를 정렬/선별하는 데 사용하고,
추후 api_collector.py로 여러 공공 API 결과를 pandas DataFrame으로 병합·비교하는 단계로 확장한다.

주의: 일부 Windows 환경(애플리케이션 제어 정책으로 numpy 네이티브 DLL이 차단되는 사내망/보안PC 등)에서는
pandas import 자체가 실패할 수 있다. 데모/실습 환경에서도 기능이 끊기지 않도록
pandas 로드 실패 시 순수 Python 정렬로 자동 폴백한다.
"""
try:
    import pandas as pd

    _PANDAS_AVAILABLE = True
except ImportError:  # pragma: no cover - 환경에 따라 numpy/pandas 네이티브 로드가 막힐 수 있음
    _PANDAS_AVAILABLE = False


def _proportional_allocate(region_sizes: dict[str, int], budget: int) -> dict[str, int]:
    """지역별 원본 개수(region_sizes)에 비례해서 budget을 배분한다 (각 지역이 가진 개수를 넘지
    않는다). 최대잔여법(largest remainder method)으로 배분한다 - 몫만큼 내림으로 나눠주고,
    남는 슬롯은 나머지(소수부)가 큰 지역부터 1개씩 채운다. 한 라운드에서 자기 몫을 다 채운
    지역이 생기면(더 줄 게 없는 지역), 그 지역이 못 받은 잔여분을 남은 지역들에 다시 비례
    배분하는 걸 반복해서, budget이 남는데도 특정 지역만 상한에 막혀 손해보는 일이 없게 한다."""
    allocation = {region: 0 for region in region_sizes}
    remaining_sizes = dict(region_sizes)
    remaining_budget = budget

    while remaining_budget > 0 and remaining_sizes:
        total_remaining = sum(remaining_sizes.values())
        if total_remaining <= remaining_budget:
            for region, size in remaining_sizes.items():
                allocation[region] += size
            remaining_budget -= total_remaining
            break

        shares = {region: remaining_budget * size / total_remaining for region, size in remaining_sizes.items()}
        floor_shares = {region: min(remaining_sizes[region], int(share)) for region, share in shares.items()}
        leftover = remaining_budget - sum(floor_shares.values())

        # 나머지(소수부)가 큰 지역부터 1개씩 우선 배분해서 leftover를 다 쓴다.
        by_remainder_desc = sorted(remaining_sizes, key=lambda r: shares[r] - floor_shares[r], reverse=True)
        for region in by_remainder_desc:
            if leftover <= 0:
                break
            if floor_shares[region] < remaining_sizes[region]:
                floor_shares[region] += 1
                leftover -= 1

        round_total = sum(floor_shares.values())
        for region, given in floor_shares.items():
            allocation[region] += given
        remaining_budget -= round_total
        remaining_sizes = {r: n - floor_shares[r] for r, n in remaining_sizes.items() if n - floor_shares[r] > 0}

        if round_total == 0:
            break  # 안전장치 - 더 배분할 게 없으면 중단 (이론상 도달 안 함)

    return allocation


def rank_by_real_cost(results: list[dict], top_n: int | None = 5, require_savings: bool = True) -> list[dict]:
    """실질 주거비(real_housing_cost) 오름차순으로 정렬해 상위 N개를 반환한다. top_n이 None이면
    자르지 않고 전부(최대치) 반환한다.

    require_savings=True면 "기존 예상 주거비(baseline_cost)보다 실제로 더 싼" 매물만 남긴다
    (절감액 monthly_savings > 0). 2026-09-28: 월세는 보증금 조정(전월세전환율/대출금리 가정)
    계산 자체를 없애서 baseline_cost가 항상 real_housing_cost와 같아지고 monthly_savings가
    항상 0이 되므로, 월세 호출 시엔 require_savings=False로 이 필터를 건너뛰고 그냥 실거래
    기준 저렴한 순으로만 보여준다(calculator.py 참고). 전세도 비교할 별도 기준이 없어 절감액이
    항상 0이라, 이 필터를 걸면 추천이 전부 사라지는 버그가 있었다(2026-09-28) - 전세 호출도
    require_savings=False로 쓴다.

    실질 주거비가 0(정책 지원금이 비용을 다 상쇄)인 매물이 여러 개면 동점이 되는데, 동점자
    사이에 2차 기준이 없으면 국토부 API가 응답한 순서(사실상 임의 순서)에 따라 반전세형
    매물(월세가 몇만원뿐인 특이 케이스)이 우연히 상위에 몰리는 문제가 있었다. 그래서 동점일
    때는 절감액(monthly_savings)이 큰 매물을 우선한다 - "최적화 전에는 더 비쌌던 곳을 우리가
    더 크게 절약해준" 매물을 보여주는 게 사용자에게 더 설득력 있다.

    지역별 배분(2026-09-28 재설계): 후보가 top_n을 넘으면, 그냥 전체를 "실질 주거비 오름차순"
    으로 한 번에 잘랐었는데, 그러면 시세가 낮은 지역(예: 강북구·도봉구)의 매물이 절대금액이
    작다는 이유만으로 상위권을 독식해서, 시세가 비교적 높은 지역(직장 근처인 경우가 많음, 예:
    광진구·중구)은 후보가 수백 건 있어도 최종 목록엔 거의 안 남는 문제가 있었다 (예전엔 지역당
    고정 상한(per_region_limit)으로 이걸 막았는데, 사용자가 "조건에 맞는 매물은 전부 보여달라"고
    해서 상한을 계속 올렸더니 이 문제가 그대로 재발했다). 그래서 고정 상한 대신, 지역마다 "그
    지역이 가진 후보 수에 비례해서" top_n을 나눠 배분한다(_proportional_allocate, 최대잔여법) -
    매물이 많은 지역은 그만큼 더 많이, 적은 지역도 자기 몫만큼은 보장받는다. 배분된 몫 안에서는
    각 지역 안에서 가장 저렴한 것부터 채운다.
    """
    if not results:
        return []

    savings_results = [r for r in results if r["monthly_savings"] > 0] if require_savings else list(results)
    if not savings_results:
        return []

    if top_n is not None and len(savings_results) > top_n:
        by_region: dict[str, list[dict]] = {}
        for r in savings_results:
            by_region.setdefault(r["region"], []).append(r)
        for region_results in by_region.values():
            region_results.sort(key=lambda r: (r["real_housing_cost"], -r["monthly_savings"]))

        allocation = _proportional_allocate({region: len(items) for region, items in by_region.items()}, top_n)
        savings_results = [item for region, items in by_region.items() for item in items[: allocation[region]]]

    if _PANDAS_AVAILABLE:
        df = pd.DataFrame(savings_results)
        df = df.sort_values(by=["real_housing_cost", "monthly_savings"], ascending=[True, False])
        return df.to_dict(orient="records")

    return sorted(savings_results, key=lambda r: (r["real_housing_cost"], -r["monthly_savings"]))
