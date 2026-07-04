# MiniTube 一鍵啟動與公網穿透腳本
Clear-Host
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "    MiniTube SQL Server 啟動與公網穿透服務   " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# 互動式安全輸入密碼
$PasswordInput = Read-Host -AsSecureString -Prompt "請輸入本地 SQL Server 'sa' 使用者的密碼"
$BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($PasswordInput)
$Password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)

Write-Host "`n[1/2] 正在啟動後端 Spring Boot 服務 (將開啟新視窗)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$env:DB_PASSWORD='$Password'; ./mvnw spring-boot:run"

Write-Host "[2/2] 正在啟動 localtunnel 公網穿透服務 (將開啟新視窗)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "npx localtunnel --port 8080"

Write-Host "`n=============================================" -ForegroundColor Green
Write-Host " 啟動指令已成功送出！" -ForegroundColor Green
Write-Host " 1. 請在新開的 localtunnel 視窗中複製您的公網 https 網址 (如 https://xxxx.loca.lt)。" -ForegroundColor White
Write-Host " 2. 在您的手機、平板或任何裝置打開：https://lionel880.github.io/MiniTube/" -ForegroundColor White
Write-Host " 3. 點選右上角『⚙️ 設定 API』並貼上該公網網址，即可開始跨裝置使用！" -ForegroundColor White
Write-Host "=============================================" -ForegroundColor Green
Write-Host "提示：若 localtunnel 因連線問題未成功產生網址，可直接在新開的視窗中按下 Ctrl+C 重試。" -ForegroundColor DarkGray
