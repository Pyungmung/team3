/**
 * [담당: 양혜승] API 통신 - AI 주거비 절약 진단 & 추천
 * 백엔드(Spring Boot, domain/recommendation - 담당: 송귀성)의
 * POST /api/recommendation/diagnosis (국토부 실거래가 기준 리포트) 와
 * POST /api/recommendation/diagnosis/listings (더미 매물 기반 추천 리포트, 2026-09-28) 를 호출한다.
 */
const RECOMMENDATION_API_BASE = "http://localhost:8080/api";

/**
 * 사용자 주거 조건을 서버로 보내 진단 결과를 받아온다.
 * @param {{annualIncome:number, coupleAnnualIncome:number|null, deposit:number, desiredDeposit:number|null, desiredRent:number|null, workLocation:string, workLat:number|null, workLon:number|null, maxCommuteMinutes:number, age:number|null, assets:number|null, noHouseholder:boolean|null, jobType:string|null, preferentialStatuses:string[], moveSchedule:string|null, transportType:string|null, useLoanPolicy:boolean, preferredBuildingTypes:string[]}} condition
 * @returns {Promise<object>} 백엔드 ApiResponse.data (진단 결과)
 */
async function requestDiagnosis(condition) {
  return postDiagnosis("/recommendation/diagnosis", condition);
}

/**
 * 같은 조건으로 더미 매물(docs/samples/dummyhouses CSV) 기반 추천을 받아온다. 실거래가는 매물별 참고
 * 정보(reference_transaction)로만 내려온다. 응답 매물 필드는 report-listings.html 참고.
 * @param {object} condition requestDiagnosis와 같은 조건 객체
 * @returns {Promise<object>} 백엔드 ApiResponse.data
 */
async function requestListingDiagnosis(condition) {
  return postDiagnosis("/recommendation/diagnosis/listings", condition);
}

async function postDiagnosis(path, condition) {
  const res = await fetch(`${RECOMMENDATION_API_BASE}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(condition),
  });

  const body = await res.json();

  if (!res.ok || body.success === false) {
    throw new Error(body.message || "진단 요청에 실패했습니다.");
  }

  return body.data;
}

/**
 * 리포트의 "정책정보 정정신고" 팝업에서 정책 정보 오류/누락을 관리자 이메일로 보낸다 (백엔드 POST /api/support/policy-corrections).
 * 받는 사람(관리자 주소)은 서버 설정이라 여기서 정하지 않는다.
 * @param {{name:string, email:string, correctionType:"INFO_ERROR"|"EXPIRED"|"MISSING"|"OTHER", policyId?:string, policyName?:string, policyAgency?:string, policyRegion?:string, currentDescription?:string, message:string}} payload
 */
async function submitPolicyCorrection(payload) {
  const res = await fetch(`${RECOMMENDATION_API_BASE}/support/policy-corrections`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });

  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    /* 본문이 JSON이 아니면 아래 기본 문구를 쓴다 */
  }

  if (!res.ok || (body && body.success === false)) {
    // 입력값 오류(400)는 data에 {필드: 메시지}가 오니 첫 메시지를 보여준다
    const fieldMessages = body && body.data && typeof body.data === "object" ? Object.values(body.data) : [];
    throw new Error(fieldMessages[0] || (body && body.message) || "정정신고를 보내지 못했어요. 잠시 후 다시 시도해주세요.");
  }
}

// eslint-disable-next-line no-unused-vars
window.CustomHouseRecommendationApi = { requestDiagnosis, requestListingDiagnosis, submitPolicyCorrection };
