<script setup>
import { ref, onMounted, computed } from "vue";
import { useRouter } from "vue-router";
import { useUploadStore } from "../store/upload";

const router = useRouter();
const uploadStore = useUploadStore();

onMounted(() => {
  // 不要在重新整理時隨意清空正在上傳的佇列
  if (!uploadStore.isUploading) {
    uploadStore.queue = [];
    uploadStore.errorMessage = "";
    uploadStore.progress = 0;
  }
});

const errorMessage = ref("");
const fileInput = ref(null);
const folderInput = ref(null);
const isDragOver = ref(false);

const ALLOWED_EXTENSIONS = [
  ".mp4", ".webm", ".mov", ".mkv", ".avi",
  ".flv", ".3gp", ".wmv", ".m4v", ".mpg", ".mpeg"
];

const selectedIds = ref([]);

// 僅可取消等待中或上傳中的項目
const cancellableQueue = computed(() => {
  return uploadStore.queue.filter(item => ["waiting", "uploading"].includes(item.status));
});

const isAllSelected = computed({
  get() {
    return cancellableQueue.value.length > 0 && selectedIds.value.length === cancellableQueue.value.length;
  },
  set(val) {
    if (val) {
      selectedIds.value = cancellableQueue.value.map(item => item.id);
    } else {
      selectedIds.value = [];
    }
  }
});

function triggerFileInput() {
  fileInput.value.click();
}

function triggerFolderInput() {
  folderInput.value.click();
}

function onFolderChange(event) {
  const selectedList = Array.from(event.target.files);
  processSelectedFiles(selectedList);
  event.target.value = "";
}

function onDragOver() {
  isDragOver.value = true;
}

function onDragLeave() {
  isDragOver.value = false;
}

async function onDrop(event) {
  isDragOver.value = false;
  
  const items = event.dataTransfer.items;
  if (!items) return;
  
  const filesList = [];
  const promises = [];
  
  for (let i = 0; i < items.length; i++) {
    const item = items[i];
    if (item.kind === "file") {
      const entry = item.webkitGetAsEntry();
      if (entry) {
        promises.push(traverseFileTree(entry, filesList));
      }
    }
  }
  
  await Promise.all(promises);
  processSelectedFiles(filesList);
}

async function traverseFileTree(entry, filesList) {
  if (entry.isFile) {
    const file = await getFileFromEntry(entry);
    filesList.push(file);
  } else if (entry.isDirectory) {
    const dirReader = entry.createReader();
    const entries = await readAllEntries(dirReader);
    const promises = [];
    for (const subEntry of entries) {
      promises.push(traverseFileTree(subEntry, filesList));
    }
    await Promise.all(promises);
  }
}

function getFileFromEntry(entry) {
  return new Promise((resolve, reject) => {
    entry.file(resolve, reject);
  });
}

function readAllEntries(dirReader) {
  return new Promise((resolve) => {
    const allEntries = [];
    function read() {
      dirReader.readEntries((entries) => {
        if (entries.length > 0) {
          allEntries.push(...entries);
          read();
        } else {
          resolve(allEntries);
        }
      }, () => resolve(allEntries));
    }
    read();
  });
}

function onFileChange(event) {
  const selectedList = Array.from(event.target.files);
  processSelectedFiles(selectedList);
  event.target.value = ""; 
}

function processSelectedFiles(selectedList) {
  errorMessage.value = "";

  if (selectedList.length === 0) {
    return;
  }

  const validFiles = selectedList.filter((file) => {
    const name = file.name.toLowerCase();
    const isImageExtension = [".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif", ".bmp"].some((ext) => name.endsWith(ext));
    const isImageMime = file.type && file.type.startsWith("image/");
    const hasVideoExtension = ALLOWED_EXTENSIONS.some((ext) => name.endsWith(ext));
    const isVideoMime = file.type && file.type.startsWith("video/");
    
    return !isImageExtension && !isImageMime && (hasVideoExtension || isVideoMime);
  });

  if (validFiles.length === 0) {
    errorMessage.value = "在所選內容中找不到任何支援的影片檔案，請重新選擇！";
    return;
  }

  const activeFolderId = sessionStorage.getItem("minitube_active_folder_id");
  const folderId = activeFolderId && activeFolderId !== "null" ? Number(activeFolderId) : null;

  // 拖曳或選取影片檔案後，立即開始分片上傳
  uploadStore.addFilesToQueue(validFiles, folderId);
}

