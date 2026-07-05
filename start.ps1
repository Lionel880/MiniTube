# MiniTube 一鍵啟動與公網穿透腳本
Clear-Host
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "    MiniTube SQL Server 啟動與公網穿透服務   " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

$ProjectDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($ProjectDir)) {
    $ProjectDir = $PWD.Path
}

Write-Host "[1/2] 正在啟動後端 Spring Boot 服務 (將開啟新視窗)..." -ForegroundColor Yellow
Start-Process powershell -WorkingDirectory $ProjectDir -ArgumentList "-NoExit", "-Command", "`$env:SPRING_PROFILES_ACTIVE='local'; ./mvnw spring-boot:run"

$EnvFile = "$ProjectDir\.env"
$NgrokToken = ""
if (Test-Path $EnvFile) {
    $EnvContent = Get-Content $EnvFile
    foreach ($Line in $EnvContent) {
        if ($Line -match "^NGROK_AUTHTOKEN=(.*)$") {
            $NgrokToken = $Matches[1].Trim()
        }
    }
}

Write-Host "[2/2] 正在啟動 ngrok 公網穿透服務 (將開啟新視窗)..." -ForegroundColor Yellow
Start-Process powershell -WorkingDirectory $ProjectDir -ArgumentList "-NoExit", "-Command", "npx ngrok http 8080 --url=denote-reveal-compel.ngrok-free.dev --authtoken=$NgrokToken"

Write-Host "`n=============================================" -ForegroundColor Green
Write-Host " 啟動指令已成功送出！" -ForegroundColor Green
Write-Host " 1. 請在新開的 Spring Boot 視窗中等待出現 'Started MiniYoutubeApplication'" -ForegroundColor White
Write-Host " 2. 請在您的手機、平板或電腦打開部署網頁" -ForegroundColor White
Write-Host " 3. 點選右上角『⚙️ 設定 API』並輸入您的固定網址：https://denote-reveal-compel.ngrok-free.dev" -ForegroundColor White
Write-Host "=============================================" -ForegroundColor Green
Write-Host ""
Write-Host "按任意鍵關閉此視窗..." -ForegroundColor DarkGray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
