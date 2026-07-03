# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Language Preference

Always write implementation plans, responses, explanations, and walkthroughs in Traditional Chinese (繁體中文).

## Commands

This is a Maven project; use the Maven wrapper (`./mvnw` on macOS/Linux, `mvnw.cmd` on Windows).

- Run the app: `./mvnw spring-boot:run`
- Build: `./mvnw clean package`
- Run all tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=LoginApiTest`
- Run a single test method: `./mvnw test -Dtest=LoginApiTest#methodName`

Both test classes are `@SpringBootTest` and require the local SQL Server (port 1433) to be running — there is no in-memory test database.

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
- **Videos**: multipart upload (limits 1 GB/file, 4 GB/request) stored on local disk under `uploads/videos` (`app.upload-dir`, handled by `FileStorageService`); metadata lives in the `videos` table.
- Lombok (`@Data`, `@Getter/@Setter`, `@RequiredArgsConstructor`, etc.) is used throughout for DTOs, entities, and constructor injection.

## Database

`application.yaml` points at a local SQL Server instance (`localhost:1433`, database `MiniYoutube`) with credentials committed in plaintext. Treat these as dev-only placeholders, not real secrets, and update the URL if pointing at a different SQL Server instance.
