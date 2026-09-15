/**
 * [담당: 양혜승] API 통신 - 토스페이먼츠 결제 연동
 * 연동 대상 백엔드: domain/payment (담당: 황진구) - /api/payments/** (JWT 인증 필요)
 */
const PAYMENT_API_BASE = "http://localhost:8080/api/payments";

/** Authorization 헤더를 자동으로 붙이고, 401이면 refreshToken으로 한 번 재시도한다. (mypage.js와 동일한 패턴) */
async function paymentFetch(url, options = {}) {
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

/** 안심 매물 리포트 단건 결제 승인. successUrl에서 받은 {paymentKey, orderId, amount}를 그대로 전달. */
async function confirmPayment({ paymentKey, orderId, amount }) {
  const res = await paymentFetch(`${PAYMENT_API_BASE}/confirm`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ paymentKey, orderId, amount }),
  });
  return unwrap(res);
}

/** 자동결제 수단(빌링키) 등록. requestBillingAuth() successUrl에서 받은 {customerKey, authKey}를 그대로 전달. */
async function issueBillingKey({ customerKey, authKey }) {
  const res = await paymentFetch(`${PAYMENT_API_BASE}/subscriptions/billing-key`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ customerKey, authKey }),
  });
  return unwrap(res);
}

async function getMySubscription() {
  const res = await paymentFetch(`${PAYMENT_API_BASE}/subscriptions/me`);
  const body = await res.json();
  if (body.code === "SUBSCRIPTION_NOT_FOUND") return null;
  if (!res.ok || body.success === false) throw new Error(body.message || "구독 조회에 실패했습니다.");
  return body.data;
}

async function chargeSubscriptionNow() {
  const res = await paymentFetch(`${PAYMENT_API_BASE}/subscriptions/charge-now`, { method: "POST" });
  return unwrap(res);
}

async function cancelSubscription() {
  const res = await paymentFetch(`${PAYMENT_API_BASE}/subscriptions`, { method: "DELETE" });
  return unwrap(res);
}

window.CustomHousePaymentApi = {
  confirmPayment,
  issueBillingKey,
  getMySubscription,
  chargeSubscriptionNow,
  cancelSubscription,
};
