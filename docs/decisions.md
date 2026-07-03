# 技術決策紀錄（Decision Log）

一條一段，新的加在最上面。每條寫清楚：決策、為什麼、影響範圍。

## 2026-07-04 — 全面安全稽核與修復（含 Vercel 部署修正）

- **決策**：執行完整安全稽核，修復 2 項 CRITICAL、3 項 HIGH、6 項 MEDIUM 安全問題。
- **CRITICAL 修復**：(1) `application.yaml` 中資料庫密碼 `!Qw1069` 與 JWT 密鑰的硬編碼預設值全部移除，改為空預設值（未設環境變數則啟動失敗）。(2) 上傳目錄的 NAS 內網路徑 `\\192.168.0.202\Disk1share\MiniTubeVideos` 改為環境變數 `APP_UPLOAD_DIR`，預設 `uploads/videos`。
- **HIGH 修復**：(1) CORS 的 `https://*.vercel.app` 萬用字元收斂為 `https://mini-tube*-lionel880s-projects.vercel.app`，防止任意 Vercel 站點發起跨域請求。(2) `.gitignore` 加入 `.env*` 與 `application-local/prod.yaml` 排除規則。
- **MEDIUM 修復**：(1) `show-sql` 預設改為 `false`。(2) `JwtAuthenticationFilter` 原本靜默吞掉例外，改為 `log.warn` 記錄失敗。(3) `GlobalExceptionHandler` 的 `printStackTrace()` 改為 SLF4J `log.error`。(4) NavBar 的 API URL 設定加入 `http(s)://` 格式驗證。(5) `http.js` 的 `getBaseURL()` 自動為自訂 URL 補上 `/api` 後綴。
- **Vercel 部署修正**：發現使用者的 Vercel 站點顯示的是 Vercel 自身的登入頁面（而非 MiniTube Vue 前端），原因是 Vercel 專案的 Root Directory 未設為 `frontend/`。新增 `frontend/vercel.json` 做 SPA rewrite fallback。
- **影響**：本機啟動後端前需設定 `SPRING_DATASOURCE_PASSWORD` 和 `JWT_SECRET` 兩個環境變數。舊的硬編碼密碼仍在 git 歷史中，SQL Server 的 `sa` 密碼應盡快更換。Vercel 使用者需到 Dashboard 將 Root Directory 改為 `frontend`。

## 2026-07-03 — 遷移至 PostgreSQL、CORS 開放 GitHub Pages（平行 session 決策，本條為事後補記）

- **決策**（commit `7496677`，由另一個 Claude session 做成）：`mssql-jdbc` → `org.postgresql`；連線參數改 `SPRING_DATASOURCE_*`／`DB_*` 巢狀環境變數（dev 預設 `postgres`/`postgres`，非機密）；CORS 加 `https://lionel880.github.io`。
- **為什麼（推斷）**：配合 GitHub Pages 前端部署，後端預計部署到雲端，Postgres 的雲端託管成本與可得性優於 SQL Server。
- **影響**：本機 5432 目前**沒有** PostgreSQL 在跑 → 後端在本機暫時無法啟動，需安裝並啟動 Postgres（建 `minitube` 資料庫），或設 `SPRING_DATASOURCE_URL` 指向遠端。既有 SQL Server 內的資料不會自動搬移。測試 H2 相容模式已同步改為 `MODE=PostgreSQL`。此變更取代同日稍早的「帳密改 `${DB_USERNAME:sa}`」方案；工作樹已無任何真實密碼。**遺留**：舊 SQL Server 密碼仍在 git 歷史（`178326a` 起），push 前擇一——更換該密碼（建議，一行 T-SQL）或改寫歷史。

## 2026-07-03 — 資料庫帳密改環境變數注入（推翻同日稍早的「維持明文提交」決策）

- **決策**：`application.yaml` 的 datasource 帳密改為 `${DB_USERNAME:sa}` / `${DB_PASSWORD:}`（密碼無預設值，未設定時啟動即失敗）。舊密碼 `Qw106969` 已存在於 git 歷史中，**必須更換**（`ALTER LOGIN`），更換後歷史裡的舊值即失效，不需要改寫 git 歷史。
- **為什麼**：平行 session 已加入 gh-pages 部署設定（commit `11c95b8`），repo 即將接上 GitHub remote；「無 remote 所以明文可接受」的前提消失了。
- **影響**：所有人（含兩個平行 Claude session）下次啟動後端前，必須先設好使用者層級的 `DB_PASSWORD` 環境變數並重開終端機／App。部署到任何真實環境時，`JWT_SECRET` 也必須設環境變數（yaml 內的預設值會隨 repo 公開）。

