<script setup>
import { onMounted, onUnmounted, ref, watch, computed } from "vue";
import { useRouter } from "vue-router";
import { storeToRefs } from "pinia";
import { useVideoStateStore } from "../store/videoState";
import { listVideos, searchVideos, batchDeleteVideos, deleteAllVideos, updateVideo } from "../api/video";
import { useAuthStore } from "../store/auth";
import VideoCard from "../components/VideoCard.vue";
import { useUploadStore } from "../store/upload";
import http from "../api/http";

const STATUS_POLL_INTERVAL_MS = 5000;

const router = useRouter();
const authStore = useAuthStore();
const uploadStore = useUploadStore();
const videoStateStore = useVideoStateStore();
const {
  page,
  size,
  searchKeyword,
  currentFolderId,
  currentFolderName,
  sortBy,
  sortDir,
  viewMode
} = storeToRefs(videoStateStore);
const videos = ref([]);
const folders = ref([]);
const currentFolderBreadcrumbs = ref([]);
const currentParentFolderId = ref(null);

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
const totalPages = ref(0);
const loading = ref(false);
const errorMessage = ref("");

// 批量刪除與移動選擇狀態
const selectedIds = ref([]);
const selectedFolderIds = ref([]);
const selectMode = ref(false);
const showBatchFolderDropdown = ref(false);

const totalSelected = computed(() => selectedIds.value.length + selectedFolderIds.value.length);

function toggleSelectMode() {
  selectMode.value = !selectMode.value;
  selectedIds.value = [];
  selectedFolderIds.value = [];
  showBatchFolderDropdown.value = false;
}

async function loadFolders() {
  if (!authStore.isLoggedIn) return;
  try {
    const res = await http.get("/folders", {
      params: { parentId: currentFolderId.value }
    });
    folders.value = res.data;
  } catch (err) {
    console.error("無法取得資料夾列表", err);
  }
}

