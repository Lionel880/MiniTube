import { defineStore } from "pinia";
import { uploadVideoChunk } from "../api/video";

export const useUploadStore = defineStore("upload", {
  state: () => ({
    isUploading: false,
    progress: 0,
    filesCount: 0,
    errorMessage: "",
    queue: [], // { id, fileName, progress, status, errorMessage, file, title, description, folderId, controller, videoId }
  }),

  actions: {
    // 追加檔案至佇列
    addFilesToQueue(files, folderId) {
      const newItems = Array.from(files).map((file) => {
        const dotIndex = file.name.lastIndexOf(".");
        const title = dotIndex >= 0 ? file.name.substring(0, dotIndex) : file.name;
        return {
          id: Date.now() + "_" + Math.random().toString(36).substring(2, 9),
          fileName: file.name,
          title: title,
          description: "",
          folderId: folderId || null,
          file: file,
          progress: 0,
          status: "waiting", // "waiting", "uploading", "success", "error", "cancelled"
          errorMessage: "",
          controller: null,
          videoId: null,
        };
      });

      this.queue.push(...newItems);
      this.filesCount = this.queue.length;

      // 如果當前沒有在上傳，則啟動上傳佇列執行器
      if (!this.isUploading) {
        this.processQueue();
      }
    },

    // 佇列順序上傳執行器
    async processQueue() {
      const nextItem = this.queue.find(item => item.status === "waiting");
      if (!nextItem) {
        this.isUploading = false;
        const allFinished = this.queue.every(item => ["success", "error", "cancelled"].includes(item.status));
        if (allFinished) {
          this.progress = 100;
        }
        return;
      }

      this.isUploading = true;
      nextItem.status = "uploading";
      this.errorMessage = "";

      try {
        await this.uploadFileInChunks(nextItem);
        if (nextItem.status === "uploading") {
          nextItem.status = "success";
          nextItem.progress = 100;
        }
      } catch (err) {
        if (nextItem.status !== "cancelled") {
          nextItem.status = "error";
          nextItem.errorMessage = err.message || "上傳失敗";
          this.errorMessage = `${nextItem.fileName} 上傳失敗: ${nextItem.errorMessage}`;
        }
      }

      this.updateTotalProgress();
      this.processQueue();
    },

    // 分片上傳影片檔案的輔助函式
    async uploadFileInChunks(queueItem) {
      const CHUNK_SIZE = 5 * 1024 * 1024; // 每片 5MB
      const file = queueItem.file;
      const totalSize = file.size;
      const totalChunks = Math.ceil(totalSize / CHUNK_SIZE);
      const uploadId = Date.now() + "_" + Math.random().toString(36).substring(2, 9);
      
      let videoId = null;

      for (let chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
        if (queueItem.status === "cancelled") {
          return;
        }

        const start = chunkIndex * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, totalSize);
        const chunkBlob = file.slice(start, end);
        const chunkFile = new File([chunkBlob], file.name, { type: file.type });

        const controller = new AbortController();
        queueItem.controller = controller;

        const res = await uploadVideoChunk({
          uploadId,
          chunkIndex,
          totalChunks,
          title: queueItem.title,
          description: queueItem.description || "",
          folderId: queueItem.folderId,
          videoId,
          file: chunkFile
        }, (progressEvent) => {
          if (progressEvent.total) {
            const chunkProgress = progressEvent.loaded / progressEvent.total;
            const overallProgress = Math.round(((chunkIndex + chunkProgress) / totalChunks) * 100);
            queueItem.progress = Math.min(overallProgress, 99);
            this.updateTotalProgress();
          }
        }, controller.signal);

        if (chunkIndex === 0 && res && res.id) {
          videoId = res.id;
          queueItem.videoId = videoId;
        }
      }
    },

    // 更新整體總進度
    updateTotalProgress() {
      if (this.queue.length === 0) {
        this.progress = 0;
        return;
      }
      const totalProgressSum = this.queue.reduce((sum, item) => sum + (item.progress || 0), 0);
      this.progress = Math.round(totalProgressSum / this.queue.length);
    },

    // 取消單支影片上傳
    cancelUpload(id) {
      const item = this.queue.find(x => x.id === id);
      if (!item) return;

      if (item.status === "uploading" || item.status === "waiting") {
        item.status = "cancelled";
        item.progress = 0;
        
        if (item.controller) {
          try {
            item.controller.abort();
          } catch (e) {
            // ignore
          }
        }
        
        this.updateTotalProgress();
      }
    },

    // 批次取消選中的影片上傳
    cancelSelected(ids) {
      if (!ids || ids.length === 0) return;
      ids.forEach(id => {
        this.cancelUpload(id);
      });
    },

    // 清除已完成/已失敗/已取消的佇列項目
    clearFinishedQueue() {
      this.queue = this.queue.filter(item => ["waiting", "uploading"].includes(item.status));
      this.filesCount = this.queue.length;
      this.updateTotalProgress();
    },

    // 徹底清除整個上傳狀態
    resetStore() {
      this.queue.forEach(item => {
        if (item.controller) {
          try {
            item.controller.abort();
          } catch (e) {
            // ignore
          }
        }
      });
      this.isUploading = false;
      this.progress = 0;
      this.filesCount = 0;
      this.errorMessage = "";
      this.queue = [];
    }
  },
});
