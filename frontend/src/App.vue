<script setup>
import { onMounted, onUnmounted, ref } from "vue";
import NavBar from "./components/NavBar.vue";
import { useAuthStore } from "./store/auth";
import { useUploadStore } from "./store/upload";

const authStore = useAuthStore();
const uploadStore = useUploadStore();

const showScrollTop = ref(false);

function handleScroll() {
  showScrollTop.value = window.scrollY > 300;
}

function scrollToTop() {
  window.scrollTo({
    top: 0,
    behavior: "smooth"
  });
}

// 頁面重新整理 (F5) 時，嘗試恢復登入狀態
onMounted(() => {
  authStore.restoreSession();

  window.addEventListener("scroll", handleScroll);

  // 監聽重新整理與關閉分頁事件，若有背景上傳則彈窗警告
  window.addEventListener("beforeunload", (e) => {
    if (uploadStore.isUploading) {
      e.preventDefault();
      e.returnValue = "您的影片正在上傳中，重新整理或關閉此網頁將會中斷上傳。確定要離開嗎？";
    }
  });
});

onUnmounted(() => {
  window.removeEventListener("scroll", handleScroll);
});
</script>

<template>
  <NavBar />
  <main>
    <RouterView />
  </main>

  <!-- 返回頂部按鈕 -->
  <button 
    v-if="showScrollTop" 
    class="scroll-top-btn" 
    type="button"
    @click="scrollToTop" 
    title="返回頂部"
  >
    TOP
  </button>
</template>

<style scoped>
.scroll-top-btn {
  position: fixed;
  bottom: 30px;
  right: 30px;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #212121;
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  font-size: 11px;
  font-weight: bold;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
  z-index: 999;
  transition: var(--transition-smooth);
}
.scroll-top-btn:hover {
  background: #333333;
  border-color: var(--text-muted);
  transform: translateY(-2px);
}
.scroll-top-btn:active {
  transform: translateY(0);
}
</style>
