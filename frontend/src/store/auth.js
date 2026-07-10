import { defineStore } from "pinia";
import { login as apiLogin, register as apiRegister, fetchMe, logout as apiLogout } from "../api/auth";
import { setMemoryToken } from "../api/http";

export const useAuthStore = defineStore("auth", {
  state: () => {
    const savedToken = localStorage.getItem("minitube_token");
    return {
      username: savedToken ? (localStorage.getItem("username") || "") : "",
    };
  },

  getters: {
    isLoggedIn: (state) => !!state.username && !!localStorage.getItem("minitube_token"),
  },

  actions: {
    async login(credentials) {
      const data = await apiLogin(credentials);

      // 後端同時回傳 token (body) + 設定 HttpOnly Cookie
      // 將 token 存入記憶體與 localStorage 作為跨網域備援
      if (data.token) {
        setMemoryToken(data.token);
        localStorage.setItem("minitube_token", data.token);
      }

      // 使用回應中的 username，或退回使用輸入的帳號
      this.username = data.username || credentials.username;
      localStorage.setItem("username", this.username);
    },

    /** 頁面重新整理時恢復登入狀態 */
    async restoreSession() {
      const savedToken = localStorage.getItem("minitube_token");
      if (!savedToken) {
        this.username = "";
        localStorage.removeItem("username");
        return;
      }

      setMemoryToken(savedToken);

      try {
        const me = await fetchMe();
        this.username = me.username;
        localStorage.setItem("username", me.username);
      } catch {
        // 憑證已失效，清除本機登入狀態
        this.username = "";
        setMemoryToken("");
        localStorage.removeItem("username");
        localStorage.removeItem("minitube_token");
      }
    },

    async register(payload) {
      return apiRegister(payload);
    },

    async logout() {
      try {
        await apiLogout();
      } catch (err) {
        // 忽略登出網路錯誤
      }
      this.username = "";
      setMemoryToken("");
      localStorage.removeItem("username");
      localStorage.removeItem("minitube_token");
    },
  },
});
