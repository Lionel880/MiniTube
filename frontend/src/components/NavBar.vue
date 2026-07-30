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
const isAppModalOpen = ref(false);
const apiUrl = ref(localStorage.getItem("minitube_api_url") || "");
const isTesting = ref(false);
const testStatus = ref(""); // "success", "error", ""
const testMessage = ref("");

import { onMounted, onUnmounted } from "vue";

const isProfileMenuOpen = ref(false);
const profileMenuRef = ref(null);
const currentTheme = ref(localStorage.getItem("minitube_theme") || "dark");

function toggleTheme() {
  const newTheme = currentTheme.value === "dark" ? "light" : "dark";
  currentTheme.value = newTheme;
  localStorage.setItem("minitube_theme", newTheme);
  document.documentElement.setAttribute("data-theme", newTheme);
}

function toggleProfileMenu(e) {
  if (e) e.stopPropagation();
  isProfileMenuOpen.value = !isProfileMenuOpen.value;
}

function handleGlobalClick(e) {
  if (isProfileMenuOpen.value && profileMenuRef.value && !profileMenuRef.value.contains(e.target)) {
    isProfileMenuOpen.value = false;
  }
}

onMounted(() => {
  window.addEventListener("click", handleGlobalClick);
  window.addEventListener("touchstart", handleGlobalClick, { passive: true });
});

onUnmounted(() => {
  window.removeEventListener("click", handleGlobalClick);
  window.removeEventListener("touchstart", handleGlobalClick);
});

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
    </div>

    <!-- 搜尋框 (登入後且非首頁、影片詳細頁、上傳頁與個人資料頁時顯示) -->
    <form v-if="authStore.isLoggedIn && route.name !== 'home' && route.name !== 'video-detail' && route.name !== 'upload' && !route.path.startsWith('/profile')" @submit.prevent="onSearch">
      <input v-model="keyword" type="text" placeholder="搜尋影片" />
      <button class="search-btn" type="submit">搜尋</button>
    </form>

    <div class="nav-actions">
      <template v-if="authStore.isLoggedIn">
        <!-- 上傳按鈕：在上傳中顯示進度並對所有頁面可見，非上傳中則只在特定頁面顯示（個人資料頁除外） -->
        <RouterLink
          v-if="route.name !== 'upload' && !route.path.startsWith('/profile') && (uploadStore.isUploading || route.name !== 'home')"
          class="btn primary"
          :class="{ 'uploading-btn': uploadStore.isUploading }"
          :to="{ name: 'upload' }"
        >
          {{ uploadStore.isUploading ? `上傳中... ${uploadStore.progress}%` : '上傳影片' }}
        </RouterLink>
        <!-- 👤 個人資料懸浮選單容器 (Floating Profile Popover Container) -->
        <div class="profile-menu-container" ref="profileMenuRef">
          <button
            class="btn secondary profile-nav-btn"
            :class="{ active: isProfileMenuOpen || route.path.startsWith('/profile') }"
            type="button"
            @click="toggleProfileMenu"
            title="開啟個人資料懸浮選單"
          >
            👤 個人資料 <span class="caret-icon">{{ isProfileMenuOpen ? '▲' : '▼' }}</span>
          </button>

          <!-- 透明遮罩層（用於手機端與電腦端點擊外部快速收合懸浮選單） -->
          <div
            v-if="isProfileMenuOpen"
            class="popover-backdrop"
            @click="isProfileMenuOpen = false"
            @touchstart.passive="isProfileMenuOpen = false"
          ></div>

          <!-- 懸浮選單卡片 (Floating Popover Menu) -->
          <Transition name="popover-slide">
            <div v-if="isProfileMenuOpen" class="profile-popover glass-card">
              <div class="popover-header">
                <span class="user-avatar-badge">👤</span>
                <div class="user-meta">
                  <span class="user-name">{{ authStore.username || '用戶' }}</span>
                </div>
              </div>
              <hr class="popover-hr" />
              <RouterLink
                class="popover-link-item"
                :to="{ name: 'profile-account' }"
                @click="isProfileMenuOpen = false"
              >
                <span class="link-icon">⚙️</span>
                <span class="link-label">帳號與密碼設定</span>
              </RouterLink>
              <RouterLink
                class="popover-link-item"
                :to="{ name: 'profile-app' }"
                @click="isProfileMenuOpen = false"
              >
                <span class="link-icon">📱</span>
                <span class="link-label">手機 App 安裝與描述檔</span>
              </RouterLink>
              <!-- 🎨 外觀主題切換 (直接在列表內部點擊切換) -->
              <div
                class="popover-link-item theme-popover-item"
                @click.stop="toggleTheme"
                title="點擊切換 黑暗 / 一般 模式"
              >
                <span class="link-icon">{{ currentTheme === 'dark' ? '🌙' : '☀️' }}</span>
                <span class="link-label">外觀模式</span>
                <span class="theme-toggle-badge" :class="currentTheme">
                  {{ currentTheme === 'dark' ? '黑暗' : '一般' }}
                </span>
              </div>
              <hr class="popover-hr" />
              <button
                class="popover-link-item danger-item"
                type="button"
                @click="() => { isProfileMenuOpen = false; onLogout(); }"
              >
                <span class="link-icon">🚪</span>
                <span class="link-label">登出帳號</span>
              </button>
            </div>
          </Transition>
        </div>
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

  <!-- 手機 App / 描述檔安裝指引 Modal -->
  <Transition name="fade">
    <div v-if="isAppModalOpen" class="modal-overlay" @click.self="isAppModalOpen = false">
      <div class="modal-content app-modal-content">
        <h3>📱 安裝 MiniTube 手機 App</h3>
        <p class="modal-desc">
          您可以透過以下兩種方式，將 MiniTube 以全螢幕 Native App 形式放置在 iPhone 或 Android 手機主畫面：
        </p>

        <div class="app-install-section">
          <h4>方法 1：下載 iOS 描述檔 (iPhone 專屬一鍵安裝)</h4>
          <p class="section-desc">點擊下方按鈕下載 Apple 描述檔，安裝後即可在 iPhone 主畫面產生專屬 MiniTube App 圖示：</p>
          <a href="/api/mobileconfig" class="btn primary install-download-btn" target="_blank" download="MiniTube.mobileconfig">
            📲 下載 iOS 描述檔 (.mobileconfig)
          </a>
          <ol class="install-steps">
            <li>點擊下載後，iOS 會提示「此網站正在嘗試下載設定描述檔」，請點擊<strong>「允許」</strong>。</li>
            <li>開啟 iPhone <strong>「設定」➔ 最上方點擊「已下載描述檔」</strong>。</li>
            <li>點擊右上角<strong>「安裝」</strong>並輸入手機解鎖密碼即完成安裝！</li>
          </ol>
        </div>

        <hr class="modal-divider" />

        <div class="app-install-section">
          <h4>方法 2：Safari / Chrome「加入主畫面」(PWA 模式)</h4>
          <ol class="install-steps">
            <li>使用手機 Safari (iOS) 或 Chrome (Android) 開啟此網站。</li>
            <li>點擊 Safari 底部的<strong>「分享 ➔」</strong>或 Chrome 右上角的<strong>「⋮」</strong>選單。</li>
            <li>選擇<strong>「加入主畫面 (Add to Home Screen)」</strong>，即可將 MiniTube 當作 App 使用！</li>
          </ol>
        </div>

        <div class="modal-actions" style="margin-top: 20px;">
          <button class="btn primary" type="button" style="width: 100%;" @click="isAppModalOpen = false">我知道了</button>
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
  display: flex;
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
  0%, 100% { opacity: 1; }
  50% { opacity: 0.75; }
}

