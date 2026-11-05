# One-time setup + deploy of the RideAlong push relay (Cloudflare Workers, free tier).
# Run from this folder in PowerShell:   .\deploy.ps1 -KeyFile "C:\path\to\service-account.json"
param([Parameter(Mandatory = $true)][string]$KeyFile)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
if (-not (Test-Path $KeyFile)) { throw "Service-account key not found: $KeyFile" }
npm install --no-audit --no-fund
npx wrangler login                      # opens the browser once (free Cloudflare account)
Get-Content -Raw $KeyFile | npx wrangler secret put SERVICE_ACCOUNT_JSON   # stored encrypted in Cloudflare
npx wrangler deploy
Write-Host ""
Write-Host "Done. Copy the https://ridealong-push.<your-subdomain>.workers.dev URL printed above"
Write-Host "into local.properties as PUSH_RELAY_URL=... and rebuild the app."
Write-Host "You can now delete the key file from your Downloads folder."