async function createFolder() {
  const name = prompt("請輸入新資料夾名稱：");
  if (!name || !name.trim()) return;
  try {
    await http.post("/folders", {
      name: name.trim(),
      parentId: currentFolderId.value
    });
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
  if (!confirm(`確定要刪除資料夾「${folder.name}」嗎？\n資料夾內的子資料夾及影片會被安全移出，影片保留在根目錄，不會被刪除。`)) return;
  try {
    await http.delete(`/folders/${folder.id}`);
    await loadFolders();
    load(0);
  } catch (err) {
    alert("刪除資料夾失敗：" + (err.response?.data?.message || err.message));
  }
}

async function navigateToFolder(folderId) {
  if (folderId === null || folderId === "null" || folderId === "") {
    currentFolderId.value = null;
    currentFolderName.value = "";
    currentFolderBreadcrumbs.value = [];
    currentParentFolderId.value = null;
    sessionStorage.removeItem("minitube_active_folder_id");
    sessionStorage.removeItem("minitube_active_folder_name");
  } else {
    currentFolderId.value = Number(folderId);
    sessionStorage.setItem("minitube_active_folder_id", folderId);
    try {
      const res = await http.get(`/folders/${folderId}`);
      currentFolderName.value = res.data.name;
      currentFolderBreadcrumbs.value = res.data.breadcrumbs || [];
      currentParentFolderId.value = res.data.parentId;
      sessionStorage.setItem("minitube_active_folder_name", res.data.name);
    } catch (err) {
      console.error("無法取得資料夾詳情", err);
      currentFolderId.value = null;
      currentFolderName.value = "";
      currentFolderBreadcrumbs.value = [];
      currentParentFolderId.value = null;
      sessionStorage.removeItem("minitube_active_folder_id");
      sessionStorage.removeItem("minitube_active_folder_name");
    }
  }
  await loadFolders();
  load(0);
}

const allFolders = ref([]);

async function loadAllFolders() {
  try {
    const res = await http.get("/folders", { params: { all: true } });
    allFolders.value = res.data;
  } catch (err) {
    console.error("無法取得所有資料夾列表", err);
  }
}

function toggleBatchFolderDropdown() {
  showBatchFolderDropdown.value = !showBatchFolderDropdown.value;
  if (showBatchFolderDropdown.value) {
    loadAllFolders();
  }
}

function getFolderFullPath(folder) {
  if (!folder.breadcrumbs || folder.breadcrumbs.length === 0) {
    return folder.name;
  }
  return folder.breadcrumbs.map(c => c.name).join(" › ");
}

function enterFolder(folder) {
  navigateToFolder(folder.id);
}

function exitFolder() {
  navigateToFolder(currentParentFolderId.value);
}

async function batchMoveToFolder(folderId) {
  showBatchFolderDropdown.value = false;
  try {
    // 移動影片
    if (selectedIds.value.length > 0) {
      const url = "/videos/batch/folder" + (folderId ? `?folderId=${folderId}` : "");
      await http.put(url, selectedIds.value);
    }
    selectMode.value = false;
    selectedIds.value = [];
    selectedFolderIds.value = [];
    load(page.value);
  } catch (err) {
    alert("批量移動影片失敗：" + (err.response?.data?.message || err.message));
  }
}



function toggleSort(field) {
  if (sortBy.value === field) {
    sortDir.value = sortDir.value === "asc" ? "desc" : "asc";
    folderSortDir.value = sortDir.value;
  } else {
    sortBy.value = field;
    sortDir.value = "desc";
    folderSortBy.value = field === "title" ? "name" : "createdAt";
    folderSortDir.value = "desc";
  }
}

function toggleSortDirection() {
  sortDir.value = sortDir.value === "asc" ? "desc" : "asc";
  folderSortDir.value = sortDir.value;
}

function onLocalSearch() {
  load(0);
}

function clearSearch() {
  searchKeyword.value = "";
  onLocalSearch();
}

function goToVideo(video) {
  if (selectMode.value) {
    handleSelect(video.id, !selectedIds.value.includes(video.id));
  } else {
    router.push({ name: "video-detail", params: { id: video.id } });
  }
}

function formatSize(bytes) {
  if (!bytes) return "0 Bytes";
  const k = 1024;
  const sizes = ["Bytes", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}

async function load(p = 0, silent = false) {
  if (!silent) {
    loading.value = true;
    errorMessage.value = "";
  }
  try {
    const folderQueryId = currentFolderId.value === null ? "root" : currentFolderId.value.toString();
    
    let data;
    if (searchKeyword.value.trim()) {
      data = await searchVideos({
        q: searchKeyword.value.trim(),
        folderId: currentFolderId.value === null ? "" : currentFolderId.value.toString(),
        page: p,
        size: size.value,
        sortBy: sortBy.value,
        sortDir: sortDir.value,
      });
    } else {
      data = await listVideos({
        folderId: folderQueryId,
        page: p,
        size: size.value,
        sortBy: sortBy.value,
        sortDir: sortDir.value,
      });
    }
    
    videos.value = data.videos || [];
    videoStateStore.videos = videos.value;
    page.value = data.page || 0;
    totalPages.value = data.totalPages || 0;
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
  if (selectedIds.value.length === 0 && selectedFolderIds.value.length === 0) return;
  const total = totalSelected.value;
  if (!confirm(`確定要刪除這 ${total} 個項目嗎？（包含影片 ${selectedIds.value.length} 個，資料夾 ${selectedFolderIds.value.length} 個）\n此動作無法復原！`)) return;
  try {
    // 刪除影片
    if (selectedIds.value.length > 0) {
      await batchDeleteVideos(selectedIds.value);
    }
    // 逐一刪除資料夾
    for (const fid of selectedFolderIds.value) {
      await http.delete(`/folders/${fid}`);
    }
    selectMode.value = false;
    selectedIds.value = [];
    selectedFolderIds.value = [];
    loadFolders();
    load(page.value);
  } catch (err) {
    alert("刪除失敗：" + err.message);
  }
}

function handleFolderSelect(id, checked) {
  if (checked) {
    if (!selectedFolderIds.value.includes(id)) selectedFolderIds.value.push(id);
  } else {
    selectedFolderIds.value = selectedFolderIds.value.filter((x) => x !== id);
  }
}

async function deleteOneVideo(video) {
  if (!confirm(`確定要刪除影片「${video.title}」嗎？此動作無法復原！`)) return;
  try {
    await batchDeleteVideos([video.id]);
    load(page.value);
  } catch (err) {
    alert("刪除失敗：" + err.message);
  }
}

async function editVideo(video) {
  const newTitle = prompt("請輸入新的影片檔名：", video.title);
  if (newTitle === null) return;
  const trimmed = newTitle.trim();
  if (!trimmed) {
    alert("影片檔名不能為空");
    return;
  }
  try {
    await updateVideo(video.id, { title: trimmed });
    load(page.value, true);
  } catch (err) {
    alert("修改檔名失敗：" + (err.response?.data?.message || err.message));
  }
}

async function clearCurrentDirectoryVideos() {
  const dirName = currentFolderId.value === null ? "全部影片（根目錄）" : `資料夾「${currentFolderName.value}」`;
  if (!confirm(`⚠️ 警告：確定要刪除當前 ${dirName} 下的所有影片嗎？\n此操作不可逆，將會刪除該目錄下的所有實體影片檔案！`)) return;

  loading.value = true;
  try {
    const folderQueryId = currentFolderId.value === null ? "root" : currentFolderId.value.toString();
    const res = await listVideos({
      folderId: folderQueryId,
      page: 0,
      size: 9999
    });

    const idsToDelete = res.content.map(v => v.id);
    if (idsToDelete.length === 0) {
      alert("當前目錄下沒有任何影片可供刪除。");
      loading.value = false;
      return;
    }

    await batchDeleteVideos(idsToDelete);
    load(0);
    alert(`已成功刪除當前 ${dirName} 下的 ${idsToDelete.length} 部影片。`);
  } catch (err) {
    alert("一鍵刪除失敗：" + (err.response?.data?.message || err.message));
  } finally {
    loading.value = false;
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

watch(size, (newVal) => {
  localStorage.setItem("minitube_page_size", newVal.toString());
  load(0);
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
      navigateToFolder(sessionStorage.getItem("minitube_active_folder_id"));
    } else {
      videos.value = [];
      folders.value = [];
      sessionStorage.removeItem("minitube_active_folder_id");
      sessionStorage.removeItem("minitube_active_folder_name");
      videoStateStore.resetState();
      stopPolling();
    }
  }
);

onMounted(() => {
  window.addEventListener("click", handleOutsideClick);
  if (authStore.isLoggedIn) {
    navigateToFolder(sessionStorage.getItem("minitube_active_folder_id"));
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
    <!-- 未登入提示 -->
    <div v-if="!authStore.isLoggedIn" class="login-prompt">
      <p>登入查看與管理你上傳的影片。</p>
      <RouterLink class="btn primary" :to="{ name: 'login' }">前往登入</RouterLink>
    </div>

    <template v-else>
      <!-- ===== 頂部操作列 ===== -->
      <div class="action-bar">
        <div class="action-bar-left">
          <RouterLink class="action-btn primary" :to="{ name: 'upload' }">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z"/>
            </svg>
            上傳
          </RouterLink>
          <button class="action-btn" @click="createFolder">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M20 6h-8l-2-2H4c-1.11 0-1.99.89-1.99 2L2 18c0 1.11.89 2 2 2h16c1.11 0 2-.89 2-2V8c0-1.11-.89-2-2-2zm-1 8h-3v3h-2v-3h-3v-2h3V9h2v3h3v2z"/>
            </svg>
            新建文件夾
          </button>
        </div>
        <div class="action-bar-right">
          <div class="search-box">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="currentColor" class="search-icon">
              <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
            </svg>
            <input
              v-model="searchKeyword"
              type="text"
              placeholder="搜尋文件"
              @input="onLocalSearch"
              @keydown.enter="onLocalSearch"
            />
            <button
              v-if="searchKeyword"
              class="search-clear-btn"
              type="button"
              @click="clearSearch"
              title="清除搜尋"
            >
              ✕
            </button>
          </div>
        </div>
      </div>

      <!-- ===== 麵包屑 / 導航列 ===== -->
      <div class="nav-bar">
        <div class="nav-bar-left">
          <button v-if="currentFolderId !== null" class="nav-back-btn" @click="exitFolder" title="返回上一層">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor">
              <path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>
            </svg>
          </button>
          <span class="nav-separator" v-if="currentFolderId !== null">|</span>
          <div class="breadcrumb-group">
            <span
              v-if="currentFolderId !== null"
              class="crumb-link"
              @click="navigateToFolder(null)"
            >全部影片</span>
            
            <template v-for="crumb in currentFolderBreadcrumbs" :key="crumb.id">
              <span class="crumb-sep">›</span>
              <span
                v-if="crumb.id !== currentFolderId"
                class="crumb-link"
                @click="navigateToFolder(crumb.id)"
              >{{ crumb.name }}</span>
              <span v-else class="crumb-current">{{ crumb.name }}</span>
            </template>
            
            <span v-if="currentFolderId === null" class="crumb-current">全部影片</span>
          </div>
        </div>

        <div class="nav-bar-right">
          <!-- 上傳中指示器 -->
          <div v-if="uploadStore.isUploading" class="upload-indicator" @click="router.push({ name: 'upload' })" title="點擊查看上傳詳情">
            <div class="spinner-tiny"></div>
            <span>{{ uploadStore.progress }}%</span>
          </div>



          <!-- 一鍵刪除當前目錄下所有影片 -->
          <button class="nav-icon-btn" style="color: var(--danger-color);" @click="clearCurrentDirectoryVideos" title="一鍵清空此目錄影片">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
            </svg>
          </button>

          <!-- 刷新 -->
          <button class="nav-icon-btn" @click="load(0)" title="刷新">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
            </svg>
          </button>

          <!-- 多選 / 批量 -->
          <button class="nav-icon-btn" :class="{ active: selectMode }" @click="toggleSelectMode" title="多選">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
              <path d="M3 5h2V3c-1.1 0-2 .9-2 2zm0 8h2v-2H3v2zm4 8h2v-2H7v2zM3 9h2V7H3v2zm10-6h-2v2h2V3zm6 0v2h2c0-1.1-.9-2-2-2zM5 21v-2H3c0 1.1.9 2 2 2zm-2-4h2v-2H3v2zM9 3H7v2h2V3zm2 18h2v-2h-2v2zm8-8h2v-2h-2v2zm0 8c1.1 0 2-.9 2-2h-2v2zm0-12h2V7h-2v2zm0 8h2v-2h-2v2zm-4 4h2v-2h-2v2zm0-16h2V3h-2v2zM7 17h10V7H7v10zm2-8h6v6H9V9z"/>
            </svg>
          </button>

          <!-- 視圖切換 (格狀/列表) -->
          <div class="view-toggle">
            <button class="nav-icon-btn" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'" title="列表視圖">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                <path d="M3 5h2V3c-1.1 0-2 .9-2 2zm0 4h2V7H3v2zm0 4h2v-2H3v2zm0 4h2v-2H3v2zm0 4h2v-2H3v2zm4 0h14v-2H7v2zm0-4h14v-2H7v2zm0-4h14v-2H7v2zm0-4h14V7H7v2zm0-6v2h14V3H7z"/>
              </svg>
            </button>
            <button class="nav-icon-btn" :class="{ active: viewMode === 'grid' }" @click="viewMode = 'grid'" title="格狀視圖">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
                <path d="M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v2zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z"/>
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- 批量操作列 (多選模式下才顯示) -->
      <div v-if="selectMode && totalSelected > 0" class="batch-bar">
        <span class="batch-count">已選擇 {{ totalSelected }} 個項目</span>
        <button class="action-btn danger" @click="deleteSelected">
          刪除 ({{ totalSelected }})
        </button>
        <div class="batch-folder-container" v-if="selectedIds.length > 0">
          <button class="action-btn" @click="toggleBatchFolderDropdown">
            移入文件夾
          </button>
          <div v-if="showBatchFolderDropdown" class="batch-folder-dropdown">
            <div class="dropdown-header">移動至：</div>
            <div class="dropdown-item" @click="batchMoveToFolder(null)">根目錄</div>
            <div
              v-for="folder in allFolders"
              :key="folder.id"
              class="dropdown-item"
              @click="batchMoveToFolder(folder.id)"
              :title="getFolderFullPath(folder)"
            >{{ getFolderFullPath(folder) }}</div>
          </div>
        </div>
      </div>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

      <!-- ===== 列表視圖 ===== -->
      <template v-if="viewMode === 'list'">
        <!-- 欄位標題列 -->
        <div class="list-header" :class="{ 'with-check': selectMode }">
          <div class="col-check" v-if="selectMode">
            <!-- 全選 checkbox -->
            <input
              type="checkbox"
              :checked="selectedIds.length === videos.length && videos.length > 0"
              @change="(e) => { if (e.target.checked) { selectedIds = videos.map(v => v.id); selectedFolderIds = sortedFolders.map(f => f.id); } else { selectedIds = []; selectedFolderIds = []; } }"
              @click.stop
            />
          </div>
          <div class="col-name" @click="toggleSort('title')">
            名稱
            <span class="sort-arrow">{{ sortBy === 'title' ? (sortDir === 'asc' ? '↑' : '↓') : '' }}</span>
          </div>
          <div class="col-date" @click="toggleSort('createdAt')">
            修改時間
            <span class="sort-arrow">{{ sortBy === 'createdAt' ? (sortDir === 'asc' ? '↑' : '↓') : '' }}</span>
          </div>
          <div class="col-size" @click="toggleSort('fileSize')">
            大小
            <span class="sort-arrow">{{ sortBy === 'fileSize' ? (sortDir === 'asc' ? '↑' : '↓') : '' }}</span>
          </div>
          <div class="col-actions-header"></div>
        </div>

        <!-- 資料夾行（且搜尋時隱藏） -->
        <template v-if="!searchKeyword.trim()">
          <div
            v-for="folder in sortedFolders"
            :key="'folder-' + folder.id"
            class="list-row folder-row"
            :class="{ 'with-check': selectMode, selected: selectedFolderIds.includes(folder.id) }"
            @click="selectMode ? handleFolderSelect(folder.id, !selectedFolderIds.includes(folder.id)) : enterFolder(folder)"
          >
            <div class="col-check" v-if="selectMode" @click.stop>
              <input
                type="checkbox"
                :checked="selectedFolderIds.includes(folder.id)"
                @change="handleFolderSelect(folder.id, $event.target.checked)"
              />
            </div>
            <div class="col-name">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" class="row-icon folder-icon-color">
                <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>
              </svg>
              <span class="row-name">{{ folder.name }}</span>
            </div>
            <div class="col-date">{{ formatDate(folder.createdAt) }}</div>
            <div class="col-size">—</div>
            <div class="col-actions" @click.stop>
              <button class="row-action-btn" @click="editFolder(folder)" title="重新命名">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                  <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                </svg>
              </button>
              <button class="row-action-btn delete" @click="deleteFolder(folder)" title="刪除">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                  <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                </svg>
              </button>
            </div>
          </div>
        </template>

        <!-- 影片行 -->
        <div v-if="loading" class="empty-state">載入中...</div>
        <template v-else>
          <div v-if="videos.length === 0 && (currentFolderId !== null || sortedFolders.length === 0)" class="empty-state">
            此處目前沒有任何影片。
          </div>
          <div
            v-for="video in videos"
            :key="video.id"
            class="list-row video-row"
            :class="{ 'with-check': selectMode, selected: selectedIds.includes(video.id) }"
            @click="goToVideo(video)"
          >
            <div class="col-check" v-if="selectMode" @click.stop>
              <input type="checkbox" :checked="selectedIds.includes(video.id)" @change="handleSelect(video.id, $event.target.checked)" />
            </div>
            <div class="col-name">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" class="row-icon video-icon-color">
                <path d="M17 10.5V7c0-.55-.45-1-1-1H4c-.55 0-1 .45-1 1v10c0 .55.45 1 1 1h12c.55 0 1-.45 1-1v-3.5l4 4v-11l-4 4z"/>
              </svg>
              <span v-if="video.status === 'UPLOADING'" class="status-badge uploading" style="display: inline-flex; align-items: center; gap: 6px;">
                <svg class="progress-ring-tiny" width="14" height="14" style="transform: rotate(-90deg);">
                  <circle class="progress-ring-bg" stroke="rgba(217, 119, 6, 0.15)" stroke-width="1.8" fill="transparent" r="5" cx="7" cy="7"/>
                  <circle class="progress-ring-circle" stroke="#d97706" stroke-width="1.8" fill="transparent" r="5" cx="7" cy="7"
                          :stroke-dasharray="2 * Math.PI * 5"
                          :stroke-dashoffset="2 * Math.PI * 5 * (1 - (video.transcodeProgress || 0) / 100)"
                          stroke-linecap="round"
                          style="transition: stroke-dashoffset 0.35s;"/>
                </svg>
                <span>轉檔 {{ video.transcodeProgress || 0 }}%</span>
              </span>
              <span class="row-name">{{ video.title }}</span>
            </div>
            <div class="col-date">{{ formatDate(video.createdAt) }}</div>
            <div class="col-size">{{ formatSize(video.fileSize) }}</div>
            <div class="col-actions" @click.stop>
              <button class="row-action-btn edit" title="修改檔名" @click="editVideo(video)" style="margin-right: 4px; background: rgba(255, 255, 255, 0.12) !important; color: var(--text-primary) !important;">
                <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
                  <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                </svg>
              </button>
              <button class="row-action-btn delete" title="刪除影片" @click="deleteOneVideo(video)">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor">
                  <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                </svg>
              </button>
            </div>
          </div>
        </template>
      </template>

      <!-- ===== 格狀視圖 ===== -->
      <template v-else>
        <!-- 資料夾格狀（且搜尋時隱藏） -->
        <template v-if="!searchKeyword.trim() && sortedFolders.length > 0">
          <div class="section-label">文件夾</div>
          <div class="folders-grid">
            <div
              v-for="folder in sortedFolders"
              :key="folder.id"
              class="folder-card"
              :class="{ selected: selectedFolderIds.includes(folder.id) }"
              @click="selectMode ? handleFolderSelect(folder.id, !selectedFolderIds.includes(folder.id)) : enterFolder(folder)"
            >
              <!-- 多選模式：左上角 checkbox -->
              <div v-if="selectMode" class="folder-card-check" @click.stop>
                <input
                  type="checkbox"
                  :checked="selectedFolderIds.includes(folder.id)"
                  @change="handleFolderSelect(folder.id, $event.target.checked)"
                />
              </div>
              <div class="folder-card-icon">
                <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor">
                  <path d="M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z"/>
                </svg>
              </div>
              <div class="folder-card-info">
                <div class="folder-card-name-row">
                  <div class="folder-card-name" :title="folder.name">{{ folder.name }}</div>
                  <button class="folder-edit-inline-btn" @click.stop="editFolder(folder)" title="重新命名">
                    <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
                      <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                    </svg>
                  </button>
                </div>
                <div class="folder-card-date">{{ formatDate(folder.createdAt) }}</div>
              </div>
              <div class="folder-card-actions" @click.stop>
                <button class="row-action-btn edit-btn" @click="editFolder(folder)" title="重新命名">
                  <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor">
                    <path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/>
                  </svg>
                </button>
                <button class="row-action-btn delete" @click="deleteFolder(folder)" title="刪除">
                  <svg viewBox="0 0 24 24" width="13" height="13" fill="currentColor">
                    <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>
          <div v-if="videos.length > 0" class="section-label" style="margin-top: 24px;">影片</div>
        </template>

        <div v-if="loading" class="empty-state">載入中...</div>
        <template v-else>
          <div v-if="videos.length === 0 && (currentFolderId !== null || sortedFolders.length === 0)" class="empty-state">
            此處目前沒有任何影片。
          </div>
          <div v-if="videos.length > 0" class="video-grid">
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
        </template>
      </template>

      <!-- 分頁與每頁數量選擇 -->
      <div class="pagination-container">
        <!-- 分頁控制（只有總頁數大於 1 時才顯示） -->
        <div class="pagination-controls" v-if="totalPages > 1">
          <button class="btn" type="button" :disabled="page === 0" @click="prevPage">上一頁</button>
          <span class="page-indicator">{{ page + 1 }} / {{ totalPages }}</span>
          <button class="btn" type="button" :disabled="page + 1 >= totalPages" @click="nextPage">下一頁</button>
        </div>

        <!-- 每頁數量選擇，永遠顯示 -->
        <div class="page-size-selector">
          <span class="select-label">每頁顯示：</span>
          <select v-model="size" class="nav-select bottom-select">
            <option :value="30">30 筆</option>
            <option :value="50">50 筆</option>
            <option :value="100">100 筆</option>
          </select>
        </div>
      </div>

    </template>
  </div>
</template>

<style scoped>
/* ===== 頂部操作列 ===== */
.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 0 12px;
  gap: 12px;
}

.action-bar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.action-bar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 18px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-primary);
  text-decoration: none;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.action-btn:hover {
  background: var(--border-color);
}

.action-btn.primary {
  background: var(--accent-blue);
  color: #fff;
  border-color: var(--accent-blue);
}

.action-btn.primary:hover {
  background: var(--accent-hover);
  border-color: var(--accent-hover);
}

.action-btn.danger {
  background: transparent;
  color: var(--danger-color);
  border-color: var(--danger-color);
}

.action-btn.danger:hover {
  background: rgba(211, 47, 47, 0.08);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 20px;
  padding: 7px 16px;
  width: 240px;
  transition: all 0.15s ease;
}

.search-box:focus-within {
  border-color: var(--accent-blue);
  box-shadow: 0 0 0 2px rgba(255, 121, 0, 0.15);
}

.search-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}

.search-box input {
  border: none;
  background: transparent;
  outline: none;
  font-size: 13px;
  color: var(--text-primary);
  flex: 1;
  min-width: 0;
}

.search-clear-btn {
  background: transparent;
  border: none;
  color: var(--text-muted);
  font-size: 13px;
  cursor: pointer;
  padding: 2px 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition-smooth);
}

.search-clear-btn:hover {
  color: var(--text-primary);
}

.search-box input::placeholder {
  color: var(--text-muted);
}

/* ===== 麵包屑 / 導航列 ===== */
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 4px;
}

.nav-bar-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--text-secondary);
}

