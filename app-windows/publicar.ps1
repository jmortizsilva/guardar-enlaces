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

# "Continue" y no "Stop" a proposito, y no es dejadez.
#
# En Windows PowerShell 5.1, lo que una herramienta de linea de comandos escribe
# en la salida de ERRORES se convierte en un registro de error y, con "Stop", en
# una excepcion que corta el script. Y gh escribe ahi "release not found"
# justo cuando la publicacion NO existe, que es el caso normal antes de
# publicarla; tambien escribe ahi el progreso de subida, que no es ningun fallo.
#
# Asi que aqui se hace lo unico fiable: mirar el CODIGO DE SALIDA despues de cada
# llamada. Los cmdlets que si deben cortar llevan su -ErrorAction Stop.
#
# Ojo al comprobarlo: PowerShell 7 NO se comporta asi, no corta. Una prueba en 7
# no dice nada sobre lo que pasara en el 5.1 que trae Windows.
$ErrorActionPreference = "Continue"
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
gh release view $etiqueta 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Ya existe una publicacion con la etiqueta $etiqueta." -ForegroundColor Red
    Write-Host "Sube el numero en guardar_enlaces/version.py antes de publicar." -ForegroundColor Red
    exit 1
}

$pendientes = git status --porcelain
if ($pendientes) {
    Write-Host "Hay cambios sin commitear. Commitea y haz push antes de publicar." -ForegroundColor Red
    Write-Host $pendientes
    exit 1
}

& .\empaquetar.ps1
if ($LASTEXITCODE -ne 0) { exit 1 }

$zip = "dist\GuardarEnlaces-$version.zip"
if (Test-Path $zip) { Remove-Item $zip }
Write-Host "Comprimiendo..." -ForegroundColor Cyan
Compress-Archive -Path "dist\GuardarEnlaces\*" -DestinationPath $zip -ErrorAction Stop

$sha = (Get-FileHash $zip -Algorithm SHA256 -ErrorAction Stop).Hash.ToLower()
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
# Sin BOM. Set-Content -Encoding UTF8 le pone uno delante en Windows PowerShell
# 5.1, y un BOM al principio de un JSON hace fallar a json.loads: el manifiesto
# se descartaria y la actualizacion no se ofreceria nunca, sin decir por que.
$json = $manifiesto | ConvertTo-Json
$rutaManifiesto = Join-Path $PSScriptRoot "dist\ultima.json"
[System.IO.File]::WriteAllText($rutaManifiesto, $json, (New-Object System.Text.UTF8Encoding $false))

Write-Host "Creando la publicacion en GitHub..." -ForegroundColor Cyan
# 2>&1 por lo mismo que arriba: gh informa del progreso de subida por la salida
# de errores, y sin esto el script aborta en mitad de una publicacion correcta.
gh release create $etiqueta $zip "dist\ultima.json" `
    --title "Guárdalo para Windows $version" `
    --notes $Novedades 2>&1 | Write-Host
if ($LASTEXITCODE -ne 0) {
    Write-Host "gh no pudo crear la publicacion." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Publicado. Las instalaciones existentes lo veran al abrirse." -ForegroundColor Green
Write-Host "SHA-256: $sha"
