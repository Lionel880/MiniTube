<script setup>
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../store/auth";
import { useUploadStore } from "../store/upload";
import axios from "axios";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const uploadStore = useUploadStore();
const keyword = ref("");

const isModalOpen = ref(false);
const apiUrl = ref(localStorage.getItem("minitube_api_url") || "");
const isTesting = ref(false);
const testStatus = ref(""); // "success", "error", ""
const testMessage = ref("");

function onSearch() {
  const q = keyword.value.trim();
  if (!q) return;
  router.push({ name: "search", query: { q } });
}

function onLogout() {
  authStore.logout();
  router.push({ name: "home" });
}

async function testConnection() {
  let url = apiUrl.value.trim();
  if (!url) {
    testStatus.value = "error";
    testMessage.value = "請先輸入 API 網址";
    return;
  }

  // 僅允許 http:// 或 https:// 開頭的網址
  if (!/^https?:\/\//i.test(url)) {
    testStatus.value = "error";
    testMessage.value = "網址必須以 http:// 或 https:// 開頭";
    return;
  }

  isTesting.value = true;
  testStatus.value = "";
  testMessage.value = "";

  try {
    let cleanUrl = url;
    if (!cleanUrl.endsWith("/api") && !cleanUrl.endsWith("/api/")) {
      cleanUrl = cleanUrl.replace(/\/+$/, "") + "/api";
    }
    
    // 發送測試連線至後端 /hello
    const res = await axios.get(`${cleanUrl}/hello`, {
      timeout: 5000,
      headers: {
        "Bypass-Tunnel-Reminder": "true",
        "ngrok-skip-browser-warning": "69420"
      }
    });
    if (res.status === 200) {
      testStatus.value = "success";
      testMessage.value = "連線成功！伺服器已回應。";
    } else {
      testStatus.value = "error";
      testMessage.value = `連線失敗 (狀態碼: ${res.status})`;
    }
  } catch (err) {
    testStatus.value = "error";
    testMessage.value = "無法連線至此伺服器，請檢查網址或確認後端已啟動。";
  } finally {
    isTesting.value = false;
  }
}

function saveApiUrl() {
  const trimmed = apiUrl.value.trim();
  if (trimmed) {
    if (!/^https?:\/\//i.test(trimmed)) {
      alert("錯誤：API 網址必須以 http:// 或 https:// 開頭");
      return;
    }
    localStorage.setItem("minitube_api_url", trimmed);
  } else {
    localStorage.removeItem("minitube_api_url");
  }
  isModalOpen.value = false;
  window.location.reload();
}

function clearApiUrl() {
  apiUrl.value = "";
  localStorage.removeItem("minitube_api_url");
  isModalOpen.value = false;
  window.location.reload();
}
</script>

<template>
  <header class="navbar">
    <div class="brand-group">
      <RouterLink class="brand" :to="{ name: 'home' }">Mini<span>Tube</span></RouterLink>
      
      <!-- 背景上傳微縮指示器 -->
      <div v-if="uploadStore.isUploading" class="navbar-upload-indicator" @click="router.push({ name: 'upload' })" title="點擊查看上傳詳情">
        <div class="navbar-spinner"></div>
        <span class="indicator-text">
          <span class="text-long">背景上傳中... </span>
          <span class="text-short">上傳中 </span>
          {{ uploadStore.progress }}%
          <span class="text-long"> ({{ uploadStore.filesCount }} 部影片)</span>
        </span>
      </div>
    </div>

    <!-- 搜尋框 (登入後且非首頁時顯示，避免重複與未登入時顯示) -->
    <form v-if="authStore.isLoggedIn && route.name !== 'home'" @submit.prevent="onSearch">
      <input v-model="keyword" type="text" placeholder="搜尋影片" />
      <button class="search-btn" type="submit">搜尋</button>
    </form>

    <div class="nav-actions">
      <template v-if="authStore.isLoggedIn">
        <!-- 上傳按鈕 (首頁時隱藏，避免重複) -->
        <RouterLink v-if="route.name !== 'home'" class="btn primary" :to="{ name: 'upload' }">上傳影片</RouterLink>
        <RouterLink class="username-tag" :to="{ name: 'profile' }" title="修改資料與密碼">{{ authStore.username }}</RouterLink>
        <button class="btn danger" type="button" @click="onLogout">登出</button>
      </template>
      <template v-else>
        <RouterLink class="btn" :to="{ name: 'login' }">登入</RouterLink>
        <RouterLink class="btn primary" :to="{ name: 'register' }">註冊</RouterLink>
      </template>
    </div>
  </header>

  <!-- 美化版連線設定彈窗 Modal -->
  <Transition name="fade">
    <div v-if="isModalOpen" class="modal-overlay" @click.self="isModalOpen = false">
      <div class="modal-content">
        <h3>API 伺服器連線設定</h3>
        <p class="modal-desc">
          若在其他裝置（手機/平板）使用部署版，請在此輸入您桌機本地後端的 **HTTPS 公網穿透網址** (由 Local Tunnel 產生)。
        </p>

        <div class="field">
          <label for="apiUrl">後端 API 網址</label>
          <input 
            id="apiUrl" 
            v-model="apiUrl" 
            type="text" 
            placeholder="例如 https://xxxx.loca.lt 或是 http://localhost:8080" 
          />
        </div>

        <!-- 測試連線回報狀態 -->
        <div v-if="testMessage" class="test-feedback" :class="testStatus">
          <span v-if="testStatus === 'success'">成功：</span>
          <span v-else>失敗：</span>
          {{ testMessage }}
        </div>

        <div class="modal-actions">
          <button class="btn" type="button" :disabled="isTesting" @click="testConnection">
            {{ isTesting ? "連線測試中..." : "測試連線" }}
          </button>
          <div style="flex-grow: 1;"></div>
          <button class="btn danger" type="button" @click="clearApiUrl">清除還原</button>
          <button class="btn primary" type="button" @click="saveApiUrl">儲存並重整</button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* 磨砂玻璃 Modal 樣式 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(6px);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background: rgba(28, 28, 28, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px 0 rgba(0, 0, 0, 0.37);
  border-radius: 12px;
  padding: 24px;
  width: 90%;
  max-width: 500px;
  color: #fff;
  text-align: left;
}

