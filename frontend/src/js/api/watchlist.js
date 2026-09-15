/**
 * [담당: 양혜승] API 통신 - 관심 매물(WatchList) & 알림
 * 연동 대상 백엔드: domain/watchlist, domain/notification (담당: 김시연) - JWT 인증 필요
 */
const WATCHLIST_API_BASE = "http://localhost:8080/api/watchlist";
const NOTIFICATION_API_BASE = "http://localhost:8080/api/notifications";

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

window.CustomHouseWatchlistApi = {
  getWatchlist,
  addToWatchlist,
  removeFromWatchlist,
  recheckPrice,
  reportProperty,
  getNotifications,
  getUnreadNotificationCount,
  markNotificationAsRead,
};
