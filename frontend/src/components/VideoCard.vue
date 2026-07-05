<script setup>
import { onMounted, onUnmounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import http from "../api/http";

const props = defineProps({
  video: { type: Object, required: true },
  showCheckbox: { type: Boolean, default: false },
  selected: { type: Boolean, default: false },
});

const emit = defineEmits(["select"]);
const router = useRouter();

/**
 * 封面圖片改用 axios 抓成 blob 再轉成 object URL，不再直接把 <img> 的 src
 * 指向 ngrok 網域。原因：ngrok 免費版的瀏覽器警告頁只認得「HTTP 標頭」
 * ngrok-skip-browser-warning，不認網址上的 query string；<img>/<video> 標籤
 * 這種瀏覽器原生資源請求沒辦法帶自訂標頭，所以一律會先撞到 ngrok 的警告頁
 * （回傳 HTML 而不是圖片），導致封面一直讀不出來。axios 這邊已經有帶正確的
 * 標頭（見 http.js），改用 axios 抓圖片就能繞過這個限制。
 */
const coverBlobUrl = ref("");

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
    const response = await http.get(props.video.coverUrl, { responseType: "blob" });
    coverBlobUrl.value = URL.createObjectURL(response.data);
  } catch (e) {
    // 抓封面失敗就顯示預設縮圖佔位，不用把整張卡片弄壞
    coverBlobUrl.value = "";
  }
}

onMounted(loadCover);
onUnmounted(releaseCoverBlobUrl);
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
      <div class="title" :title="video.title">{{ video.title }}</div>
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
</style>
