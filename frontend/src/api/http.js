import axios from "axios";

// 統一的 axios 實例。baseURL 使用相對路徑 "/api"：
// - 開發模式下由 vite.config.js 的 proxy 轉發到 http://localhost:8080
// - 若之後把前後端部署在同一個網域，也不需要修改任何程式碼
const http = axios.create({
  baseURL: "/api",
  timeout: 30000,
});

// 每次發送請求前，自動帶上登入後取得的 JWT token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
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
