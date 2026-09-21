(function () {
  async function request(url, options = {}) {
    const response = await mypageFetch(url, options);

    let body = null;

    try {
      body = await response.json();
    } catch {
      body = null;
    }

    if (!response.ok || (body && body.success === false)) {
      const message =
        body?.message ||
        body?.error ||
        "요청을 처리하지 못했습니다.";

      const error = new Error(message);

      error.status = response.status;

      // 백엔드에 아직 API가 구현되지 않은 경우
      const isNotReady =
        response.status === 404 ||
        response.status === 405 ||
        message.includes("No static resource") ||
        message.includes("Request method") ||
        message.includes("not supported");

      if (isNotReady) {
        error.status = 404;
        error.message = "준비 중";
      }

      throw error;
    }

    return body?.data ?? null;
  }

  window.CustomHouseUserApi = {
    // 내 정보 조회
    getMe() {
      return request("http://localhost:8080/api/users/me", {
        method: "GET",
      });
    },

    // 회원정보 수정
    updateMe({ nickname, phone }) {
      return request("http://localhost:8080/api/users/me", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          nickname,
          phone,
        }),
      });
    },

    // 비밀번호 변경
    changePassword({ currentPassword, newPassword }) {
      return request(
        "http://localhost:8080/api/users/me/password",
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            currentPassword,
            newPassword,
          }),
        }
      );
    },

    // 알림 설정
    setNotification(enabled) {
      return request(
        "http://localhost:8080/api/mypage/notification",
        {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            notificationEnabled: enabled,
          }),
        }
      );
    },
  };
})();