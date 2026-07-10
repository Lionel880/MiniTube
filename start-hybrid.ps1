# MiniTube Hybrid Mode Startup Utility (Pure ASCII)
Clear-Host
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "    MiniTube Hybrid Mode Service Startup     " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

$ProjectDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($ProjectDir)) {
    $ProjectDir = $PWD.Path
}

# 1. Build frontend Vue SPA
Write-Host "[1/5] Building Vue frontend..." -ForegroundColor Yellow
Set-Location -Path "$ProjectDir\frontend"
npm run build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Frontend build failed."
    exit 1
}

# 2. Sync to Spring Boot static resources
Write-Host "[2/5] Syncing frontend static resources..." -ForegroundColor Yellow
$StaticDir = "$ProjectDir\src\main\resources\static"
if (Test-Path $StaticDir) {
    Remove-Item -Recurse -Force "$StaticDir\*"
} else {
    New-Item -ItemType Directory -Path $StaticDir -Force
}
Copy-Item -Path "$ProjectDir\frontend\dist\*" -Destination $StaticDir -Recurse -Force

# 3. Start Spring Boot locally on host
Write-Host "[3/5] Starting Spring Boot locally (opens in new window)..." -ForegroundColor Yellow
Set-Location -Path $ProjectDir
Start-Process powershell -WorkingDirectory $ProjectDir -ArgumentList "-NoExit", "-Command", "`$env:SPRING_PROFILES_ACTIVE='local'; ./mvnw spring-boot:run"

# 4. Start Cloudflare Tunnel in Docker
Write-Host "[4/5] Starting Cloudflare Tunnel in Docker..." -ForegroundColor Yellow
# Stop existing tunnel container if running
docker stop minitube-tunnel 2>$null
docker rm minitube-tunnel 2>$null

# Run tunnel container
docker run -d --name minitube-tunnel --add-host=host.docker.internal:host-gateway cloudflare/cloudflared:latest tunnel --url http://host.docker.internal:8080

# 5. Retrieve Public URL
Write-Host "[5/5] Waiting for Cloudflare Tunnel to establish connection..." -ForegroundColor Yellow
$tunnelUrl = $null
$timeout = 30
$elapsed = 0
while ($elapsed -lt $timeout) {
    Start-Sleep -Seconds 1
    $log = docker logs minitube-tunnel 2>&1
    if ($log -match "(https://[a-zA-Z0-9-]+\.trycloudflare\.com)") {
        $tunnelUrl = $Matches[1]
        break
    }
    $elapsed++
}

if ($tunnelUrl) {
    Write-Host ""
    Write-Host "=============================================" -ForegroundColor Green
    Write-Host "  MiniTube Hybrid Started successfully!" -ForegroundColor Green
    Write-Host "  --------------------------------------------" -ForegroundColor Green
    Write-Host "  Public Tunnel URL:" -ForegroundColor Green
    Write-Host "  $tunnelUrl" -ForegroundColor Cyan
    Write-Host "=============================================" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Warning "Could not retrieve Cloudflare Tunnel URL within 30 seconds."
    Write-Warning "Please run 'docker logs minitube-tunnel' to inspect logs manually."
}

Write-Host ""
Write-Host "Press any key to close this window..." -ForegroundColor DarkGray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
