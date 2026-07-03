import { defineStore } from "pinia";
import { login as apiLogin, register as apiRegister, fetchMe, logout as apiLogout } from "../api/auth";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    username: localStorage.getItem("username") || "",
  }),

  getters: {
    isLoggedIn: (state) => !!state.username,
  },

  actions: {
    async login(credentials) {
      await apiLogin(credentials);

      // 登入成功後立刻用 /api/auth/me 換回目前使用者名稱，並存起來供畫面顯示
      const me = await fetchMe();
      this.username = me.username;
      localStorage.setItem("username", me.username);
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
      localStorage.removeItem("username");
    },
  },
});
