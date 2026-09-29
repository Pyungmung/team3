/**
 * [담당: 송귀성] API 통신 - 관리자 수정 > 전세자금대출 조건
 * 연동 대상 백엔드: domain/loan (AdminLoanController, /api/admin/loans) - 관리자(ADMIN) JWT만 접근 가능
 */
const ADMIN_LOANS_API_BASE = "http://localhost:8080/api/admin/loans";

/** Authorization 헤더를 자동으로 붙이고, 401이면 refreshToken으로 한 번 재시도한다. (watchlist.js와 동일한 패턴) */
async function adminLoansFetch(url, options = {}) {
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
async function adminLoansUnwrap(res, fallback) {
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

/** 대출 5종의 현재 조건 + 화면이 그릴 우대사항 목록: {preferenceOptions:[{key,label}], loans:[...]} */
async function getLoans() {
  const res = await adminLoansFetch(ADMIN_LOANS_API_BASE);
  return adminLoansUnwrap(res, "대출 조건을 불러오지 못했어요.");
}

/** 수정완료: 조건 저장. payload = {minAge, maxAge, maxIncomeSingle, maxIncomeCouple, maxAsset, maxExclusiveArea, preferences:{KEY:{required, discount}}} */
async function saveLoan(type, payload) {
  const res = await adminLoansFetch(`${ADMIN_LOANS_API_BASE}/${encodeURIComponent(type)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return adminLoansUnwrap(res, "저장하지 못했어요. 잠시 후 다시 시도해주세요.");
}

/** 삭제: 저장된 조건을 지운다. */
async function deleteLoan(type) {
  const res = await adminLoansFetch(`${ADMIN_LOANS_API_BASE}/${encodeURIComponent(type)}`, { method: "DELETE" });
  return adminLoansUnwrap(res, "삭제하지 못했어요. 잠시 후 다시 시도해주세요.");
}

/** 참고 확인 페이지 주소만 저장한다 (대출 자격 조건과 무관 - 그냥 참고용 링크 보관). 빈 문자열을 보내면 저장된 주소를 지운다. */
async function saveLoanReferenceUrl(type, referenceUrl) {
  const res = await adminLoansFetch(`${ADMIN_LOANS_API_BASE}/${encodeURIComponent(type)}/reference-url`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ referenceUrl }),
  });
  return adminLoansUnwrap(res, "참고 페이지 주소를 저장하지 못했어요. 잠시 후 다시 시도해주세요.");
}

window.CustomHouseAdminLoansApi = { getLoans, saveLoan, deleteLoan, saveLoanReferenceUrl };