.uploading-btn {
  background: var(--accent-orange, #ff7a00) !important;
  border-color: var(--accent-orange, #ff7a00) !important;
  color: #fff !important;
  animation: pulse 2s infinite ease-in-out;
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

.app-modal-content {
  max-width: 480px;
}
.app-install-section {
  margin-top: 16px;
  text-align: left;
}
.app-install-section h4 {
  font-size: 14px;
  color: var(--accent-blue);
  margin-bottom: 6px;
}
.section-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 12px;
}
.install-download-btn {
  display: block;
  text-align: center;
  text-decoration: none;
  padding: 10px;
  font-weight: 600;
  margin-bottom: 12px;
}
.install-steps {
  font-size: 12px;
  color: var(--text-secondary);
  padding-left: 20px;
  line-height: 1.7;
  margin: 8px 0;
}
.modal-divider {
  border: none;
  border-top: 1px solid var(--border-color);
  margin: 16px 0;
}

/* 👤 個人資料懸浮選單 (Floating Profile Popover Menu) */
.profile-menu-container {
  position: relative;
  display: inline-block;
}

.popover-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 9998;
  background: transparent;
}

.caret-icon {
  font-size: 9px;
  margin-left: 4px;
  opacity: 0.7;
}

.profile-popover {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 250px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 16px;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.45);
  padding: 12px;
  z-index: 9999;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
}

.popover-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 10px 10px;
}

.user-avatar-badge {
  font-size: 20px;
  background: rgba(255, 122, 0, 0.15);
  color: var(--accent-blue);
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 122, 0, 0.3);
}

.user-meta {
  display: flex;
  flex-direction: column;
}

.user-name {
  font-weight: 700;
  font-size: 14px;
  color: var(--text-primary);
}

.user-status-online {
  font-size: 11px;
  color: var(--success-color);
  margin-top: 2px;
}

.popover-hr {
  border: none;
  border-top: 1px solid var(--border-color);
  margin: 6px 0;
}

.popover-link-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  color: var(--text-primary);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  border: none;
  background: transparent;
  width: 100%;
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
}

.popover-link-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: var(--accent-blue);
  transform: translateX(2px);
}

.theme-popover-item {
  user-select: none;
}

.theme-toggle-badge {
  margin-left: auto;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  transition: all 0.2s ease;
}

.theme-toggle-badge.dark {
  background: rgba(255, 179, 0, 0.15);
  color: #ffb300;
  border-color: rgba(255, 179, 0, 0.3);
}

.theme-toggle-badge.light {
  background: rgba(33, 150, 243, 0.15);
  color: #2196f3;
  border-color: rgba(33, 150, 243, 0.3);
}

.popover-link-item .link-icon {
  font-size: 16px;
}

.popover-link-item.danger-item {
  color: var(--danger-color);
}

.popover-link-item.danger-item:hover {
  background: rgba(255, 59, 48, 0.12);
  color: var(--danger-color);
}

/* 懸浮選單進入/離開動畫 */
.popover-slide-enter-active,
.popover-slide-leave-active {
  transition: opacity 0.18s cubic-bezier(0.25, 1, 0.5, 1), transform 0.18s cubic-bezier(0.25, 1, 0.5, 1);
}

.popover-slide-enter-from,
.popover-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.95);
}
</style>
