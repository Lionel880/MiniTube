import { defineStore } from "pinia";
import { login as apiLogin, register as apiRegister, fetchMe } from "../api/auth";

export const useAuthStore = defineStore("auth", {
  state: () => ({
    token: localStorage.getItem("token") || "",
    username: localStorage.getItem("username") || "",
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
  },

  actions: {
    async login(credentials) {
      const data = await apiLogin(credentials);
      this.token = data.token;
      localStorage.setItem("token", data.token);

      // 登入成功後立刻用 /api/auth/me 換回目前使用者名稱，並存起來供畫面顯示
      const me = await fetchMe();
      this.username = me.username;
      localStorage.setItem("username", me.username);
    },

    async register(payload) {
      return apiRegister(payload);
    },

    logout() {
      this.token = "";
      this.username = "";
      localStorage.removeItem("token");
      localStorage.removeItem("username");
    },
  },
});
