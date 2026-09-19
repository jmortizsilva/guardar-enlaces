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

# Si la aplicacion esta abierta, PyInstaller no puede vaciar dist\ y el fallo
# sale como cuarenta lineas de traza que terminan en "Acceso denegado" sobre un
# fichero de prism, que no dice nada de lo que pasa de verdad.
#
# OJO: tiene icono en la bandeja, asi que cerrar la ventana NO la cierra.
$enMarcha = Get-Process -Name "GuardarEnlaces" -ErrorAction SilentlyContinue
if ($enMarcha) {
    Write-Host "Guardalo esta abierta y bloquea los ficheros que hay que reemplazar." -ForegroundColor Red
    Write-Host "Cierrala del todo desde su icono de la bandeja: cerrar la ventana no basta." -ForegroundColor Red
    exit 1
}

# Lo mismo cuando quien tiene la carpeta cogida es otro: lo mas habitual,
# OneDrive sincronizandola. Se intenta aqui para poder decirlo en una linea.
if (Test-Path "dist\GuardarEnlaces") {
    try {
        Remove-Item "dist\GuardarEnlaces" -Recurse -Force -ErrorAction Stop
    }
    catch {
        Write-Host "No se puede vaciar dist\GuardarEnlaces: algo tiene abierto un fichero de dentro." -ForegroundColor Red
        Write-Host "Suele ser la propia aplicacion, OneDrive sincronizando la carpeta, o el antivirus." -ForegroundColor Red
        exit 1
    }
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

$prismNativo = & $python -c "import os, prism; print(os.path.join(os.path.dirname(prism.__file__), '_native'))"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path (Join-Path $prismNativo "_prism_cffi.pyd"))) {
    Write-Host "No se encuentra el modulo nativo de prism en el entorno virtual." -ForegroundColor Red
    exit 1
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
        --collect-all prism `
        --add-binary "$prismNativo\_prism_cffi.pyd;prism\_native" `
        --exclude-module keyring.testing `
        --exclude-module pytest `
        --exclude-module numpy `
        --exclude-module PIL `
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
# --collect-all prism: prism lleva su DLL y su modulo nativo en la carpeta
#   prism\_native y los busca ahi al importarse. Si faltan, la aplicacion arranca
#   igual pero no le dice nada al lector de pantalla.
# --add-binary _prism_cffi.pyd: --collect-all se trae prism.dll pero no este
#   modulo. prism no lo importa desde su carpeta sino anadiendo _native a su ruta
#   al arrancar, y PyInstaller no lo ve ("missing module named prism._prism_cffi"
#   en build\GuardarEnlaces\warn-GuardarEnlaces.txt). Comprobado con prismatoid
#   0.18.2: sin esta linea el ejecutable empaqueta sin errores y queda mudo.
# --exclude-module: --collect-all keyring se trae tambien keyring.testing, que
#   importa pytest; pytest importa numpy, y pygments (que viene con el) importa
#   Pillow. La aplicacion no usa ninguno y eran unos 40 MB de los 92 del paquete.
#   Se excluyen los cuatro por nombre: si otro paquete volviera a traer numpy o
#   Pillow por su cuenta, tampoco entrarian.

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
Write-Host "  4. Que habla con el lector: copiar una URL tiene que decir 'URL copiada'."
Write-Host "  5. La primera vez, Windows dira que protegio tu PC: Mas informacion"
Write-Host "     -> Ejecutar de todas formas. Es por no estar firmado."
