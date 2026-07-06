# MiniTube Docker Compose 一鍵啟動與 Cloudflare 公網穿透腳本
Clear-Host
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "     MiniTube Docker Compose 服務啟動工具    " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

$ProjectDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($ProjectDir)) {
    $ProjectDir = $PWD.Path
}

# 停止舊容器
Write-Host "[1/3] 正在清理舊有的 Docker 容器..." -ForegroundColor Yellow
docker compose down --remove-orphans

# 編譯並啟動
Write-Host "[2/3] 正在建置並啟動 Docker 容器 (背景執行)..." -ForegroundColor Yellow
docker compose up --build -d

# 獲取 Cloudflare 穿透網址
Write-Host "[3/3] 正在等待 Cloudflare Tunnel 建立連線並取得網址..." -ForegroundColor Yellow
$maxAttempts = 30
$attempt = 1
$tunnelUrl = ""

while ($attempt -le $maxAttempts) {
    Start-Sleep -Seconds 1
    $logs = docker logs minitube-tunnel 2>&1
    foreach ($line in $logs) {
        if ($line -match "https://[a-zA-Z0-9\-]+\.trycloudflare\.com") {
            $tunnelUrl = $Matches[0]
            break
        }
    }
    if ($tunnelUrl) {
        break
    }
    $attempt++
}

if ($tunnelUrl) {
    Write-Host "`n=============================================" -ForegroundColor Green
    Write-Host " 🎉 MiniTube 已成功啟動並完成公網穿透！" -ForegroundColor Green
    Write-Host " ---------------------------------------------" -ForegroundColor Gray
    Write-Host " 🔗 手機/電腦外網播放專屬網址：" -ForegroundColor White
    Write-Host "    $tunnelUrl" -ForegroundColor Cyan
    Write-Host " ---------------------------------------------" -ForegroundColor Gray
    Write-Host " 💡 提示：使用此網址進入，前端與後端為【完全同源】，" -ForegroundColor White
    Write-Host "    手機 iOS Safari 將不會阻擋任何 Cookie，能完美播放影片！" -ForegroundColor White
    Write-Host "=============================================" -ForegroundColor Green
} else {
    Write-Warning "無法在 30 秒內取得 Cloudflare 穿透網址，請執行 'docker logs minitube-tunnel' 手動檢查日誌。"
}

Write-Host ""
Write-Host "按任意鍵關閉此視窗..." -ForegroundColor DarkGray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
