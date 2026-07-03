import axios from "axios";

// 統一的 axios 實例。baseURL 使用相對路徑 "/api"：
// - 開發模式下由 vite.config.js 的 proxy 轉發到 http://localhost:8080
// - 若之後把前後端部署在同一個網域，也不需要修改任何程式碼
const http = axios.create({
  baseURL: "/api",
  timeout: 30000,
  withCredentials: true, // 啟用跨網域傳遞 Cookie (例如 GitHub Pages -> Render)
});

// 每次發送請求前，自動攜帶 Cookie (不需前端手動寫入 Authorization header)
http.interceptors.request.use((config) => {
  return config;
});

// 統一錯誤格式：後端 GlobalExceptionHandler 回傳 { status, message, path, timestamp }
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message =
      error.response?.data?.message ||
      error.message ||
      "發生未知錯誤，請稍後再試";

    // 401：token 失效或未登入，清除本機登入狀態
    if (error.response?.status === 401) {
      localStorage.removeItem("token");
      localStorage.removeItem("username");
    }

    return Promise.reject(new Error(message));
  }
);

export default http;
