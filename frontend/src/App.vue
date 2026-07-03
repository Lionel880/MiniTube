<script setup>
import { onMounted } from "vue";
import NavBar from "./components/NavBar.vue";
import { useAuthStore } from "./store/auth";

const authStore = useAuthStore();

// 頁面重新整理 (F5) 時，嘗試用 Cookie 恢復登入狀態
onMounted(() => {
  if (authStore.isLoggedIn) {
    authStore.restoreSession();
  }
});
</script>

<template>
  <NavBar />
  <main>
    <RouterView />
  </main>
</template>
