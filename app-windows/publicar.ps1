# Publica una version nueva de la aplicacion de Windows en GitHub Releases,
# que es de donde se la bajan los que ya la tienen instalada.
#
# Antes de lanzarlo:
#   1. Subir el numero en guardar_enlaces/version.py
#   2. Commitear y hacer push (la publicacion apunta al commit actual)
#
# Uso:  .\publicar.ps1 -Novedades "Lo que ha cambiado, en una frase"

param(
    [Parameter(Mandatory = $true)]
    [string]$Novedades
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$python = ".\venv\Scripts\python.exe"
$version = & $python -c "from guardar_enlaces.version import VERSION; print(VERSION)"
$etiqueta = "windows-v$version"

Write-Host "Publicando la version $version" -ForegroundColor Cyan

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Host "Falta la herramienta gh (GitHub CLI). Instalala y ejecuta 'gh auth login'." -ForegroundColor Red
    exit 1
}

# Que no se publique una version que ya existe: la URL "latest/download" sirve
# el ultimo publicado, y dos releases con la misma etiqueta dejan a los
# clientes sin saber cual les toca.
if (gh release view $etiqueta 2>$null) {
    Write-Host "Ya existe una publicacion con la etiqueta $etiqueta." -ForegroundColor Red
    Write-Host "Sube el numero en guardar_enlaces/version.py antes de publicar." -ForegroundColor Red
    exit 1
}

if ((git status --porcelain) -ne $null) {
    Write-Host "Hay cambios sin commitear. Commitea y haz push antes de publicar." -ForegroundColor Red
    exit 1
}

& .\empaquetar.ps1
if ($LASTEXITCODE -ne 0) { exit 1 }

$zip = "dist\GuardarEnlaces-$version.zip"
if (Test-Path $zip) { Remove-Item $zip }
Write-Host "Comprimiendo..." -ForegroundColor Cyan
Compress-Archive -Path "dist\GuardarEnlaces\*" -DestinationPath $zip

$sha = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLower()
$url = "https://github.com/jmortizsilva/guardar-enlaces/releases/download/$etiqueta/GuardarEnlaces-$version.zip"

# El manifiesto que lee la aplicacion instalada. Va como fichero suelto de la
# publicacion para poder pedirlo por la URL fija "latest/download/ultima.json",
# que no gasta el limite de peticiones de la API de GitHub ni necesita token.
$manifiesto = [ordered]@{
    version   = $version
    url       = $url
    sha256    = $sha
    novedades = $Novedades
}
$manifiesto | ConvertTo-Json | Set-Content "dist\ultima.json" -Encoding UTF8

Write-Host "Creando la publicacion en GitHub..." -ForegroundColor Cyan
gh release create $etiqueta $zip "dist\ultima.json" `
    --title "Guardar enlaces para Windows $version" `
    --notes $Novedades

Write-Host ""
Write-Host "Publicado. Las instalaciones existentes lo veran al abrirse." -ForegroundColor Green
Write-Host "SHA-256: $sha"
