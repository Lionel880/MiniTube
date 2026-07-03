<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { uploadVideo, uploadVideosBatch } from "../api/video";

const router = useRouter();

const title = ref("");
const description = ref("");
const files = ref([]);
const errorMessage = ref("");
const uploading = ref(false);
const progress = ref(0);

const ALLOWED_EXTENSIONS = [".mp4", ".webm", ".mov", ".mkv", ".avi"];

function onFileChange(event) {
  const selectedList = Array.from(event.target.files);
  errorMessage.value = "";
  files.value = [];

  if (selectedList.length === 0) {
    return;
  }

  const invalid = selectedList.find((file) => {
    const name = file.name.toLowerCase();
    return !ALLOWED_EXTENSIONS.some((ext) => name.endsWith(ext));
  });

  if (invalid) {
    errorMessage.value = `檔案「${invalid.name}」格式不支援，僅支援：${ALLOWED_EXTENSIONS.join(", ")}`;
    event.target.value = "";
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
  // 上傳中不允許直接關閉，避免誤觸中斷
  if (uploading.value) return;
  router.push({ name: "home" });
}

async function onSubmit() {
  errorMessage.value = "";

  if (files.value.length === 0) {
    errorMessage.value = "請選擇要上傳的影片檔案";
    return;
  }

  uploading.value = true;
  progress.value = 0;

  try {
    if (files.value.length === 1) {
      // 單檔上傳，傳送自訂標題與描述
      const video = await uploadVideo(
        { title: title.value, description: description.value, file: files.value[0] },
        (event) => {
          if (event.total) {
            progress.value = Math.round((event.loaded / event.total) * 100);
          }
        }
      );
      router.push({ name: "video-detail", params: { id: video.id } });
    } else {
      // 多檔批量上傳，由後端以檔名當作標題
      await uploadVideosBatch(files.value, (event) => {
        if (event.total) {
          progress.value = Math.round((event.loaded / event.total) * 100);
        }
      });
      router.push({ name: "home" });
    }
  } catch (err) {
    errorMessage.value = err.message;
  } finally {
    uploading.value = false;
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
        :disabled="uploading"
        @click="onCancel"
      >
        ✕
      </button>
      <h2>上傳影片</h2>

      <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

      <form @submit.prevent="onSubmit">
        <div class="field">
          <label for="file">選擇影片檔案（支援複選批量上傳，單檔上限 3GB）</label>
          <input id="file" type="file" accept="video/*" multiple @change="onFileChange" :disabled="uploading" required />
        </div>

        <!-- 只有在單檔上傳時顯示標題與描述輸入欄位 -->
        <template v-if="files.length === 1">
          <div class="field">
            <label for="title">標題（必填，最多 200 字）</label>
            <input id="title" v-model="title" type="text" maxlength="200" :disabled="uploading" required />
          </div>
          <div class="field">
            <label for="description">描述（選填，最多 2000 字）</label>
            <textarea id="description" v-model="description" maxlength="2000" :disabled="uploading"></textarea>
          </div>
        </template>

        <!-- 批量上傳提示 -->
        <div v-else-if="files.length > 1" class="batch-upload-notice">
          已選擇 <strong>{{ files.length }}</strong> 個影片，系統將會自動以檔名做為標題進行批量上傳。
          <ul class="file-list">
            <li v-for="(file, idx) in files" :key="idx">{{ file.name }}</li>
          </ul>
        </div>

        <div v-if="uploading" class="progress-container">
          <div class="progress-bar">
            <div class="fill" :style="{ width: progress + '%' }"></div>
          </div>
          <span class="progress-text">上傳中... {{ progress }}%</span>
        </div>

        <div class="form-actions">
          <button class="btn" type="button" :disabled="uploading" @click="onCancel">取消</button>
          <button class="btn primary" type="submit" :disabled="uploading || files.length === 0">
            {{ uploading ? `上傳中... ${progress}%` : "開始上傳" }}
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
.form-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}
.form-actions .btn {
  flex: 1;
}
</style>
