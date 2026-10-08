/**
 * [담당: 송귀성] API 통신 - 관리자 수정 > 광고 현황 (광고 주문 목록, 환불)
 * 연동 대상 백엔드: domain/ad (AdminAdController, /api/admin/ads) - 관리자(ADMIN) JWT만 접근 가능
 * admin-app-settings.js와 같은 fetch/401 재시도/에러 변환 패턴이다.
 */
const ADMIN_ADS_API_BASE = `${["localhost", "127.0.0.1"].includes(location.hostname) ? "http://localhost:8080" : "https://team3-q05z.onrender.com"}/api/admin/ads`;

async function adminAdsFetch(url, options = {}) {
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

async function adminAdsUnwrap(res, fallback) {
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    /* 본문이 JSON이 아니면 기본 문구를 쓴다 */
  }
  if (!res.ok || (body && body.success === false)) {
    const err = new Error((body && body.message) || fallback);
    err.code = (body && body.code) || (res.status === 403 ? "FORBIDDEN" : "ERROR");
    err.status = res.status;
    throw err;
  }
  return body ? body.data : null;
}

/** 광고 주문 전체(최신순): [{orderId, userId, userEmail, listingId, amount, periodDays, status, failReason, createdAt, adExpiresAt, adActive}] */
async function listAdOrders() {
  const res = await adminAdsFetch(ADMIN_ADS_API_BASE);
  return (await adminAdsUnwrap(res, "광고 현황을 불러오지 못했어요.")) || [];
}

/** 환불: 토스 결제 전액 취소 + 그 주문이 늘려 준 광고 기간 차감. 갱신된 주문 한 줄을 돌려준다. */
async function refundAdOrder(orderId) {
  const res = await adminAdsFetch(`${ADMIN_ADS_API_BASE}/${encodeURIComponent(orderId)}/cancel`, { method: "POST" });
  return adminAdsUnwrap(res, "환불하지 못했어요. 잠시 후 다시 시도해주세요.");
}

window.CustomHouseAdminAdsApi = { listAdOrders, refundAdOrder };
