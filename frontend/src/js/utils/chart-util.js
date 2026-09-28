/**
 * [담당: 양혜승] Chart.js 시각화 유틸
 * 진단 결과 리포트 페이지(report-result.html)에서 지역별 실질 주거비/절감액을 그래프로 보여준다.
 * Chart.js는 각 HTML에서 CDN 스크립트로 미리 로드되어 있어야 한다.
 */

/**
 * 월세 매물의 실거래 월세를 막대그래프로 그린다.
 * 2026-09-28: 예전엔 "기존 예상 주거비 vs 맞집 추천 실질 주거비"를 두 막대로 비교했는데, 월세는
 * 보증금 조정(전월세전환율) 계산을 없애서 baseline_cost가 항상 real_housing_cost와 같아져
 * 두 막대가 완전히 겹치는 의미 없는 비교가 됐다. 그래서 "실거래 월세(원본)" 막대 하나로 단순화
 * 했다 - 실제 거래 월세를 있는 그대로 비교해서 보여주는 게 가장 중요하다는 요청에 맞춘다.
 * @param {string} canvasId
 * @param {Array<{building_name:string, region:string, listing_monthly_rent:number}>} recommendations
 */
function renderSavingsChart(canvasId, recommendations) {
  const ctx = document.getElementById(canvasId);
  if (!ctx || typeof Chart === "undefined") return;

  new Chart(ctx, {
    type: "bar",
    data: {
      labels: recommendations.map((r) => r.building_name || r.region),
      datasets: [
        {
          label: "실거래 월세(만원, 원본)",
          data: recommendations.map((r) => r.listing_monthly_rent),
          backgroundColor: "#0f172a", // 메인 컬러(다크 네이비)
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: "bottom" },
        title: { display: true, text: "매물별 실거래 월세 비교" },
      },
      scales: {
        y: { beginAtZero: true, title: { display: true, text: "만원 / 월" } },
      },
    },
  });
}

window.CustomHouseChartUtil = { renderSavingsChart };
