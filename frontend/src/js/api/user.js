/**
 * [담당: 양혜승] API 통신 - 마이페이지 회원정보 (조회/수정/비밀번호 변경/알림 설정)
 * 연동 대상 백엔드: domain/user (담당: 허겸) - /api/users/me/** (JWT 인증 필요), domain/mypage - 알림 수신 여부
 */
const USER_API_BASE = "http://localhost:8080/api/users";
const USER_NICKNAME_CACHE_KEY = "customhouse:nicknameCache"; // header.js와 같은 키 (헤더 닉네임 캐시)

/** Authorization 헤더를 붙이고 401이면 refreshToken으로 한 번 재시도한다. 실패 시 status/code가 담긴 Error를 던진다. */
async function userFetch(path, { method = "GET", body } = {}) {
  const token = CustomHouseAuthApi.getAccessToken();
  if (!token) {
    const err = new Error("로그인이 필요합니다.");
    err.code = "UNAUTHORIZED";
    throw err;
  }

  const send = (accessToken) =>
    fetch(USER_API_BASE + path, {
      method,
      headers: {
        Authorization: `Bearer ${accessToken}`,
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

  let res = await send(token);
  if (res.status === 401) {
    try {
      res = await send((await CustomHouseAuthApi.refreshAccessToken()).accessToken);
    } catch {
      const err = new Error("로그인이 만료되었습니다. 다시 로그인해주세요.");
      err.code = "UNAUTHORIZED";
      throw err;
    }
  }

  let json = null;
  try {
    json = await res.json();
  } catch {
    /* 본문 없음 */
  }
  if (!res.ok || (json && json.success === false)) {
    // 입력값 검증 실패면 data에 {필드: 메시지}가 담겨오므로 첫 메시지를 보여준다 (message는 "입력값이 올바르지 않습니다."로 뭉뚱그려짐)
    const fieldMsg = json && json.data && typeof json.data === "object" ? Object.values(json.data)[0] : null;
    const err = new Error(fieldMsg || (json && json.message) || "요청이 실패했습니다.");
    err.status = res.status;
    err.code = json && json.code;
    throw err;
  }
  return json ? json.data : null;
}

const getMe = () => userFetch("/me");

/** 이름/휴대폰 수정. 성공하면 헤더의 닉네임 캐시도 함께 갱신한다. */
async function updateMe({ nickname, phone }) {
  const user = await userFetch("/me", { method: "PUT", body: { nickname, phone: phone || null } });
  try {
    localStorage.setItem(USER_NICKNAME_CACHE_KEY, JSON.stringify({ email: user.email, nickname: user.nickname }));
  } catch {
    /* 캐시는 없어도 헤더가 서버에서 다시 읽어온다 */
  }
  return user;
}

const changePassword = ({ currentPassword, newPassword }) =>
  userFetch("/me/password", { method: "PUT", body: { currentPassword, newPassword } });

/** 회원 탈퇴. 성공하면 저장된 토큰/닉네임 캐시를 지워서 다음 페이지 이동 시 로그아웃 상태가 되게 한다. */
async function deleteAccount(password) {
  await userFetch("/me", { method: "DELETE", body: { password: password || null } });
  CustomHouseAuthApi.clearTokens();
  try {
    localStorage.removeItem(USER_NICKNAME_CACHE_KEY);
  } catch {
    /* 캐시는 없어도 무방 */
  }
}

/**
 * 관심 매물 변동 알림 수신 여부. 이 값은 주거 조건(housing_conditions)에 저장되므로 조건이 먼저 있어야 한다.
 * 저장된 조건을 읽어 알림 값만 바꿔 다시 저장한다 (다른 항목은 그대로 유지).
 */
async function setNotification(enabled) {
  const condition = await CustomHouseMypageApi.getMyCondition();
  if (!condition) {
    throw new Error("주거 조건을 먼저 저장해야 알림을 설정할 수 있어요.");
  }
  return CustomHouseMypageApi.updateMyCondition({ ...condition, notificationEnabled: enabled });
}

/**
 * 광고·마케팅 목적 개인정보 수집·이용 동의 여부 (선택). 회원(User) 엔티티에 바로 저장되는
 * 별도 엔드포인트라 notificationEnabled와 달리 주거 조건이 없어도(회원가입 직후에도) 바로 쓸 수 있다.
 */
const setMarketingConsent = (enabled) =>
  userFetch("/me/marketing-consent", { method: "PUT", body: { marketingConsent: enabled } });

window.CustomHouseUserApi = { getMe, updateMe, changePassword, deleteAccount, setNotification, setMarketingConsent };
