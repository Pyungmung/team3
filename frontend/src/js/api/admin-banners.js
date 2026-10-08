/**
 * [담당: 송귀성] API 통신 - 관리자 수정 > 배너 광고 (직접 광고 배너 목록/등록/수정/삭제)
 * 연동 대상 백엔드: domain/banner (AdminBannerController, /api/admin/banners) - 관리자(ADMIN) JWT만 접근 가능
 * admin-ads.js와 같은 fetch/401 재시도/에러 변환 패턴이다. 방문자에게 보이는 공개 목록은 GET /api/banners (ad-slot.js가 사용).
 */
const ADMIN_BANNERS_API_BASE = `${["localhost", "127.0.0.1"].includes(location.hostname) ? "http://localhost:8080" : "https://team3-q05z.onrender.com"}/api/admin/banners`;

async function adminBannersFetch(url, options = {}) {
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
async function adminBannersUnwrap(res, fallback) {
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

/** 전체 배너(꺼진 것/기간 지난 것 포함): [{id, title, category, imageUrl, linkUrl, altText, segment, regions[], moveWithin, slots[], startsOn, endsOn, active, sortOrder}] */
async function listBanners() {
  const res = await adminBannersFetch(ADMIN_BANNERS_API_BASE);
  return (await adminBannersUnwrap(res, "배너 목록을 불러오지 못했어요.")) || [];
}

/** 등록 (payload는 listBanners 항목과 같은 모양, id 제외) */
async function createBanner(payload) {
  const res = await adminBannersFetch(ADMIN_BANNERS_API_BASE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return adminBannersUnwrap(res, "배너를 등록하지 못했어요. 잠시 후 다시 시도해주세요.");
}

async function updateBanner(id, payload) {
  const res = await adminBannersFetch(`${ADMIN_BANNERS_API_BASE}/${encodeURIComponent(id)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  return adminBannersUnwrap(res, "배너를 수정하지 못했어요. 잠시 후 다시 시도해주세요.");
}

async function deleteBanner(id) {
  const res = await adminBannersFetch(`${ADMIN_BANNERS_API_BASE}/${encodeURIComponent(id)}`, { method: "DELETE" });
  return adminBannersUnwrap(res, "배너를 삭제하지 못했어요. 잠시 후 다시 시도해주세요.");
}

window.CustomHouseAdminBannersApi = { listBanners, createBanner, updateBanner, deleteBanner };
