<script setup>
import { onMounted, onUnmounted, ref, watch, computed } from "vue";
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

// 獨立的資料夾排序狀態
const folderSortBy = ref(localStorage.getItem("minitube_folder_sort_by") || "createdAt");
const folderSortDir = ref(localStorage.getItem("minitube_folder_sort_dir") || "desc");

watch([folderSortBy, folderSortDir], () => {
  localStorage.setItem("minitube_folder_sort_by", folderSortBy.value);
  localStorage.setItem("minitube_folder_sort_dir", folderSortDir.value);
});

const sortedFolders = computed(() => {
  const list = [...folders.value];
  const multiplier = folderSortDir.value === "asc" ? 1 : -1;
  if (folderSortBy.value === "name") {
    return list.sort((a, b) => a.name.localeCompare(b.name, "zh-TW") * multiplier);
  } else {
    // 預設為建立時間 (createdAt)
    return list.sort((a, b) => (new Date(a.createdAt) - new Date(b.createdAt)) * multiplier);
  }
});
const page = ref(0);
const totalPages = ref(0);
const loading = ref(false);
const errorMessage = ref("");

// 資料夾階層狀態
const currentFolderId = ref(null); // null 代表根目錄，數字代表資料夾 ID
const currentFolderName = ref("");

// 影片排序狀態
const sortBy = ref(localStorage.getItem("minitube_sort_by") || "createdAt");
const sortDir = ref(localStorage.getItem("minitube_sort_dir") || "desc");

// 瀏覽模式
const viewMode = ref(localStorage.getItem("minitube_view_mode") || "grid");

// 批量刪除與移動選擇狀態
const selectedIds = ref([]);
const selectMode = ref(false);
const showBatchFolderDropdown = ref(false);