function goBack() {
  router.go(-1);
}

function handleCancelSingle(id) {
  uploadStore.cancelUpload(id);
  selectedIds.value = selectedIds.value.filter(x => x !== id);
}

function handleCancelSelected() {
  uploadStore.cancelSelected(selectedIds.value);
  selectedIds.value = [];
}

function handleClearFinished() {
  uploadStore.clearFinishedQueue();
  selectedIds.value = [];
}
</script>

<template>
  <div class="page">
    <div class="upload-form glass-card">
      <!-- 頂部返回與關閉按鈕區 -->
      <div class="top-nav-bar">
        <button class="btn-back" @click="goBack">
          ← 返回上一頁
        </button>
        <button
          class="close-btn-custom"
          type="button"
          title="返回首頁"
          @click="router.push({ name: 'home' })"
        >
          ✕
        </button>
      </div>

      <h2>上傳影片</h2>

      <p v-if="errorMessage || uploadStore.errorMessage" class="error-message">
        {{ errorMessage || uploadStore.errorMessage }}
      </p>

      <!-- 拖曳上傳框 -->
      <div 
        class="drag-drop-zone" 
        :class="{ dragover: isDragOver }"
        @dragover.prevent="onDragOver"
        @dragleave.prevent="onDragLeave"
        @drop.prevent="onDrop"
        @click="triggerFileInput"
      >
        <input 
          id="file" 
          ref="fileInput"
          type="file" 
          accept="video/*" 
          multiple 
          @change="onFileChange" 
          class="hidden-file-input"
        />
        <div class="drag-drop-content">
          <svg viewBox="0 0 24 24" width="40" height="40" fill="currentColor" class="upload-icon">
            <path d="M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z"/>
          </svg>
          <p>拖曳影片檔案至此處，或點擊選擇檔案即刻上傳</p>
          <span class="file-hint">支援 MP4, WebM, MKV, AVI, MOV 等影片格式</span>
        </div>
      </div>

      <!-- 資料夾上傳按鈕 -->
      <div class="folder-upload-actions" style="margin-bottom: 24px; text-align: center;">
        <input
          id="folderInput"
          ref="folderInput"
          type="file"
          webkitdirectory
          directory
          multiple
          style="display: none;"
          @change="onFolderChange"
        />
        <button
          class="btn secondary"
          type="button"
          style="max-width: 250px; margin: 0 auto;"
          @click="triggerFolderInput"
        >
          📂 選擇整個資料夾直接上傳
        </button>
      </div>

      <!-- 全局進度與狀態摘要 -->
      <div v-if="uploadStore.queue.length > 0" class="upload-summary-panel">
        <div class="overall-progress-row">
          <span class="overall-label">總上傳進度：</span>
          <span class="overall-percentage">{{ uploadStore.progress }}%</span>
        </div>
        <div class="progress-bar">
          <div class="fill" :style="{ width: uploadStore.progress + '%' }"></div>
        </div>
        <div v-if="!uploadStore.isUploading" class="summary-status-alert">
          <div v-if="uploadStore.errorMessage" class="alert-box error-alert">
            ⚠️ 部分影片上傳失敗，詳細錯誤請見下方列表。
          </div>
          <div v-else class="alert-box success-alert">
            🎉 所有影片已完成上傳與發送背景合併！
          </div>
        </div>
      </div>

      <!-- 逐個影片佇列與個別進度 -->
      <div v-if="uploadStore.queue.length > 0" class="upload-queue-container">
        <div class="queue-header-row">
          <h3>上傳清單 (共 {{ uploadStore.filesCount }} 部)</h3>
          
          <div class="queue-bulk-actions">
            <button 
              v-if="selectedIds.length > 0" 
              class="btn danger-sm" 
              type="button" 
              @click="handleCancelSelected"
            >
              取消所選 ({{ selectedIds.length }})
            </button>
            <button 
              v-if="!uploadStore.isUploading && uploadStore.queue.some(item => ['success', 'error', 'cancelled'].includes(item.status))"
              class="btn secondary-sm"
              type="button"
              @click="handleClearFinished"
            >
              清除已完成
            </button>
          </div>
        </div>

        <div class="queue-list">
          <!-- 全選控制列 -->
          <div v-if="cancellableQueue.length > 0" class="queue-select-all-row">
            <label class="checkbox-label">
              <input type="checkbox" v-model="isAllSelected" />
              <span>全選進行中項目</span>
            </label>
          </div>

          <div v-for="item in uploadStore.queue" :key="item.id" class="queue-item">
            <div class="queue-item-left">
              <!-- 核取方塊 (僅限進行中任務) -->
              <input 
                v-if="['waiting', 'uploading'].includes(item.status)"
                type="checkbox" 
                :value="item.id" 
                v-model="selectedIds"
                class="item-checkbox" 
              />
              <span v-else class="checkbox-placeholder"></span>
              
              <div class="queue-item-info">
                <div class="queue-item-header">
                  <span class="queue-item-name" :title="item.fileName">{{ item.fileName }}</span>
                  <span class="queue-item-status" :class="item.status">
                    <template v-if="item.status === 'waiting'">等待中</template>
                    <template v-else-if="item.status === 'uploading'">上傳中 {{ item.progress }}%</template>
                    <template v-else-if="item.status === 'success'">✓ 完成</template>
                    <template v-else-if="item.status === 'error'">✗ 失敗</template>
                    <template v-else-if="item.status === 'cancelled'">已取消</template>
                  </span>
                </div>
                <div class="queue-item-bar-container">
                  <div class="queue-item-bar">
                    <div class="fill" :class="item.status" :style="{ width: item.progress + '%' }"></div>
                  </div>
                </div>
                <p v-if="item.status === 'error' && item.errorMessage" class="queue-item-error">
                  {{ item.errorMessage }}
                </p>
              </div>
            </div>

            <!-- 右側操作按鈕：取消/移除 -->
            <div class="queue-item-actions">
              <button 
                v-if="['waiting', 'uploading'].includes(item.status)"
                class="btn-icon-cancel" 
                type="button" 
                title="取消上傳"
                @click="handleCancelSingle(item.id)"
              >
                ✕
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 頁面底部返回按鈕 -->
      <div class="form-actions-bottom">
        <button class="btn secondary" type="button" @click="goBack">
          返回上一頁
        </button>
        <button class="btn primary" type="button" @click="router.push({ name: 'home' })">
          回到首頁
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.upload-form {
  position: relative;
  padding-top: 56px;
}

