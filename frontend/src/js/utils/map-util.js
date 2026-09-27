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
};

// 지도 핀 <-> 우측 매물 목록 상호 이동에 쓰는 현재 지도 상태. 렌더링할 때마다 갱신된다.
let currentMap = null;
const markersByRegion = new Map();
const infoWindowsByRegion = new Map();

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

function renderKakaoMap(mapEl, recommendations, onMarkerClick) {
  const map = new window.kakao.maps.Map(mapEl, {
    center: new window.kakao.maps.LatLng(37.5665, 126.978),
    level: 8,
  });

  currentMap = map;
  markersByRegion.clear();
  infoWindowsByRegion.clear();

  // 국토부 실거래가 데이터에는 매물 개별 좌표가 없고 "구" 단위 대표좌표(REGION_COORDS)만 있다.
  // 예전엔 매물 개수만큼 같은 좌표 주변에 황금각+고정 반지름으로 점을 흩뿌렸는데, 반지름이
  // 전부 같다 보니 매물이 많은 지역마다 마커가 도넛(원형 고리) 모양으로 뭉쳐 보이는 문제가 있었다
  // (2026-09-27 발견 - 실제 위치와 무관한 착시라 "직장 위치 기준 통근권과 안 맞는다"는 오해를 줌).
  // 없는 정밀도를 억지로 흉내내는 대신, 지역당 마커 1개 + "매물 N건, 최저 OO만원" 요약으로 바꾼다.
  const bounds = new window.kakao.maps.LatLngBounds();
  const byRegion = new Map();
  recommendations.forEach((r) => {
    if (!byRegion.has(r.region)) byRegion.set(r.region, []);
    byRegion.get(r.region).push(r);
  });

  let markerCount = 0;

  byRegion.forEach((items, region) => {
    const coord = REGION_COORDS[region];
    if (!coord) return;

    const position = new window.kakao.maps.LatLng(coord[0], coord[1]);
    const marker = new window.kakao.maps.Marker({ position, map });
    bounds.extend(position);
    markerCount += 1;

    const cheapest = items.reduce((min, r) => (r.real_housing_cost < min.real_housing_cost ? r : min), items[0]);
    const infowindow = new window.kakao.maps.InfoWindow({
      content: `<div style="padding:6px 10px;font-size:12px;white-space:nowrap;">
        <b>${region}</b> · 매물 ${items.length}건<br/>최저 ${cheapest.real_housing_cost}만원/월 · ${cheapest.building_name}
      </div>`,
    });
    window.kakao.maps.event.addListener(marker, "mouseover", () => infowindow.open(map, marker));
    window.kakao.maps.event.addListener(marker, "mouseout", () => infowindow.close());
    // 핀 클릭 -> 우측 매물 목록에서 이 지역의 첫 카드로 스크롤 이동(호출부에서 처리).
    window.kakao.maps.event.addListener(marker, "click", () => onMarkerClick && onMarkerClick(region));

    markersByRegion.set(region, marker);
    infoWindowsByRegion.set(region, infowindow);
  });

  if (markerCount > 0) {
    map.setBounds(bounds);
  }
}

/**
 * @param {string} containerId
 * @param {Array<{region:string, real_housing_cost:number}>} recommendations
 * @param {(region: string) => void} [onMarkerClick] 지도 핀을 클릭했을 때 호출 (우측 목록 이동용)
 */
function renderRecommendedRegionsMap(containerId, recommendations, onMarkerClick) {
  const el = document.getElementById(containerId);
  if (!el) return;

  const kakaoKey = window.CUSTOMHOUSE_CONFIG && window.CUSTOMHOUSE_CONFIG.KAKAO_MAP_APP_KEY;
  if (!kakaoKey) {
    renderFallbackList(el, recommendations);
    return;
  }

  // 지도를 담는 #map-container가 카드 높이를 100%로 채우도록 되어 있으므로(report-result.html의
  // .report-map-card 참고), 이 안쪽 div도 고정 높이 대신 100%로 채워야 실제로 꽉 차 보인다.
  const mapElId = `${containerId}-kakao-map`;
  el.innerHTML = `<div id="${mapElId}" style="width:100%;height:100%;border-radius:12px;"></div>`;
  const mapEl = document.getElementById(mapElId);

  // report-result.html의 <head>에서 SDK를 autoload=false로 비동기 삽입하므로,
  // window.kakao가 아직 없을 수 있어 준비될 때까지 짧게 폴링한다.
  (function waitForKakaoSdk(retriesLeft) {
    if (window.kakao && window.kakao.maps) {
      window.kakao.maps.load(() => renderKakaoMap(mapEl, recommendations, onMarkerClick));
    } else if (retriesLeft > 0) {
      setTimeout(() => waitForKakaoSdk(retriesLeft - 1), 100);
    } else {
      renderFallbackList(el, recommendations);
    }
  })(50); // 최대 5초 대기
}

/** 우측 매물 목록에서 건물명을 클릭했을 때, 그 매물이 속한 지역 핀으로 지도를 이동시키고 정보창을 띄운다. */
function focusRegion(region) {
  const map = currentMap;
  const marker = markersByRegion.get(region);
  const infowindow = infoWindowsByRegion.get(region);
  if (!map || !marker) return;

  map.panTo(marker.getPosition());
  infoWindowsByRegion.forEach((iw) => iw.close());
  if (infowindow) infowindow.open(map, marker);
}

window.CustomHouseMapUtil = { renderRecommendedRegionsMap, focusRegion };
