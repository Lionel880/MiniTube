import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// https://vitejs.dev/config/
export default defineConfig({
  base: "./",
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 開發模式下，前端對 /api 的請求會被轉發到後端 Spring Boot (8080)，
      // 這樣就不需要在瀏覽器端處理 CORS，影片串流的 Range 請求也能正常轉發。
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
