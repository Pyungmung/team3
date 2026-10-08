/**
 * [담당: 양혜승] API 통신 - 관심 매물(WatchList) & 알림
 * 연동 대상 백엔드: domain/watchlist, domain/notification (담당: 김시연) - JWT 인증 필요
 */
const WATCHLIST_ORIGIN = ["localhost", "127.0.0.1"].includes(location.hostname) ? "http://localhost:8080" : "https://team3-q05z.onrender.com";
const WATCHLIST_API_BASE = `${WATCHLIST_ORIGIN}/api/watchlist`;
const NOTIFICATION_API_BASE = `${WATCHLIST_ORIGIN}/api/notifications`;
const LISTING_API_BASE = `${WATCHLIST_ORIGIN}/api/listings`;

/** Authorization 헤더를 자동으로 붙이고, 401이면 refreshToken으로 한 번 재시도한다. (mypage.js와 동일한 패턴) */
async function watchlistFetch(url, options = {}) {
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

async function unwrap(res) {
  const body = await res.json();
  if (!res.ok || body.success === false) {
    throw new Error(body.message || "요청이 실패했습니다.");
  }
  return body.data;
}

async function getNotifications() {
  const res = await watchlistFetch(NOTIFICATION_API_BASE);
  return unwrap(res);
}

async function getUnreadNotificationCount() {
  const res = await watchlistFetch(`${NOTIFICATION_API_BASE}/unread-count`);
  const data = await unwrap(res);
  return data.count;
}

async function markNotificationAsRead(notificationId) {
  const res = await watchlistFetch(`${NOTIFICATION_API_BASE}/${notificationId}/read`, { method: "PATCH" });
  return unwrap(res);
}

/** 알림 1건 삭제 (본인 알림만) - 알림함의 오른쪽 삭제 버튼. */
async function deleteNotification(notificationId) {
  const res = await watchlistFetch(`${NOTIFICATION_API_BASE}/${notificationId}`, { method: "DELETE" });
  return unwrap(res);
}

// ---------- 추천 매물(더미 매물 리포트) 관심매물 / 허위매물 신고 ----------
// 추천 매물은 CSV에서 오고 매물등록번호(listing_id)로 식별한다. 관심매물은 리포트 카드의 하트로만 담는다
// (예전 "관심 매물 직접 등록/가격 재확인" 방식은 삭제됨). 담은 매물의 가격 변동/허위매물 경고는 알림함에 쌓인다. (백엔드 domain/listing)

/** 오류 응답에서 사용자에게 보여줄 문구를 뽑는다: 입력값 오류(400)는 data에 {필드: 메시지}가 오니 첫 메시지를 쓴다. */
async function listingUnwrap(res, fallback) {
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    /* 본문이 JSON이 아니면 기본 문구를 쓴다 */
  }
  if (!res.ok || (body && body.success === false)) {
    const fieldMessages = body && body.data && typeof body.data === "object" ? Object.values(body.data) : [];
    const err = new Error(fieldMessages[0] || (body && body.message) || fallback);
    err.code = body && body.code;
    throw err;
  }
  return body ? body.data : null;
}

/** 내가 관심매물로 담은 추천 매물 번호 목록 (리포트 카드의 하트 표시용). */
async function getFavoriteListingIds() {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/listings/ids`);
  return listingUnwrap(res, "관심 매물을 불러오지 못했어요.");
}

/** 마이페이지에 그릴 내 관심 추천 매물 목록 (허위매물 신고 현황 포함). */
async function getFavoriteListings() {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/listings`);
  return listingUnwrap(res, "관심 매물을 불러오지 못했어요.");
}

/** @param {{listingId:string, address?:string, region?:string, leaseType?:string, buildingName?:string, propertyType?:string, unitLabel?:string, deposit?:number, monthlyRent?:number, maintenanceFee?:number}} listing */
async function addListingFavorite(listing) {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/listings`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(listing),
  });
  return listingUnwrap(res, "관심 매물로 담지 못했어요.");
}

/**
 * 관심매물 새로고침 - 매물번호로 그 매물을 내 진단 조건으로 다시 계산해 가격과 카드 전체 정보를 새로 받는다.
 * @param {string} listingId
 * @param {object} condition AI 주거 진단 때 입력한 조건 (localStorage customhouse:lastDiagnosisCondition)
 */
async function refreshListingFavorite(listingId, condition) {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/listings/${encodeURIComponent(listingId)}/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(condition),
  });
  return listingUnwrap(res, "새로고침하지 못했어요.");
}

async function removeListingFavorite(listingId) {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/listings/${encodeURIComponent(listingId)}`, { method: "DELETE" });
  return listingUnwrap(res, "관심 매물에서 빼지 못했어요.");
}