## 2026-07-03 — 不新增 MCP server；effortLevel 設 xhigh；不建 CLI alias

- **決策**：使用者層級 MCP server 維持 0 個。專案 `settings.json` 設 `"effortLevel": "xhigh"`（官方文件已查證：settings 接受 low–xhigh，Opus 4.8 支援 xhigh、預設 high，不支援的模型自動降檔）。CLI alias（如 `copus`）不建立。
- **為什麼**：MCP 沒有具體需求就不加——repo 無 GitHub remote、DB 檢查頻率低。xhigh 換更深推理，符合「長任務穩定」目標，成本可接受。alias 的前提是 `claude` CLI 在 PATH 上，但這台機器只裝了桌面 App，alias 沒有施力點。
- **觸發條件（何時翻案）**：repo 推上 GitHub → 加 GitHub MCP；頻繁需要直接查 SQL Server 資料 → 評估 DB MCP；安裝 CLI（`npm i -g @anthropic-ai/claude-code`）→ 建 `copus` function。
- **影響**：xhigh 對本專案所有 session 生效（含 Fable，token 花費上升）；單次 session 可用 `/effort` 覆蓋。

## 2026-07-03 — 測試資料庫改用 H2 in-memory（MSSQLServer 相容模式）

- **決策**：`src/test/resources/application.yaml` 覆蓋 datasource 指向 H2，`ddl-auto: create-drop`；pom 加 `com.h2database:h2`（test scope）。
- **為什麼**：兩個測試類都是 `@SpringBootTest`，原本依賴本機 SQL Server，乾淨環境（CI、agent 自主驗證）跑不起來。Testcontainers 需要 Docker，H2 零依賴；實體只用標準 JPA + JPQL、無 native query，相容風險低。
- **影響**：`mvnw test` 不再需要 SQL Server。代價是測試蓋不到真 SQL Server 行為（collation、identity 邊界等），上線前仍需對真庫手動驗證。

## 2026-07-03 — `server.port` 改為 `${PORT:8080}`

- **決策**：`application.yaml` 的 `server.port` 支援 `PORT` 環境變數覆蓋，預設仍 8080。
- **為什麼**：preview 工具的 autoPort 需要動態指派 port，而 Spring Boot 不會自動讀 `PORT`。
- **影響**：`.claude/launch.json` backend 設 `autoPort: true`。已知限制：Vite proxy 寫死 8080，後端被分配到其他 port 時前端 dev proxy 打不到。

## 2026-07-03 — 行為紀律放 CLAUDE.md，不用 output-style

- **決策**：12 條工作紀律直接寫進專案 CLAUDE.md（「工作紀律」章節）。
- **為什麼**：單一專案，CLAUDE.md 每個 session 自動載入；`~/.claude/output-styles/` 是跨專案機制，現在用是多一層維護成本。
- **影響**：規則不會跟到其他專案；若之後多專案共用，再抽出去。

## 2026-07-03 — dev 資料庫帳密維持明文提交

- **決策**：`application.yaml` 的本機 SQL Server 帳密照舊提交；新增的 secret 一律 `${ENV:default}` 注入（如 `jwt.secret`）。
- **為什麼**：本機開發便利，repo 目前無 remote；例外條款已寫入 `.agents/AGENTS.md`。
- **影響**：推上任何 remote 之前必須先清理（改環境變數並更換該密碼）。

## （回溯）`GET /api/videos/**` 維持公開

- **決策**：影片詳情與串流不要求 JWT；列表與搜尋（`GET /api/videos`、`/api/videos/search`）要求登入。
- **為什麼**：`<video>` 標籤無法帶 Authorization header，強制驗證會讓播放器直接壞掉；「拿到連結就能看單支影片」是接受的取捨。
- **影響**：串流 URL 等同分享連結，無存取控制；之後若要私人影片，需改用簽名 URL 或 cookie-based auth。
