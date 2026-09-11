# Empaqueta la aplicacion en un ejecutable de Windows.
#
# Uso:  .\empaquetar.ps1
# Sale en:  dist\GuardarEnlaces\GuardarEnlaces.exe
#
# Es una carpeta y no un .exe suelto a proposito: el de fichero unico se
# descomprime en temporales en cada arranque (mas lento) y es el formato que mas
# falsos positivos provoca en Defender. Que el antivirus lo ponga en cuarentena
# en silencio es de los peores fallos posibles para quien no ve el aviso.

# "Continue" y no "Stop" a proposito: en Windows PowerShell 5.1, lo que pip y
# PyInstaller escriben en la salida de ERRORES (avisos, y su registro normal) se
# convierte con "Stop" en una excepcion que corta el script en mitad de un
# empaquetado que iba bien. Lo unico fiable es el codigo de salida.
#
# Ojo al comprobarlo: PowerShell 7 NO se comporta asi. Una prueba en 7 no dice
# nada sobre lo que pasara en el 5.1 que trae Windows.
$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

$python = ".\venv\Scripts\python.exe"

if (-not (Test-Path $python)) {
    Write-Host "No hay entorno virtual en .\venv - crealo primero." -ForegroundColor Red
    exit 1
}

function Invoke-Paso {
    param([string]$Descripcion, [scriptblock]$Accion)

    Write-Host $Descripcion -ForegroundColor Cyan
    & $Accion 2>&1 | Write-Host
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Fallo: $Descripcion" -ForegroundColor Red
        exit 1
    }
}

Invoke-Paso "Instalando herramientas de empaquetado (si faltan)..." {
    & $python -m pip install --quiet --upgrade pyinstaller pillow
}

Invoke-Paso "Generando el icono..." {
    & $python recursos\generar_icono.py
}

Invoke-Paso "Empaquetando..." {
    & $python -m PyInstaller `
        --noconfirm `
        --clean `
        --windowed `
        --name GuardarEnlaces `
        --icon recursos\guardar-enlaces.ico `
        --collect-all keyring `
        --collect-all win32ctypes `
        lanzar.py
}

# --windowed: sin ventana de consola. Ademas de la consola negra, evita que
#   aparezca una ventana de mas en el orden de tabulacion entre aplicaciones.
# --collect-all keyring: keyring carga su motor de Windows por "entry points",
#   que es justo lo que los empaquetadores se dejan fuera.
# --collect-all win32ctypes: y ese motor, a su vez, habla con el Administrador
#   de credenciales a traves de win32ctypes. Comprobado que sin esta linea el
#   paquete se queda sin el (PyInstaller no lo ve porque el import va escondido
#   dentro del backend), la app arranca igual pero NO recuerda la sesion y pide
#   entrar con Google cada vez. Es lo primero que hay que probar del ejecutable.

$destino = Join-Path $PSScriptRoot "dist\GuardarEnlaces\GuardarEnlaces.exe"
if (-not (Test-Path $destino)) {
    Write-Host "El empaquetado termino sin errores pero no hay ejecutable." -ForegroundColor Red
    exit 1
}

$tamano = [math]::Round((Get-ChildItem "dist\GuardarEnlaces" -Recurse |
    Measure-Object -Property Length -Sum).Sum / 1MB, 1)

Write-Host ""
Write-Host "Listo: $destino ($tamano MB con todo lo de al lado)" -ForegroundColor Green
Write-Host ""
Write-Host "Que hay que comprobar a mano, porque ninguna prueba lo cubre:" -ForegroundColor Yellow
Write-Host "  1. Que arranca sin ventana de consola."
Write-Host "  2. Que RECUERDA la sesion al cerrarlo y volverlo a abrir (keyring)."
Write-Host "  3. Que el titulo de la ventana dice con que cuenta has entrado."
Write-Host "  4. La primera vez, Windows dira que protegio tu PC: Mas informacion"
Write-Host "     -> Ejecutar de todas formas. Es por no estar firmado."
