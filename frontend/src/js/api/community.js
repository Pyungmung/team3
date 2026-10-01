/**
 * [담당: 미정 - 커뮤니티 게시판] API 통신 + 화면 공통 헬퍼
 * 연동 대상 백엔드: domain/board - /api/posts/**, /api/comments/**
 * 조회(GET)는 비로그인도 가능하고, 로그인 상태면 토큰을 함께 보내 내 글/좋아요/스크랩/투표 여부를 받는다.
 * 쓰기 API는 로그인이 필요하다 (토큰이 없으면 요청 없이 UNAUTHORIZED 에러).
 */
const COMMUNITY_API_ROOT = `${["localhost", "127.0.0.1"].includes(location.hostname) ? "http://localhost:8080" : "https://team3-q05z.onrender.com"}/api`;

const COMMUNITY_CATEGORIES = [
  { key: "HOUSING", label: "집 구하기 고민", desc: "매물 비교, 계약 전 고민을 나눠요" },
  { key: "INTERIOR", label: "인테리어 고민", desc: "내 방 사진을 올리고 조언을 받아요" },
  { key: "SAFETY", label: "전세사기·법률", desc: "익명으로 묻고 답변을 채택해요" },
  { key: "COMMUNITY", label: "자취 꿀팁·수다", desc: "자취 노하우와 일상 이야기" },
];

/**
 * auth: "none"(토큰 안 보냄) | "optional"(있으면 보냄) | "required"(없으면 에러).
 * 401이면 refreshToken으로 한 번 재시도하고, optional인데 갱신도 실패하면 비로그인으로 다시 요청한다.
 */
