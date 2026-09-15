/**
 * [담당: 양혜승] API 통신 - 마이페이지 (조건 설정)
 * 연동 대상 백엔드: domain/mypage (담당: 황진구) - /api/mypage/condition (JWT 인증 필요)
 */
const MYPAGE_API_BASE = "http://localhost:8080/api/mypage";

/** Authorization 헤더를 자동으로 붙이고, 401이면 refreshToken으로 한 번 재시도한다. */
async function mypageFetch(url, options = {}) {
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
    try {
      const refreshed = await CustomHouseAuthApi.refreshAccessToken();
      res = await doFetch(refreshed.accessToken);
    } catch {
      const err = new Error("로그인이 만료되었습니다. 다시 로그인해주세요.");
      err.code = "UNAUTHORIZED";
      throw err;
    }
  }

  return res;
}

/** 저장된 조건이 없으면 null을 반환한다 (에러 아님). */
async function getMyCondition() {
  const res = await mypageFetch(`${MYPAGE_API_BASE}/condition`);
  const body = await res.json();

  if (body.code === "NOT_FOUND") return null;
  if (!res.ok || body.success === false) {
    throw new Error(body.message || "마이페이지 조회에 실패했습니다.");
  }
  return body.data;
}

async function updateMyCondition(condition) {
  const res = await mypageFetch(`${MYPAGE_API_BASE}/condition`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(condition),
  });
  const body = await res.json();

  if (!res.ok || body.success === false) {
    throw new Error(body.message || "마이페이지 저장에 실패했습니다.");
  }
  return body.data;
}

window.CustomHouseMypageApi = { getMyCondition, updateMyCondition };
