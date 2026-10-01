/**
 * [담당: 양혜승] Kakao Map 연동 유틸
 * 추천 매물을 지도 위에 마커로 표시한다. config.js에 카카오맵 앱 키가 없으면
 * 지도 대신 지역 목록 카드로 대체 렌더링(폴백)한다.
 *
 * 2026-09-28: 매물마다 실제 좌표(lat/lon, customhouse-ai의 kakao_geocode.py가 도로명/지번주소를
 * 지오코딩)가 오게 되면서, 좌표가 있는 매물은 그 정확한 위치에 마커 1개씩 찍는다. 좌표가 없는
 * 매물(단독다가구처럼 애초에 주소 자체가 없는 경우)만 예전처럼 지역 대표 좌표(REGION_COORDS)에
 * 지역당 마커 1개로 묶어서 보여준다 - 좌표가 없는데 억지로 흩뿌리면(예전 황금각 지터 방식)
 * 실제 위치와 무관한 도넛 모양 착시가 생겼던 문제(2026-09-27)가 재발한다.
 */

// 정보창/폴백 목록 HTML에 넣는 매물명·주소·직장 주소는 이스케이프한다. 직장 주소는 사용자가 직접 입력하고,
// 앞으로 "신규 매물 생성" 탭에서 매물명/주소도 사용자가 입력하게 되므로 그대로 HTML에 넣으면 스크립트가 실행될 수 있다
// (2026-09-28 검증 중 발견).
function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

// 자치구 대표 좌표 (구청 기준 근사값) - 좌표를 못 구한 매물(단독다가구 등)의 지역 대표 마커용.
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

// 직장/학교 위치 기준점 마커 - 매물 마커(기본 파란 핀)와 확실히 구분되도록 크고 빨간 핀으로
// 그린다 (2026-09-28: "핀포인트 더 잘 보이게 해달라"는 요청). data URI라 별도 이미지 호스팅이
// 필요 없다.
const WORK_MARKER_SVG =
  '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="44" viewBox="0 0 40 44">' +
  '<path d="M20 0C9 0 0 9 0 20c0 15 20 24 20 24s20-9 20-24C40 9 31 0 20 0z" fill="#E11D48" stroke="#ffffff" stroke-width="2.5"/>' +
  '<circle cx="20" cy="19" r="7" fill="#ffffff"/>' +
  "</svg>";
const WORK_MARKER_IMAGE_SRC = "data:image/svg+xml;charset=UTF-8," + encodeURIComponent(WORK_MARKER_SVG);

// 지도 핀 <-> 우측 매물 목록 상호 이동에 쓰는 현재 지도 상태. 렌더링할 때마다 갱신된다.
// key는 정확한 좌표가 있는 매물이면 그 매물의 인덱스(숫자), 좌표가 없는 매물이면 지역명 문자열이다
// (같은 지역의 좌표 없는 매물끼리는 마커 하나를 공유하므로 markerKeyByIdx로 실제 매물 인덱스 ->
// 공유 키를 한 번 더 찾아간다).
let currentMap = null;
const markersByKey = new Map();
const infoWindowsByKey = new Map();
const markerKeyByIdx = new Map();

function renderFallbackList(el, recommendations) {
  el.innerHTML = `
    <div class="text-xs text-gray-400 mb-2">🗺️ 카카오맵 연동 예정 (현재는 목록으로 표시)</div>
    <ul class="grid grid-cols-2 sm:grid-cols-3 gap-2">
      ${recommendations
        .map(
          (r) => `<li class="card px-3 py-2 text-sm">
            <div class="font-semibold">${escapeHtml(r.building_name || r.region)}</div>
            <div class="text-gray-500">${r.real_housing_cost}만원/월</div>
          </li>`
        )
        .join("")}
    </ul>
  `;
}

