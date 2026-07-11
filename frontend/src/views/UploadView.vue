<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { useUploadStore } from "../store/upload";

const router = useRouter();
const uploadStore = useUploadStore();

onMounted(() => {
  if (!uploadStore.isUploading) {
    uploadStore.queue = [];
    uploadStore.errorMessage = "";
    uploadStore.progress = 0;
  }
});

const title = ref("");
const files = ref([]);
const errorMessage = ref("");
const fileInput = ref(null);
const folderInput = ref(null);
const isDragOver = ref(false);

const ALLOWED_EXTENSIONS = [
  ".mp4", ".webm", ".mov", ".mkv", ".avi",
  ".flv", ".3gp", ".wmv", ".m4v", ".mpg", ".mpeg"
];

function triggerFileInput() {
  if (uploadStore.isUploading) return;
  fileInput.value.click();
}

function triggerFolderInput() {
  if (uploadStore.isUploading) return;
  folderInput.value.click();
}

function onFolderChange(event) {
  const selectedList = Array.from(event.target.files);
  processSelectedFiles(selectedList);
  event.target.value = "";
}

function onDragOver() {
  if (uploadStore.isUploading) return;
  isDragOver.value = true;
}

function onDragLeave() {
  isDragOver.value = false;
}

async function onDrop(event) {
  if (uploadStore.isUploading) return;
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
  event.target.value = ""; // 重設以利重複選取同檔案
}

function processSelectedFiles(selectedList) {
  errorMessage.value = "";
  files.value = [];

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

  files.value = validFiles;
  if (files.value.length === 1) {
    const name = files.value[0].name;
    const dotIndex = name.lastIndexOf('.');
    title.value = dotIndex >= 0 ? name.substring(0, dotIndex) : name;
  }
}

function onCancel() {
  if (uploadStore.isUploading) return;
  router.push({ name: "home" });
}

async function onSubmit() {
  errorMessage.value = "";

  if (files.value.length === 0) {
    errorMessage.value = "請選擇要上傳的影片檔案";
    return;
  }

  const activeFolderId = sessionStorage.getItem("minitube_active_folder_id");
  const folderId = activeFolderId && activeFolderId !== "null" ? Number(activeFolderId) : null;

  if (files.value.length === 1) {
    uploadStore.startUploadSingle({
      title: title.value,
      folderId: folderId,
      file: files.value[0]
    });
  } else {
    uploadStore.startUploadBatch(files.value, folderId);
  }
}
</script>