function toggleSelectMode() {
  selectMode.value = !selectMode.value;
  selectedIds.value = [];
  showBatchFolderDropdown.value = false;
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

async function editFolder(folder) {
  const name = prompt("請輸入新的資料夾名稱：", folder.name);
  if (!name || !name.trim() || name.trim() === folder.name) return;
  try {
    await http.put(`/folders/${folder.id}`, { name: name.trim() });
    await loadFolders();
  } catch (err) {
    alert("修改資料夾名稱失敗：" + (err.response?.data?.message || err.message));
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
  sessionStorage.setItem("minitube_active_folder_id", folder.id);
  sessionStorage.setItem("minitube_active_folder_name", folder.name);
  load(0);
}

function exitFolder() {
  currentFolderId.value = null;
  currentFolderName.value = "";
  sessionStorage.removeItem("minitube_active_folder_id");
  sessionStorage.removeItem("minitube_active_folder_name");
  loadFolders();
  load(0);
}

async function batchMoveToFolder(folderId) {
  showBatchFolderDropdown.value = false;
  try {
    const url = "/videos/batch/folder" + (folderId ? `?folderId=${folderId}` : "");
    await http.put(url, selectedIds.value);
    selectMode.value = false;
    load(page.value);
  } catch (err) {
    alert("批量移動影片失敗：" + (err.response?.data?.message || err.message));
  }
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

function handleOutsideClick(e) {
  if (showBatchFolderDropdown.value && !e.target.closest(".batch-folder-container")) {
    showBatchFolderDropdown.value = false;
  }
}

onUnmounted(() => {
  stopPolling();
  window.removeEventListener("click", handleOutsideClick);
});

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

function restoreFolderState() {
  const storedFolderId = sessionStorage.getItem("minitube_active_folder_id");
  const storedFolderName = sessionStorage.getItem("minitube_active_folder_name");
  if (storedFolderId !== null && storedFolderId !== "null") {
    currentFolderId.value = Number(storedFolderId);
    currentFolderName.value = storedFolderName || "";
  } else {
    currentFolderId.value = null;
    currentFolderName.value = "";
  }
}

watch(
  () => authStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn) {
      restoreFolderState();
      loadFolders();
      load(0);
    } else {
      videos.value = [];
      folders.value = [];
      sessionStorage.removeItem("minitube_active_folder_id");
      sessionStorage.removeItem("minitube_active_folder_name");
      currentFolderId.value = null;
      currentFolderName.value = "";
      stopPolling();
    }
  }
);

onMounted(() => {
  window.addEventListener("click", handleOutsideClick);
  if (authStore.isLoggedIn) {
    restoreFolderState();
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
      <div v-if="currentFolderId !== null" class="breadcrumb-container">
        <button class="btn btn-back" @click="exitFolder">返回上一頁</button>
        <div class="breadcrumb">
          <span class="crumb-link" @click="exitFolder">全部影片</span>
          <span class="crumb-separator">/</span>
          <span class="crumb-current">{{ currentFolderName }}</span>
        </div>
      </div>

      <!-- 資料夾列表區塊（僅在根目錄顯示） -->
      <div v-if="currentFolderId === null" class="folders-section">
        <div class="section-title-row">
          <div class="section-left">
            <h3 class="section-title">資料夾</h3>
            <div class="folder-sorting">
              <span class="label">排序依據：</span>
              <select v-model="folderSortBy" class="select-input">
                <option value="createdAt">建立時間</option>
                <option value="name">名稱</option>
              </select>
              <select v-model="folderSortDir" class="select-input">
                <option value="desc">降冪</option>
                <option value="asc">升冪</option>
              </select>
            </div>
          </div>
          <button class="btn" @click="createFolder">新增資料夾</button>
        </div>
        <div class="folders-grid">
          <div
            v-for="folder in sortedFolders"
            :key="folder.id"
            class="folder-card"
            @click="enterFolder(folder)"
          >
            <div class="folder-icon">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" style="color: #aaa; display: block;">
                <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>
              </svg>
            </div>
            <div class="folder-info">
              <div class="folder-name" :title="folder.name">{{ folder.name }}</div>
              <div class="folder-date">{{ formatDate(folder.createdAt) }}</div>
            </div>
            <div class="folder-actions" @click.stop>
              <button class="folder-action-btn" @click="editFolder(folder)" title="修改資料夾名稱">編輯</button>
              <button class="folder-action-btn delete" @click="deleteFolder(folder)" title="刪除資料夾">刪除</button>
            </div>
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
              卡片
            </button>
            <button
              class="icon-btn"
              type="button"
              :class="{ active: viewMode === 'list' }"
              title="橫向清單模式"
              @click="viewMode = 'list'"
            >
              清單
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
            {{ selectMode ? '取消多選' : '多選影片' }}
          </button>
          
          <!-- 批量刪除 -->
          <button
            v-if="selectMode && selectedIds.length > 0"
            class="btn danger bulk-delete-btn"
            type="button"
            @click="deleteSelected"
          >
            確認刪除 ({{ selectedIds.length }})
          </button>

          <!-- 批量移動至資料夾 -->
          <div v-if="selectMode && selectedIds.length > 0" class="batch-folder-container">
            <button
              class="btn"
              type="button"
              @click="showBatchFolderDropdown = !showBatchFolderDropdown"
            >
              移入資料夾 ({{ selectedIds.length }})
            </button>
            <div v-if="showBatchFolderDropdown" class="batch-folder-dropdown">
              <div class="dropdown-header">移動選取影片至：</div>
              <div class="dropdown-item" @click="batchMoveToFolder(null)">根目錄 (無)</div>
              <div
                v-for="folder in folders"
                :key="folder.id"
                class="dropdown-item"
                @click="batchMoveToFolder(folder.id)"
              >
                {{ folder.name }}
              </div>
            </div>
          </div>

          <button
            class="btn danger"
            type="button"
            @click="deleteAll"
          >
            一鍵全部刪除
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
.breadcrumb-container {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.btn-back {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  padding: 6px 12px;
  font-size: 13px;
  color: var(--text-primary);
  border-radius: var(--border-radius-md);
  cursor: pointer;
  transition: var(--transition-smooth);
}

.btn-back:hover {
  background: var(--border-color);
  border-color: var(--text-muted);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
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
  flex-wrap: wrap;
  gap: 12px;
}

.section-left {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.folder-sorting {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.folder-sorting .select-input {
  padding: 4px 8px;
  font-size: 12px;
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
  padding-right: 16px;
}

.folder-date {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.folder-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
}

.folder-action-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
  font-size: 11px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.folder-action-btn:hover {
  color: var(--text-primary);
  background-color: rgba(255, 255, 255, 0.05);
}

.folder-action-btn.delete:hover {
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

/* 批量移動下拉選單樣式 */
.batch-folder-container {
  position: relative;
  display: inline-block;
}

.batch-folder-dropdown {
  position: absolute;
  left: 0;
  top: 100%;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  min-width: 160px;
  box-shadow: var(--shadow-premium);
  z-index: 20;
  margin-top: 6px;
  overflow: hidden;
}

.batch-folder-dropdown .dropdown-header {
  padding: 8px 12px;
  font-size: 11px;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-color);
  background-color: rgba(255, 255, 255, 0.02);
  font-weight: 500;
  white-space: nowrap;
}

.batch-folder-dropdown .dropdown-item {
  padding: 10px 14px;
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
  transition: background-color 0.2s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.batch-folder-dropdown .dropdown-item:hover {
  background-color: var(--border-color);
}

@media (max-width: 768px) {
  .toolbar-upload-indicator {
    display: none;
  }
}
</style>
