/**
 * [담당: 양혜승] Chart.js 시각화 유틸
 * 진단 결과 리포트 페이지(report-result.html)에서 지역별 실질 주거비/절감액을 그래프로 보여준다.
 * Chart.js는 각 HTML에서 CDN 스크립트로 미리 로드되어 있어야 한다.
 */

/**
 * 추천 매물들의 실질 주거비 vs 기존(baseline) 주거비를 막대그래프로 그린다.
 * @param {string} canvasId
 * @param {Array<{building_name:string, region:string, real_housing_cost:number, baseline_cost:number}>} recommendations
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
          label: "기존 예상 주거비(만원)",
          data: recommendations.map((r) => r.baseline_cost),
          backgroundColor: "#cbd5e1", // 쿨톤 뉴트럴(slate) 그레이
        },
        {
          label: "맞집 추천 실질 주거비(만원)",
          data: recommendations.map((r) => r.real_housing_cost),
          backgroundColor: "#0f172a", // 메인 컬러(다크 네이비)
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { position: "bottom" },
        title: { display: true, text: "매물별 실질 주거비 비교" },
      },
      scales: {
        y: { beginAtZero: true, title: { display: true, text: "만원 / 월" } },
      },
    },
  });
}

window.CustomHouseChartUtil = { renderSavingsChart };
