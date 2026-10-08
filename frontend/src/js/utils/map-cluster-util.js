/**
 * [담당: 양혜승] 매물 수 클러스터 지도 (직방식) - 추천 매물 리포트(report-listings.html) 전용
 *
 * 핀 대신 "그 지점에 묶인 매물 수"가 적힌 원(브랜드 네이비보다 밝은 파란색 #3B82F6)으로 보여주고, 지도를 확대/축소하거나 움직이면 화면 픽셀 기준으로
 * 다시 묶는다 (축소하면 넓은 범위로 크게 묶이고, 확대하면 잘게 나뉘어 마지막엔 1건씩 남는다).
 *  - 묶음(2건 이상) 클릭: 그 매물들이 한 화면에 들어오도록 확대. 좌표가 같은 매물(같은 건물)끼리라 더 안 나뉘면
 *    우측 목록의 첫 매물로 이동한다.
 *  - 1건짜리 원 클릭: 우측 목록의 해당 카드로 이동(onSelect) + 정보창. 마우스를 올리면 정보창이 뜬다.
 * 기존 실거래가 리포트가 쓰는 map-util.js(핀 방식)는 그대로 두고, 이 파일은 새 리포트만 쓴다.
 * (2026-10-08: 터치 이동/확대·축소와 +/- 버튼은 enhanceMap으로 매물 등록 화면의 지도도 같이 쓴다)
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

  // ---------- 터치 이동/확대·축소 + 확대·축소(+/-) 버튼 ----------
  // 카카오맵 SDK는 터치를 쓸지 여부를 `"ontouchstart" in document.documentElement && (UA에 "Chrome"이 없거나 Android)`로 정한다.
  // 그래서 Windows 터치스크린 노트북의 Chrome/Edge에서는 마우스 이벤트만 듣고(터치 드래그는 마우스 이벤트가 아니다), 두 손가락 확대·축소는 iOS 전용
  // gesturechange 이벤트에 의존해서 둘 다 동작하지 않는다 (2026-10-08 확인). 그런 환경에서만 포인터 이벤트로 직접 구현한다:
  //   한 손가락(또는 두 손가락 중심) 드래그 = 이동, 두 손가락 벌리기/모으기 = 확대/축소 (거리가 2배가 될 때마다 한 단계).
  // 카카오가 이미 터치를 처리하는 환경(Android/iOS)에서는 같은 동작이 두 번 일어나지 않도록 켜지 않는다. 마우스/휠은 카카오 기본 기능을 그대로 쓴다.
  const KAKAO_HANDLES_TOUCH =
    "ontouchstart" in document.documentElement && (navigator.userAgent.indexOf("Chrome") < 0 || navigator.userAgent.indexOf("Android") >= 0);
  const MIN_LEVEL = 1;
  const MAX_LEVEL = 14;
  const clampLevel = (level) => Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));

  function enableTouchGestures(map, mapEl) {
    if (KAKAO_HANDLES_TOUCH || !(navigator.maxTouchPoints > 0) || !window.PointerEvent) return;
    mapEl.style.touchAction = "none"; // 브라우저가 터치를 페이지 스크롤/확대로 가로채지 않고 포인터 이벤트로 넘겨준다
    const pointers = new Map(); // pointerId -> {x, y}
    let last = null; // 직전 중심점 {x, y}
    let pinch = null; // {dist, level, anchor}

    const centroid = () => {
      const pts = [...pointers.values()];
      return { x: pts.reduce((a, p) => a + p.x, 0) / pts.length, y: pts.reduce((a, p) => a + p.y, 0) / pts.length };
    };
    const distance = () => {
      const [a, b] = [...pointers.values()];
      return Math.hypot(a.x - b.x, a.y - b.y);
    };
    const toContainerPoint = (clientX, clientY) => {
      const rect = mapEl.getBoundingClientRect();
      return new window.kakao.maps.Point(clientX - rect.left, clientY - rect.top);
    };
    const resetBaseline = () => {
      last = pointers.size ? centroid() : null;
      pinch = null;
      if (pointers.size === 2) {
        const c = centroid();
        pinch = {
          dist: Math.max(distance(), 1),
          level: map.getLevel(),
          anchor: map.getProjection().coordsFromContainerPoint(toContainerPoint(c.x, c.y)),
        };
      }
    };

    mapEl.addEventListener("pointerdown", (e) => {
      if (e.pointerType !== "touch" || e.target.closest(".cl-zoom-ctl")) return;
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      resetBaseline();
    });
    mapEl.addEventListener("pointermove", (e) => {
      if (!pointers.has(e.pointerId)) return;
      pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
      const c = centroid();
      if (last && (c.x !== last.x || c.y !== last.y)) {
        const w = mapEl.clientWidth;
        const h = mapEl.clientHeight;
        const proj = map.getProjection();
        map.setCenter(proj.coordsFromContainerPoint(new window.kakao.maps.Point(w / 2 - (c.x - last.x), h / 2 - (c.y - last.y))));
      }
      last = c;
      if (pinch && pointers.size === 2) {
        const target = clampLevel(pinch.level - Math.round(Math.log2(distance() / pinch.dist)));
        if (target !== map.getLevel()) map.setLevel(target, { anchor: pinch.anchor });
      }
    });
    const end = (e) => {
      if (!pointers.delete(e.pointerId)) return;
      resetBaseline(); // 손가락 수가 바뀌면 기준을 다시 잡아서 지도가 튀지 않게 한다
    };
    mapEl.addEventListener("pointerup", end);
    mapEl.addEventListener("pointercancel", end);
  }

  /**
   * 카카오 기본 확대/축소 바(세로로 긴 +/- 와 확대 단계 표시)를 지도 오른쪽 "세로 가운데"로 옮긴다 - 카카오 기본 위치(오른쪽 위)는
   * 오른쪽 위의 주택 유형 상자와 겹친다. 좁은 화면(폰)에서는 주택 유형 상자가 세로로 길어지므로 왼쪽 위쪽(패널 접기 버튼 아래)에 둔다.
   * 카카오가 지도 크기가 바뀔 때 위치를 다시 잡기 때문에, 크기 변화 뒤에 다시 옮긴다.
   */
  function addZoomButtons(map, mapEl) {
    map.addControl(new window.kakao.maps.ZoomControl(), window.kakao.maps.ControlPosition.RIGHT);
    const bar = (mapEl.querySelector('button[title="확대"]') || {}).parentElement;
    if (!bar) return;
    bar.classList.add("cl-zoom-ctl");
    // 카카오는 지도가 그려지거나 크기가 바뀔 때 바 위치를 자기 기본값(오른쪽 위)으로 되돌린다 - 원하는 위치와 다르면 다시 옮긴다 (같으면 건드리지 않아 무한 반복이 없다)
    const place = () => {
      const narrow = mapEl.clientWidth <= 480;
      // 폰: 왼쪽 위 "패널 접기" 버튼 바로 아래(왼쪽 아래 안내 상자 위)에 둔다. 그 외: 오른쪽 세로 가운데
      const top = (narrow ? 62 : Math.max(8, Math.round((mapEl.clientHeight - bar.offsetHeight) / 2))) + "px";
      const left = (narrow ? 12 : Math.max(0, mapEl.clientWidth - bar.offsetWidth - 12)) + "px";
      if (bar.style.top !== top) bar.style.top = top;
      if (bar.style.left !== left) bar.style.left = left;
    };
    place();
    if (window.MutationObserver) new MutationObserver(place).observe(bar, { attributes: true, attributeFilter: ["style"] });
    if (window.ResizeObserver) new ResizeObserver(() => setTimeout(place, 60)).observe(mapEl);
    window.addEventListener("resize", () => setTimeout(place, 60));
    // 바 위에서 시작한 터치가 지도 이동으로 번지지 않게 한다
    bar.addEventListener("pointerdown", (e) => e.stopPropagation());
  }

  function renderKakao(mapEl, listings, onSelect, workLocation) {
    const map = new window.kakao.maps.Map(mapEl, { center: new window.kakao.maps.LatLng(37.5665, 126.978), level: 8 });
    map.setDraggable(true);
    map.setZoomable(true);
    addZoomButtons(map, mapEl);
    enableTouchGestures(map, mapEl);
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

  /**
   * 지도 폭이 바뀐 직후 호출한다 (왼쪽 패널을 접고 펼칠 때). 지도 안의 내용은 화면에서 제자리에 두고,
   * 넓어진 만큼은 왼쪽으로만 새로 드러나게(줄어들 땐 왼쪽부터 가려지게) 중심을 보정한다 (2026-10-07).
   * 카카오 지도는 relayout()만 하면 중심을 가운데에 맞춰서 내용이 폭 변화량의 절반만큼 밀리기 때문이다.
   * @param {number} oldWidth 크기가 바뀌기 전 지도 요소의 폭(px)
   */
  function relayoutKeepView(oldWidth) {
    if (!state) return;
    const map = state.map;
    const projection = map.getProjection();
    // 카카오 relayout()은 지도 "왼쪽 위 모서리"를 고정해서, 지도 요소가 왼쪽으로 넓어지면 내용이 화면에서 같이 밀려난다 (panBy는 이 환경에서 듣지 않았다).
    // 그래서 바뀌기 전 중심(p0)에서 폭 변화량(delta)의 절반만큼 옮긴 지점을 새 중심으로 직접 정한다:
    // 요소가 왼쪽으로 delta만큼 넓어지면 새 중심은 화면에서 옛 중심보다 delta/2 왼쪽에 있으므로, 지도 좌표(오른쪽이 +, 단위=화면 픽셀)로는 p0.x - delta/2다.
    const p0 = projection.pointFromCoords(map.getCenter());
    map.relayout();
    const delta = state.mapEl.clientWidth - oldWidth;
    const target = projection.coordsFromPoint(new window.kakao.maps.Point(p0.x - delta / 2, p0.y));
    map.setCenter(target);
    redraw();
    // 카카오가 크기 변경을 늦게 반영해도 같은 중심으로 다시 맞춘다 (새로 드러난 자리의 타일도 이때 채워진다)
    setTimeout(() => {
      if (!state || state.map !== map) return;
      map.relayout();
      map.setCenter(target);
      redraw();
    }, 200);
  }

  /** 다른 화면의 카카오 지도(예: 매물 등록의 주소 확인 지도)에도 같은 터치 이동/확대·축소와 +/- 버튼을 붙인다. */
  function enhanceMap(map, mapEl) {
    addZoomButtons(map, mapEl);
    enableTouchGestures(map, mapEl);
  }

  window.CustomHouseClusterMapUtil = { renderListingsMap, focusListing, relayout, relayoutKeepView, enhanceMap };
})();
