<script setup>
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useAuthStore } from "../store/auth";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const username = ref("");
const password = ref("");
const errorMessage = ref("");
const loading = ref(false);

async function onSubmit() {
  errorMessage.value = "";
  loading.value = true;
  try {
    await authStore.login({ username: username.value, password: password.value });
    const redirect = route.query.redirect || "/";
    router.push(redirect);
  } catch (err) {
    errorMessage.value = err.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="center-page">
    <h2>登入</h2>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

    <form @submit.prevent="onSubmit">
      <div class="field">
        <label for="username">使用者名稱</label>
        <input id="username" v-model="username" type="text" autocomplete="username" required />
      </div>
      <div class="field">
        <label for="password">密碼</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" required />
      </div>
      <button class="btn primary" type="submit" :disabled="loading" style="width: 100%">
        {{ loading ? "登入中..." : "登入" }}
      </button>
    </form>

    <p style="margin-top: 16px; color: #aaa">
      還沒有帳號？<RouterLink to="/register" style="color: #3ea6ff">前往註冊</RouterLink>
    </p>
  </div>
</template>
