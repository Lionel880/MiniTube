import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "../store/auth";

const routes = [
  {
    path: "/",
    name: "home",
    component: () => import("../views/HomeView.vue"),
  },
  {
    path: "/login",
    name: "login",
    component: () => import("../views/LoginView.vue"),
  },
  {
    path: "/register",
    name: "register",
    component: () => import("../views/RegisterView.vue"),
  },
  {
    path: "/upload",
    name: "upload",
    component: () => import("../views/UploadView.vue"),
    meta: { requiresAuth: true },
  },
  {
    path: "/videos/:id",
    name: "video-detail",
    component: () => import("../views/VideoDetailView.vue"),
    props: true,
  },
  {
    path: "/search",
    name: "search",
    component: () => import("../views/SearchView.vue"),
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 需要登入的頁面（例如上傳），未登入時導向登入頁
router.beforeEach((to) => {
  if (to.meta.requiresAuth) {
    const authStore = useAuthStore();
    if (!authStore.isLoggedIn) {
      return { name: "login", query: { redirect: to.fullPath } };
    }
  }
  return true;
});

export default router;
