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
    """실질 주거비(real_housing_cost) 오름차순으로 정렬해 상위 N개 지역을 반환한다."""
    if not results:
        return []

    if _PANDAS_AVAILABLE:
        df = pd.DataFrame(results)
        df = df.sort_values(by="real_housing_cost", ascending=True).head(top_n)
        return df.to_dict(orient="records")

    # pandas 사용 불가 환경을 위한 순수 Python 폴백 (결과는 동일)
    return sorted(results, key=lambda r: r["real_housing_cost"])[:top_n]
