<script setup>
import { onMounted } from "vue";
import NavBar from "./components/NavBar.vue";
import { useAuthStore } from "./store/auth";
import { useUploadStore } from "./store/upload";

const authStore = useAuthStore();
const uploadStore = useUploadStore();

// 頁面重新整理 (F5) 時，嘗試用 Cookie 恢復登入狀態
onMounted(() => {
  if (authStore.isLoggedIn) {
    authStore.restoreSession();
  }

  // 監聽重新整理與關閉分頁事件，若有背景上傳則彈窗警告
  window.addEventListener("beforeunload", (e) => {
    if (uploadStore.isUploading) {
      e.preventDefault();
      e.returnValue = "您的影片正在上傳中，重新整理或關閉此網頁將會中斷上傳。確定要離開嗎？";
    }
  });
});
</script>

<template>
  <NavBar />
  <main>
    <RouterView />
  </main>
</template>
