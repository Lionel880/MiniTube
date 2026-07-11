import { defineStore } from "pinia";
import { uploadVideo } from "../api/video";

export const useUploadStore = defineStore("upload", {
  state: () => ({
    isUploading: false,
    progress: 0,
    filesCount: 0,
    errorMessage: "",
    queue: [], // 每個項目： { fileName, progress, status, errorMessage }
  }),

  actions: {
    async startUploadSingle({ title, description, folderId, file }, onDone) {
      this.isUploading = true;
      this.progress = 0;
      this.filesCount = 1;
      this.errorMessage = "";
      this.queue = [
        {
          fileName: file.name,
          progress: 0,
          status: "uploading",
          errorMessage: "",
        },
      ];

      try {
        const video = await uploadVideo(
          { title, description: "", folderId, file },
          (event) => {
            if (event.total) {
              const fileProgress = Math.round((event.loaded / event.total) * 100);
              this.progress = fileProgress;
              if (this.queue[0]) {
                this.queue[0].progress = fileProgress;
              }
            }
          }
        );
        this.isUploading = false;
        this.progress = 100;
        if (this.queue[0]) {
          this.queue[0].progress = 100;
          this.queue[0].status = "success";
        }
        if (onDone) onDone(video);
      } catch (err) {
        this.errorMessage = err.message;
        if (this.queue[0]) {
          this.queue[0].status = "error";
          this.queue[0].errorMessage = err.message;
        }
        this.isUploading = false;
      }
    },

    async startUploadBatch(files, folderId, onDone) {
      this.isUploading = true;
      this.progress = 0;
      this.filesCount = files.length;
      this.errorMessage = "";
      this.queue = Array.from(files).map((file) => ({
        fileName: file.name,
        progress: 0,
        status: "waiting",
        errorMessage: "",
      }));

      let successCount = 0;
      let failCount = 0;
      const errors = [];

      for (let i = 0; i < files.length; i++) {
        const file = files[i];
        const dotIndex = file.name.lastIndexOf(".");
        const fileTitle = dotIndex >= 0 ? file.name.substring(0, dotIndex) : file.name;

        if (this.queue[i]) {
          this.queue[i].status = "uploading";
        }

        try {
          await uploadVideo(
            {
              title: fileTitle,
              description: "",
              folderId: folderId,
              file: file,
            },
            (event) => {
              if (event.total) {
                const currentFileProgress = Math.round((event.loaded / event.total) * 100);
                if (this.queue[i]) {
                  this.queue[i].progress = currentFileProgress;
                }
                const totalLoadedFraction = (i + event.loaded / event.total) / files.length;
                this.progress = Math.min(Math.round(totalLoadedFraction * 100), 99);
              }
            }
          );
          successCount++;
          if (this.queue[i]) {
            this.queue[i].progress = 100;
            this.queue[i].status = "success";
          }
        } catch (err) {
          failCount++;
          if (this.queue[i]) {
            this.queue[i].status = "error";
            this.queue[i].errorMessage = err.message;
          }
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