async function communityFetch(path, { method = "GET", body, auth = "none" } = {}) {
  const token = CustomHouseAuthApi.getAccessToken();
  if (auth === "required" && !token) {
    const err = new Error("로그인이 필요합니다.");
    err.code = "UNAUTHORIZED";
    throw err;
  }

  const send = (accessToken) =>
    fetch(COMMUNITY_API_ROOT + path, {
      method,
      headers: {
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

  let res = await send(auth === "none" ? null : token);
  if (res.status === 401 && token && auth !== "none") {
    try {
      const refreshed = await CustomHouseAuthApi.refreshAccessToken();
      res = await send(refreshed.accessToken);
    } catch {
      if (auth === "required") {
        const err = new Error("로그인이 만료되었습니다. 다시 로그인해주세요.");
        err.code = "UNAUTHORIZED";
        throw err;
      }
      res = await send(null);
    }
  }

  let json = null;
  try {
    json = await res.json();
  } catch {
    /* 본문 없음 */
  }
  if (!res.ok || (json && json.success === false)) {
    const err = new Error((json && json.message) || "요청이 실패했습니다.");
    err.status = res.status;
    err.code = json && json.code;
    throw err;
  }
  return json ? json.data : null;
}

function listPosts({ category, page = 0, size = 10, sort = "latest" } = {}) {
  const q = new URLSearchParams({ page, size, sort });
  if (category) q.set("category", category);
  return communityFetch(`/posts?${q}`, { auth: "optional" });
}

function listMyScraps({ page = 0, size = 10 } = {}) {
  return communityFetch(`/posts/scraps?page=${page}&size=${size}`, { auth: "required" });
}

const getPost = (id) => communityFetch(`/posts/${id}`, { auth: "optional" });
const createPost = (payload) => communityFetch("/posts", { method: "POST", body: payload, auth: "required" });
const updatePost = (id, payload) => communityFetch(`/posts/${id}`, { method: "PUT", body: payload, auth: "required" });
const deletePost = (id) => communityFetch(`/posts/${id}`, { method: "DELETE", auth: "required" });
const addComment = (id, content, parentId) =>
  communityFetch(`/posts/${id}/comments`, { method: "POST", body: { content, parentId: parentId || null }, auth: "required" });
const selectComment = (commentId) => communityFetch(`/comments/${commentId}/select`, { method: "POST", auth: "required" });
const updateComment = (commentId, content) => communityFetch(`/comments/${commentId}`, { method: "PUT", body: { content }, auth: "required" });
const deleteComment = (commentId) => communityFetch(`/comments/${commentId}`, { method: "DELETE", auth: "required" });
const votePost = (id, optionId) => communityFetch(`/posts/${id}/vote`, { method: "POST", body: { optionId }, auth: "required" });
const toggleLike = (id) => communityFetch(`/posts/${id}/like`, { method: "POST", auth: "required" });
const toggleScrap = (id) => communityFetch(`/posts/${id}/scrap`, { method: "POST", auth: "required" });

// ---- 화면 공통 헬퍼 ----

/** 사용자가 입력한 값을 innerHTML 템플릿에 넣기 전에 반드시 거친다 (XSS 방지). */
function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/** http(s) 주소만 통과시킨다 (javascript: 같은 스킴 차단). 아니면 빈 문자열. */
function safeUrl(url) {
  return /^https?:\/\//i.test(String(url || "").trim()) ? String(url).trim() : "";
}

/**
 * list.html의 왼쪽 카테고리 사이드 카드(.side-card)와 같은 마크업. detail.html/write.html에서도
 * 이동 중 계속 보이도록 그대로 재사용한다 (list.html은 탭 클릭 시 그 안에서 필터링하지만, 여기서는
 * 단순 링크로 list.html?tab=키 로 이동한다). activeKey가 없으면 어떤 탭도 강조하지 않는다.
 */
function communitySidebarHtml(activeKey) {
  const TABS = [{ key: "ALL", label: "전체" }, ...COMMUNITY_CATEGORIES, { key: "SCRAPS", label: "내 스크랩" }];
  return `
    <aside class="side-card">
      <div class="side-head">
        <div class="side-icon">💬</div>
        <div>
          <div class="side-title">커뮤니티</div>
          <div class="side-note">고민을 나누고 함께 답을 찾아요.</div>
        </div>
      </div>
      <nav class="side-nav" id="community-side-nav">
        ${TABS.map((t) => `<a href="list.html?tab=${t.key}" data-tab-key="${t.key}" class="${t.key === activeKey ? "active" : ""}">${esc(t.label)}</a>`).join("")}
      </nav>
      <a href="write.html" class="write-side-btn">+ 글쓰기</a>
    </aside>`;
}

/** communitySidebarHtml로 그린 사이드바에서 강조된 탭만 바꾼다 (예: 글쓰기 화면에서 게시판 선택이 바뀔 때). */
function setCommunitySidebarActive(activeKey) {
  const nav = document.getElementById("community-side-nav");
  if (!nav) return;
  nav.querySelectorAll("[data-tab-key]").forEach((a) => a.classList.toggle("active", a.dataset.tabKey === activeKey));
}

function categoryLabel(key) {
  const c = COMMUNITY_CATEGORIES.find((x) => x.key === key);
  return c ? c.label : key;
}

function timeAgo(iso) {
  if (!iso) return "";
  const diff = (Date.now() - new Date(iso).getTime()) / 1000;
  if (diff < 60) return "방금 전";
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`;
  if (diff < 86400 * 7) return `${Math.floor(diff / 86400)}일 전`;
  return new Date(iso).toLocaleDateString("ko-KR");
}

window.CustomHouseCommunityApi = {
  listPosts,
  listMyScraps,
  getPost,
  createPost,
  updatePost,
  deletePost,
  addComment,
  selectComment,
  updateComment,
  deleteComment,
  votePost,
  toggleLike,
  toggleScrap,
  esc,
  safeUrl,
  communitySidebarHtml,
  setCommunitySidebarActive,
  categoryLabel,
  timeAgo,
  CATEGORIES: COMMUNITY_CATEGORIES,
};
