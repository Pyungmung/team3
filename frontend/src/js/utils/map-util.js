/**
 * [담당: 양혜승] Kakao Map 연동 유틸
 * 추천 지역을 지도 위에 표시한다. 카카오 개발자 앱 키 발급 전까지는
 * 지도 대신 지역 목록 카드로 대체 렌더링(폴백)한다.
 *
 * TODO: 카카오맵 JS SDK 스크립트 태그에 실제 앱 키를 넣고 주석 해제
 *   <script src="//dapi.kakao.com/v2/maps/sdk.js?appkey=YOUR_APP_KEY"></script>
 */

const KAKAO_MAP_READY = typeof window !== "undefined" && typeof window.kakao !== "undefined";

/**
 * @param {string} containerId
 * @param {Array<{region:string, real_housing_cost:number}>} recommendations
 */
function renderRecommendedRegionsMap(containerId, recommendations) {
  const el = document.getElementById(containerId);
  if (!el) return;

  if (!KAKAO_MAP_READY) {
    // 카카오맵 SDK 미연동 상태의 폴백: 지역 카드 리스트로 표시
    el.innerHTML = `
      <div class="text-xs text-gray-400 mb-2">🗺️ 카카오맵 연동 예정 (현재는 목록으로 표시)</div>
      <ul class="grid grid-cols-2 sm:grid-cols-3 gap-2">
        ${recommendations
          .map(
            (r) => `<li class="card px-3 py-2 text-sm">
              <div class="font-semibold">${r.region}</div>
              <div class="text-gray-500">${r.real_housing_cost}만원/월</div>
            </li>`
          )
          .join("")}
      </ul>
    `;
    return;
  }

  // TODO: 카카오맵 SDK 연동 후 실제 좌표 기반 마커 렌더링 구현
  const map = new window.kakao.maps.Map(el, {
    center: new window.kakao.maps.LatLng(37.5665, 126.978),
    level: 8,
  });
  return map;
}

window.CustomHouseMapUtil = { renderRecommendedRegionsMap };
