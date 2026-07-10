<script setup>
import { onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { listVideos, batchDeleteVideos, deleteAllVideos } from "../api/video";
import { useAuthStore } from "../store/auth";
import VideoCard from "../components/VideoCard.vue";

import { useUploadStore } from "../store/upload";

// 只要目前這頁還有影片狀態是 UPLOADING（背景轉碼中），就每隔這麼久自動重新整理一次列表
const STATUS_POLL_INTERVAL_MS = 5000;

const router = useRouter();
const authStore = useAuthStore();
const uploadStore = useUploadStore();
const videos = ref([]);
const page = ref(0);
const totalPages = ref(0);
const loading = ref(false);
const errorMessage = ref("");

// 排序狀態
const sortBy = ref(localStorage.getItem("minitube_sort_by") || "createdAt");
const sortDir = ref(localStorage.getItem("minitube_sort_dir") || "desc");

// 瀏覽模式："grid" 或 "list"
const viewMode = ref(localStorage.getItem("minitube_view_mode") || "grid");

// 批量刪除選擇狀態
const selectedIds = ref([]);
const selectMode = ref(false);

function toggleSelectMode() {
  selectMode.value = !selectMode.value;
  selectedIds.value = [];
}

/**
 * silent=true 用於背景輪詢：更新資料但不觸發整頁「載入中」狀態、也不清空使用者當下的勾選，
 * 讓正在轉碼的影片卡片可以自動變成可播放，不用使用者手動重整整頁。
 */
async function load(p = 0, silent = false) {
  if (!silent) {
    loading.value = true;
    errorMessage.value = "";
  }
  try {
    const data = await listVideos({ page: p, size: 12, sortBy: sortBy.value, sortDir: sortDir.value });
    videos.value = data.videos;
    page.value = data.page;
    totalPages.value = data.totalPages;
    if (!silent) {
      selectedIds.value = [];
    }
  } catch (err) {
    if (!silent) {
      errorMessage.value = err.message;
    }
  } finally {
    if (!silent) {
      loading.value = false;
    }
  }
  syncPolling();
}

let pollTimer = null;

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer);
    pollTimer = null;
  }
}

/** 目前這頁只要還有任何一支影片卡在 UPLOADING（轉碼中），就要繼續輪詢。 */
function syncPolling() {
  const shouldPoll = videos.value.some((v) => v.status === "UPLOADING");
  if (shouldPoll && !pollTimer) {
    pollTimer = setInterval(() => load(page.value, true), STATUS_POLL_INTERVAL_MS);
  } else if (!shouldPoll) {
    stopPolling();
  }
}

onUnmounted(stopPolling);

function handleSelect(id, checked) {
  if (checked) {
    if (!selectedIds.value.includes(id)) {
      selectedIds.value.push(id);
    }
  } else {
    selectedIds.value = selectedIds.value.filter((x) => x !== id);
  }
}

async function deleteSelected() {
  if (selectedIds.value.length === 0) return;
  if (!confirm(`確定要刪除這 ${selectedIds.value.length} 支影片嗎？此動作無法復原！`)) return;
  try {
    await batchDeleteVideos(selectedIds.value);
    selectMode.value = false; // 刪除完後自動恢復，關閉選取模式
    load(page.value);
  } catch (err) {
    alert("刪除失敗：" + err.message);
  }
}

async function deleteAll() {
  if (!confirm("⚠️ 確定要刪除您帳號中所有的影片嗎？此操作不可逆！")) return;
  try {
    await deleteAllVideos();
    selectMode.value = false;
    load(0);
  } catch (err) {
    alert("一鍵刪除失敗：" + err.message);
  }
}

function prevPage() {
  if (page.value > 0) load(page.value - 1);
}

function nextPage() {
  if (page.value + 1 < totalPages.value) load(page.value + 1);
}

// 當排序變更時自動重載並記憶
watch([sortBy, sortDir], ([newBy, newDir]) => {
  localStorage.setItem("minitube_sort_by", newBy);
  localStorage.setItem("minitube_sort_dir", newDir);
  load(0);
});

// 當檢視模式變更時寫入 localStorage
watch(viewMode, (newVal) => {
  localStorage.setItem("minitube_view_mode", newVal);
});

// 監聽背景上傳完成，自動刷新首頁列表
watch(
  () => uploadStore.isUploading,
  (newVal, oldVal) => {
    if (oldVal === true && newVal === false && !uploadStore.errorMessage) {
      load(0);
    }
  }
);

// 登入 / 登出時重載或清空列表（影片跟著使用者走）
watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      load(0);
    } else {
      videos.value = [];
      stopPolling();
    }
  }
);

onMounted(() => {
  if (authStore.isLoggedIn) load(0);
});
</script>

