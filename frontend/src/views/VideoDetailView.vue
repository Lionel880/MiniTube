<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { getVideo, updateVideo } from "../api/video";
import { useAuthStore } from "../store/auth";

const props = defineProps({
  id: { type: [String, Number], required: true },
});

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

const video = ref(null);
const loading = ref(false);
const errorMessage = ref("");

// 編輯狀態（只有影片擁有者能編輯）
const isOwner = computed(
  () => authStore.isLoggedIn && video.value && video.value.uploaderUsername === authStore.username
);
const editing = ref(false);
const editTitle = ref("");
const editDescription = ref("");
const saving = ref(false);
const editError = ref("");

function startEdit() {
  editTitle.value = video.value.title;
  editDescription.value = video.value.description || "";
  editError.value = "";
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
  editError.value = "";
}

function goBack() {
  // 有瀏覽紀錄就返回上一頁，否則回首頁
  if (window.history.state && window.history.state.back) {
    router.back();
  } else {
    router.push({ name: "home" });
  }
}

async function saveEdit() {
  if (!editTitle.value.trim()) {
    editError.value = "影片標題不能為空";
    return;
  }
  saving.value = true;
  editError.value = "";
  try {
    video.value = await updateVideo(props.id, {
      title: editTitle.value.trim(),
      description: editDescription.value.trim(),
    });
    editing.value = false;
  } catch (err) {
    editError.value = err.message;
  } finally {
    saving.value = false;
  }
}

async function load() {
  loading.value = true;
  errorMessage.value = "";
  try {
    video.value = await getVideo(props.id);
  } catch (err) {
    errorMessage.value = err.message;
  } finally {
    loading.value = false;
  }
}

function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleString("zh-TW", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function formatSize(bytes) {
  if (!bytes) return "0 Bytes";
  const k = 1024;
  const sizes = ["Bytes", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}

watch(() => props.id, load);
onMounted(load);
</script>

<template>
  <div class="page">
    <button class="btn back-btn" type="button" @click="goBack">← 返回</button>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

    <div v-if="loading" class="empty-state">載入中...</div>

    <div v-else-if="video" class="video-detail-layout glass-card">
      <div class="video-player-container">
        <video class="video-player" :src="video.videoUrl" controls preload="metadata"></video>
      </div>

      <div class="video-info-section">
        <!-- 檢視模式 -->
        <template v-if="!editing">
          <div class="title-row">
            <h1 class="video-title">{{ video.title }}</h1>
            <button v-if="isOwner" class="btn" type="button" @click="startEdit">✏️ 編輯</button>
          </div>

          <div class="video-meta-row">
            <div class="uploader-info">
              <span class="uploader-name">{{ video.uploaderUsername }}</span>
            </div>
            <div class="stats-info">
              <span>{{ video.viewCount }} 次觀看</span>
              <span class="dot">•</span>
              <span>檔案大小：{{ formatSize(video.fileSize) }}</span>
              <span class="dot">•</span>
              <span>上傳時間：{{ formatDate(video.createdAt) }}</span>
            </div>
          </div>

          <div class="video-description-box">
            <h3>影片描述</h3>
            <p class="video-description">{{ video.description || "（沒有描述）" }}</p>
          </div>
        </template>

        <!-- 編輯模式 -->
        <div v-else class="edit-form">
          <p v-if="editError" class="error-message">{{ editError }}</p>

          <div class="field">
            <label for="edit-title">標題（必填，最多 200 字）</label>
            <input id="edit-title" v-model="editTitle" type="text" maxlength="200" :disabled="saving" />
          </div>
          <div class="field">
            <label for="edit-description">描述（選填，最多 2000 字）</label>
            <textarea id="edit-description" v-model="editDescription" maxlength="2000" rows="5" :disabled="saving"></textarea>
          </div>

          <div class="edit-actions">
            <button class="btn" type="button" :disabled="saving" @click="cancelEdit">取消</button>
            <button class="btn primary" type="button" :disabled="saving" @click="saveEdit">
              {{ saving ? "儲存中..." : "儲存變更" }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.back-btn {
  margin-bottom: 20px;
}
.title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.edit-form .field {
  margin-bottom: 12px;
}
.edit-form label {
  display: block;
  margin-bottom: 4px;
}
.edit-form input,
.edit-form textarea {
  width: 100%;
  box-sizing: border-box;
}
.edit-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
