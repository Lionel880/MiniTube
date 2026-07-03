<script setup>
import { useRouter } from "vue-router";

const props = defineProps({
  video: { type: Object, required: true },
  showCheckbox: { type: Boolean, default: false },
  selected: { type: Boolean, default: false },
});

const emit = defineEmits(["select"]);
const router = useRouter();

function goToDetail() {
  router.push({ name: "video-detail", params: { id: props.video.id } });
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
      <div class="thumb"></div>
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
