<script setup>
import { onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { searchVideos, batchDeleteVideos } from "../api/video";
import { useAuthStore } from "../store/auth";
import VideoCard from "../components/VideoCard.vue";

const route = useRoute();
const authStore = useAuthStore();
const videos = ref([]);
const page = ref(0);
const totalPages = ref(0);
const loading = ref(false);
const errorMessage = ref("");

// 排序狀態
const sortBy = ref("createdAt");
const sortDir = ref("desc");

// 批量刪除選擇狀態
const selectedIds = ref([]);

async function load(p = 0) {
  const q = route.query.q || "";
  if (!q || !authStore.isLoggedIn) {
    videos.value = [];
    return;
  }
  loading.value = true;
  errorMessage.value = "";
  try {
    const data = await searchVideos({ q, page: p, size: 12, sortBy: sortBy.value, sortDir: sortDir.value });
    videos.value = data.videos;
    page.value = data.page;
    totalPages.value = data.totalPages;
    selectedIds.value = [];
  } catch (err) {
    errorMessage.value = err.message;
  } finally {
    loading.value = false;
  }
}

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
    load(page.value);
  } catch (err) {
    alert("刪除失敗：" + err.message);
  }
}

function prevPage() {
  if (page.value > 0) load(page.value - 1);
}

function nextPage() {
  if (page.value + 1 < totalPages.value) load(page.value + 1);
}

// 當排序或查詢字串變更時自動重載
watch([sortBy, sortDir, () => route.query.q], () => {
  load(0);
});

onMounted(() => load(0));
</script>

<template>
  <div class="page">
    <!-- 搜尋範圍限定在自己的影片，未登入時提示先登入 -->
    <div v-if="!authStore.isLoggedIn" class="login-prompt">
      <p>搜尋只會找你自己上傳的影片，請先登入。</p>
      <RouterLink class="btn primary" :to="{ name: 'login' }">前往登入</RouterLink>
    </div>

    <template v-else>
    <h2>搜尋結果：「{{ route.query.q }}」</h2>

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
      </div>

      <div class="batch-controls">
        <button
          v-if="selectedIds.length > 0"
          class="btn danger bulk-delete-btn"
          type="button"
          @click="deleteSelected"
        >
          刪除選取影片 ({{ selectedIds.length }})
        </button>
      </div>
    </div>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

    <div v-if="loading" class="empty-state">搜尋中...</div>

    <template v-else>
      <div v-if="videos.length === 0" class="empty-state">找不到符合的影片</div>

      <div v-else class="video-grid">
        <VideoCard
          v-for="video in videos"
          :key="video.id"
          :video="video"
          :show-checkbox="authStore.isLoggedIn && video.uploaderUsername === authStore.username"
          :selected="selectedIds.includes(video.id)"
          @select="handleSelect"
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
