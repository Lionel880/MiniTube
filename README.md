# Mini YouTube

簡易版 YouTube：Spring Boot（後端 API）＋ Vue 3（前端）。支援註冊 / 登入、上傳影片、影片列表、播放（含拖曳進度條）、搜尋、按讚、留言。

## 專案結構

```
mini-youtube/
├─ src/main/java/mini_youtube/   Spring Boot 後端
│  ├─ controller/                REST API（Auth、Video）
│  ├─ service/                   商業邏輯（UserService、VideoService、FileStorageService）
│  ├─ repository/                Spring Data JPA
│  ├─ entity/                    User、Video、Comment、VideoLike
│  ├─ dto/Request、dto/Response  API 邊界用的 DTO（不直接暴露 entity）
│  ├─ security/                  JWT 產生/驗證、認證失敗處理
│  ├─ exception/                 全域例外處理
│  └─ config/                    Spring Security、CORS、PasswordEncoder
└─ frontend/                     Vue 3 + Vite 前端
   └─ src/{views,components,api,store,router}
```

## 事前準備

- JDK 21
- SQL Server（本機 `localhost:1433`，資料庫 `MiniYoutube`）。帳密設定於 `src/main/resources/application.yaml`，`ddl-auto: update` 會自動建表，不需要手動建立資料表。
- Node.js 18+（用來跑前端 Vite）

## 啟動後端

```bash
./mvnw spring-boot:run
```

- 預設監聽 `http://localhost:8080`
- 影片檔案會存在專案根目錄下的 `uploads/videos/`（自動建立，已加入 `.gitignore`，不會進版控）
- 上傳大小上限：1GB（`application.yaml` 的 `spring.servlet.multipart.max-file-size`）
- JWT 簽章密鑰預設寫死在 `application.yaml`（`jwt.secret`），僅供本機開發使用；正式環境請改用環境變數 `JWT_SECRET` 覆寫，不要沿用預設值。

## 啟動前端

```bash
cd frontend
npm install
npm run dev
```

- 預設監聽 `http://localhost:5173`
- `vite.config.js` 已設定 dev proxy，把 `/api/**` 轉發到後端 `http://localhost:8080`，開發時不會有 CORS 問題
- 若要打包正式版：`npm run build`，產出的 `dist/` 需要搭配一個反向代理，把 `/api/**` 轉發到後端

## API 一覽

| Method | Path | 需要登入 | 說明 |
|---|---|---|---|
| POST | `/api/auth/register` | 否 | 註冊 |
| POST | `/api/auth/login` | 否 | 登入，回傳 JWT |
| GET | `/api/auth/me` | 是 | 用 token 換回目前使用者名稱 |
| GET | `/api/videos` | 否 | 影片列表（分頁） |
| GET | `/api/videos/search?q=` | 否 | 依標題/描述搜尋 |
| GET | `/api/videos/{id}` | 否 | 影片詳情（含留言、按讚數，會累加觀看數） |
| GET | `/api/videos/{id}/stream` | 否 | 影片串流（支援 Range，可拖曳進度條） |
| POST | `/api/videos/upload` | 是 | 上傳影片（multipart：title、description、file） |
| POST | `/api/videos/{id}/like` | 是 | 按讚 / 取消讚（toggle） |
| POST | `/api/videos/{id}/comments` | 是 | 新增留言 |

前端呼叫需在 `Authorization: Bearer <token>` 帶上登入取得的 JWT（`frontend/src/api/http.js` 已自動處理）。

## 這次完善時做的安全性檢查與修正

- **密碼**：BCrypt 雜湊儲存，任何 API 回應都不會包含密碼欄位。
- **登入/註冊錯誤訊息**：統一回「帳號或密碼錯誤」，不透露帳號是否存在，避免被拿來列舉帳號。
- **例外處理**：新增 `BusinessException` 區分「可以安全顯示給使用者的錯誤」與「未預期的例外」；未預期例外（例如 NullPointerException）一律回通用訊息，不會把內部堆疊、類別或欄位名稱洩漏給前端。
- **401 / 403**：改成回傳跟其他錯誤一致的 JSON 格式，取代 Spring Security 預設的空白錯誤頁（也就是先前遇到的「不知道為什麼是空白 403」）。
- **影片上傳**：副檔名白名單 + MIME type 雙重檢查、儲存檔名一律用系統產生的 UUID（不用使用者原始檔名），並在讀取檔案時檢查路徑沒有跳脫出上傳目錄，防止路徑穿越攻擊。
- **輸入長度限制**：使用者名稱、Email、密碼、影片標題/描述、留言內容都有長度上限，且與資料庫欄位長度一致，避免未預期的資料庫例外把細節洩漏出去。
- **CORS**：只開放給開發用的 `http://localhost:5173`，沒有使用萬用字元 `*`。
- **權限控制**：只有 GET 類型的影片瀏覽端點公開，上傳、按讚、留言都需要登入才能操作。
- **前端 XSS 防護**：所有使用者輸入內容（標題、描述、留言、使用者名稱、搜尋關鍵字）都透過 Vue 的 `{{ }}` 文字插值輸出，全程沒有使用 `v-html`，瀏覽器端會自動跳脫，不會有儲存型/反射型 XSS。

## 已知限制（MVP 範圍外，之後可以再加強）

- 沒有登入嘗試次數限制 / 帳號鎖定機制，正式上線前建議加上速率限制（rate limiting）。
- 影片一律直接用 MP4/WebM 等原始格式播放，沒有做 FFmpeg 轉檔或 HLS 切片。
- 沒有「不公開影片」的概念，所有上傳的影片都可以被匿名瀏覽（比照原始需求的公開影片平台設計）。
- JWT 存放在瀏覽器 `localStorage`，是 SPA + Bearer token 常見的做法，但如果網站本身出現 XSS 漏洞，token 有被竊取的風險；如果要更嚴謹，可以改成 httpOnly cookie + CSRF 保護。
