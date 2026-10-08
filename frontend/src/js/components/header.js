/**
 * [담당: 양혜승] 공통 UI 컴포넌트 - 헤더(네비게이션 바)
 * 사용법: 페이지의 <div id="app-header"></div>에 renderHeader()를 호출한다.
 *
 * auth.js가 로드되지 않은 페이지에서도 동작해야 하므로, 로그인 여부는
 * localStorage를 직접 읽어 판단한다 (키 이름은 js/api/auth.js와 동일하게 맞춰둠).
 */
const HEADER_ACCESS_TOKEN_KEY = "customhouse:accessToken";
const HEADER_NICKNAME_CACHE_KEY = "customhouse:nicknameCache";
const HEADER_API_BASE = ["localhost", "127.0.0.1"].includes(location.hostname) ? "http://localhost:8080" : "https://team3-q05z.onrender.com";
const HEADER_USER_ME_URL = `${HEADER_API_BASE}/api/users/me`;
const HEADER_NOTIFICATION_PREVIEW_COUNT = 5;
const HEADER_NOTIFICATION_POLL_MS = 60000;

function renderHeader(activeMenu = "") {
  const el = document.getElementById("app-header");
  if (!el) return;

  const menus = [
    { key: "diagnosis", label: "AI 주거비 진단", href: computeHref("diagnosis/input-form.html") },
    { key: "community", label: "커뮤니티", href: computeHref("community/list.html") },
    { key: "watchlist", label: "관심매물", href: computeHref("watchlist/list.html") },
    { key: "mypage", label: "마이페이지", href: computeHref("mypage/index.html") },
  ];
  // 관리자 계정에게만 "관리자 수정" 탭이 보인다 (표시용일 뿐, 실제 접근 제한은 백엔드 /api/admin/** 가 한다)
  if (getLoggedInRole() === "ADMIN") {
    menus.push({ key: "admin", label: "관리자 수정", href: computeHref("admin/loans.html") });
  }

  const email = getLoggedInEmail();

  const authArea = email
    ? `<div class="flex items-center gap-2 xl:gap-4 2xl:gap-5">
        <div id="header-bell-wrap" class="relative shrink-0">
          <button id="header-bell-btn" type="button" aria-label="알림" aria-haspopup="true" aria-expanded="false" class="relative w-10 h-10 xl:w-11 xl:h-11 rounded-full flex items-center justify-center text-slate-500 hover:bg-slate-100 hover:text-[var(--brand-600)]">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" class="w-6 h-6 xl:w-7 xl:h-7" aria-hidden="true"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>
            <span id="header-bell-badge" class="hidden absolute top-0 right-0 min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[11px] font-bold leading-[18px] text-center"></span>
          </button>
          <div id="header-bell-panel" class="hidden absolute right-0 top-full mt-2 w-[min(22rem,calc(100vw-1.5rem))] max-sm:fixed max-sm:inset-x-3 max-sm:top-[5rem] max-sm:mt-0 max-sm:w-auto bg-white border border-slate-200 rounded-2xl shadow-xl overflow-hidden z-20 text-left"></div>
        </div>
        <span id="header-user-name" class="hidden lg:inline-block truncate lg:max-w-[9rem] xl:max-w-[12rem] 2xl:max-w-[16rem] min-[1800px]:max-w-[22rem] text-sm xl:text-base 2xl:text-lg min-[1800px]:text-xl text-gray-500"></span>
        <button id="header-logout-btn" class="hidden sm:inline-block text-sm xl:text-[15px] 2xl:text-base min-[1800px]:text-lg px-4 xl:px-5 2xl:px-6 py-2 2xl:py-2.5 rounded-xl text-white brand-gradient font-semibold whitespace-nowrap shrink-0">로그아웃</button>
      </div>`
    : `<a href="${computeHref("auth/login.html")}" class="text-sm xl:text-[15px] 2xl:text-base min-[1800px]:text-lg px-3 sm:px-5 xl:px-6 2xl:px-7 py-2 sm:py-2.5 rounded-xl text-white brand-gradient font-semibold inline-block whitespace-nowrap">
        로그인 / 회원가입
      </a>`;

  el.innerHTML = `
    <header class="w-full border-b border-slate-200 bg-white sticky top-0 z-10">
      <div class="w-full px-6 sm:px-12 h-20 xl:h-[5.5rem] 2xl:h-24 min-[1800px]:h-28 flex items-center justify-between">

        <a href="${computeHref("index.html")}" class="flex items-center shrink-0 -ml-2">
          <img src="${computeHref("../assets/images/맞집 로고(최종).png")}" alt="맞집 로고" class="h-14 xl:h-16 2xl:h-[4.5rem] min-[1800px]:h-20 w-auto object-contain" />
        </a>

        <nav class="hidden sm:flex items-center gap-0 lg:gap-1 xl:gap-3 2xl:gap-5 mx-2 lg:mx-4 xl:mx-0 min-[1800px]:absolute min-[1800px]:left-1/2 min-[1800px]:-translate-x-1/2 text-[14px] lg:text-[15px] xl:text-[16px] 2xl:text-[18px] min-[1800px]:text-[21px] font-semibold text-slate-500">
          ${menus
            .map(
              (m) => `<a href="${m.href}" class="relative px-2 lg:px-3 xl:px-4 2xl:px-5 min-[1800px]:px-5 py-2.5 rounded-xl whitespace-nowrap hover:bg-slate-100 hover:text-[var(--brand-600)] ${
                activeMenu === m.key ? "font-bold text-[var(--brand-600)]" : ""
              }" style="${activeMenu === m.key ? "color:var(--brand-600)" : ""}">
                ${m.label}
              </a>`
            )
            .join("")}
        </nav>

        <div class="flex items-center gap-1 sm:gap-0">
          ${authArea}
          <!-- 폰(sm 미만)에서만 보이는 메뉴 버튼: 위쪽 메뉴(nav)가 숨겨지는 대신 아래로 펼쳐지는 메뉴를 연다 -->
          <button id="header-menu-btn" type="button" aria-label="메뉴 열기" aria-expanded="false" aria-controls="header-mobile-menu" class="sm:hidden w-11 h-11 rounded-xl flex items-center justify-center text-slate-600 hover:bg-slate-100">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" aria-hidden="true"><path id="header-menu-icon" d="M4 7h16M4 12h16M4 17h16"/></svg>
          </button>
        </div>
      </div>
      <!-- 폰 전용 펼침 메뉴 -->
      <nav id="header-mobile-menu" class="hidden sm:hidden absolute left-0 right-0 top-full bg-white border-b border-slate-200 shadow-lg px-4 py-3" aria-label="메뉴">
        ${menus
          .map(
            (m) => `<a href="${m.href}" class="flex items-center px-4 py-3.5 rounded-xl text-[17px] font-semibold ${
              activeMenu === m.key ? "bg-slate-100 font-bold text-[var(--brand-600)]" : "text-slate-600 hover:bg-slate-50"
            }">${m.label}</a>`
          )
          .join("")}
        ${email ? `<button id="header-mobile-logout" type="button" class="mt-2 w-full px-4 py-3.5 rounded-xl text-[17px] font-semibold text-white brand-gradient">로그아웃</button>` : ""}
      </nav>
    </header>
  `;

  // 헤더 높이는 화면 폭에 따라 달라지므로, 페이지들이 sticky 위치를 맞출 수 있게 CSS 변수로 알려준다.
  const headerEl = el.querySelector("header");
  const syncHeaderHeight = () => document.documentElement.style.setProperty("--header-h", headerEl.offsetHeight + "px");
  syncHeaderHeight();
  // Tailwind CDN이 스타일을 입히기 전에는 높이가 다르게 측정되므로 로드 완료/크기 변경 때 다시 잰다.
  window.addEventListener("load", syncHeaderHeight);
  window.addEventListener("resize", syncHeaderHeight);
  requestAnimationFrame(() => requestAnimationFrame(syncHeaderHeight));
  if (window.ResizeObserver) new ResizeObserver(syncHeaderHeight).observe(headerEl);

  const logout = () => {
    localStorage.removeItem("customhouse:accessToken");
    localStorage.removeItem("customhouse:refreshToken");
    localStorage.removeItem(HEADER_NICKNAME_CACHE_KEY);
    window.location.href = computeHref("index.html");
  };
  const logoutBtn = document.getElementById("header-logout-btn");
  if (logoutBtn) logoutBtn.addEventListener("click", logout);
  const mobileLogoutBtn = document.getElementById("header-mobile-logout");
  if (mobileLogoutBtn) mobileLogoutBtn.addEventListener("click", logout);

  setupHeaderMobileMenu(headerEl);

  if (email) {
    showHeaderNickname(email);
    setupHeaderNotifications();
  }
}

/**
 * 폰(sm 미만) 메뉴: 햄버거 버튼으로 펼쳤다 접는다. 바깥을 누르거나 Esc, 화면이 넓어지면(sm 이상) 닫힌다.
 * 열려 있는 동안 헤더를 지도 위 요소(z-50)보다 위로 올린다 - 헤더 안의 펼침 메뉴/알림창이 페이지의 지도 버튼 등에 가려지지 않게 하려는 것이다.
 */
function setupHeaderMobileMenu(headerEl) {
  const btn = document.getElementById("header-menu-btn");
  const menu = document.getElementById("header-mobile-menu");
  if (!btn || !menu) return;
  const icon = document.getElementById("header-menu-icon");

  const setOpen = (open) => {
    menu.classList.toggle("hidden", !open);
    btn.setAttribute("aria-expanded", String(open));
    btn.setAttribute("aria-label", open ? "메뉴 닫기" : "메뉴 열기");
    icon.setAttribute("d", open ? "M6 6l12 12M18 6L6 18" : "M4 7h16M4 12h16M4 17h16");
    syncHeaderLayer(headerEl);
  };
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    setOpen(menu.classList.contains("hidden"));
  });
  menu.addEventListener("click", (e) => e.stopPropagation());
  document.addEventListener("click", () => setOpen(false));
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") setOpen(false);
  });
  window.matchMedia("(min-width: 640px)").addEventListener("change", (e) => {
    if (e.matches) setOpen(false);
  });
}

/** 메뉴나 알림창이 열려 있는 동안만 헤더를 페이지 요소들(지도 버튼 등, z-50)보다 위에 둔다. 닫으면 원래대로(z-10). */
function syncHeaderLayer(headerEl) {
  const menu = document.getElementById("header-mobile-menu");
  const bell = document.getElementById("header-bell-panel");
  const open = (menu && !menu.classList.contains("hidden")) || (bell && !bell.classList.contains("hidden"));
  headerEl.style.zIndex = open ? "100" : "";
}

/**
 * 헤더에 이메일 대신 닉네임을 표시한다. 닉네임은 JWT에 없어서 GET /api/users/me로 가져온다.
 * 페이지를 옮길 때마다 깜빡이지 않도록 마지막 값을 localStorage에 캐시하고(같은 이메일일 때만 사용),
 * 조회에 실패하면(서버 꺼짐/토큰 만료 등) 이메일로 대신 표시한다.
 * 닉네임은 사용자가 입력한 값이라 innerHTML이 아니라 textContent로만 넣는다.
 */
async function showHeaderNickname(email) {
  const nameEl = document.getElementById("header-user-name");
  if (!nameEl) return;

  const cached = readHeaderNicknameCache();
  if (cached && cached.email === email) renderWelcome(nameEl, cached.nickname);

  try {
    const res = await fetch(HEADER_USER_ME_URL, {
      headers: { Authorization: `Bearer ${localStorage.getItem(HEADER_ACCESS_TOKEN_KEY)}` },
    });
    const body = await res.json();
    if (!res.ok || !body.data || !body.data.nickname) throw new Error("nickname unavailable");

    renderWelcome(nameEl, body.data.nickname);
    localStorage.setItem(HEADER_NICKNAME_CACHE_KEY, JSON.stringify({ email, nickname: body.data.nickname }));
  } catch {
    if (!nameEl.textContent) renderWelcome(nameEl, email);
  }
}

/**
 * 헤더 인사말을 그린다. 예: "홍길동님 환영합니다." (이름만 굵게 강조).
 * 이름은 사용자가 입력한 값이라 innerHTML이 아니라 textContent로만 넣는다.
 */
function renderWelcome(el, name) {
  const strong = document.createElement("strong");
  strong.className = "font-bold text-gray-900";
  strong.textContent = name;

  el.replaceChildren(strong, document.createTextNode("님 환영합니다."));
  el.title = `${name}님 환영합니다.`;
}

function readHeaderNicknameCache() {
  try {
    return JSON.parse(localStorage.getItem(HEADER_NICKNAME_CACHE_KEY));
  } catch {
    return null;
  }
}

/**
 * 헤더 알림 벨: 알림함(/api/notifications)과 같은 데이터를 쓴다.
 * 벨 아이콘에 안읽은 알림 수를 보여주고, 누르면 최근 알림 몇 건을 간단히 펼쳐 보여준다 (별도 페이지 없음, 전체는 알림함 링크).
 * 알림 제목/내용은 textContent로만 넣는다.
 */
let headerNotificationState = { unread: 0, items: [], loaded: false };

function setupHeaderNotifications() {
  const btn = document.getElementById("header-bell-btn");
  const panel = document.getElementById("header-bell-panel");
  if (!btn || !panel) return;

  const setOpen = (open) => {
    panel.classList.toggle("hidden", !open);
    btn.setAttribute("aria-expanded", open ? "true" : "false");
    syncHeaderLayer(btn.closest("header"));
    if (open) refreshHeaderNotifications();
  };
  btn.addEventListener("click", (e) => {
    e.stopPropagation();
    setOpen(panel.classList.contains("hidden"));
  });
  panel.addEventListener("click", (e) => e.stopPropagation());
  document.addEventListener("click", () => setOpen(false));
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") setOpen(false);
  });

  renderHeaderNotificationPanel();
  refreshHeaderNotifications();
  setInterval(() => {
    if (!document.hidden) refreshHeaderNotifications();
  }, HEADER_NOTIFICATION_POLL_MS);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) refreshHeaderNotifications();
  });
}

async function headerNotificationFetch(path, options = {}) {
  const res = await fetch(`${HEADER_API_BASE}/api/notifications${path}`, {
    ...options,
    headers: { Authorization: `Bearer ${localStorage.getItem(HEADER_ACCESS_TOKEN_KEY)}` },
  });
  const body = await res.json();
  if (!res.ok) throw new Error("notification request failed");
  return body.data;
}

/** 알림 목록과 안읽은 수를 다시 불러와 벨을 갱신한다. 알림함 페이지에서도 읽음/삭제 뒤에 호출한다. */
async function refreshHeaderNotifications() {
  if (!document.getElementById("header-bell-btn")) return;
  try {
    const [list, unread] = await Promise.all([headerNotificationFetch(""), headerNotificationFetch("/unread-count")]);
    headerNotificationState = { unread: Number(unread && unread.count) || 0, items: Array.isArray(list) ? list : [], loaded: true };
  } catch {
    return; // 서버 꺼짐/토큰 만료 등 - 벨은 이전 상태 그대로 두고 화면 전체를 막지 않는다
  }
  renderHeaderNotificationPanel();
}

function headerRelativeTime(value) {
  const t = new Date(value).getTime();
  if (!Number.isFinite(t)) return "";
  const min = Math.floor((Date.now() - t) / 60000);
  if (min < 1) return "방금 전";
  if (min < 60) return `${min}분 전`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}시간 전`;
  const day = Math.floor(hour / 24);
  if (day < 7) return `${day}일 전`;
  return new Date(t).toLocaleDateString();
}