function renderKakaoMap(mapEl, recommendations, onMarkerClick, workLocation) {
  const map = new window.kakao.maps.Map(mapEl, {
    center: new window.kakao.maps.LatLng(37.5665, 126.978),
    level: 8,
  });

  currentMap = map;
  markersByKey.clear();
  infoWindowsByKey.clear();
  markerKeyByIdx.clear();

  const bounds = new window.kakao.maps.LatLngBounds();
  let markerCount = 0;

  function addMarker(key, position, content, idx) {
    const marker = new window.kakao.maps.Marker({ position, map });
    bounds.extend(position);
    markerCount += 1;

    const infowindow = new window.kakao.maps.InfoWindow({
      content: `<div style="padding:6px 10px;font-size:12px;white-space:nowrap;">${content}</div>`,
    });
    window.kakao.maps.event.addListener(marker, "mouseover", () => infowindow.open(map, marker));
    window.kakao.maps.event.addListener(marker, "mouseout", () => infowindow.close());
    window.kakao.maps.event.addListener(marker, "click", () => onMarkerClick && onMarkerClick(idx));

    markersByKey.set(key, marker);
    infoWindowsByKey.set(key, infowindow);
  }

  // 0) 직장/학교 위치 - 정확한 좌표가 있으면 그 좌표, 없으면(드롭다운으로만 고른 경우) 지역
  // 대표좌표로 대체한다. 매물 마커보다 훨씬 크고 빨간 핀 이미지를 써서 한눈에 기준점임을 알 수
  // 있게 하고, 정보창도 호버 없이 처음부터 열어둔다.
  if (workLocation) {
    const coord =
      workLocation.lat != null && workLocation.lon != null
        ? [workLocation.lat, workLocation.lon]
        : REGION_COORDS[workLocation.region];
    if (coord) {
      const position = new window.kakao.maps.LatLng(coord[0], coord[1]);
      const markerImage = new window.kakao.maps.MarkerImage(
        WORK_MARKER_IMAGE_SRC,
        new window.kakao.maps.Size(40, 44),
        { offset: new window.kakao.maps.Point(20, 44) }
      );
      const workMarker = new window.kakao.maps.Marker({ position, map, image: markerImage, zIndex: 999 });
      bounds.extend(position);
      markerCount += 1;

      const workInfowindow = new window.kakao.maps.InfoWindow({
        zIndex: 999,
        content: `<div style="padding:7px 12px;font-size:12px;font-weight:700;white-space:nowrap;color:#E11D48;">🏢 직장/학교 위치<br/>${escapeHtml(workLocation.label || workLocation.region)}</div>`,
      });
      workInfowindow.open(map, workMarker); // 기준점이라 호버 없이 항상 라벨을 띄워둔다.

      markersByKey.set("work-location", workMarker);
      infoWindowsByKey.set("work-location", workInfowindow);
    }
  }

  // 1) 좌표가 있는 매물 - 매물마다 정확한 위치에 마커 1개.
  recommendations.forEach((r, idx) => {
    if (r.lat == null || r.lon == null) return;
    const position = new window.kakao.maps.LatLng(r.lat, r.lon);
    addMarker(
      idx,
      position,
      `<b>${escapeHtml(r.building_name)}</b> · ${r.real_housing_cost}만원/월<br/>${escapeHtml(r.address || r.region)}`,
      idx
    );
    markerKeyByIdx.set(idx, idx);
  });

  // 2) 좌표가 없는 매물(단독다가구 등) - 지역별로 묶어서 대표 마커 1개 + "매물 N건" 요약.
  const noCoordByRegion = new Map();
  recommendations.forEach((r, idx) => {
    if (r.lat != null && r.lon != null) return;
    if (!noCoordByRegion.has(r.region)) noCoordByRegion.set(r.region, []);
    noCoordByRegion.get(r.region).push(idx);
  });

  noCoordByRegion.forEach((idxList, region) => {
    const coord = REGION_COORDS[region];
    if (!coord) return;
    const items = idxList.map((i) => recommendations[i]);
    const cheapest = items.reduce((min, r) => (r.real_housing_cost < min.real_housing_cost ? r : min), items[0]);
    const key = `region:${region}`;
    addMarker(
      key,
      new window.kakao.maps.LatLng(coord[0], coord[1]),
      `<b>${region}</b> · 매물 ${items.length}건 (정확한 주소 미확인)<br/>최저 ${cheapest.real_housing_cost}만원/월 · ${escapeHtml(cheapest.building_name)}`,
      idxList[0]
    );
    idxList.forEach((i) => markerKeyByIdx.set(i, key));
  });

  if (markerCount > 0) {
    map.setBounds(bounds);
  }
}

/**
 * @param {string} containerId
 * @param {Array<{region:string, real_housing_cost:number, lat?:number|null, lon?:number|null}>} recommendations
 * @param {(idx: number) => void} [onMarkerClick] 지도 핀을 클릭했을 때 호출 (우측 목록의 해당 인덱스 카드로 이동용)
 * @param {{lat?:number|null, lon?:number|null, region:string, label?:string}} [workLocation] 직장/학교 위치 기준점 핀
 */
function renderRecommendedRegionsMap(containerId, recommendations, onMarkerClick, workLocation) {
  const el = document.getElementById(containerId);
  if (!el) return;

  const kakaoKey = window.CUSTOMHOUSE_CONFIG && window.CUSTOMHOUSE_CONFIG.KAKAO_MAP_APP_KEY;
  if (!kakaoKey) {
    renderFallbackList(el, recommendations);
    return;
  }

  // 지도를 담는 #map-container가 카드 높이를 100%로 채우도록 되어 있으므로(report-listings.html의
  // .report-map-card 참고), 이 안쪽 div도 고정 높이 대신 100%로 채워야 실제로 꽉 차 보인다.
  const mapElId = `${containerId}-kakao-map`;
  el.innerHTML = `<div id="${mapElId}" style="width:100%;height:100%;border-radius:12px;"></div>`;
  const mapEl = document.getElementById(mapElId);

  // report-listings.html의 <head>에서 SDK를 autoload=false로 비동기 삽입하므로,
  // window.kakao가 아직 없을 수 있어 준비될 때까지 짧게 폴링한다.
  (function waitForKakaoSdk(retriesLeft) {
    if (window.kakao && window.kakao.maps) {
      window.kakao.maps.load(() => renderKakaoMap(mapEl, recommendations, onMarkerClick, workLocation));
    } else if (retriesLeft > 0) {
      setTimeout(() => waitForKakaoSdk(retriesLeft - 1), 100);
    } else {
      renderFallbackList(el, recommendations);
    }
  })(50); // 최대 5초 대기
}

/** 우측 매물 목록에서 건물명을 클릭했을 때, 그 매물의 마커(좌표가 없으면 지역 대표 마커)로
 * 지도를 이동시키고 정보창을 띄운다. */
function focusBuilding(idx) {
  const map = currentMap;
  const key = markerKeyByIdx.get(idx);
  if (key === undefined) return;

  const marker = markersByKey.get(key);
  const infowindow = infoWindowsByKey.get(key);
  if (!map || !marker) return;

  map.panTo(marker.getPosition());
  infoWindowsByKey.forEach((iw) => iw.close());
  if (infowindow) infowindow.open(map, marker);
}

// REGION_COORDS는 새 리포트의 클러스터 지도(map-cluster-util.js)가 직장 위치 대표좌표로 같이 쓴다.
window.CustomHouseMapUtil = { renderRecommendedRegionsMap, focusBuilding, REGION_COORDS };