.modal-content h3 {
  margin-top: 0;
  font-size: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding-bottom: 12px;
}

.modal-desc {
  font-size: 13px;
  color: #aaa;
  line-height: 1.6;
  margin-bottom: 20px;
}

.modal-content .field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
}

.modal-content label {
  font-size: 14px;
  color: #ddd;
}

.modal-content input {
  background: #121212;
  border: 1px solid #333;
  color: #fff;
  padding: 10px;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
}

.modal-content input:focus {
  border-color: var(--accent-blue);
}

.test-feedback {
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 13px;
  margin-bottom: 20px;
  line-height: 1.4;
}

.test-feedback.success {
  background: rgba(46, 125, 50, 0.15);
  border: 1px solid #2e7d32;
  color: #81c784;
}

.test-feedback.error {
  background: rgba(198, 40, 40, 0.15);
  border: 1px solid #c62828;
  color: #e57373;
}

.modal-actions {
  display: flex;
  gap: 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  padding-top: 16px;
}

/* ⚙️ 設定 API 的微調，與 NavBar 原有樣式對齊 */
.username-tag {
  color: #fff;
  font-weight: bold;
  cursor: pointer;
  transition: var(--transition-smooth);
}

.username-tag:hover {
  color: var(--accent-blue);
}

/* 動畫過渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.brand-group {
  display: flex;
  align-items: center;
  gap: 16px;
}

.navbar-upload-indicator {
  display: none;
  align-items: center;
  gap: 8px;
  background: rgba(255, 122, 0, 0.1);
  border: 1px solid rgba(255, 122, 0, 0.3);
  padding: 6px 12px;
  border-radius: 20px;
  font-size: 12px;
  color: var(--accent-blue);
  font-weight: 500;
  backdrop-filter: blur(4px);
  animation: pulse 2s infinite ease-in-out;
  cursor: pointer;
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

.navbar-upload-indicator:hover {
  background: rgba(255, 122, 0, 0.2);
  border-color: rgba(255, 122, 0, 0.55);
}

.navbar-spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(255, 122, 0, 0.2);
  border-left-color: var(--accent-blue);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.9; }
  50% { opacity: 0.6; }
}

.text-short {
  display: none;
}

@media (max-width: 768px) {
  .text-long {
    display: none;
  }
  .text-short {
    display: inline;
  }
  .navbar-upload-indicator {
    display: flex;
    position: fixed;
    bottom: 24px;
    left: 50%;
    transform: translateX(-50%);
    z-index: 9999;
    background: rgba(18, 18, 18, 0.85);
    border: 1px solid rgba(62, 166, 255, 0.4);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.6);
    padding: 10px 20px;
    font-size: 13px;
    border-radius: 30px;
    backdrop-filter: blur(8px);
  }
}
</style>
