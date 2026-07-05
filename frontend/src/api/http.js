import axios from "axios";

/** 統一補上 /api 後綴，避免使用者填網址時漏帶或多帶斜線。 */
function normalizeApiUrl(rawUrl) {
  let url = rawUrl.trim();
  if (!url.endsWith("/api") && !url.endsWith("/api/")) {
    url = url.replace(/\/+$/, "") + "/api";
  }
  return url;
}

const getBaseURL = () => {
  // 優先順序 1：使用者在畫面上手動填過的網址（存在 localStorage，執行期可覆蓋，不用重新建置）
  const savedUrl = localStorage.getItem("minitube_api_url");
  if (savedUrl) {
    return normalizeApiUrl(savedUrl);
  }

  // 優先順序 2：建置時透過 VITE_API_BASE_URL 指定固定網址（例如正式站網域）。
  // 用法：在 frontend/.env.local 或部署平台的環境變數設定 VITE_API_BASE_URL=https://your-api-domain
  const envUrl = import.meta.env.VITE_API_BASE_URL;
  if (envUrl) {
    return normalizeApiUrl(envUrl);
  }

  // 優先順序 3（最後手段）：目前個人測試用的 ngrok 免費通道網址。
  // 免費版網址會不定期失效／換新，正式使用請改用上面兩種方式其中一種覆寫。
  return "https://denote-reveal-compel.ngrok-free.dev/api";
};

const http = axios.create({
  baseURL: getBaseURL(),
  timeout: 30000,
  withCredentials: true, // 啟用跨網域傳遞 Cookie
  headers: {
    "Bypass-Tunnel-Reminder": "true",
    "ngrok-skip-browser-warning": "69420",
    "Cache-Control": "no-cache, no-store, must-revalidate",
    "Pragma": "no-cache",
    "Expires": "0",
  },
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
    let message =
      error.response?.data?.message ||
      error.message ||
      "發生未知錯誤，請稍後再試";

    // 針對 Mixed Content (HTTPS 前端連 HTTP 後端) 或後端服務未啟動造成的 Network Error 進行引導
    if (error.message === "Network Error") {
      message = "無法連線至後端服務。若您使用 GitHub Pages 網址，請確保本地後端服務已啟動，並在瀏覽器設定中允許此網站的「不安全內容 (Insecure content)」以解除混合內容 (Mixed Content) 限制；或請直接在本地使用 http://localhost:5173 開啟網頁進行測試以完全避免此瀏覽器限制。";
    }

    return Promise.reject(new Error(message));
  }
);

export default http;