.nav-bar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-back-btn {
  display: flex;
  align-items: center;
  background: transparent;
  border: none;
  cursor: pointer;
  color: var(--text-secondary);
  padding: 4px;
  border-radius: 4px;
  transition: all 0.15s ease;
}

@media (hover: hover) {
  .nav-back-btn:hover {
    background: var(--border-color);
    color: var(--text-primary);
  }
}

.nav-separator {
  color: var(--border-color);
  font-weight: 300;
  margin: 0 2px;
}

.breadcrumb-group {
  display: flex;
  align-items: center;
  gap: 4px;
}

.crumb-link {
  cursor: pointer;
  color: var(--accent-blue);
  transition: color 0.15s ease;
}

.crumb-link:hover {
  text-decoration: underline;
}

.crumb-sep {
  color: var(--text-muted);
  font-size: 16px;
  line-height: 1;
}

.crumb-current {
  font-weight: 500;
  color: var(--text-primary);
}

.crumb-arrow {
  color: var(--text-muted);
  margin-left: 2px;
}

.size-select-container {
  display: flex;
  align-items: center;
}

.nav-select {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  border-radius: 4px;
  font-size: 12px;
  padding: 4px 8px;
  outline: none;
  cursor: pointer;
  height: 34px;
  box-sizing: border-box;
  transition: all 0.15s ease;
}

