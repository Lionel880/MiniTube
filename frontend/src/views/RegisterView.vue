<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../store/auth";

const router = useRouter();
const authStore = useAuthStore();

const username = ref("");
const email = ref("");
const password = ref("");
const errorMessage = ref("");
const successMessage = ref("");
const loading = ref(false);

async function onSubmit() {
  errorMessage.value = "";
  successMessage.value = "";
  loading.value = true;
  try {
    await authStore.register({ username: username.value, email: email.value, password: password.value });
    successMessage.value = "註冊成功，正在跳轉到登入頁...";
    setTimeout(() => router.push("/login"), 1000);
  } catch (err) {
    errorMessage.value = err.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="center-page">
    <h2>註冊帳號</h2>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>
    <p v-if="successMessage" class="success-message">{{ successMessage }}</p>

    <form @submit.prevent="onSubmit">
      <div class="field">
        <label for="username">使用者名稱（3-50 字元）</label>
        <input id="username" v-model="username" type="text" autocomplete="username" required minlength="3" maxlength="50" />
      </div>
      <div class="field">
        <label for="email">Email</label>
        <input id="email" v-model="email" type="email" autocomplete="email" required maxlength="100" />
      </div>
      <div class="field">
        <label for="password">密碼（至少 6 碼）</label>
        <input id="password" v-model="password" type="password" autocomplete="new-password" required minlength="6" maxlength="100" />
      </div>
      <button class="btn primary" type="submit" :disabled="loading" style="width: 100%">
        {{ loading ? "註冊中..." : "註冊" }}
      </button>
    </form>

    <p style="margin-top: 16px; color: #aaa">
      已經有帳號了？<RouterLink to="/login" style="color: var(--accent-blue)">前往登入</RouterLink>
    </p>
  </div>
</template>