<template>
  <div class="page">
    <!-- 未登入時不顯示任何影片，提示先登入 -->
    <div v-if="!authStore.isLoggedIn" class="login-prompt">
      <p>登入查看與管理你上傳的影片。</p>
      <RouterLink class="btn primary" :to="{ name: 'login' }">前往登入</RouterLink>
    </div>

    <template v-else>
    <div class="toolbar">
      <div class="sorting-controls">
        <span class="label">排序依據：</span>
        <select id="sortBy" v-model="sortBy" class="select-input">
          <option value="createdAt">上傳時間</option>
          <option value="title">名稱</option>
          <option value="fileSize">檔案大小</option>
        </select>
        <select v-model="sortDir" class="select-input">
          <option value="desc">降冪 (大到小 / 新到舊)</option>
          <option value="asc">升冪 (小到大 / 舊到新)</option>
        </select>

        <!-- 檢視模式切換按鈕 -->
        <div class="view-mode-toggle">
          <button
            class="icon-btn"
            type="button"
            :class="{ active: viewMode === 'grid' }"
            title="卡片網格模式"
            @click="viewMode = 'grid'"
          >
            🎚️ 卡片
          </button>
          <button
            class="icon-btn"
            type="button"
            :class="{ active: viewMode === 'list' }"
            title="橫向清單模式"
            @click="viewMode = 'list'"
          >
            ☰ 清單
          </button>
        </div>
      </div>

      <!-- 首頁 Toolbar 內的上傳進度展示 (PC端適用) -->
      <div v-if="uploadStore.isUploading" class="toolbar-upload-indicator" @click="router.push({ name: 'upload' })" title="點擊查看上傳詳情">
        <div class="spinner-small"></div>
        <span>上傳中... {{ uploadStore.progress }}% ({{ uploadStore.filesCount }} 部影片)</span>
      </div>

      <div class="batch-controls">
        <button
          class="btn"
          type="button"
          @click="toggleSelectMode"
        >
          {{ selectMode ? '取消選取' : '🗑️ 選取刪除' }}
        </button>
        <button
          v-if="selectMode && selectedIds.length > 0"
          class="btn danger bulk-delete-btn"
          type="button"
          @click="deleteSelected"
        >
          確認刪除 ({{ selectedIds.length }})
        </button>
        <button
          class="btn danger"
          type="button"
          @click="deleteAll"
        >
          🧹 一鍵全部刪除
        </button>
      </div>
    </div>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

    <div v-if="loading" class="empty-state">載入中...</div>

    <template v-else>
      <div v-if="videos.length === 0" class="empty-state">你還沒有上傳任何影片，快來上傳第一支吧！</div>

      <div v-else class="video-grid" :class="{ 'list-view': viewMode === 'list' }">
        <VideoCard
          v-for="video in videos"
          :key="video.id"
          :video="video"
          :show-checkbox="selectMode"
          :selected="selectedIds.includes(video.id)"
          @select="handleSelect"
        />
      </div>

      <div v-if="totalPages > 1" class="pagination">
        <button class="btn" type="button" :disabled="page === 0" @click="prevPage">上一頁</button>
        <span>{{ page + 1 }} / {{ totalPages }}</span>
        <button class="btn" type="button" :disabled="page + 1 >= totalPages" @click="nextPage">下一頁</button>
      </div>
    </template>
    </template>
  </div>
</template>

<style scoped>
.view-mode-toggle {
  display: flex;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 2px;
  margin-left: 8px;
}

.view-mode-toggle .icon-btn {
  background: transparent;
  border: none;
  color: #aaa;
  padding: 6px 12px;
  font-size: 13px;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.2s ease;
}

.view-mode-toggle .icon-btn:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.05);
}

.view-mode-toggle .icon-btn.active {
  color: #fff;
  background: #3ea6ff;
}

.toolbar-upload-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(62, 166, 255, 0.1);
  border: 1px solid rgba(62, 166, 255, 0.25);
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  color: #3ea6ff;
  font-weight: 500;
  backdrop-filter: blur(4px);
  cursor: pointer;
  transition: all 0.2s ease;
}

.toolbar-upload-indicator:hover {
  background: rgba(62, 166, 255, 0.18);
  border-color: rgba(62, 166, 255, 0.45);
  transform: scale(1.02);
}

.spinner-small {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(62, 166, 255, 0.2);
  border-left-color: #3ea6ff;
  border-radius: 50%;
  animation: spin-small 1s linear infinite;
}

@keyframes spin-small {
  to { transform: rotate(360deg); }
}

@media (max-width: 768px) {
  .toolbar-upload-indicator {
    display: none;
  }
}
</style>
