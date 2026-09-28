/**
 * [담당: 양혜승] API 통신 - 관심 매물(WatchList) & 알림
 * 연동 대상 백엔드: domain/watchlist, domain/notification (담당: 김시연) - JWT 인증 필요
 */
const WATCHLIST_API_BASE = "http://localhost:8080/api/watchlist";
const NOTIFICATION_API_BASE = "http://localhost:8080/api/notifications";
const LISTING_API_BASE = "http://localhost:8080/api/listings";

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

async function getWatchlist() {
  const res = await watchlistFetch(WATCHLIST_API_BASE);
  return unwrap(res);
}

async function addToWatchlist(property) {
  const res = await watchlistFetch(WATCHLIST_API_BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(property),
  });
  return unwrap(res);
}

async function removeFromWatchlist(watchlistItemId) {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/${watchlistItemId}`, { method: "DELETE" });
  return unwrap(res);
}

/** 데모/테스트용 "가격 재확인" - 실제로는 배치/외부 API가 수행할 시세 재조회를 사용자가 직접 트리거한다. */
async function recheckPrice(propertyId, { deposit, monthlyRent }) {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/properties/${propertyId}/recheck-price`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ deposit, monthlyRent }),
  });
  return unwrap(res);
}

async function reportProperty(propertyId) {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/properties/${propertyId}/report`, { method: "POST" });
  return unwrap(res);
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

// ---------- 추천 매물(더미 매물 리포트) 관심매물 / 허위매물 신고 ----------
// 추천 매물은 CSV에서 오고 매물등록번호(listing_id)로 식별한다. 기존 관심 매물(properties)과는 별개 테이블이지만
// 마이페이지 "관심 매물"(watchlist/list.html)에 함께 보인다. (백엔드 domain/listing)

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

async function removeListingFavorite(listingId) {
  const res = await watchlistFetch(`${WATCHLIST_API_BASE}/listings/${encodeURIComponent(listingId)}`, { method: "DELETE" });
  return listingUnwrap(res, "관심 매물에서 빼지 못했어요.");
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

window.CustomHouseWatchlistApi = {
  getWatchlist,
  addToWatchlist,
  removeFromWatchlist,
  recheckPrice,
  reportProperty,
  getNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
  getFavoriteListingIds,
  getFavoriteListings,
  addListingFavorite,
  removeListingFavorite,
  reportListing,
  getListingReportStatuses,
};