.nav-select:focus,
.nav-select:hover {
  border-color: var(--accent-blue);
  color: var(--accent-blue);
}

.nav-icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
}

@media (hover: hover) {
  .nav-icon-btn:hover {
    background: var(--border-color);
    color: var(--text-primary);
  }
}

.nav-icon-btn.active {
  color: var(--accent-blue);
  background: rgba(255, 121, 0, 0.1);
}

.view-toggle {
  display: flex;
  align-items: center;
  gap: 2px;
}

.upload-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--accent-blue);
  font-weight: 500;
  cursor: pointer;
  padding: 4px 10px;
  background: rgba(255, 121, 0, 0.08);
  border-radius: 12px;
  border: 1px solid rgba(255, 121, 0, 0.2);
}

.spinner-tiny {
  width: 10px;
  height: 10px;
  border: 2px solid rgba(255, 121, 0, 0.2);
  border-left-color: var(--accent-blue);
  border-radius: 50%;
  animation: spin-tiny 0.8s linear infinite;
}

@keyframes spin-tiny {
  to { transform: rotate(360deg); }
}

/* ===== 批量操作列 ===== */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  background: rgba(255, 121, 0, 0.06);
  border: 1px solid rgba(255, 121, 0, 0.15);
  border-radius: 8px;
  margin: 8px 0;
}

