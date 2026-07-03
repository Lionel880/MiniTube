<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "../store/auth";

const router = useRouter();
const authStore = useAuthStore();
const keyword = ref("");

function onSearch() {
  const q = keyword.value.trim();
  if (!q) return;
  router.push({ name: "search", query: { q } });
}

function onLogout() {
  authStore.logout();
  router.push({ name: "home" });
}
</script>

<template>
  <header class="navbar">
    <RouterLink class="brand" :to="{ name: 'home' }">Mini<span>Tube</span></RouterLink>

    <form @submit.prevent="onSearch">
      <input v-model="keyword" type="text" placeholder="搜尋影片" />
      <button class="search-btn" type="submit">搜尋</button>
    </form>

    <div class="nav-actions">
      <template v-if="authStore.isLoggedIn">
        <RouterLink class="btn primary" :to="{ name: 'upload' }">上傳影片</RouterLink>
        <span class="username-tag">{{ authStore.username }}</span>
        <button class="btn danger" type="button" @click="onLogout">登出</button>
      </template>
      <template v-else>
        <RouterLink class="btn" :to="{ name: 'login' }">登入</RouterLink>
        <RouterLink class="btn primary" :to="{ name: 'register' }">註冊</RouterLink>
      </template>
    </div>
  </header>
</template>
