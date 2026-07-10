<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useUploadStore } from "../store/upload";

const router = useRouter();
const uploadStore = useUploadStore();

const title = ref("");
const description = ref("");
const files = ref([]);
const errorMessage = ref("");
const fileInput = ref(null);
const isDragOver = ref(false);

const ALLOWED_EXTENSIONS = [
  ".mp4", ".webm", ".mov", ".mkv", ".avi",
  ".flv", ".3gp", ".wmv", ".m4v", ".mpg", ".mpeg"
];

function triggerFileInput() {
  if (uploadStore.isUploading) return;
  fileInput.value.click();
}

function onDragOver() {
  if (uploadStore.isUploading) return;
  isDragOver.value = true;
}

function onDragLeave() {
  isDragOver.value = false;
}

function onDrop(event) {
  if (uploadStore.isUploading) return;
  isDragOver.value = false;
  const droppedFiles = Array.from(event.dataTransfer.files);
  processSelectedFiles(droppedFiles);
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

  const invalid = selectedList.find((file) => {
    const name = file.name.toLowerCase();
    const isImageExtension = [".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif", ".bmp"].some((ext) => name.endsWith(ext));
    const isImageMime = file.type && file.type.startsWith("image/");
    return isImageExtension || isImageMime || !ALLOWED_EXTENSIONS.some((ext) => name.endsWith(ext));
  });

  if (invalid) {
    const name = invalid.name.toLowerCase();
    const isImage = [".jpg", ".jpeg", ".png", ".gif", ".webp", ".heic", ".heif", ".bmp"].some((ext) => name.endsWith(ext)) || (invalid.type && invalid.type.startsWith("image/"));
    if (isImage) {
      errorMessage.value = `檔案「${invalid.name}」為圖片格式。本平台僅供上傳影片檔案，請重新選擇！`;
    } else {
      errorMessage.value = `檔案「${invalid.name}」格式不支援，僅支援：${ALLOWED_EXTENSIONS.join(", ")}`;
    }
    return;
  }

  files.value = selectedList;
  if (files.value.length === 1) {
    // 只有一個檔案時，預填標題為檔案主檔名
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

  if (files.value.length === 1) {
    // 單檔背景上傳
    uploadStore.startUploadSingle({
      title: title.value,
      description: description.value,
      file: files.value[0]
    });
  } else {
    // 多檔背景批量上傳
    uploadStore.startUploadBatch(files.value);
  }

  // 按下上傳後立刻秒回首頁，讓它在背景慢慢傳
  router.push({ name: "home" });
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

        <!-- 只有在單檔上傳時顯示標題與描述輸入欄位 -->
        <template v-if="files.length === 1">
          <div class="field">
            <label for="title">標題（必填，最多 200 字）</label>
            <input id="title" v-model="title" type="text" maxlength="200" :disabled="uploadStore.isUploading" required />
          </div>
          <div class="field">
            <label for="description">描述（選填，最多 2000 字）</label>
            <textarea id="description" v-model="description" maxlength="2000" :disabled="uploadStore.isUploading"></textarea>
          </div>
        </template>

        <!-- 批量上傳提示 -->
        <div v-else-if="files.length > 1" class="batch-upload-notice">
          已選擇 <strong>{{ files.length }}</strong> 個影片，系統將會自動以檔名做為標題進行批量上傳。
          <ul class="file-list">
            <li v-for="(file, idx) in files" :key="idx">{{ file.name }}</li>
          </ul>
        </div>

        <div v-if="uploadStore.isUploading" class="progress-container">
          <div class="progress-bar">
            <div class="fill" :style="{ width: uploadStore.progress + '%' }"></div>
          </div>
          <span class="progress-text">上傳中... {{ uploadStore.progress }}%</span>
        </div>

        <div class="form-actions">
          <button class="btn" type="button" :disabled="uploadStore.isUploading" @click="onCancel">取消</button>
          <button class="btn primary" type="submit" :disabled="uploadStore.isUploading || files.length === 0">
            {{ uploadStore.isUploading ? `上傳中... ${uploadStore.progress}%` : "開始上傳" }}
          </button>
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
  margin-top: 16px;
}
.form-actions .btn {
  flex: 1;
}
</style>
