/**
 * [담당: 양혜승] API 통신 - AI 주거비 절약 진단 & 추천
 * 백엔드(Spring Boot, domain/recommendation - 담당: 송귀성)의
 * POST /api/recommendation/diagnosis 를 호출한다.
 */
const RECOMMENDATION_API_BASE = "http://localhost:8080/api";

/**
 * 사용자 주거 조건을 서버로 보내 진단 결과를 받아온다.
 * @param {{annualIncome:number, coupleAnnualIncome:number|null, deposit:number, desiredDeposit:number|null, desiredRent:number|null, workLocation:string, workLat:number|null, workLon:number|null, maxCommuteMinutes:number, age:number|null, assets:number|null, noHouseholder:boolean|null, jobType:string|null, preferentialStatuses:string[]}} condition
 * @returns {Promise<object>} 백엔드 ApiResponse.data (진단 결과)
 */
async function requestDiagnosis(condition) {
  const res = await fetch(`${RECOMMENDATION_API_BASE}/recommendation/diagnosis`, {
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

// eslint-disable-next-line no-unused-vars
window.CustomHouseRecommendationApi = { requestDiagnosis };
