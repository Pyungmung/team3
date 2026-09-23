/**
 * [담당: 양혜승] API 통신 - 회원가입 / 로그인 / 네이버 OAuth2
 * 연동 대상 백엔드: domain/user (담당: 허겸) - /api/auth/**
 * CLAUDE.md 컨벤션: JWT는 로그인 시 발급 → localStorage 저장 → Authorization 헤더로 전송.
 */
const AUTH_API_BASE = "http://localhost:8080/api/auth";
const ACCESS_TOKEN_KEY = "customhouse:accessToken";
const REFRESH_TOKEN_KEY = "customhouse:refreshToken";

async function signup({ email, password, nickname, phone, marketingConsent }) {
  const res = await fetch(`${AUTH_API_BASE}/signup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, nickname, phone: phone || null, marketingConsent: !!marketingConsent }),
  });
  const body = await res.json();
  if (!res.ok || body.success === false) {
    throw new Error(body.message || "회원가입에 실패했습니다.");
  }
  return body.data;
}

/** 이메일 중복 확인. 이미 사용 중이면 true, 사용 가능하면 false를 돌려준다. */
async function checkEmail(email) {
  const res = await fetch(`${AUTH_API_BASE}/check-email?email=${encodeURIComponent(email)}`);
  const body = await res.json();
  if (!res.ok || body.success === false) {
    throw new Error(body.message || "이메일 중복 확인에 실패했습니다.");
  }
  return body.data;
}

async function login({ email, password }) {
  const res = await fetch(`${AUTH_API_BASE}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  const body = await res.json();
  if (!res.ok || body.success === false) {
    throw new Error(body.message || "로그인에 실패했습니다.");
  }
  saveTokens(body.data);
  return body.data;
}

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) throw new Error("저장된 refreshToken이 없습니다.");

  const res = await fetch(`${AUTH_API_BASE}/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  const body = await res.json();
  if (!res.ok || body.success === false) {
    clearTokens();
    throw new Error(body.message || "토큰 재발급에 실패했습니다.");
  }
  saveTokens(body.data);
  return body.data;
}

function loginWithNaver() {
  // 백엔드 SecurityConfig가 등록한 네이버 OAuth2 로그인 진입점으로 이동.
  // 로그인 성공 시 backend/src/main/resources/application.yml의 oauth2.success-redirect-url(oauth-callback.html)로
  // accessToken/refreshToken을 쿼리 파라미터에 담아 되돌려준다.
  window.location.href = "http://localhost:8080/oauth2/authorization/naver";
}

function saveTokens({ accessToken, refreshToken }) {
  if (accessToken) localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  if (refreshToken) localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

function clearTokens() {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

function getAccessToken() {
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

function isLoggedIn() {
  return !!getAccessToken();
}

function logout() {
  clearTokens();
  window.location.href = computeAuthHref("index.html");
}

/** header.js의 computeHref와 동일한 규칙(pages/ 기준 상대경로 보정) */
function computeAuthHref(targetFromPagesRoot) {
  const depth = window.location.pathname.split("/pages/")[1]?.split("/").length - 1 || 0;
  return "../".repeat(Math.max(depth, 0)) + targetFromPagesRoot;
}

window.CustomHouseAuthApi = {
  signup,
  checkEmail,
  login,
  refreshAccessToken,
  loginWithNaver,
  logout,
  getAccessToken,
  isLoggedIn,
  saveTokens,
  clearTokens,
};
