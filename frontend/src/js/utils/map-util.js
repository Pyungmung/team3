/**
 * [담당: 양혜승] Kakao Map 연동 유틸
 * 추천 지역을 지도 위에 마커로 표시한다. config.js에 카카오맵 앱 키가 없으면
 * 지도 대신 지역 목록 카드로 대체 렌더링(폴백)한다.
 */

// 자치구 대표 좌표 (구청 기준 근사값). regions.json에 좌표 데이터가 없어 여기서 관리한다.
const REGION_COORDS = {
  관악구: [37.4784, 126.9516],
  동작구: [37.5124, 126.9393],
  마포구: [37.5663, 126.9014],
  은평구: [37.6027, 126.9291],
  서대문구: [37.5791, 126.9368],
  성북구: [37.5894, 127.0167],
  노원구: [37.6542, 127.0568],
  강북구: [37.6396, 127.0257],
  동대문구: [37.5744, 127.0396],
  광진구: [37.5384, 127.0822],
  강동구: [37.5301, 127.1238],
  송파구: [37.5145, 127.1059],
  금천구: [37.4569, 126.8956],
  강서구: [37.5509, 126.8495],
  양천구: [37.517, 126.8664],
  강남구: [37.5172, 127.0473],
  서초구: [37.4837, 127.0324],
  용산구: [37.5326, 126.9903],
  성동구: [37.5634, 127.0371],
  중랑구: [37.6063, 127.0925],
  도봉구: [37.6688, 127.0471],
  구로구: [37.4954, 126.8874],
  영등포구: [37.5264, 126.8962],
  종로구: [37.5735, 126.9788],
  중구: [37.5641, 126.9979],
  "경기 부천시": [37.5035, 126.766],
  "경기 광명시": [37.4787, 126.8646],
  "경기 안양시": [37.3943, 126.9568],
};

function renderFallbackList(el, recommendations) {
  el.innerHTML = `
    <div class="text-xs text-gray-400 mb-2">🗺️ 카카오맵 연동 예정 (현재는 목록으로 표시)</div>
    <ul class="grid grid-cols-2 sm:grid-cols-3 gap-2">
      ${recommendations
        .map(
          (r) => `<li class="card px-3 py-2 text-sm">
            <div class="font-semibold">${r.building_name || r.region}</div>
            <div class="text-gray-500">${r.real_housing_cost}만원/월</div>
          </li>`
        )
        .join("")}
    </ul>
  `;
}

function renderKakaoMap(mapEl, recommendations) {
  const map = new window.kakao.maps.Map(mapEl, {
    center: new window.kakao.maps.LatLng(37.5665, 126.978),
    level: 8,
  });

  const bounds = new window.kakao.maps.LatLngBounds();
  let markerCount = 0;
  const regionSeenCount = {};

  recommendations.forEach((r, idx) => {
    const coord = REGION_COORDS[r.region];
    if (!coord) return;

    // 같은 지역(구)에 매물이 여러 개면 정확히 같은 좌표에 마커가 겹치므로
    // 지역별로 몇 번째인지 세서 살짝 원형으로 퍼뜨린다 (구청 좌표는 실제 건물 위치가 아닌 근사값이라 상관없음).
    const seenIndex = regionSeenCount[r.region] || 0;
    regionSeenCount[r.region] = seenIndex + 1;
    const jitterAngle = (seenIndex * 137.5 * Math.PI) / 180; // 황금각으로 겹침 최소화
    const jitterRadius = seenIndex === 0 ? 0 : 0.004;
    const lat = coord[0] + jitterRadius * Math.cos(jitterAngle);
    const lng = coord[1] + jitterRadius * Math.sin(jitterAngle);

    const position = new window.kakao.maps.LatLng(lat, lng);
    const marker = new window.kakao.maps.Marker({ position, map });
    bounds.extend(position);
    markerCount += 1;

    const infowindow = new window.kakao.maps.InfoWindow({
      content: `<div style="padding:6px 10px;font-size:12px;white-space:nowrap;">
        <b>#${idx + 1} ${r.building_name || r.region}</b><br/>${r.real_housing_cost}만원/월
      </div>`,
    });
    window.kakao.maps.event.addListener(marker, "mouseover", () => infowindow.open(map, marker));
    window.kakao.maps.event.addListener(marker, "mouseout", () => infowindow.close());
  });

  if (markerCount > 0) {
    map.setBounds(bounds);
  }
}

/**
 * @param {string} containerId
 * @param {Array<{region:string, real_housing_cost:number}>} recommendations
 */
function renderRecommendedRegionsMap(containerId, recommendations) {
  const el = document.getElementById(containerId);
  if (!el) return;

  const kakaoKey = window.CUSTOMHOUSE_CONFIG && window.CUSTOMHOUSE_CONFIG.KAKAO_MAP_APP_KEY;
  if (!kakaoKey) {
    renderFallbackList(el, recommendations);
    return;
  }

  const mapElId = `${containerId}-kakao-map`;
  el.innerHTML = `<div id="${mapElId}" style="width:100%;height:320px;border-radius:12px;"></div>`;
  const mapEl = document.getElementById(mapElId);

  // report-result.html의 <head>에서 SDK를 autoload=false로 비동기 삽입하므로,
  // window.kakao가 아직 없을 수 있어 준비될 때까지 짧게 폴링한다.
  (function waitForKakaoSdk(retriesLeft) {
    if (window.kakao && window.kakao.maps) {
      window.kakao.maps.load(() => renderKakaoMap(mapEl, recommendations));
    } else if (retriesLeft > 0) {
      setTimeout(() => waitForKakaoSdk(retriesLeft - 1), 100);
    } else {
      renderFallbackList(el, recommendations);
    }
  })(50); // 최대 5초 대기
}

window.CustomHouseMapUtil = { renderRecommendedRegionsMap };
