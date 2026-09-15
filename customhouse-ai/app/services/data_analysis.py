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


def rank_by_real_cost(results: list[dict], top_n: int = 5) -> list[dict]:
    """실질 주거비(real_housing_cost)가 기존 예상 주거비(baseline_cost)보다 낮은 지역만 골라
    실질 주거비 오름차순으로 정렬해 상위 N개를 반환한다.

    맞집은 "더 저렴한 곳을 추천"하는 앱이라, 보증금 전환/정책 지원 혜택이 전혀 없어
    절감액(monthly_savings)이 0 이하인 지역은 애초에 추천 목록에 넣지 않는다
    (그래프의 "맞집 추천 실질 주거비" 막대가 "기존 예상 주거비" 막대보다 항상 낮아야 한다).

    실질 주거비가 0(정책 지원금이 비용을 다 상쇄)인 매물이 여러 개면 동점이 되는데, 동점자
    사이에 2차 기준이 없으면 국토부 API가 응답한 순서(사실상 임의 순서)에 따라 반전세형
    매물(월세가 몇만원뿐인 특이 케이스)이 우연히 상위에 몰리는 문제가 있었다. 그래서 동점일
    때는 절감액(monthly_savings)이 큰 매물을 우선한다 - "최적화 전에는 더 비쌌던 곳을 우리가
    더 크게 절약해준" 매물을 보여주는 게 사용자에게 더 설득력 있다.
    """
    if not results:
        return []

    savings_results = [r for r in results if r["monthly_savings"] > 0]
    if not savings_results:
        return []

    if _PANDAS_AVAILABLE:
        df = pd.DataFrame(savings_results)
        df = df.sort_values(by=["real_housing_cost", "monthly_savings"], ascending=[True, False]).head(top_n)
        return df.to_dict(orient="records")

    # pandas 사용 불가 환경을 위한 순수 Python 폴백 (결과는 동일)
    return sorted(savings_results, key=lambda r: (r["real_housing_cost"], -r["monthly_savings"]))[:top_n]
