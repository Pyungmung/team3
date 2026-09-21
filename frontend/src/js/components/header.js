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
    ? `<div class="flex items-center gap-2">
        <span id="header-user-name" class="inline-block truncate max-w-[7.5rem] sm:max-w-[13rem] text-xs text-gray-500"></span>
        <button id="header-logout-btn" class="text-sm px-3 sm:px-4 py-2 rounded-xl text-white brand-gradient font-semibold whitespace-nowrap shrink-0">로그아웃</button>
      </div>`
    : `<a href="${computeHref("auth/login.html")}" class="text-sm px-5 py-2.5 rounded-xl text-white brand-gradient font-semibold inline-block">
        로그인 / 회원가입
      </a>`;

  el.innerHTML = `
    <header class="w-full border-b border-slate-200 bg-white sticky top-0 z-10">
      <div class="w-full px-6 sm:px-12 h-20 flex items-center justify-between">

        <a href="${computeHref("index.html")}" class="flex items-center shrink-0 -ml-2">
          <img src="${computeHref("../assets/images/맞집 로고(최종).png")}" alt="맞집 로고" class="h-14 w-auto object-contain" />
        </a>

        <nav class="hidden sm:flex items-center gap-3 lg:absolute lg:left-1/2 lg:-translate-x-1/2 text-sm font-medium text-slate-500">
          ${menus
            .map(
              (m) => `<a href="${m.href}" class="relative px-3 py-2 rounded-xl hover:bg-slate-100 hover:text-[var(--brand-600)] ${
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