function renderHeaderNotificationPanel() {
  const badge = document.getElementById("header-bell-badge");
  const panel = document.getElementById("header-bell-panel");
  if (!badge || !panel) return;
  const { unread, items, loaded } = headerNotificationState;

  badge.textContent = unread > 99 ? "99+" : String(unread);
  badge.classList.toggle("hidden", unread <= 0);
  document.getElementById("header-bell-btn").setAttribute("aria-label", unread > 0 ? `알림 (안읽은 알림 ${unread}건)` : "알림");

  const el = (tag, className, text) => {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  };

  const head = el("div", "flex items-center justify-between px-4 py-3 border-b border-slate-100");
  head.append(el("span", "font-bold text-slate-900 text-base", "알림함"), el("span", "text-xs " + (unread > 0 ? "text-red-500 font-semibold" : "text-slate-400"), unread > 0 ? `안읽음 ${unread}` : "모두 읽었어요"));

  const list = el("div", "max-h-[24rem] overflow-y-auto bg-slate-50 p-2 space-y-2");
  if (!loaded || items.length === 0) {
    list.append(el("div", "py-8 text-center text-sm text-slate-400", loaded ? "아직 받은 알림이 없어요." : "알림을 불러오는 중이에요..."));
  } else {
    items.slice(0, HEADER_NOTIFICATION_PREVIEW_COUNT).forEach((n) => {
      const row = el("button", "w-full text-left bg-white rounded-xl px-3 py-2.5 shadow-sm hover:bg-slate-50");
      row.type = "button";
      const top = el("div", "flex items-center gap-1.5");
      if (!n.read) top.append(el("span", "w-2 h-2 rounded-full bg-red-500 shrink-0"));
      top.append(el("span", "text-sm font-bold text-slate-900 truncate", n.title || ""), el("span", "ml-auto pl-2 text-[11px] text-slate-400 shrink-0 whitespace-nowrap", headerRelativeTime(n.createdAt)));
      const content = el("div", "mt-1 text-[13px] leading-snug text-slate-600 break-words", n.content || "");
      content.style.cssText = "display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden";
      row.append(top, content);
      row.addEventListener("click", async () => {
        if (!n.read) {
          try {
            await headerNotificationFetch(`/${n.id}/read`, { method: "PATCH" });
          } catch {
            // 읽음 처리에 실패해도 알림함으로는 이동한다
          }
          await refreshHeaderNotifications();
        }
        panel.classList.add("hidden");
        window.location.href = computeHref("watchlist/list.html") + "#notification-area";
      });
      list.append(row);
    });
  }

  const more = el("a", "block text-center px-4 py-3 text-sm font-semibold border-t border-slate-100 hover:bg-slate-50", "알림함에서 전체 보기");
  more.href = computeHref("watchlist/list.html") + "#notification-area";
  more.style.color = "var(--brand-600)";

  panel.replaceChildren(head, list, more);
}

