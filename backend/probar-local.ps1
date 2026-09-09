# Arranca el backend en local, listo para probar la app de Windows: fija las
# variables de entorno necesarias y deja el servidor corriendo. La cuenta de
# pruebas se crea sola la primera vez que se entra con ese correo.
#
# Uso: clic derecho -> "Ejecutar con PowerShell", o desde una terminal:
#   .\probar-local.ps1

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$env:PORT = "8090"
$env:DB_PATH = ".\datos\prueba.sqlite"
$env:ENLACES_TOKEN_SECRET = "secreto-de-pruebas-local"
$env:GOOGLE_CLIENT_ID = "x"
$env:GOOGLE_CLIENT_SECRET = "x"
$env:APPLE_CLIENT_ID = "x"
$env:APPLE_TEAM_ID = "x"
$env:APPLE_KEY_ID = "x"
$env:APPLE_PRIVATE_KEY = "x"
$env:PERMITIR_LOGIN_DEV = "true"

$correoPrueba = "prueba@local.test"

if (-not (Test-Path "node_modules")) {
    Write-Host "Instalando dependencias (solo la primera vez)..." -ForegroundColor Cyan
    npm.cmd install
}

Write-Host ""
Write-Host "Backend arrancando en http://localhost:8090" -ForegroundColor Green
Write-Host "Correo para iniciar sesion en la app: $correoPrueba" -ForegroundColor Green
Write-Host "Deja esta ventana abierta. Ctrl+C para pararlo." -ForegroundColor Yellow
Write-Host ""

npm.cmd run dev
