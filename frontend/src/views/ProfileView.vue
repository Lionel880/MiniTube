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
</style>
