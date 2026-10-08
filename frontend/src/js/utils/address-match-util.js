/**
 * [담당: 양혜승] 주소 -> 가장 가까운 직장 권역(work hub) 매칭 유틸
 * input-form.html에서 카카오 주소검색으로 받은 좌표를, customhouse-ai/app/data/work_locations.json의
 * work_hubs(서울 25개 자치구 전체 + 분당구) 중 직선거리가 가장 가까운 곳으로 매칭한다.
 *
 * 실제 도로/대중교통 통근시간 API(카카오모빌리티 길찾기 등)는 아직 미연동이라 직선거리 근사치를 쓴다.
 * 이 매칭 결과(work hub 이름)를 그대로 기존 workLocation 값으로 넘기면 백엔드/AI 엔진은 변경할 필요가
 * 없다 (AI 엔진도 같은 방식으로 직선거리 기반 통근시간을 추정한다 - calculator.py 참고).
 */

// 직장 권역 대표 좌표(구청 기준 근사값). customhouse-ai/app/data/work_locations.json과 동일하게 유지.
const WORK_HUB_COORDS = {
  강남구: [37.5172, 127.0473],
  강동구: [37.5301, 127.1238],
  강북구: [37.6396, 127.0257],
  강서구: [37.5509, 126.8495],
  관악구: [37.4784, 126.9516],
  광진구: [37.5384, 127.0822],
  구로구: [37.4954, 126.8874],
  금천구: [37.4569, 126.8956],
  노원구: [37.6542, 127.0568],
  도봉구: [37.6688, 127.0471],
  동대문구: [37.5744, 127.0396],
  동작구: [37.5124, 126.9393],
  마포구: [37.5663, 126.9014],
  서대문구: [37.5791, 126.9368],
  서초구: [37.4837, 127.0324],
  성동구: [37.5634, 127.0371],
  성북구: [37.5894, 127.0167],
  송파구: [37.5145, 127.1059],
  양천구: [37.5170, 126.8664],
  영등포구: [37.5264, 126.8962],
  용산구: [37.5326, 126.9903],
  은평구: [37.6027, 126.9291],
  종로구: [37.5735, 126.9788],
  중구: [37.5641, 126.9979],
  중랑구: [37.6063, 127.0925],
  분당구: [37.3826, 127.1189],
};

const WORK_HUB_LABELS = {
  구로구: "구로구(가산디지털단지)",
  성동구: "성동구(성수)",
  영등포구: "영등포구(여의도)",
  분당구: "분당구(판교)",
};

function labelForHub(hub) {
  return WORK_HUB_LABELS[hub] || hub;
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371; // 지구 반지름(km)
  const toRad = (deg) => (deg * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/**
 * 주소 글자에서 실제 자치구를 찾는다 (2026-10-06). "서울 서초구 동작대로 132" -> "서초구", "경기 성남시 분당구 판교역로 1" -> "분당구".
 * 직선거리로 "가장 가까운 구청"을 고르면 서초구처럼 구청이 한쪽 끝에 있는 큰 구의 주소가 이웃한 구(관악구)로 분류돼서
 * 주거정책 추천이 엉뚱한 구로 나왔다 - 주소에 구 이름이 있으면 그걸 우선한다. 못 찾으면 null(호출하는 쪽이 가까운 구로 대신한다).
 * @param {string} address
 * @returns {string|null}
 */
function hubFromAddress(address) {
  if (!address) return null;
  // 먼저 공백으로 나눈 낱말이 구 이름과 정확히 같은 것을 찾는다 ("서울 서초구 서초중앙로 5" -> "서초구"; 도로명 속 글자에 우연히 들어 있는 경우는 피한다)
  for (const token of String(address).split(/\s+/)) {
    if (Object.prototype.hasOwnProperty.call(WORK_HUB_COORDS, token)) return token;
  }
  let best = null;
  for (const hub of Object.keys(WORK_HUB_COORDS)) {
    const idx = address.indexOf(hub);
    if (idx >= 0 && (best === null || idx < best.idx)) best = { hub, idx };
  }
  return best ? best.hub : null;
}

/**
 * @param {number} lat
 * @param {number} lon
 * @returns {{hub: string, label: string, distanceKm: number}}
 */
function findNearestWorkHub(lat, lon) {
  let best = null;
  for (const [hub, coord] of Object.entries(WORK_HUB_COORDS)) {
    const distanceKm = haversineKm(lat, lon, coord[0], coord[1]);
    if (!best || distanceKm < best.distanceKm) {
      best = { hub, label: labelForHub(hub), distanceKm };
    }
  }
  return best;
}

/**
 * 저장된 조건의 직장 구(workLocation)를 직장 주소(workAddress)의 구 이름에 맞춘다 (2026-10-08).
 * 2026-10-06 이전에 저장된 조건은 "가장 가까운 구청" 기준이라 서초구 주소가 관악구로 남아 있다 - 홈 미리보기/광고 노출/정책 추천이
 * 엉뚱한 구 기준으로 동작하는 원인이라, 조건을 읽는 곳마다 주소의 구를 우선해 바로잡는다. 바꿀 게 없으면 같은 객체를 돌려준다.
 * @param {object|null} condition
 * @returns {object|null}
 */
function normalizeWorkLocation(condition) {
  if (!condition || !condition.workAddress) return condition;
  const hub = hubFromAddress(condition.workAddress);
  return hub && hub !== condition.workLocation ? { ...condition, workLocation: hub } : condition;
}

window.CustomHouseAddressMatchUtil = { findNearestWorkHub, hubFromAddress, normalizeWorkLocation, WORK_HUB_LABELS, labelForHub };
