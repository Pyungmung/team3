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

window.CustomHouseAddressMatchUtil = { findNearestWorkHub, WORK_HUB_LABELS, labelForHub };