<template>
  <div class="page">
    <div class="upload-form glass-card">
      <button
        class="close-btn"
        type="button"
        title="關閉"
        :disabled="uploadStore.isUploading"
        @click="onCancel"
      >
        ✕
      </button>
      <h2>上傳影片</h2>

      <p v-if="errorMessage || uploadStore.errorMessage" class="error-message">
        {{ errorMessage || uploadStore.errorMessage }}
      </p>

      <form @submit.prevent="onSubmit">
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
            :disabled="uploadStore.isUploading" 
            class="hidden-file-input"
          />
          <div class="drag-drop-content">
            <svg viewBox="0 0 24 24" width="40" height="40" fill="currentColor" class="upload-icon">
              <path d="M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z"/>
            </svg>
            <p v-if="files.length === 0">拖曳影片檔案至此處，或點擊選擇檔案上傳</p>
            <p v-else class="selected-text">已選擇 {{ files.length }} 個影片檔案</p>
            <span class="file-hint">支援 MP4, WebM, MKV, AVI, MOV 等影片格式</span>
          </div>
        </div>

        <div v-if="uploadStore.queue.length === 0" class="folder-upload-actions" style="margin-bottom: 24px; text-align: center;">
          <input
            id="folderInput"
            ref="folderInput"
            type="file"
            webkitdirectory
            directory
            multiple
            style="display: none;"
            @change="onFolderChange"
            :disabled="uploadStore.isUploading"
          />
          <button
            class="btn secondary"
            type="button"
            style="max-width: 250px; margin: 0 auto;"
            :disabled="uploadStore.isUploading"
            @click="triggerFolderInput"
          >
            📂 選擇整個資料夾上傳
          </button>
        </div>

        <!-- 只有在單檔上傳時顯示標題輸入欄位 -->
        <template v-if="files.length === 1 && uploadStore.queue.length === 0">
          <div class="field">
            <label for="title">標題（必填，最多 200 字）</label>
            <input id="title" v-model="title" type="text" maxlength="200" :disabled="uploadStore.isUploading" required />
          </div>
        </template>

        <!-- 批量上傳提示 (尚未上傳時顯示) -->
        <div v-else-if="files.length > 1 && uploadStore.queue.length === 0" class="batch-upload-notice">
          已選擇 <strong>{{ files.length }}</strong> 個影片，系統將會自動以檔名做為標題進行批量上傳。
          <ul class="file-list">
            <li v-for="(file, idx) in files" :key="idx">{{ file.name }}</li>
          </ul>
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
              🎉 所有影片均已成功上傳！
            </div>
          </div>
        </div>

        <!-- 逐個影片佇列與個別進度 -->
        <div v-if="uploadStore.queue.length > 0" class="upload-queue-container">
          <h3>上傳清單 (共 {{ uploadStore.filesCount }} 部)</h3>
          <div class="queue-list">
            <div v-for="(item, idx) in uploadStore.queue" :key="idx" class="queue-item">
              <div class="queue-item-header">
                <span class="queue-item-name" :title="item.fileName">{{ item.fileName }}</span>
                <span class="queue-item-status" :class="item.status">
                  <template v-if="item.status === 'waiting'">等待中</template>
                  <template v-else-if="item.status === 'uploading'">上傳中 {{ item.progress }}%</template>
                  <template v-else-if="item.status === 'success'">✓ 成功</template>
                  <template v-else-if="item.status === 'error'">✗ 失敗</template>
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
        </div>

        <div class="form-actions">
          <template v-if="uploadStore.queue.length > 0 && !uploadStore.isUploading">
            <button class="btn primary" type="button" @click="router.push({ name: 'home' })">完成並返回首頁</button>
          </template>
          <template v-else>
            <button class="btn" type="button" :disabled="uploadStore.isUploading" @click="onCancel">取消</button>
            <button class="btn primary" type="submit" :disabled="uploadStore.isUploading || files.length === 0">
              {{ uploadStore.isUploading ? `上傳中... ${uploadStore.progress}%` : "開始上傳" }}
            </button>
          </template>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.upload-form {
  position: relative;
}
.close-btn {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  background: rgba(128, 128, 128, 0.15);
  color: inherit;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}
.close-btn:hover:not(:disabled) {
  background: rgba(128, 128, 128, 0.35);
}
.close-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 拖曳上傳框樣式 */
.drag-drop-zone {
  border: 2px dashed var(--border-color);
  border-radius: var(--border-radius-lg);
  padding: 48px 24px;
  text-align: center;
  cursor: pointer;
  transition: var(--transition-smooth);
  background: rgba(255, 255, 255, 0.01);
  margin-bottom: 24px;
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
  gap: 14px;
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
.selected-text {
  color: var(--accent-blue);
  font-weight: 500;
}

.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 24px;
}
.form-actions .btn {
  flex: 1;
}

/* 批次上傳提示 */
.batch-upload-notice {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border-color);
  padding: 16px;
  border-radius: var(--border-radius-md);
  margin-bottom: 24px;
  font-size: 13px;
  line-height: 1.6;
}
.file-list {
  margin: 12px 0 0;
  padding-left: 20px;
  max-height: 150px;
  overflow-y: auto;
  color: var(--text-secondary);
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
  margin-top: 16px;
}
.alert-box {
  padding: 12px;
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

/* 逐個影片佇列與個別進度 */
.upload-queue-container {
  margin-bottom: 24px;
}
.upload-queue-container h3 {
  font-size: 14px;
  margin-bottom: 12px;
  color: var(--text-secondary);
}
.queue-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  background: rgba(0, 0, 0, 0.15);
}
.queue-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}
.queue-item:last-child {
  padding-bottom: 0;
  border-bottom: none;
}
.queue-item-header {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  gap: 12px;
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
.queue-item-error {
  font-size: 11px;
  color: #e57373;
  margin: 2px 0 0 0;
  word-break: break-all;
}
</style>
