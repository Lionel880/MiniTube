import http from "./http";

export function listVideos({ folderId = "", page = 0, size = 12, sortBy = "createdAt", sortDir = "desc" } = {}) {
  return http.get("/videos", { params: { folderId, page, size, sortBy, sortDir } }).then((res) => res.data);
}

export function searchVideos({ q, folderId = "", page = 0, size = 12, sortBy = "createdAt", sortDir = "desc" }) {
  return http.get("/videos/search", { params: { q, folderId, page, size, sortBy, sortDir } }).then((res) => res.data);
}

export function getVideo(id) {
  return http.get(`/videos/${id}`).then((res) => res.data);
}

export function uploadVideo({ title, description, folderId, file }, onUploadProgress) {
  const formData = new FormData();
  formData.append("title", title);
  formData.append("description", description || "");
  if (folderId) {
    formData.append("folderId", folderId);
  }
  formData.append("file", file);

  return http
    .post("/videos/upload", formData, {
      headers: { "Content-Type": "multipart/form-data" },
      onUploadProgress,
      timeout: 0, // 上傳大檔案不設限
    })
    .then((res) => res.data);
}

export function uploadVideosBatch(files, folderId, onUploadProgress) {
  const formData = new FormData();
  for (let i = 0; i < files.length; i++) {
    formData.append("files", files[i]);
  }
  if (folderId) {
    formData.append("folderId", folderId);
  }

  return http
    .post("/videos/upload/batch", formData, {
      headers: { "Content-Type": "multipart/form-data" },
      onUploadProgress,
      timeout: 0, // 批量上傳大檔案不設限
    })
    .then((res) => res.data);
}

export function updateVideo(id, { title, description }) {
  return http.put(`/videos/${id}`, { title, description }).then((res) => res.data);
}

export function batchDeleteVideos(ids) {
  return http.delete("/videos/batch", { data: ids }).then((res) => res.data);
}

export function deleteAllVideos() {
  return http.delete("/videos/all").then((res) => res.data);
}