.batch-count {
  font-size: 13px;
  font-weight: 500;
  color: var(--accent-blue);
  margin-right: auto;
}

/* ===== 列表欄位標題 ===== */
/* ===== 列表行 ===== */
.list-header,
.list-row {
  display: grid;
  grid-template-columns: 1fr 180px 100px 60px;
  padding: 9px 12px;
  align-items: center;
}

.list-header.with-check,
.list-row.with-check {
  grid-template-columns: 32px 1fr 180px 100px 60px;
}

.list-header {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-color);
  user-select: none;
  padding: 8px 12px;
}

.list-row {
  border-bottom: 1px solid var(--border-color);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.12s ease;
}

.list-header .col-name,
.list-header .col-date,
.list-header .col-size {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
}

.list-header .col-name:hover,
.list-header .col-date:hover,
.list-header .col-size:hover {
  color: var(--text-primary);
}

.sort-arrow {
  font-size: 11px;
  color: var(--accent-blue);
}

.col-actions-header {
  /* placeholder for actions column in header */
}

.list-row:hover {
  background: rgba(0, 0, 0, 0.03);
}

.list-row.selected {
  background: rgba(255, 121, 0, 0.06);
}

.col-name {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  color: var(--text-primary);
  font-weight: 500;
}

.row-icon {
  flex-shrink: 0;
}

