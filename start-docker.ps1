# MiniTube Docker Compose Startup Script
Clear-Host
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "     MiniTube Docker Compose Start Utility   " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

$ProjectDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($ProjectDir)) {
    $ProjectDir = $PWD.Path
}

# Stop old containers
Write-Host "[1/3] Cleaning up old Docker containers..." -ForegroundColor Yellow
docker compose down --remove-orphans

# Build and start
Write-Host "[2/3] Building and starting Docker containers (Background)..." -ForegroundColor Yellow
docker compose up --build -d

# Get Cloudflare Tunnel URL
Write-Host "[3/3] Waiting for Cloudflare Tunnel to establish connection..." -ForegroundColor Yellow
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
    Write-Host ""
    Write-Host "=============================================" -ForegroundColor Green
    Write-Host " 🎉 MiniTube started successfully!" -ForegroundColor Green
    Write-Host " ---------------------------------------------" -ForegroundColor Gray
    Write-Host " 🔗 Public Tunnel URL:" -ForegroundColor White
    Write-Host "    $tunnelUrl" -ForegroundColor Cyan
    Write-Host " =============================================" -ForegroundColor Green
} else {
    Write-Warning "Could not retrieve Cloudflare Tunnel URL within 30 seconds."
    Write-Warning "Please run 'docker logs minitube-tunnel' to inspect logs manually."
}

Write-Host ""
Write-Host "Press any key to close this window..." -ForegroundColor DarkGray
if ($Host.Name -eq "ConsoleHost") {
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
} else {
    Start-Sleep -Seconds 3
}
