<script setup>
import { onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { listVideos, batchDeleteVideos, deleteAllVideos } from "../api/video";
import { useAuthStore } from "../store/auth";
import VideoCard from "../components/VideoCard.vue";
import { useUploadStore } from "../store/upload";
import http from "../api/http";

const STATUS_POLL_INTERVAL_MS = 5000;

const router = useRouter();
const authStore = useAuthStore();
const uploadStore = useUploadStore();
const videos = ref([]);
const folders = ref([]);
const page = ref(0);
const totalPages = ref(0);
const loading = ref(false);
const errorMessage = ref("");

// 資料夾階層狀態
const currentFolderId = ref(null); // null 代表根目錄，數字代表資料夾 ID
const currentFolderName = ref("");

// 排序狀態
const sortBy = ref(localStorage.getItem("minitube_sort_by") || "createdAt");
const sortDir = ref(localStorage.getItem("minitube_sort_dir") || "desc");

// 瀏覽模式
const viewMode = ref(localStorage.getItem("minitube_view_mode") || "grid");

// 批量刪除選擇狀態
const selectedIds = ref([]);
const selectMode = ref(false);

function toggleSelectMode() {
  selectMode.value = !selectMode.value;
  selectedIds.value = [];
}

async function loadFolders() {
  if (!authStore.isLoggedIn) return;
  try {
    const res = await http.get("/folders");
    folders.value = res.data;
  } catch (err) {
    console.error("無法取得資料夾列表", err);
  }
}

async function createFolder() {
  const name = prompt("請輸入新資料夾名稱：");
  if (!name || !name.trim()) return;
  try {
    await http.post("/folders", { name: name.trim() });
    await loadFolders();
  } catch (err) {
    alert("建立資料夾失敗：" + (err.response?.data?.message || err.message));
  }
}

async function deleteFolder(folder) {
  if (!confirm(`確定要刪除資料夾「${folder.name}」嗎？\n資料夾內的影片會被安全移回「全部影片（根目錄）」，不會被刪除。`)) return;
  try {
    await http.delete(`/folders/${folder.id}`);
    await loadFolders();
    if (currentFolderId.value === null) {
      load(0);
    }
  } catch (err) {
    alert("刪除資料夾失敗：" + (err.response?.data?.message || err.message));
  }
}

function enterFolder(folder) {
  currentFolderId.value = folder.id;
  currentFolderName.value = folder.name;
  load(0);
}

function exitFolder() {
  currentFolderId.value = null;
  currentFolderName.value = "";
  loadFolders();
  load(0);
}

async function load(p = 0, silent = false) {
  if (!silent) {
    loading.value = true;
    errorMessage.value = "";
  }
  try {
    const folderQueryId = currentFolderId.value === null ? "root" : currentFolderId.value.toString();
    const data = await listVideos({
      folderId: folderQueryId,
      page: p,
      size: 12,
      sortBy: sortBy.value,
      sortDir: sortDir.value,
    });
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
    selectMode.value = false;
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

watch([sortBy, sortDir], () => {
  localStorage.setItem("minitube_sort_by", sortBy.value);
  localStorage.setItem("minitube_sort_dir", sortDir.value);
  load(0);
});

watch(viewMode, (newVal) => {
  localStorage.setItem("minitube_view_mode", newVal);
});

watch(
  () => uploadStore.isUploading,
  (newVal, oldVal) => {
    if (oldVal === true && newVal === false && !uploadStore.errorMessage) {
      load(0);
    }
  }
);

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      currentFolderId.value = null;
      currentFolderName.value = "";
      loadFolders();
      load(0);
    } else {
      videos.value = [];
      folders.value = [];
      stopPolling();
    }
  }
);

onMounted(() => {
  if (authStore.isLoggedIn) {
    loadFolders();
    load(0);
  }
});

function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleDateString("zh-TW", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}
</script>

