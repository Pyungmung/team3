/**
 * [담당: 양혜승] 매물 수 클러스터 지도 (직방식) - 추천 매물 리포트(report-listings.html) 전용
 *
 * 핀 대신 "그 지점에 묶인 매물 수"가 적힌 원(브랜드 네이비보다 밝은 파란색 #3B82F6)으로 보여주고, 지도를 확대/축소하거나 움직이면 화면 픽셀 기준으로
 * 다시 묶는다 (축소하면 넓은 범위로 크게 묶이고, 확대하면 잘게 나뉘어 마지막엔 1건씩 남는다).
 *  - 묶음(2건 이상) 클릭: 그 매물들이 한 화면에 들어오도록 확대. 좌표가 같은 매물(같은 건물)끼리라 더 안 나뉘면
 *    우측 목록의 첫 매물로 이동한다.
 *  - 1건짜리 원 클릭: 우측 목록의 해당 카드로 이동(onSelect) + 정보창. 마우스를 올리면 정보창이 뜬다.
 * 기존 실거래가 리포트가 쓰는 map-util.js(핀 방식)는 그대로 두고, 이 파일은 새 리포트만 쓴다.
 */
(function () {
  const CELL_PX = 72; // 화면에서 이 픽셀 격자 안의 매물은 하나로 묶는다
  const STYLE_ID = "cluster-map-style";

  const WORK_MARKER_SVG =
    '<svg xmlns="http://www.w3.org/2000/svg" width="40" height="44" viewBox="0 0 40 44">' +
    '<path d="M20 0C9 0 0 9 0 20c0 15 20 24 20 24s20-9 20-24C40 9 31 0 20 0z" fill="#E11D48" stroke="#ffffff" stroke-width="2.5"/>' +
    '<circle cx="20" cy="19" r="7" fill="#ffffff"/></svg>';
  const WORK_MARKER_IMAGE_SRC = "data:image/svg+xml;charset=UTF-8," + encodeURIComponent(WORK_MARKER_SVG);

  let state = null; // 현재 지도 상태 (렌더링할 때마다 새로 만든다)

  function esc(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  }

  function ensureStyle() {
    if (document.getElementById(STYLE_ID)) return;
    const style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = `
      .cluster-circle {
        display: flex; align-items: center; justify-content: center; border-radius: 50%;
        background: rgba(59, 130, 246, 0.92); color: #fff; font-weight: 800; border: 2px solid rgba(255, 255, 255, 0.95);
        box-shadow: 0 2px 8px rgba(30, 64, 175, 0.35); cursor: pointer; user-select: none;
        transition: transform .12s ease; font-family: inherit;
      }
      .cluster-circle:hover { transform: scale(1.12); }
      .cluster-circle.selected { background: #fff; color: #2563eb; border: 3px solid #2563eb; transform: scale(1.18); }
    `;
    document.head.appendChild(style);
  }

  // 묶인 개수가 많을수록 크게 (1건 34px ~ 최대 68px)
  function sizeFor(count) {
    return Math.min(68, 34 + Math.round(Math.sqrt(count - 1) * 5));
  }

  function infoHtml(item) {
    const r = item.data;
    const price = r.lease_type === "전세" ? `전세 ${r.listing_deposit}만원` : `${r.listing_monthly_rent}만원/월 · 보증금 ${r.listing_deposit}만원`;
    return (
      `<div style="padding:6px 10px;font-size:12px;white-space:nowrap;"><b>${esc(r.building_name)}</b><br/>` +
      `${esc(price)}<br/>실질 ${r.real_housing_cost}만원/월 · 보증금전환 ${Math.round((r.deposit_converted_cost ?? 0) * 10) / 10}만원/월<br/>${esc(r.address || r.region)}</div>`
    );
  }

  function openInfo(html, latlng) {
    if (!state) return;
    state.infoWindow.setContent(html);
    state.infoWindow.setPosition(latlng);
    state.infoWindow.open(state.map);
  }

  function closeInfo() {
    if (state) state.infoWindow.close();
  }

  function select(idx) {
    if (!state) return;
    state.selectedIdx = idx;
    const item = state.byIdx.get(idx);
    if (item) openInfo(infoHtml(item), item.latlng);
    if (state.onSelect) state.onSelect(idx);
    redraw();
  }

  function onClusterClick(members, center) {
    const map = state.map;
    if (members.length === 1) return select(members[0].idx);

    const sameSpot = members.every((m) => Math.abs(m.lat - members[0].lat) < 1e-6 && Math.abs(m.lon - members[0].lon) < 1e-6);
    if (sameSpot || map.getLevel() <= 1) {
      // 같은 건물에 있는 매물들이라 확대해도 안 나뉜다 - 목록의 첫 매물로 보내고 개수를 알려준다.
      openInfo(
        `<div style="padding:6px 10px;font-size:12px;white-space:nowrap;"><b>${esc(members[0].data.building_name)}</b><br/>같은 위치 매물 ${members.length}건 · 우측 목록에서 확인하세요</div>`,
        center
      );
      if (state.onSelect) state.onSelect(members[0].idx);
      return;
    }

    const prevLevel = map.getLevel();
    const bounds = new window.kakao.maps.LatLngBounds();
    members.forEach((m) => bounds.extend(m.latlng));
    map.setBounds(bounds, 70, 70, 70, 70);
    if (map.getLevel() >= prevLevel) map.setLevel(prevLevel - 1, { anchor: center }); // 화면에 이미 다 들어 있어도 한 단계는 확대
  }

  function circleElement(members, selected) {
    const count = members.length;
    const size = sizeFor(count);
    const el = document.createElement("div");
    el.className = "cluster-circle" + (selected ? " selected" : "");
    el.style.width = el.style.height = `${size}px`;
    el.style.fontSize = count >= 100 ? "15px" : "14px";
    el.textContent = String(count);
    el.title = count > 1 ? `매물 ${count}건 (클릭하면 확대)` : "";
    return el;
  }

  // 현재 지도 확대 수준에서 화면 픽셀 격자로 매물을 다시 묶어 원(CustomOverlay)으로 그린다.
  function redraw() {
    if (!state) return;
    const { map, items, mapEl } = state;
    const proj = map.getProjection();
    if (!proj) return;

    state.overlays.forEach((o) => o.setMap(null));
    state.overlays = [];

    const cells = new Map();
    const width = mapEl.clientWidth;
    const height = mapEl.clientHeight;
    items.forEach((it) => {
      const pt = proj.containerPointFromCoords(it.latlng);
      const key = `${Math.floor(pt.x / CELL_PX)}:${Math.floor(pt.y / CELL_PX)}`;
      if (!cells.has(key)) cells.set(key, { members: [], sx: 0, sy: 0, lat: 0, lon: 0 });
      const c = cells.get(key);
      c.members.push(it);
      c.sx += pt.x;
      c.sy += pt.y;
      c.lat += it.lat;
      c.lon += it.lon;
    });

    cells.forEach((c) => {
      const n = c.members.length;
      const sx = c.sx / n;
      const sy = c.sy / n;
      if (sx < -80 || sy < -80 || sx > width + 80 || sy > height + 80) return; // 화면 밖은 그리지 않는다
      const center = new window.kakao.maps.LatLng(c.lat / n, c.lon / n);
      const selected = n === 1 && c.members[0].idx === state.selectedIdx;
      const el = circleElement(c.members, selected);
      el.addEventListener("click", (e) => {
        e.stopPropagation();
        onClusterClick(c.members, center);
      });
      if (n === 1) {
        el.addEventListener("mouseenter", () => openInfo(infoHtml(c.members[0]), c.members[0].latlng));
        el.addEventListener("mouseleave", () => {
          if (state.selectedIdx !== c.members[0].idx) closeInfo();
        });
      }
      const overlay = new window.kakao.maps.CustomOverlay({
        position: center, content: el, xAnchor: 0.5, yAnchor: 0.5, clickable: true, zIndex: selected ? 20 : 10 + Math.min(n, 9),
      });
      overlay.setMap(map);
      state.overlays.push(overlay);
    });
  }

  function renderKakao(mapEl, listings, onSelect, workLocation) {
    const map = new window.kakao.maps.Map(mapEl, { center: new window.kakao.maps.LatLng(37.5665, 126.978), level: 8 });
    const items = [];
    const byIdx = new Map();
    listings.forEach((r, idx) => {
      if (r.lat == null || r.lon == null) return;
      const item = { idx, data: r, lat: r.lat, lon: r.lon, latlng: new window.kakao.maps.LatLng(r.lat, r.lon) };
      items.push(item);
      byIdx.set(idx, item);
    });

    state = {
      map, mapEl, items, byIdx, onSelect, selectedIdx: null, overlays: [],
      infoWindow: new window.kakao.maps.InfoWindow({ zIndex: 30 }),
    };

    const bounds = new window.kakao.maps.LatLngBounds();
    items.forEach((it) => bounds.extend(it.latlng));

    // 직장/학교 위치 기준점 - 정확한 좌표가 있으면 그것, 없으면 자치구 대표좌표 (기존 map-util.js와 같은 규칙)
    if (workLocation) {
      const regionCoords = (window.CustomHouseMapUtil && window.CustomHouseMapUtil.REGION_COORDS) || {};
      const coord =
        workLocation.lat != null && workLocation.lon != null ? [workLocation.lat, workLocation.lon] : regionCoords[workLocation.region];
      if (coord) {
        const position = new window.kakao.maps.LatLng(coord[0], coord[1]);
        const image = new window.kakao.maps.MarkerImage(WORK_MARKER_IMAGE_SRC, new window.kakao.maps.Size(40, 44), {
          offset: new window.kakao.maps.Point(20, 44),
        });
        new window.kakao.maps.Marker({ position, map, image, zIndex: 999 });
        bounds.extend(position);
        const label = new window.kakao.maps.CustomOverlay({
          position, yAnchor: 2.3, zIndex: 999,
          content: `<div style="padding:5px 10px;font-size:12px;font-weight:700;color:#E11D48;background:#fff;border:1px solid #fecdd3;border-radius:8px;white-space:nowrap;box-shadow:0 2px 6px rgba(0,0,0,.15)">🏢 직장/학교 위치<br/>${esc(workLocation.label || workLocation.region)}</div>`,
        });
        label.setMap(map);
      }
    }

    window.kakao.maps.event.addListener(map, "idle", redraw);
    window.kakao.maps.event.addListener(map, "click", () => {
      state.selectedIdx = null;
      closeInfo();
      redraw();
    });
    if (items.length > 0) map.setBounds(bounds, 60, 60, 60, 60);
    setTimeout(redraw, 0); // setBounds로 움직이지 않는 경우에도 처음 한 번은 그린다
  }

  /**
   * @param {string} containerId 지도를 담는 요소 id
   * @param {Array<object>} listings 추천 매물 목록 (lat/lon, building_name, real_housing_cost, ... - 목록 인덱스가 카드 인덱스와 같아야 한다)
   * @param {(idx:number)=>void} [onSelect] 원(1건)을 클릭했을 때 호출 - 우측 목록의 해당 카드로 이동용
   * @param {{lat?:number|null, lon?:number|null, region:string, label?:string}} [workLocation]
   */
  function renderListingsMap(containerId, listings, onSelect, workLocation) {
    const el = document.getElementById(containerId);
    if (!el) return;
    state = null;

    const kakaoKey = window.CUSTOMHOUSE_CONFIG && window.CUSTOMHOUSE_CONFIG.KAKAO_MAP_APP_KEY;
    const fallback = () => window.CustomHouseMapUtil && window.CustomHouseMapUtil.renderRecommendedRegionsMap(containerId, listings, onSelect, workLocation);
    if (!kakaoKey) return fallback();

    ensureStyle();
    const mapElId = `${containerId}-kakao-cluster-map`;
    el.innerHTML = `<div id="${mapElId}" style="width:100%;height:100%;border-radius:12px;"></div>`;
    const mapEl = document.getElementById(mapElId);

    (function waitForKakaoSdk(retriesLeft) {
      if (window.kakao && window.kakao.maps) {
        window.kakao.maps.load(() => renderKakao(mapEl, listings, onSelect, workLocation));
      } else if (retriesLeft > 0) {
        setTimeout(() => waitForKakaoSdk(retriesLeft - 1), 100);
      } else {
        fallback();
      }
    })(50); // 최대 5초 대기
  }

  /** 우측 목록에서 건물명을 클릭했을 때: 그 매물 위치로 확대 이동하고 정보창을 띄운다. */
  function focusListing(idx) {
    if (!state) return;
    const item = state.byIdx.get(idx);
    if (!item) return;
    state.selectedIdx = idx;
    state.map.setLevel(2, { anchor: item.latlng });
    state.map.setCenter(item.latlng);
    openInfo(infoHtml(item), item.latlng);
    setTimeout(redraw, 0);
  }

  /** 지도를 담은 요소의 크기가 바뀐 뒤(창 크기 변경 등) 호출한다. */
  function relayout() {
    if (!state) return;
    state.map.relayout();
    redraw();
  }

  window.CustomHouseClusterMapUtil = { renderListingsMap, focusListing, relayout };
})();
