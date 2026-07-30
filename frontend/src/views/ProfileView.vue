<script setup>
import { onMounted, ref } from "vue";
import http from "../api/http";

const username = ref("");
const email = ref("");
const oldPassword = ref("");
const newPassword = ref("");
const confirmPassword = ref("");

const loading = ref(false);
const errorMessage = ref("");
const successMessage = ref("");

import { useAuthStore } from "../store/auth";
import { useRouter } from "vue-router";

const authStore = useAuthStore();
const router = useRouter();

function handleLogout() {
  if (confirm("確定要登出系統嗎？")) {
    authStore.logout();
    router.push({ name: "home" });
  }
}

async function loadProfile() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const res = await http.get("/users/profile");
    username.value = res.data.username;
    email.value = res.data.email;
  } catch (err) {
    errorMessage.value = "載入個人資料失敗：" + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
}

async function handleUpdate() {
  errorMessage.value = "";
  successMessage.value = "";

  if (!email.value || !email.value.trim()) {
    errorMessage.value = "電子信箱不能為空";
    return;
  }

  // 密碼檢查
  if (newPassword.value) {
    if (!oldPassword.value) {
      errorMessage.value = "修改密碼必須輸入舊密碼";
      return;
    }
    if (newPassword.value !== confirmPassword.value) {
      errorMessage.value = "新密碼與確認密碼不一致";
      return;
    }
  }

  loading.value = true;
  try {
    const payload = {
      email: email.value.trim(),
    };
    if (newPassword.value) {
      payload.oldPassword = oldPassword.value;
      payload.newPassword = newPassword.value;
    }
    await http.put("/users/profile", payload);
    successMessage.value = "個人資料更新成功！";
    // 清除密碼欄位
    oldPassword.value = "";
    newPassword.value = "";
    confirmPassword.value = "";
  } catch (err) {
    errorMessage.value = "更新個人資料失敗：" + (err.response?.data?.message || err.message);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadProfile();
});
</script>

<template>
  <div class="page">
    <div class="center-page glass-card">
      <h2>個人資料設定</h2>

      <div v-if="loading && !username" class="loading-placeholder">
        載入中...
      </div>

      <form v-else @submit.prevent="handleUpdate">
        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
        <p v-if="successMessage" class="success-message">{{ successMessage }}</p>

        <div class="field">
          <label>使用者名稱 (帳號)</label>
          <input type="text" :value="username" disabled class="disabled-input" />
        </div>

        <div class="field">
          <label for="email">電子信箱 (Email)</label>
          <input id="email" v-model="email" type="email" required />
        </div>

        <hr class="divider" />
        <h4 class="section-subtitle">變更密碼 (若不修改請留空)</h4>

        <div class="field">
          <label for="oldPassword">舊密碼</label>
          <input id="oldPassword" v-model="oldPassword" type="password" />
        </div>

        <div class="field">
          <label for="newPassword">新密碼</label>
          <input id="newPassword" v-model="newPassword" type="password" />
        </div>

        <div class="field">
          <label for="confirmPassword">確認新密碼</label>
          <input id="confirmPassword" v-model="confirmPassword" type="password" />
        </div>

        <button class="btn primary submit-btn" type="submit" :disabled="loading">
          {{ loading ? "儲存中..." : "儲存變更" }}
        </button>

        <hr class="divider" />
        <h4 class="section-subtitle">📱 手機 App 安裝與描述檔</h4>
        <div class="app-install-card">
          <p class="app-install-desc">
            您可以將 MiniTube 以全螢幕 Native App 形式放置在 iPhone 或 Android 手機主畫面：
          </p>
          <a href="/api/mobileconfig" class="btn secondary install-download-btn" target="_blank" download="MiniTube.mobileconfig">
            📲 下載 iOS 描述檔 (.mobileconfig)
          </a>
          <details class="install-guide-details">
            <summary>📖 查看詳細安裝步驟指引</summary>
            <div class="guide-content">
              <h5>方法 1：iOS 描述檔一鍵安裝 (iPhone 專屬)</h5>
              <ol>
                <li>點擊上方「下載」按鈕，iOS 提示時點擊<strong>「允許」</strong>。</li>
                <li>開啟 iPhone <strong>「設定」➔ 最上方點擊「已下載描述檔」</strong>。</li>
                <li>點擊右上角<strong>「安裝」</strong>並輸入解鎖密碼即完成桌面 App 建立！</li>
              </ol>

              <h5>方法 2：Safari / Chrome「加入主畫面」(PWA 模式)</h5>
              <ol>
                <li>使用 Safari (iOS) 或 Chrome (Android) 開啟此頁面。</li>
                <li>點擊 Safari 底部的<strong>「分享 ➔」</strong>或 Chrome 右上角的<strong>「⋮」</strong>選單。</li>
                <li>選擇<strong>「加入主畫面 (Add to Home Screen)」</strong>即可！</li>
              </ol>
            </div>
          </details>
        </div>

        <hr class="divider" />

        <div class="logout-container">
          <button class="btn danger logout-btn" type="button" @click="handleLogout">
            🚪 登出帳號
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.disabled-input {
  background: rgba(255, 255, 255, 0.05) !important;
  color: var(--text-secondary) !important;
  cursor: not-allowed;
}

.divider {
  border: none;
  border-top: 1px solid var(--border-color);
  margin: 24px 0;
}

.section-subtitle {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.submit-btn {
  width: 100%;
  padding: 12px;
  font-size: 15px;
  margin-top: 10px;
}

.loading-placeholder {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 0;
}

.logout-container {
  margin-top: 16px;
  text-align: center;
}

.logout-btn {
  width: 100%;
  padding: 12px;
  font-size: 14px;
  font-weight: 600;
  border-radius: 8px;
  justify-content: center;
}

.app-install-card {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 16px;
  margin-top: 10px;
}

.app-install-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin-bottom: 14px;
}

.install-download-btn {
  display: block;
  text-align: center;
  text-decoration: none;
  padding: 12px;
  font-weight: 600;
  border-radius: 8px;
  background: rgba(62, 166, 255, 0.15) !important;
  color: var(--accent-blue) !important;
  border: 1px solid rgba(62, 166, 255, 0.4) !important;
  margin-bottom: 12px;
  transition: all 0.2s ease;
}

.install-download-btn:hover {
  background: rgba(62, 166, 255, 0.25) !important;
}

.install-guide-details {
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}

.install-guide-details summary {
  font-weight: 500;
  color: var(--accent-blue);
  padding: 4px 0;
}

.guide-content {
  margin-top: 12px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.2);
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
}

.guide-content h5 {
  color: var(--text-primary);
  font-size: 13px;
  margin: 10px 0 6px;
}

.guide-content ol {
  padding-left: 18px;
  margin: 0;
}
</style>
