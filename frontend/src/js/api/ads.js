/**
 * [담당: 송귀성] API 통신 - 광고하기 (토스페이먼츠 단건결제 + 매물 광고 접수)
 * 연동 대상 백엔드: domain/ad (AdController, /api/ads/**) - 로그인(JWT) 필요
 *
 * 흐름: 주문 생성(createOrder, 서버가 가격/기간을 정한다) -> 결제 화면 ad-checkout.html(토스 결제위젯/결제창, 결제 후 successUrl로 돌아온다)
 *       -> ad-success.html에서 confirmOrder(결제 승인 + 매물 등록 + 광고 접수).
 * auth.js를 먼저 로드한 페이지에서 쓴다. 토스 SDK는 결제 화면(ad-checkout.html)만 로드한다.
 */
const ADS_API_BASE = `${["localhost", "127.0.0.1"].includes(location.hostname) ? "http://localhost:8080" : "https://team3-q05z.onrender.com"}/api/ads`;

/** Authorization 헤더를 자동으로 붙이고, 401이면 refreshToken으로 한 번 재시도한다. (watchlist.js와 같은 패턴) */
async function adsFetch(url, options = {}) {
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
async function adsUnwrap(res, fallback) {
  let body = null;
  try {
    body = await res.json();
  } catch (e) {
    /* 본문이 JSON이 아니면 기본 문구를 쓴다 */
  }
  if (!res.ok || (body && body.success === false)) {
    const fieldMessages = body && body.data && typeof body.data === "object" ? Object.values(body.data) : [];
    const err = new Error(fieldMessages[0] || (body && body.message) || fallback);
    err.code = (body && body.code) || "ERROR";
    err.status = res.status;
    throw err;
  }
  return body ? body.data : null;
}

/** 광고 가격/노출 기간 (관리자 기타 설정). 반환: {priceWon, periodDays, clientKey} */
async function getConfig() {
  const res = await adsFetch(`${ADS_API_BASE}/config`);
  return adsUnwrap(res, "광고 정보를 불러오지 못했어요.");
}

/**
 * 결제 전 주문 생성. target은 {listingId}(이미 등록한 내 매물) 또는 {listing}(매물 등록 폼 내용 - 결제가 끝나면 서버가 등록한다).
 * 반환: {orderId, orderName, amount, periodDays, clientKey}
 */
async function createOrder(target) {
  const res = await adsFetch(`${ADS_API_BASE}/orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(target),
  });
  return adsUnwrap(res, "광고 주문을 만들지 못했어요. 잠시 후 다시 시도해주세요.");
}

/** 토스 결제창에서 돌아온 뒤 승인. 반환: {orderId, listingId, registered, amount, periodDays, expiresAt} */
async function confirmOrder({ paymentKey, orderId, amount }) {
  const res = await adsFetch(`${ADS_API_BASE}/confirm`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ paymentKey, orderId, amount }),
  });
  return adsUnwrap(res, "결제를 승인하지 못했어요.");
}

/** 내 매물의 광고 현황 [{listingId, active, expiresAt, remainingDays}] */
async function getMyAds() {
  const res = await adsFetch(`${ADS_API_BASE}/mine`);
  return (await adsUnwrap(res, "광고 현황을 불러오지 못했어요.")) || [];
}

/** 토스 결제 후 돌아올 페이지 주소: 지금 페이지가 어느 경로(/src/pages/... 등)에 있든 같은 pages 폴더 기준으로 만든다. */
function adPageUrl(fileName) {
  const idx = location.pathname.indexOf("/pages/");
  const base = idx >= 0 ? location.pathname.slice(0, idx) : "";
  return `${location.origin}${base}/pages/payment/${fileName}`;
}

/** 결제 화면(ad-checkout.html)이 읽는 주문 보관 키: 주문 응답(주문번호/금액/상품명/클라이언트 키)을 이 탭의 sessionStorage에 잠깐 둔다. */
const AD_ORDER_STORAGE_KEY = "customhouse:adOrder";

/**
 * 광고하기 결제를 시작한다: 주문 생성 -> 결제 화면(ad-checkout.html)으로 이동. 결제 화면에서 결제 수단을 고르고 토스 결제를 진행하면
 * 결제 후 ad-success.html로 돌아온다. 주문 생성에 실패하면 오류를 던진다(이동하지 않는다).
 * 결제 화면에서 가격이 보이므로, 호출하는 쪽에서 따로 금액을 확인받을 필요는 없지만 한 번 더 안내하는 건 자유다.
 */
async function payForAd(target) {
  const order = await createOrder(target);
  try {
    sessionStorage.setItem(AD_ORDER_STORAGE_KEY, JSON.stringify(order));
  } catch (e) {
    throw new Error("브라우저가 임시 저장을 막고 있어 결제 화면을 열 수 없어요. 시크릿 모드라면 일반 창에서 다시 시도해주세요.");
  }
  location.href = `${adPageUrl("ad-checkout.html")}?orderId=${encodeURIComponent(order.orderId)}`;
  return true;
}

/** 결제 화면이 읽는 주문 정보 (주소의 orderId와 일치할 때만). 없으면 null. */
function readPendingAdOrder(orderId) {
  try {
    const order = JSON.parse(sessionStorage.getItem(AD_ORDER_STORAGE_KEY) || "null");
    return order && order.orderId === orderId ? order : null;
  } catch (e) {
    return null;
  }
}

window.CustomHouseAdsApi = { getConfig, createOrder, confirmOrder, getMyAds, payForAd, readPendingAdOrder, adPageUrl };
