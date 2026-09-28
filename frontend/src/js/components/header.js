/**
 * [담당: 양혜승] 공통 UI 컴포넌트 - 헤더(네비게이션 바)
 * 사용법: 페이지의 <div id="app-header"></div>에 renderHeader()를 호출한다.
 *
 * auth.js가 로드되지 않은 페이지에서도 동작해야 하므로, 로그인 여부는
 * localStorage를 직접 읽어 판단한다 (키 이름은 js/api/auth.js와 동일하게 맞춰둠).
 */
const HEADER_ACCESS_TOKEN_KEY = "customhouse:accessToken";
const HEADER_NICKNAME_CACHE_KEY = "customhouse:nicknameCache";
const HEADER_USER_ME_URL = "http://localhost:8080/api/users/me";

function renderHeader(activeMenu = "") {
  const el = document.getElementById("app-header");
  if (!el) return;

  const menus = [
    { key: "diagnosis", label: "AI 주거비 진단", href: computeHref("diagnosis/input-form.html") },
    { key: "community", label: "커뮤니티", href: computeHref("community/list.html") },
    { key: "watchlist", label: "관심 매물", href: computeHref("watchlist/list.html") },
    { key: "mypage", label: "마이페이지", href: computeHref("mypage/index.html") },
  ];

  const email = getLoggedInEmail();

  const authArea = email
    ? `<div class="flex items-center gap-2 xl:gap-5 2xl:gap-6">
        <span id="header-user-name" class="hidden lg:inline-block truncate lg:max-w-[9rem] xl:max-w-[12rem] 2xl:max-w-[16rem] min-[1800px]:max-w-[22rem] text-sm xl:text-base 2xl:text-lg min-[1800px]:text-xl text-gray-500"></span>
        <button id="header-logout-btn" class="text-sm xl:text-[15px] 2xl:text-base min-[1800px]:text-lg px-4 xl:px-5 2xl:px-6 py-2 2xl:py-2.5 rounded-xl text-white brand-gradient font-semibold whitespace-nowrap shrink-0">로그아웃</button>
      </div>`
    : `<a href="${computeHref("auth/login.html")}" class="text-sm xl:text-[15px] 2xl:text-base min-[1800px]:text-lg px-5 xl:px-6 2xl:px-7 py-2.5 rounded-xl text-white brand-gradient font-semibold inline-block">
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

        ${authArea}
      </div>
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

  const logoutBtn = document.getElementById("header-logout-btn");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", () => {
      localStorage.removeItem("customhouse:accessToken");
      localStorage.removeItem("customhouse:refreshToken");
      localStorage.removeItem(HEADER_NICKNAME_CACHE_KEY);
      window.location.href = computeHref("index.html");
    });
  }

  if (email) showHeaderNickname(email);
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

/** pages/ 하위 어느 깊이에서 호출돼도 루트(pages/) 기준 상대경로를 맞춰준다. */
function computeHref(targetFromPagesRoot) {
  const depth = window.location.pathname.split("/pages/")[1]?.split("/").length - 1 || 0;
  return "../".repeat(Math.max(depth, 0)) + targetFromPagesRoot;
}