.top-nav-bar {
  position: absolute;
  top: 16px;
  left: 20px;
  right: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.btn-back {
  background: none;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
  padding: 6px 12px;
  border-radius: var(--border-radius-md);
  font-size: 13px;
  cursor: pointer;
  transition: var(--transition-smooth);
}
.btn-back:hover {
  border-color: var(--accent-blue);
  color: var(--accent-blue);
  background: rgba(62, 166, 255, 0.05);
}

.close-btn-custom {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: rgba(128, 128, 128, 0.15);
  color: inherit;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition-smooth);
}
.close-btn-custom:hover {
  background: rgba(128, 128, 128, 0.35);
}

/* 拖曳上傳框樣式 */
.drag-drop-zone {
  border: 2px dashed var(--border-color);
  border-radius: var(--border-radius-lg);
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  transition: var(--transition-smooth);
  background: rgba(255, 255, 255, 0.01);
  margin-bottom: 20px;
}
.drag-drop-zone:hover, .drag-drop-zone.dragover {
  border-color: var(--accent-blue);
  background: rgba(62, 166, 255, 0.04);
}
.hidden-file-input {
  display: none;
}
.drag-drop-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--text-primary);
}
.upload-icon {
  color: var(--text-secondary);
  transition: var(--transition-smooth);
}
.drag-drop-zone:hover .upload-icon, .drag-drop-zone.dragover .upload-icon {
  color: var(--accent-blue);
  transform: translateY(-4px);
}
.file-hint {
  font-size: 12px;
  color: var(--text-muted);
}

