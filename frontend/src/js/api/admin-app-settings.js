/**
 * [담당: 송귀성] API 통신 - 관리자 수정 > 기타 설정 (추천 개수 상한 등)
 * 연동 대상 백엔드: domain/appsetting (AdminAppSettingController, /api/admin/app-settings) - 관리자(ADMIN) JWT만 접근 가능
 * admin-income-standard.js와 같은 fetch/401 재시도/에러 변환 패턴을 그대로 다시 구현한다.
 */
const ADMIN_APP_SETTINGS_API_BASE = `${["localhost", "127.0.0.1"].includes(location.hostname) ? "http://localhost:8080" : "https://team3-q05z.onrender.com"}/api/admin/app-settings`;

/** Authorization 헤더를 자동으로 붙이고, 401이면 refreshToken으로 한 번 재시도한다. */
async function adminAppSettingsFetch(url, options = {}) {
  const token = CustomHouseAuthApi.getAccessToken();
  if (!token) {
    const err = new Error("로그인이 필요합니다.");
    err.code = "UNAUTHORIZED";
    throw err;
  }

  const doFetch = (accessToken) =>
    fetch(url, {
      ...options,
      headers: { ...(options.headers || {}), Authorization: `Bearer ${accessToken}` },
    });

  let res = await doFetch(token);
  if (res.status === 401) {
    const refreshed = await CustomHouseAuthApi.refreshAccessToken();
    res = await doFetch(refreshed.accessToken);
  }
  return res;
}

/** 실패 응답의 문구를 사용자에게 보여줄 Error로 바꾼다: 입력값 오류(400)는 data에 {필드: 메시지}가 오니 첫 메시지를 쓴다. */
async function adminAppSettingsUnwrap(res, fallback) {
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    /* 본문이 JSON이 아니면 기본 문구를 쓴다 */
  }
  if (!res.ok || (body && body.success === false)) {
    const fieldMessages = body && body.data && typeof body.data === "object" ? Object.values(body.data) : [];
    const err = new Error(fieldMessages[0] || (body && body.message) || fallback);
    err.code = (body && body.code) || (res.status === 403 ? "FORBIDDEN" : "ERROR");
    err.status = res.status;
    throw err;
  }
  return body ? body.data : null;
}

/** 현재 기타 설정: {recommendationLimit, updatedAt} */
async function getAppSettings() {
  const res = await adminAppSettingsFetch(ADMIN_APP_SETTINGS_API_BASE);
  return adminAppSettingsUnwrap(res, "기타 설정을 불러오지 못했어요.");
}

/** 저장: payload는 {recommendationLimit} */
async function saveAppSettings(payload) {
  const res = await adminAppSettingsFetch(ADMIN_APP_SETTINGS_API_BASE, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return adminAppSettingsUnwrap(res, "저장하지 못했어요. 잠시 후 다시 시도해주세요.");
}

window.CustomHouseAdminAppSettingsApi = { getAppSettings, saveAppSettings };
