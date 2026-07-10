# MiniTube Local Hybrid Startup Script (Pure ASCII)
Clear-Host
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "    MiniTube Local Hybrid Service Startup    " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

$ProjectDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($ProjectDir)) {
    $ProjectDir = $PWD.Path
}

# 1. Load .env file variables into current process environment
$EnvFile = "$ProjectDir\.env"
if (Test-Path $EnvFile) {
    Write-Host "Loading environment variables from .env..." -ForegroundColor Gray
    Get-Content $EnvFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line -match "^([^=]+)=(.*)$") {
            $key = $Matches[1].Trim()
            $value = $Matches[2].Trim()
            [Environment]::SetEnvironmentVariable($key, $value, "Process")
        }
    }
}

# 2. Build frontend Vue SPA
Write-Host "[1/4] Building Vue frontend..." -ForegroundColor Yellow
Set-Location -Path "$ProjectDir\frontend"
npm run build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Frontend build failed."
    exit 1
}

# 3. Sync to Spring Boot static resources
Write-Host "[2/4] Syncing frontend static resources..." -ForegroundColor Yellow
$StaticDir = "$ProjectDir\src\main\resources\static"
if (Test-Path $StaticDir) {
    Remove-Item -Recurse -Force "$StaticDir\*"
} else {
    New-Item -ItemType Directory -Path $StaticDir -Force
}
Copy-Item -Path "$ProjectDir\frontend\dist\*" -Destination $StaticDir -Recurse -Force

# 4. Start Spring Boot locally on host
Write-Host "[3/4] Starting Spring Boot locally (opens in new window)..." -ForegroundColor Yellow
Set-Location -Path $ProjectDir
Start-Process powershell -WorkingDirectory $ProjectDir -ArgumentList "-NoExit", "-Command", "`$env:SPRING_PROFILES_ACTIVE='local'; ./mvnw spring-boot:run"

# 5. Start Cloudflare Tunnel locally
Write-Host "[4/4] Starting Cloudflare Tunnel locally (opens in new window)..." -ForegroundColor Yellow
# If cloudflared.exe is missing, download it
if (-not (Test-Path "$ProjectDir\cloudflared.exe")) {
    Write-Host "Downloading cloudflared.exe..." -ForegroundColor Gray
    curl.exe -L -o "$ProjectDir\cloudflared.exe" "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
}

# Check if Zero Trust Tunnel Token is configured in .env
$TunnelToken = [Environment]::GetEnvironmentVariable("CLOUDFLARE_TUNNEL_TOKEN")
if (-not [string]::IsNullOrEmpty($TunnelToken)) {
    Write-Host "Detected permanent Cloudflare Tunnel Token. Starting Zero Trust Tunnel..." -ForegroundColor Green
    Start-Process powershell -WorkingDirectory $ProjectDir -ArgumentList "-NoExit", "-Command", "./cloudflared.exe tunnel --no-autoupdate run --token $TunnelToken"
} else {
    Write-Host "No Tunnel Token found in .env. Starting fallback Quick Tunnel (random URL)..." -ForegroundColor Gray
    Start-Process powershell -WorkingDirectory $ProjectDir -ArgumentList "-NoExit", "-Command", "./cloudflared.exe tunnel --url http://localhost:8080"
}

Write-Host ""
Write-Host "=============================================" -ForegroundColor Green
Write-Host " Startup commands sent successfully!" -ForegroundColor Green
Write-Host " 1. Wait for 'Started MiniYoutubeApplication' in the Spring Boot window." -ForegroundColor White
Write-Host " 2. Find your trycloudflare.com URL in the cloudflared window!" -ForegroundColor White
Write-Host "=============================================" -ForegroundColor Green
Write-Host ""
Write-Host "Press any key to close this window..." -ForegroundColor DarkGray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