/* 全局進度與狀態摘要 */
.upload-summary-panel {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 24px;
}
.overall-progress-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
}
.overall-percentage {
  color: var(--accent-blue);
}
.progress-bar {
  width: 100%;
  height: 8px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 4px;
  overflow: hidden;
}
.progress-bar .fill {
  height: 100%;
  background: var(--accent-blue);
  border-radius: 4px;
  transition: width 0.3s ease;
}
.summary-status-alert {
  margin-top: 12px;
}
.alert-box {
  padding: 10px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  text-align: center;
}
.alert-box.success-alert {
  background: rgba(46, 125, 50, 0.15);
  border: 1px solid #2e7d32;
  color: #81c784;
}
.alert-box.error-alert {
  background: rgba(198, 40, 40, 0.15);
  border: 1px solid #c62828;
  color: #e57373;
}

/* 佇列管理區域 */
.upload-queue-container {
  margin-bottom: 24px;
}
.queue-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.queue-header-row h3 {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0;
}
.queue-bulk-actions {
  display: flex;
  gap: 8px;
}

.btn-icon-cancel {
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  color: var(--text-muted);
  font-size: 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: var(--transition-smooth);
}
.btn-icon-cancel:hover {
  color: #e57373;
  background: rgba(229, 115, 115, 0.1);
}

.btn.danger-sm {
  background: #c62828;
  color: #fff;
  border: none;
  padding: 4px 8px;
  font-size: 11px;
  border-radius: 4px;
  cursor: pointer;
}
.btn.danger-sm:hover {
  background: #b71c1c;
}

.btn.secondary-sm {
  background: rgba(255, 255, 255, 0.05);
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
  padding: 4px 8px;
  font-size: 11px;
  border-radius: 4px;
  cursor: pointer;
}
.btn.secondary-sm:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--text-primary);
}

.queue-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-height: 350px;
  overflow-y: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.15);
}

.queue-select-all-row {
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.checkbox-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
}

.queue-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}
.queue-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.queue-item-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  overflow: hidden;
}

.item-checkbox {
  cursor: pointer;
  width: 14px;
  height: 14px;
}
.checkbox-placeholder {
  width: 14px;
  height: 14px;
}

.queue-item-info {
  flex: 1;
  overflow: hidden;
}

.queue-item-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  gap: 12px;
  margin-bottom: 4px;
}
.queue-item-name {
  color: var(--text-primary);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.queue-item-status {
  font-weight: 600;
  font-size: 11px;
}
.queue-item-status.waiting {
  color: var(--text-muted);
}
.queue-item-status.uploading {
  color: var(--accent-blue);
}
.queue-item-status.success {
  color: #81c784;
}
.queue-item-status.error {
  color: #e57373;
}
.queue-item-status.cancelled {
  color: var(--text-muted);
}

.queue-item-bar-container {
  width: 100%;
}
.queue-item-bar {
  width: 100%;
  height: 4px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 2px;
  overflow: hidden;
}
.queue-item-bar .fill {
  height: 100%;
  background: var(--accent-blue);
  border-radius: 2px;
  transition: width 0.3s ease;
}
.queue-item-bar .fill.success {
  background: #2e7d32;
}
.queue-item-bar .fill.error {
  background: #c62828;
}
.queue-item-bar .fill.cancelled {
  background: rgba(255, 255, 255, 0.1);
}

.queue-item-error {
  font-size: 11px;
  color: #e57373;
  margin: 2px 0 0 0;
  word-break: break-all;
}

.form-actions-bottom {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}
.form-actions-bottom .btn {
  flex: 1;
}
</style>
