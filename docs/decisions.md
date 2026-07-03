# 技術決策紀錄（Decision Log）

一條一段，新的加在最上面。每條寫清楚：決策、為什麼、影響範圍。

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
