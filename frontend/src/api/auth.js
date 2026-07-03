import http from "./http";

export function register({ username, email, password }) {
  return http.post("/auth/register", { username, email, password }).then((res) => res.data);
}

export function login({ username, password }) {
  return http.post("/auth/login", { username, password }).then((res) => res.data);
}

export function fetchMe() {
  return http.get("/auth/me").then((res) => res.data);
}

export function logout() {
  return http.post("/auth/logout").then((res) => res.data);
}
