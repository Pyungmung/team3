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
      const error = new Error(
        body?.message || "요청을 처리하지 못했습니다."
      );
      error.status = response.status;
      throw error;
    }

    return body?.data ?? null;
  }

  window.CustomHouseUserApi = {
    getMe() {
      return request("http://localhost:8080/api/users/me", {
        method: "GET",
      });
    },

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

    changePassword({ currentPassword, newPassword }) {
      return request("http://localhost:8080/api/users/me/password", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          currentPassword,
          newPassword,
        }),
      });
    },

    setNotification(enabled) {
      return request("http://localhost:8080/api/mypage/notification", {
        method: "PUT",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          notificationEnabled: enabled,
        }),
      });
    },
  };
})();