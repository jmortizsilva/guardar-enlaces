# Arranca la app de Windows apuntando al backend compartido en el servidor
# (api.jmortiz.es), sin depender de tener nada corriendo en este ordenador.
#
# Uso: clic derecho -> "Ejecutar con PowerShell", o desde una terminal:
#   .\usar-servidor.ps1

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

$env:GUARDAR_ENLACES_API = "https://api.jmortiz.es"

Write-Host ""
Write-Host "Abriendo la app contra el backend compartido (api.jmortiz.es)." -ForegroundColor Green
Write-Host "Inicia sesion con Google o Apple; la cuenta se crea sola la primera vez." -ForegroundColor Green
Write-Host ""

& ".\venv\Scripts\python.exe" -m guardar_enlaces
