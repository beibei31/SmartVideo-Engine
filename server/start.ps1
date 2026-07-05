# SmartVideo-Engine start script
Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Host "[!] .env file not found" -ForegroundColor Yellow
    exit 1
}

Write-Host "[*] Loading .env ..." -ForegroundColor Cyan
Get-Content $envFile | Where-Object { $_ -match '=' -and $_ -notmatch '^\s*#' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process")
}

$env:JAVA_HOME = "C:\Users\20799\.jdks\ms-21.0.11"
Write-Host "[*] Starting Spring Boot..." -ForegroundColor Green
mvn spring-boot:run