.folder-icon-color {
  color: #ff8c00; /* vibrant folder orange */
}

.video-icon-color {
  color: var(--accent-blue);
}

.row-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.col-date {
  color: var(--text-secondary);
  font-size: 12px;
}

.col-size {
  color: var(--text-secondary);
  font-size: 12px;
}

.col-check {
  display: flex;
  align-items: center;
  width: 32px;
}

.col-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: flex-end;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.list-row:hover .col-actions {
  opacity: 1;
}

.row-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.12s ease;
}

.row-action-btn:hover {
  background: var(--border-color);
  color: var(--text-primary);
}

.row-action-btn.delete:hover {
  background: rgba(211, 47, 47, 0.1);
  color: var(--danger-color);
}

.status-badge {
  font-size: 10px;
  padding: 2px 7px;
  border-radius: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.status-badge.uploading {
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
  border: 1px solid rgba(245, 158, 11, 0.3);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* ===== 格狀視圖：文件夾 ===== */
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  padding: 12px 4px 8px;
}

.folders-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 8px;
}

.folder-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  position: relative;
  transition: all 0.15s ease;
}

.folder-card:hover {
  border-color: var(--accent-blue);
  box-shadow: 0 2px 8px rgba(255, 121, 0, 0.12);
}

.folder-card.selected {
  border-color: var(--accent-blue);
  background: rgba(255, 121, 0, 0.05);
}

