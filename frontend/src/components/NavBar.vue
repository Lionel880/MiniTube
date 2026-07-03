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

function onSetApiUrl() {
  const current = localStorage.getItem("minitube_api_url") || "";
  const newUrl = prompt("請輸入後端 API 公網網址 (例如 https://xxxx.loca.lt 或是 http://114.34.34.4:8080)\n留空則還原本地預設值 (/api) :", current);
  if (newUrl !== null) {
    const trimmed = newUrl.trim();
    if (trimmed) {
      // 僅允許 http:// 或 https:// 開頭的網址，防止非預期的 URL scheme
      if (!/^https?:\/\//i.test(trimmed)) {
        alert("錯誤：API 網址必須以 http:// 或 https:// 開頭");
        return;
      }
      localStorage.setItem("minitube_api_url", trimmed);
      alert("API 網址已設定為：" + trimmed + "\n網頁即將重新整理以套用新設定！");
      window.location.reload();
    } else {
      localStorage.removeItem("minitube_api_url");
      alert("已還原本地預設值 (/api)，網頁即將重新整理！");
      window.location.reload();
    }
  }
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
      <button class="btn" type="button" @click="onSetApiUrl" title="設定 API 伺服器網址">⚙️ 設定 API</button>
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
