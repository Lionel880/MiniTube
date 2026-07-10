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

      let successCount = 0;
      let failCount = 0;
      const errors = [];

      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const dotIndex = file.name.lastIndexOf('.');
        const fileTitle = dotIndex >= 0 ? file.name.substring(0, dotIndex) : file.name;

        try {
          await uploadVideo(
            {
              title: fileTitle,
              description: "",
              folderId: folderId,
              file: file
            },
            (event) => {
              if (event.total) {
                const currentFileProgress = event.loaded / event.total;
                const totalProgress = ((i + currentFileProgress) / files.length) * 100;
                this.progress = Math.min(Math.round(totalProgress), 99);
              }
            }
          );
          successCount++;
        } catch (err) {
          failCount++;
          errors.push(`${file.name}: ${err.message}`);
        }
      }

      this.isUploading = false;
      this.progress = 100;

      if (failCount > 0) {
        this.errorMessage = `批量上傳完成。成功: ${successCount}，失敗: ${failCount} 部影片。\n失敗原因:\n${errors.join("\n")}`;
      } else {
        if (onDone) onDone();
      }
    },
  },
});