/**
 * 회원이 매물을 직접 등록한다 (로그인 필요). 주소는 검색어 그대로 보내고, 자치구·좌표는 서버(AI 엔진)가 채운다.
 * @param {{addressKeyword:string, propertyType:string, leaseType:string, deposit:number, monthlyRent:number, exclusiveArea:number, buildingName?:string, unitLabel?:string, floor?:string, rooms?:number, bathrooms?:number, builtYear?:number, maintenanceFee?:number, maintenanceFeeItems?:string, parking?:string, elevator?:boolean, moveInDate?:string, description?:string, jeonseLoanAvailable?:boolean, photoUrl?:string}} payload
 * @returns {Promise<{listingId:string, region:string}>}
 */
async function registerListing(payload) {
  const res = await watchlistFetch(LISTING_API_BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return listingUnwrap(res, "매물을 등록하지 못했어요. 잠시 후 다시 시도해주세요.");
}

/**
 * 매물 등록 폼의 대표 사진 1장을 업로드하고 접근 가능한 URL을 받는다 (로그인 필요).
 * 서버는 "/uploads/listings/xxx.jpg"처럼 자기 origin 기준 상대경로만 돌려주므로, 카드가 어디서
 * 렌더링되든(같은 WATCHLIST_ORIGIN을 모르는 다른 페이지라도) 그대로 쓸 수 있게 절대경로로 만들어 돌려준다.
 */
async function uploadListingPhoto(file) {
  const formData = new FormData();
  formData.append("file", file);
  const res = await watchlistFetch(`${LISTING_API_BASE}/photos`, { method: "POST", body: formData });
  const data = await listingUnwrap(res, "사진을 업로드하지 못했어요. 잠시 후 다시 시도해주세요.");
  return `${WATCHLIST_ORIGIN}${data.photoUrl}`;
}

/**
 * 등록한 매물을 삭제한다 (등록한 본인 또는 관리자만 가능 - 서버가 최종 판단). region은 더미 매물처럼
 * 소유권 기록이 없는 매물을 관리자가 지울 때 어느 자치구 CSV인지 알려주는 용도 - 내가 등록한 매물을
 * 지울 때는 서버가 소유권 기록에서 자치구를 알아내므로 안 넘겨도 된다.
 */
async function deleteListing(listingId, region) {
  const query = region ? `?region=${encodeURIComponent(region)}` : "";
  const res = await watchlistFetch(`${LISTING_API_BASE}/${encodeURIComponent(listingId)}${query}`, { method: "DELETE" });
  return listingUnwrap(res, "매물을 삭제하지 못했어요. 잠시 후 다시 시도해주세요.");
}

/** 내가 등록한 매물 전부 (진단 조건과 무관 - 마이페이지 "등록한 매물 관리" 탭용, 로그인 필요). */
async function getMyListings() {
  const res = await watchlistFetch(`${LISTING_API_BASE}/mine`);
  return (await listingUnwrap(res, "등록한 매물을 불러오지 못했어요.")) || [];
}

/** 매물 1건의 원본 정보 (수정 폼 프리필용). "/api/listings/**"가 로그인 필요라 이 조회도 로그인 필요. */
async function getListing(listingId) {
  const res = await watchlistFetch(`${LISTING_API_BASE}/${encodeURIComponent(listingId)}`);
  return listingUnwrap(res, "매물 정보를 불러오지 못했어요.");
}

/** 등록한 매물을 수정한다 (등록한 본인만 가능 - 관리자도 예외 없음). payload는 registerListing과 동일. */
async function updateListing(listingId, payload) {
  const res = await watchlistFetch(`${LISTING_API_BASE}/${encodeURIComponent(listingId)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return listingUnwrap(res, "매물을 수정하지 못했어요. 잠시 후 다시 시도해주세요.");
}

/**
 * 허위매물 신고 (로그인 필요, 회원당 매물 1번). 성공하면 이 매물의 신고 현황 {count, flagged}를 돌려준다.
 * @param {string} listingId
 * @param {{reportType:"FAKE_PRICE"|"ALREADY_SOLD"|"WRONG_INFO"|"PHOTO_MISMATCH"|"OTHER", message:string, contactEmail?:string, address?:string, leaseType?:string, deposit?:number, monthlyRent?:number}} payload
 */
async function reportListing(listingId, payload) {
  const res = await watchlistFetch(`${LISTING_API_BASE}/${encodeURIComponent(listingId)}/reports`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return listingUnwrap(res, "신고를 보내지 못했어요. 잠시 후 다시 시도해주세요.");
}

/** 매물번호 여러 개의 허위매물 신고 현황 {listingId: {count, flagged}} (신고가 있는 매물만). 로그인 없이 조회된다. */
async function getListingReportStatuses(listingIds) {
  if (!listingIds.length) return {};
  const res = await fetch(`${LISTING_API_BASE}/reports/counts`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ listingIds: listingIds.slice(0, 200) }),
  });
  return (await listingUnwrap(res, "신고 현황을 불러오지 못했어요.")) || {};
}

/** 매물 카드의 "실거래 참고"를 펼칠 때 그 매물의 최근 국토부 실거래를 실시간 조회한다. 로그인 없이 조회된다. */
async function getListingReference(listingId) {
  const res = await fetch(`${LISTING_API_BASE}/${encodeURIComponent(listingId)}/reference`);
  return (await listingUnwrap(res, "실거래 정보를 불러오지 못했어요.")) || { scope: "none", transactions: [] };
}

/** 추천 응답의 "통근 약 ~분"은 직선거리 추정치다. 화면에 보이는 카드에 대해서만 이 API로 그 매물 하나의
 * 정확한 카카오 API 통근시간을 불러와 표시를 갱신한다(listing-card.js의 wireCommuteAutoLoad). 로그인 없이 조회된다. */
async function getListingCommute(listingId, workLat, workLon, transportType) {
  const params = new URLSearchParams({ workLat, workLon });
  if (transportType) params.set("transportType", transportType);
  const res = await fetch(`${LISTING_API_BASE}/${encodeURIComponent(listingId)}/commute?${params}`);
  return listingUnwrap(res, "통근시간을 불러오지 못했어요.");
}

// ---------- 관심정책 (주거진단 리포트 "주거정책 추천" 표의 하트 / 관심매물 페이지 "관심정책 조회") ----------
// 정책은 관리자 수정 > 주거지원정책(DB)에서 관리하고, 정책 id로 가리킨다. 관심정책 목록은 정책의 "현재" 내용을 돌려주므로
// 관리자가 고치면 바로 반영되고, 정책이 삭제되면 목록에서 빠지며 알림함에 알림이 온다. (백엔드 domain/policy, domain/housingpolicy)

/** 내가 담은 정책의 id 목록 (리포트 표의 하트 표시용). */
async function getFavoritePolicyIds() {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/policies/ids`);
  return (await listingUnwrap(res, "관심 정책을 불러오지 못했어요.")) || [];
}

/** 관심매물 페이지에 그릴 내 관심정책 목록(최근에 담은 순): [{policyId, region, agency, name, description, link}] */
async function getFavoritePolicies() {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/policies`);
  return (await listingUnwrap(res, "관심 정책을 불러오지 못했어요.")) || [];
}

/** @param {number} policyId 리포트 표의 정책 한 줄(policies_by_region의 항목)의 id */
async function addPolicyFavorite(policyId) {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/policies`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ policyId }),
  });
  return listingUnwrap(res, "관심 정책으로 담지 못했어요.");
}

async function removePolicyFavorite(policyId) {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/policies/${encodeURIComponent(policyId)}`, { method: "DELETE" });
  return listingUnwrap(res, "관심 정책에서 빼지 못했어요.");
}

window.CustomHouseWatchlistApi = {
  getFavoritePolicyIds,
  getFavoritePolicies,
  addPolicyFavorite,
  removePolicyFavorite,
  getNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
  deleteNotification,
  getFavoriteListingIds,
  getFavoriteListings,
  addListingFavorite,
  removeListingFavorite,
  refreshListingFavorite,
  reportListing,
  getListingReportStatuses,
  getListingReference,
  getListingCommute,
  registerListing,
  deleteListing,
  uploadListingPhoto,
  getMyListings,
  getListing,
  updateListing,
};