.folder-card-check {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  background: transparent;
  border-radius: 4px;
}

.folder-card-icon {
  color: #ff8c00; /* vibrant folder orange */
  flex-shrink: 0;
}

.folder-card-info {
  flex: 1;
  min-width: 0;
}

.folder-card-name-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}

.folder-card-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.folder-edit-inline-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  border: none;
  background: rgba(255, 255, 255, 0.08);
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.12s ease;
  flex-shrink: 0;
}

.folder-edit-inline-btn:hover {
  background: rgba(255, 122, 0, 0.15);
  color: var(--accent-blue);
}

.folder-card-date {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: 2px;
}

.folder-card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  position: absolute;
  right: 8px;
  top: 8px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.folder-card:hover .folder-card-actions {
  opacity: 1;
}

/* 提高在資料夾卡片內的按鈕對比度，解決暗底看不清的問題 */
.folder-card-actions .row-action-btn {
  background: rgba(255, 255, 255, 0.15) !important;
  color: var(--text-primary) !important;
  border: 1px solid var(--border-color);
  width: 28px;
  height: 28px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
}

.folder-card-actions .row-action-btn:hover {
  background: var(--accent-blue) !important;
  color: #fff !important;
}

.folder-card-actions .row-action-btn.delete {
  color: #ff453a !important;
  background: rgba(255, 69, 58, 0.15) !important;
}