<template>
  <div class="page">
    <!-- 未登入時不顯示任何影片，提示先登入 -->
    <div v-if="!authStore.isLoggedIn" class="login-prompt">
      <p>登入查看與管理你上傳的影片。</p>
      <RouterLink class="btn primary" :to="{ name: 'login' }">前往登入</RouterLink>
    </div>

    <template v-else>
      <!-- 麵包屑導航（當在資料夾內時） -->
      <div v-if="currentFolderId !== null" class="breadcrumb">
        <span class="crumb-link" @click="exitFolder">全部影片</span>
        <span class="crumb-separator">/</span>
        <span class="crumb-current">{{ currentFolderName }}</span>
      </div>

      <!-- 資料夾列表區塊（僅在根目錄顯示） -->
      <div v-if="currentFolderId === null" class="folders-section">
        <div class="section-title-row">
          <h3 class="section-title">資料夾</h3>
          <button class="btn" @click="createFolder">📁 新增資料夾</button>
        </div>
        <div class="folders-grid">
          <div
            v-for="folder in folders"
            :key="folder.id"
            class="folder-card"
            @click="enterFolder(folder)"
          >
            <div class="folder-icon">📁</div>
            <div class="folder-info">
              <div class="folder-name" :title="folder.name">{{ folder.name }}</div>
              <div class="folder-date">{{ formatDate(folder.createdAt) }}</div>
            </div>
            <button class="folder-delete-btn" @click.stop="deleteFolder(folder)" title="刪除資料夾">
              ✕
            </button>
          </div>
          <div v-if="folders.length === 0" class="folders-empty">
            目前沒有資料夾，點擊上方按鈕建立新資料夾。
          </div>
        </div>
      </div>

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
        <div v-if="videos.length === 0" class="empty-state">此資料夾內目前沒有任何影片。</div>

        <div v-else class="video-grid" :class="{ 'list-view': viewMode === 'list' }">
          <VideoCard
            v-for="video in videos"
            :key="video.id"
            :video="video"
            :show-checkbox="selectMode"
            :selected="selectedIds.includes(video.id)"
            :folders="folders"
            @select="handleSelect"
            @refresh="load(page, true)"
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
  background: var(--accent-blue);
}

.toolbar-upload-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(37, 99, 235, 0.1);
  border: 1px solid rgba(37, 99, 235, 0.25);
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 13px;
  color: var(--accent-blue);
  font-weight: 500;
  backdrop-filter: blur(4px);
  cursor: pointer;
  transition: all 0.2s ease;
}

.toolbar-upload-indicator:hover {
  background: rgba(37, 99, 235, 0.18);
  border-color: rgba(37, 99, 235, 0.45);
  transform: scale(1.02);
}

.spinner-small {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(37, 99, 235, 0.2);
  border-left-color: var(--accent-blue);
  border-radius: 50%;
  animation: spin-small 1s linear infinite;
}

@keyframes spin-small {
  to { transform: rotate(360deg); }
}

/* 麵包屑樣式 */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  margin-bottom: 24px;
  color: var(--text-secondary);
}

.crumb-link {
  cursor: pointer;
  color: var(--accent-blue);
  transition: color 0.2s;
}

.crumb-link:hover {
  color: var(--accent-hover);
}

.crumb-separator {
  color: var(--text-muted);
}

.crumb-current {
  color: var(--text-primary);
  font-weight: 500;
}

/* 資料夾區塊樣式 */
.folders-section {
  margin-bottom: 32px;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.folders-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.folder-card {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--border-radius-md);
  cursor: pointer;
  position: relative;
  transition: var(--transition-smooth);
}

.folder-card:hover {
  border-color: var(--border-hover);
  background-color: var(--bg-secondary);
}

.folder-icon {
  font-size: 24px;
  margin-right: 12px;
}

.folder-info {
  flex: 1;
  min-width: 0;
}

.folder-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.folder-date {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.folder-delete-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  font-size: 12px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
  margin-left: 8px;
}

.folder-delete-btn:hover {
  color: var(--danger-color);
  background-color: rgba(220, 38, 38, 0.1);
}

.folders-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 24px;
  color: var(--text-muted);
  border: 1px dashed var(--border-color);
  border-radius: var(--border-radius-md);
  font-size: 13px;
}

@media (max-width: 768px) {
  .toolbar-upload-indicator {
    display: none;
  }
}
</style>
