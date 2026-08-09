# Arranca la app de Windows apuntando al backend local (ver probar-local.ps1
# en ../backend, debe estar corriendo ya en otra ventana).
#
# Uso: clic derecho -> "Ejecutar con PowerShell", o desde una terminal:
#   .\probar-local.ps1

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path "venv")) {
    Write-Host "No existe el entorno virtual (venv). Creandolo..." -ForegroundColor Cyan
    python -m venv venv
    & ".\venv\Scripts\python.exe" -m pip install --upgrade pip -q
    & ".\venv\Scripts\python.exe" -m pip install -r requirements-dev.txt
} else {
    # Comprueba que wxPython esta instalado; si falta algo, lo instala.
    & ".\venv\Scripts\python.exe" -c "import wx" 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Instalando dependencias..." -ForegroundColor Cyan
        & ".\venv\Scripts\python.exe" -m pip install -r requirements-dev.txt
    }
}

$env:GUARDAR_ENLACES_API = "http://localhost:8081"

Write-Host ""
Write-Host "Abriendo la app. Correo de pruebas: prueba@local.test" -ForegroundColor Green
Write-Host "(el backend debe estar ya corriendo: probar-local.ps1 en ../backend)" -ForegroundColor Yellow
Write-Host ""

& ".\venv\Scripts\python.exe" -m guardar_enlaces
