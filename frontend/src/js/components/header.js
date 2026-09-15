/**
 * [담당: 양혜승] 공통 UI 컴포넌트 - 헤더(네비게이션 바)
 * 사용법: 페이지의 <div id="app-header"></div>에 renderHeader()를 호출한다.
 *
 * auth.js가 로드되지 않은 페이지에서도 동작해야 하므로, 로그인 여부는
 * localStorage를 직접 읽어 판단한다 (키 이름은 js/api/auth.js와 동일하게 맞춰둠).
 */
const HEADER_ACCESS_TOKEN_KEY = "customhouse:accessToken";

function renderHeader(activeMenu = "") {
  const el = document.getElementById("app-header");
  if (!el) return;

  const menus = [
    { key: "diagnosis", label: "AI 주거비 진단", href: computeHref("diagnosis/input-form.html") },
    { key: "watchlist", label: "관심 매물", href: computeHref("watchlist/list.html") },
    { key: "mypage", label: "마이페이지", href: computeHref("mypage/index.html") },
  ];

  const email = getLoggedInEmail();

  const authArea = email
    ? `<div class="flex items-center gap-2">
         <span class="hidden sm:inline text-xs text-gray-500">${email}</span>
         <button id="header-logout-btn" class="text-sm px-4 py-2 rounded-full text-white brand-gradient">로그아웃</button>
       </div>`
    : `<a href="${computeHref("auth/login.html")}" class="text-sm px-4 py-2 rounded-full text-white brand-gradient">로그인</a>`;

  el.innerHTML = `
    <header class="w-full border-b border-gray-100 bg-white/80 backdrop-blur sticky top-0 z-10">
      <div class="max-w-5xl mx-auto px-4 h-16 flex items-center justify-between">
        <a href="${computeHref("index.html")}" class="flex items-center gap-2 font-bold text-lg" style="color:var(--brand-600)">
          <span>🏠</span><span>맞집</span>
        </a>
        <nav class="hidden sm:flex items-center gap-6 text-sm text-gray-600">
          ${menus
            .map(
              (m) => `<a href="${m.href}" class="${
                activeMenu === m.key ? "font-semibold" : ""
              } hover:text-[var(--brand-600)]" style="${activeMenu === m.key ? "color:var(--brand-600)" : ""}">${m.label}</a>`
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
      window.location.href = computeHref("index.html");
    });
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
