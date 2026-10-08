/**
 * [담당: 송귀성] API 통신 - 관리자 수정 > 주거지원정책 (리포트 "주거정책 추천"에 쓰는 정책 목록, 2026-10-08부터 CSV 대신 DB)
 * 연동 대상 백엔드: domain/housingpolicy (AdminHousingPolicyController, /api/admin/housing-policies) - 관리자(ADMIN) JWT만 접근 가능
 * admin-income-standard.js와 같은 fetch/401 재시도/에러 변환 패턴을 이 파일에서 그대로 다시 구현한다 (헬퍼가 짧아 공용 모듈보다 중복이 낫다고 판단).
 */
const ADMIN_HOUSING_POLICY_API_BASE = `${["localhost", "127.0.0.1"].includes(location.hostname) ? "http://localhost:8080" : "https://team3-q05z.onrender.com"}/api/admin/housing-policies`;

/** Authorization 헤더를 자동으로 붙이고, 401이면 refreshToken으로 한 번 재시도한다. (admin-income-standard.js와 동일한 패턴) */
async function adminHousingPolicyFetch(url, options = {}) {
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
async function adminHousingPolicyUnwrap(res, fallback) {
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

/**
 * 전체 정책 (id 순서 = 리포트 표시 순서):
 * [{id, region, agency, name, description, minAge, maxAge, maxAnnualIncome, maxAsset, medianIncomePercent,
 *   requireBasicLivelihood, requireSme, requireNewlywed, requireNoHousehold, loan, note, link, updatedAt}]
 */
async function getHousingPolicies() {
  const res = await adminHousingPolicyFetch(ADMIN_HOUSING_POLICY_API_BASE);
  return (await adminHousingPolicyUnwrap(res, "주거지원정책을 불러오지 못했어요.")) || [];
}

/** 정책 추가: payload는 getHousingPolicies()의 항목에서 id/updatedAt을 뺀 필드. 결과 {policy, notifiedUsers} */
async function createHousingPolicy(payload) {
  const res = await adminHousingPolicyFetch(ADMIN_HOUSING_POLICY_API_BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return adminHousingPolicyUnwrap(res, "추가하지 못했어요. 잠시 후 다시 시도해주세요.");
}

/** 정책 수정(한 줄 저장). 내용이 바뀌면 그 정책을 관심정책으로 담은 회원에게 알림이 간다. 결과 {policy, notifiedUsers} */
async function updateHousingPolicy(id, payload) {
  const res = await adminHousingPolicyFetch(`${ADMIN_HOUSING_POLICY_API_BASE}/${encodeURIComponent(id)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return adminHousingPolicyUnwrap(res, "저장하지 못했어요. 잠시 후 다시 시도해주세요.");
}

/** 정책 삭제. 담았던 회원의 관심정책에서도 빠지고 알림이 간다. 결과 {policy: null, notifiedUsers} */
async function deleteHousingPolicy(id) {
  const res = await adminHousingPolicyFetch(`${ADMIN_HOUSING_POLICY_API_BASE}/${encodeURIComponent(id)}`, { method: "DELETE" });
  return adminHousingPolicyUnwrap(res, "삭제하지 못했어요. 잠시 후 다시 시도해주세요.");
}

window.CustomHouseAdminHousingPolicyApi = { getHousingPolicies, createHousingPolicy, updateHousingPolicy, deleteHousingPolicy };
