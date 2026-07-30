import { defineStore } from "pinia";

export const useVideoStateStore = defineStore("videoState", {
  state: () => {
    const savedSize = Number(localStorage.getItem("minitube_page_size"));
    return {
      page: 0,
      size: [30, 50, 100].includes(savedSize) ? savedSize : 30,
      searchKeyword: "",
      currentFolderId: null,
      currentFolderName: "",
      sortBy: localStorage.getItem("minitube_sort_by") || "createdAt",
      sortDir: localStorage.getItem("minitube_sort_dir") || "desc",
      viewMode: localStorage.getItem("minitube_view_mode") || "grid",
      scrollY: 0,
    };
  },
  actions: {
    resetState() {
      const savedSize = Number(localStorage.getItem("minitube_page_size"));
      this.page = 0;
      this.size = [30, 50, 100].includes(savedSize) ? savedSize : 30;
      this.searchKeyword = "";
      this.currentFolderId = null;
      this.currentFolderName = "";
      this.sortBy = localStorage.getItem("minitube_sort_by") || "createdAt";
      this.sortDir = localStorage.getItem("minitube_sort_dir") || "desc";
      this.viewMode = localStorage.getItem("minitube_view_mode") || "grid";
      this.scrollY = 0;
    }
  }
});
