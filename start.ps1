# MiniTube - One-click Start and Tunnel Script
Clear-Host
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "    MiniTube Service and Tunnel Startup      " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

$ProjectDir = $PSScriptRoot
if ([string]::IsNullOrEmpty($ProjectDir)) {
    $ProjectDir = $PWD.Path
}

# Load environment variables from .env file into the process environment
$EnvFile = "$ProjectDir\.env"
$NgrokToken = ""
if (Test-Path $EnvFile) {
    $EnvContent = Get-Content $EnvFile
    foreach ($Line in $EnvContent) {
        if ($Line -match "^([^=]+)=(.*)$") {
            $Key = $Matches[1].Trim()
            $Val = $Matches[2].Trim()
            [Environment]::SetEnvironmentVariable($Key, $Val, "Process")
            if ($Key -eq "NGROK_AUTHTOKEN") {
                $NgrokToken = $Val
            }
        }
    }
}

Write-Host "[1/2] Starting backend Spring Boot service (in new window)..." -ForegroundColor Yellow
Start-Process powershell -WorkingDirectory $ProjectDir -ArgumentList "-NoExit", "-Command", "`$env:SPRING_PROFILES_ACTIVE='local'; ./mvnw spring-boot:run"

Write-Host "[2/2] Starting ngrok tunnel service (in new window)..." -ForegroundColor Yellow
Start-Process powershell -WorkingDirectory $ProjectDir -ArgumentList "-NoExit", "-Command", "npx ngrok http 8080 --url=denote-reveal-compel.ngrok-free.dev --authtoken=$NgrokToken"

Write-Host "`n=============================================" -ForegroundColor Green
Write-Host " Startup commands sent successfully!" -ForegroundColor Green
Write-Host " 1. Wait for 'Started MiniYoutubeApplication' in the new Spring Boot window." -ForegroundColor White
Write-Host " 2. Open your Vercel deployment URL on your device." -ForegroundColor White
Write-Host " 3. No API configuration needed (defaults to https://denote-reveal-compel.ngrok-free.dev)." -ForegroundColor White
Write-Host "=============================================" -ForegroundColor Green
Write-Host ""
Read-Host "Press Enter to close this window..."