/** JWT payload의 email 클레임을 읽어온다 (서명 검증은 백엔드가 담당하므로 여기선 표시 용도로만 사용). */
function getLoggedInEmail() {
  const token = localStorage.getItem(HEADER_ACCESS_TOKEN_KEY);
  if (!token) return null;

  try {
    const payload = token.split(".")[1];
    const decoded = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return decoded.email || null;
  } catch {
    return null;
  }
}

/** JWT payload의 role 클레임(USER/ADMIN)을 읽어온다. 클레임이 없는 예전 토큰은 USER. 표시 용도로만 사용한다. */
function getLoggedInRole() {
  const token = localStorage.getItem(HEADER_ACCESS_TOKEN_KEY);
  if (!token) return null;

  try {
    const payload = token.split(".")[1];
    const decoded = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return decoded.role || "USER";
  } catch {
    return null;
  }
}

/**
 * pages/ 하위 어느 깊이에서 호출돼도 루트(pages/) 기준 상대경로를 맞춰준다.
 * 2026-09-29: 메인 홈페이지(index.html)가 src/ 밖(사이트 진짜 루트)으로 옮겨지면서 두 가지를 특별 처리한다.
 *   1) target이 "index.html"이면 pages/ 기준이 아니라 src/ 밖을 가리켜야 하므로 depth에 2를 더 올라간다
 *      (pages/ 한 칸 + src/ 한 칸). 이미 index.html에 있으면 자기 자신이라 그대로 "index.html".
 *   2) 지금 있는 페이지 자체가 index.html이면(경로에 "/pages/"가 없음) 다른 target은 전부 "src/pages/" 밑에
 *      있으므로 그 접두사를 붙인다. "../assets/images/..."처럼 이미 위로 올라가는 target이 와도
 *      "src/pages/../assets/images/..." → "src/assets/images/..."로 정상적으로 상쇄된다.
 */
function computeHref(targetFromPagesRoot) {
  const afterPages = window.location.pathname.split("/pages/")[1];
  const onSiteRoot = afterPages === undefined;

  if (targetFromPagesRoot === "index.html") {
    if (onSiteRoot) return "index.html";
    const depth = afterPages.split("/").length - 1;
    return "../".repeat(depth + 2) + "index.html";
  }
  if (onSiteRoot) return "src/pages/" + targetFromPagesRoot;

  const depth = afterPages.split("/").length - 1;
  return "../".repeat(Math.max(depth, 0)) + targetFromPagesRoot;
}