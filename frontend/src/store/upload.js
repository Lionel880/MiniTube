import { defineStore } from "pinia";
import { uploadVideo, uploadVideosBatch } from "../api/video";

export const useUploadStore = defineStore("upload", {
  state: () => ({
    isUploading: false,
    progress: 0,
    filesCount: 0,
    errorMessage: "",
  }),

  actions: {
    async startUploadSingle({ title, description, folderId, file }, onDone) {
      this.isUploading = true;
      this.progress = 0;
      this.filesCount = 1;
      this.errorMessage = "";

      try {
        const video = await uploadVideo(
          { title, description, folderId, file },
          (event) => {
            if (event.total) {
              this.progress = Math.round((event.loaded / event.total) * 100);
            }
          }
        );
        this.isUploading = false;
        this.progress = 100;
        if (onDone) onDone(video);
      } catch (err) {
        this.errorMessage = err.message;
        this.isUploading = false;
      }
    },

    async startUploadBatch(files, folderId, onDone) {
      this.isUploading = true;
      this.progress = 0;
      this.filesCount = files.length;
      this.errorMessage = "";

      try {
        await uploadVideosBatch(files, folderId, (event) => {
          if (event.total) {
            this.progress = Math.round((event.loaded / event.total) * 100);
          }
        });
        this.isUploading = false;
        this.progress = 100;
        if (onDone) onDone();
      } catch (err) {
        this.errorMessage = err.message;
        this.isUploading = false;
      }
    },
  },
});
