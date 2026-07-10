<script setup>
import { onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import http from "../api/http";

const props = defineProps({
  video: { type: Object, required: true },
  showCheckbox: { type: Boolean, default: false },
  selected: { type: Boolean, default: false },
  folders: { type: Array, default: () => [] },
});

const emit = defineEmits(["select", "refresh"]);
const router = useRouter();

const coverBlobUrl = ref("");
const showDropdown = ref(false);

function releaseCoverBlobUrl() {
  if (coverBlobUrl.value) {
    URL.revokeObjectURL(coverBlobUrl.value);
    coverBlobUrl.value = "";
  }
}

async function loadCover() {
  releaseCoverBlobUrl();
  if (!props.video.coverUrl) {
    return;
  }
  try {
    const cleanUrl = props.video.coverUrl.startsWith("/api/")
      ? props.video.coverUrl.substring(4)
      : props.video.coverUrl;
    const response = await http.get(cleanUrl, { responseType: "blob" });
    coverBlobUrl.value = URL.createObjectURL(response.data);
  } catch (e) {
    coverBlobUrl.value = "";
  }
}

async function moveToFolder(folderId) {
  showDropdown.value = false;
  try {
    const url = "videos/" + props.video.id + "/folder" + (folderId ? "?folderId=" + folderId : "");
    await http.put(url);
    emit("refresh");
  } catch (err) {
    alert("移動影片失敗：" + (err.response?.data?.message || err.message));
  }
}

function closeDropdown(e) {
  if (showDropdown.value && !e.target.closest(".folder-move-container")) {
    showDropdown.value = false;
  }
}

onMounted(() => {
  window.addEventListener("click", closeDropdown);
  loadCover();
});

onUnmounted(() => {
  window.removeEventListener("click", closeDropdown);
  releaseCoverBlobUrl();
});

watch(() => props.video.coverUrl, loadCover);

function goToDetail() {
  if (props.showCheckbox) {
    emit("select", props.video.id, !props.selected);
  } else {
    router.push({ name: "video-detail", params: { id: props.video.id } });
  }
}

function formatDate(value) {
  if (!value) return "";
  return new Date(value).toLocaleDateString("zh-TW", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function formatSize(bytes) {
  if (!bytes) return "0 Bytes";
  const k = 1024;
  const sizes = ["Bytes", "KB", "MB", "GB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
}
</script>

<template>
  <div class="video-card" @click="goToDetail">
    <div class="thumb-container">
      <img v-if="coverBlobUrl" :src="coverBlobUrl" class="thumb-img" alt="cover" />
      <div v-else class="thumb"></div>
      <div v-if="video.status === 'UPLOADING'" class="status-overlay processing">
        <div class="spinner-small"></div> 轉碼中...
      </div>
      <div v-else-if="video.status === 'FAILED'" class="status-overlay failed">
        ❌ 轉碼失敗
      </div>
      <input
        v-if="showCheckbox"
        type="checkbox"
        class="card-checkbox"
        :checked="selected"
        @click.stop
        @change="emit('select', video.id, $event.target.checked)"
      />
    </div>
    <div class="info">
      <div class="title-row" @click.stop>
        <div class="title" :title="video.title" @click="goToDetail">{{ video.title }}</div>
        <div v-if="folders && folders.length" class="folder-move-container">
          <button class="folder-move-btn" @click="showDropdown = !showDropdown" title="移動到資料夾">
            📁
          </button>
          <div v-if="showDropdown" class="folder-dropdown">
            <div class="dropdown-item header">移動至資料夾：</div>
            <div class="dropdown-item" @click="moveToFolder(null)">根目錄 (無)</div>
            <div
              v-for="folder in folders"
              :key="folder.id"
              class="dropdown-item"
              :class="{ active: video.folderId === folder.id }"
              @click="moveToFolder(folder.id)"
            >
              {{ folder.name }}
            </div>
          </div>
        </div>
      </div>
      <div class="meta-uploader">{{ video.uploaderUsername }}</div>
      <div class="meta-stats">
        <span>{{ video.viewCount }} 次觀看</span>
        <span class="dot">•</span>
        <span>{{ formatSize(video.fileSize) }}</span>
        <span class="dot">•</span>
        <span>{{ formatDate(video.createdAt) }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 8px;
  display: block;
}
.status-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.65);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 500;
  gap: 8px;
  border-radius: 8px;
  backdrop-filter: blur(2px);
}
.status-overlay.failed {
  background: rgba(220, 38, 38, 0.75);
}
.spinner-small {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.2);
  border-left-color: #ffffff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

.title-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}

.title {
  flex: 1;
  cursor: pointer;
}

.folder-move-container {
  position: relative;
}

.folder-move-btn {
  background: transparent;
  border: none;
  font-size: 14px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  color: var(--text-secondary);
  transition: background-color 0.2s;
}

.folder-move-btn:hover {
  background-color: var(--border-color);
}

.folder-dropdown {
  position: absolute;
  right: 0;
  top: 100%;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  min-width: 140px;
  box-shadow: var(--shadow-premium);
  z-index: 10;
  margin-top: 4px;
  overflow: hidden;
}

.dropdown-item {
  padding: 8px 12px;
  font-size: 12px;
  color: var(--text-primary);
  cursor: pointer;
  transition: background-color 0.2s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dropdown-item:hover {
  background-color: var(--border-color);
}

.dropdown-item.header {
  color: var(--text-muted);
  cursor: default;
  background-color: rgba(255, 255, 255, 0.02);
  border-bottom: 1px solid var(--border-color);
  font-weight: 500;
}

.dropdown-item.active {
  color: var(--accent-blue);
  font-weight: 600;
}
</style>
