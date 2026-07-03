import { defineStore } from "pinia";
import { login as apiLogin, register as apiRegister, fetchMe, logout as apiLogout } from "../api/auth";
import { setMemoryToken } from "../api/http";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    username: localStorage.getItem("username") || "",
  }),

  getters: {
    isLoggedIn: (state) => !!state.username,
  },

  actions: {
    async login(credentials) {
      const data = await apiLogin(credentials);

      // 後端同時回傳 token (body) + 設定 HttpOnly Cookie
      // 將 token 存入記憶體，供 Authorization header 作為備援使用
      if (data.token) {
        setMemoryToken(data.token);
      }

      // 使用回應中的 username，或退回使用輸入的帳號
      this.username = data.username || credentials.username;
      localStorage.setItem("username", this.username);
    },

    /** 頁面重新整理時用 Cookie 恢復登入狀態 */
    async restoreSession() {
      try {
        const me = await fetchMe();
        this.username = me.username;
        localStorage.setItem("username", me.username);
      } catch {
        // Cookie 已失效，清除本機登入狀態
        this.username = "";
        setMemoryToken("");
        localStorage.removeItem("username");
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
    },
  },
});
