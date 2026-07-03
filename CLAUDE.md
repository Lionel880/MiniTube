# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language Preference

Always write implementation plans, responses, explanations, and walkthroughs in Traditional Chinese (繁體中文).

## 工作紀律（Behavioral Rules）

適用於所有在此 repo 工作的 session，長任務尤其要遵守：

1. **結果先行**：每次回覆的第一段直接回答「發生了什麼／發現了什麼」，過程與細節放後面。
2. **最後訊息完整性**：結論、發現、交付物必須出現在回合最後一則訊息裡，不能只散落在中途的狀態更新或 thinking 中。
3. **收尾自檢**：回覆結尾若是計畫、承諾（「我會…」）或問句，立刻用 tool call 把它做完，不准就此停住。
4. **自主性邊界**：可逆且在原任務範圍內的操作直接做；破壞性操作或範圍變更才停下來詢問。
5. **評估 vs 修復**：使用者在描述問題、提問或請求診斷時，交付物是分析；等使用者裁決後才動手修。
6. **改系統狀態前先驗證**：重啟、刪除、改 config 之前，確認證據確實支持這個動作，而不是憑症狀模式猜測。
7. **驗證閉環**：改完程式碼用實際執行證明（測試、preview、HTTP 請求），不以「應該可以」作結；測試失敗照實回報，不粉飾。
8. **交付必附入口**：啟動 dev server 或完成可見功能後，必附可點的 URL（前端 http://localhost:5173、後端 http://localhost:8080，或 PORT 被覆蓋時的實際分配值）。
9. **交付前驗收清單**：逐條核對使用者本輪提出的每一個需求，逐項回報「做了／沒做／原因」；不可默默只交付一部分。
10. **環境診斷優先**：後端連不上時先定位再行動——`Test-NetConnection localhost -Port 1433`（SQL Server）、`-Port 8080`（後端）——再決定重試、重啟或明確請使用者啟動服務；不要停在錯誤訊息上等救援。
11. **可讀性優先**：完整句子、講清楚為什麼；不用箭頭鏈（A → B → 掛了）、不堆縮寫、不要求讀者回頭對照編號。
12. **平行工具呼叫**：無相依關係的工具呼叫在同一回應中一次發出，不逐一等待。
13. **決策留痕**：重大技術決策（架構、安全、資料模型、對外行為）記錄到 `docs/decisions.md`——一條一段，寫清楚「為什麼」與取捨。

## Commands

This is a Maven project; use the Maven wrapper (`./mvnw` on macOS/Linux, `mvnw.cmd` on Windows).

- Run the app: `./mvnw spring-boot:run`
- Build: `./mvnw clean package`
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=LoginApiTest`
- Run a single test method: `./mvnw test -Dtest=LoginApiTest#methodName`

Tests run against an in-memory H2 database (`src/test/resources/application.yaml` shadows the main config), so `./mvnw test` needs no local SQL Server — that is only required to run the app itself.

The backend listens on port 8080 by default; `server.port` is `${PORT:8080}` (`src/main/resources/application.yaml`), so the `PORT` environment variable overrides it (used by preview/dev-server tooling when 8080 is taken).

### Frontend

`frontend/` is a Vue 3 + Vite SPA (Pinia, Vue Router, axios):

- Dev server: `npm --prefix frontend run dev` (port 5173)
- Build: `npm --prefix frontend run build`

In dev mode Vite proxies `/api` → `http://localhost:8080` (`frontend/vite.config.js`), so the browser never makes cross-origin calls; axios uses the relative base URL `/api` (`frontend/src/api/http.js`).

## Architecture

Spring Boot 4.0.6 app, Java 21, package root `mini_youtube` (note underscore — `mini-youtube` is not a valid Java package name, see `HELP.md`).

- **Layering**: `controller` → `service` → `repository` → `entity`, with `dto/Request` and `dto/Response` used at the controller boundary (never expose entities directly).
- **Persistence**: Spring Data JPA against SQL Server (`mssql-jdbc`), `ddl-auto: update` — schema is auto-migrated from entities on startup, no separate migration tool.
- **Security**: `config/SecurityConfig.java` defines the `SecurityFilterChain`. CSRF disabled, sessions STATELESS, `JwtAuthenticationFilter` runs before username/password auth. Route rules in declaration order (first match wins — keep `/api/auth/me` above the `/api/auth/**` rule):
  1. `/api/auth/me` — authenticated
  2. `/hello`, `/api/auth/**` — permitAll
  3. `GET /api/videos`, `GET /api/videos/search` — authenticated (listings are per-user)
  4. `GET /api/videos/**` — permitAll (detail + streaming stay public so the `<video>` tag works without a JWT header)
  5. everything else — authenticated
  CORS allows only `http://localhost:5173` / `http://127.0.0.1:5173`. Unauthenticated/forbidden requests get JSON errors via `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`.
- **Auth**: `AuthController` (`/api/auth/register`, `/api/auth/login`, `/api/auth/me`) delegates to `UserService` (BCrypt password hashing). Login issues a JWT (`security/JwtUtil`, jjwt 0.12, secret from `${JWT_SECRET:...}` in `application.yaml`); `JwtAuthenticationFilter` validates the `Authorization: Bearer` header on every request.
- **Errors**: services throw `BusinessException` for user-facing failures; `exception/GlobalExceptionHandler` (`@RestControllerAdvice`) maps it — plus validation, upload-size, and DB-constraint errors — to a JSON body `{ status, message, path, timestamp }`. No raw 500s or stack traces reach the client.
- **Videos**: multipart upload (limits 3 GB/file, 12 GB/request) stored on local disk under `uploads/videos` (`app.upload-dir`, handled by `FileStorageService`); metadata lives in the `videos` table.
- Lombok (`@Data`, `@Getter/@Setter`, `@RequiredArgsConstructor`, etc.) is used throughout for DTOs, entities, and constructor injection.

## Database

`application.yaml` points at a local SQL Server instance (`localhost:1433`, database `MiniYoutube`) with credentials committed in plaintext. Treat these as dev-only placeholders, not real secrets, and update the URL if pointing at a different SQL Server instance.