.folder-card-actions .row-action-btn.delete:hover {
  background: #ff453a !important;
  color: #fff !important;
}

/* ===== 批量下拉選單 ===== */
.batch-folder-container {
  position: relative;
}

.batch-folder-dropdown {
  position: absolute;
  left: 0;
  top: calc(100% + 4px);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  min-width: 160px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
  z-index: 100;
  overflow: hidden;
}

.dropdown-header {
  padding: 8px 12px;
  font-size: 11px;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-color);
  font-weight: 500;
}

.dropdown-item {
  padding: 9px 14px;
  font-size: 13px;
  color: var(--text-primary);
  cursor: pointer;
  transition: background 0.12s ease;
}

.dropdown-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

/* ===== 危險區 / 分頁 ===== */
.danger-zone {
  display: flex;
  justify-content: center;
  padding: 32px 0 8px;
}

.small-btn {
  font-size: 12px;
  padding: 6px 14px;
  opacity: 0.5;
}

.small-btn:hover {
  opacity: 1;
}

/* ===== 格狀影片 ===== */
.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  padding-top: 8px;
}

.pagination-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
  margin-top: 40px;
  flex-wrap: wrap;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 16px;
}

.page-indicator {
  font-size: 13px;
  color: var(--text-primary);
  min-width: 40px;
  text-align: center;
}

.page-size-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}

.select-label {
  font-size: 13px;
  color: var(--text-muted);
}

.bottom-select {
  height: 30px !important;
  padding: 2px 6px !important;
}

/* ===== 響應式 ===== */
@media (max-width: 768px) {
  .action-bar-right {
    width: 100%;
  }

  .search-box {
    width: 100% !important;
  }

  .list-row,
  .list-header {
    grid-template-columns: 1fr 85px 70px;
  }

  .list-row.with-check,
  .list-header.with-check {
    grid-template-columns: 32px 1fr 85px 70px;
  }

  .col-actions,
  .col-actions-header {
    display: none !important;
  }

  .folder-card-actions {
    display: none !important; /* 手機版不顯示資料夾卡片右上角刪除按鈕 */
  }

  .folder-edit-inline-btn {
    display: flex !important;
    width: 24px;
    height: 24px;
    background: rgba(255, 255, 255, 0.12) !important;
    color: var(--text-primary) !important;
  }

  .action-bar {
    flex-wrap: wrap;
    gap: 8px;
  }

  .folders-grid {
    grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  }

  .pagination-container {
    flex-direction: column;
    gap: 16px;
    margin-top: 24px;
  }

  .status-badge {
    font-size: 9px !important;
    padding: 1px 4px !important;
  }

  .status-badge .spinner-tiny {
    width: 9px !important;
    height: 9px !important;
    border-width: 1.5px !important;
  }
}

</style>
