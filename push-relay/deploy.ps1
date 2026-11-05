# One-time setup + deploy of the RideAlong push relay (Cloudflare Workers, free tier).
# Run from this folder in PowerShell:
#   .\deploy.ps1                      (finds *firebase-adminsdk*.json in your Downloads folder)
#   .\deploy.ps1 -KeyFile "C:\full\path\to\key.json"
param([string]$KeyFile)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not $KeyFile) {
    $found = Get-ChildItem "$env:USERPROFILE\Downloads" -Filter "*firebase-adminsdk*.json" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $found) {
        throw "No *firebase-adminsdk*.json found in Downloads. Generate it in Firebase console > Project settings > Service accounts, or pass -KeyFile."
    }
    $KeyFile = $found.FullName
}
$KeyFile = $KeyFile.Trim('"', "'", ' ')
if ($KeyFile -match '[<>]') { throw "Replace the <placeholder> with the real file name, e.g. -KeyFile `"$env:USERPROFILE\Downloads\my-key.json`"" }
if (-not (Test-Path -LiteralPath $KeyFile)) { throw "Service-account key not found: $KeyFile" }
Write-Host "Using service-account key: $KeyFile"

npm install --no-audit --no-fund
npx wrangler login                      # opens the browser once (free Cloudflare account)
Get-Content -Raw -LiteralPath $KeyFile | npx wrangler secret put SERVICE_ACCOUNT_JSON   # stored encrypted in Cloudflare
npx wrangler deploy
Write-Host ""
Write-Host "Done. Send the https://ridealong-push.<your-subdomain>.workers.dev URL printed above to Claude,"
Write-Host "or put it into local.properties as PUSH_RELAY_URL=... and rebuild the app."
Write-Host "You can now delete the key file: $KeyFile"
