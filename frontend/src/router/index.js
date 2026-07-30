import { createRouter, createWebHashHistory } from "vue-router";
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
    path: "/profile",
    redirect: "/profile/account",
    meta: { requiresAuth: true },
  },
  {
    path: "/profile/account",
    name: "profile-account",
    component: () => import("../views/ProfileView.vue"),
    props: { activeTab: "account" },
    meta: { requiresAuth: true },
  },
  {
    path: "/profile/theme",
    name: "profile-theme",
    component: () => import("../views/ProfileView.vue"),
    props: { activeTab: "theme" },
    meta: { requiresAuth: true },
  },
  {
    path: "/profile/app",
    name: "profile-app",
    component: () => import("../views/ProfileView.vue"),
    props: { activeTab: "app" },
    meta: { requiresAuth: true },
  },
  {
    path: "/profile/legacy",
    name: "profile",
    redirect: "/profile/account",
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
  history: createWebHashHistory(import.meta.env.BASE_URL),
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
