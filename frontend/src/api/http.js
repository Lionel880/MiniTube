import axios from "axios";

// 統一的 axios 實例。baseURL 使用相對路徑 "/api"：
// - 開發模式下由 vite.config.js 的 proxy 轉發到 http://localhost:8080
// - 若之後把前後端部署在同一個網域，也不需要修改任何程式碼
const http = axios.create({
  baseURL: "/api",
  timeout: 30000,
  withCredentials: true, // 啟用跨網域傳遞 Cookie (例如 GitHub Pages -> Render)
});

// --- 記憶體內 token (由 auth store 設定) ---
let _memoryToken = "";
export function setMemoryToken(token) {
  _memoryToken = token;
}
export function getMemoryToken() {
  return _memoryToken;
}

// 每次發送請求前，如果記憶體中有 token 就加到 Authorization header
// 這是 HttpOnly Cookie 的備援機制，確保即使 Cookie 沒被瀏覽器接受，請求仍然帶有認證
http.interceptors.request.use((config) => {
  if (_memoryToken) {
    config.headers.Authorization = `Bearer ${_memoryToken}`;
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

    return Promise.reject(new Error(message));
  }
);

export default http;
