# Guardar enlaces — Windows

Cliente de escritorio en Python + wxPython. wxPython envuelve controles Win32
nativos (`wx.ListCtrl` en modo report para la lista, por ejemplo), así que
NVDA/JAWS/Narrador los anuncian igual que a una app nativa.

## Desarrollo

```
python -m venv venv
venv\Scripts\activate
pip install -r requirements-dev.txt
python -m guardar_enlaces          # arranca la app
pytest                              # tests de la logica pura (modelo.py)
```

## Configuración

La app apunta al backend por la variable de entorno `GUARDAR_ENLACES_API`
(por defecto `http://localhost:8081`). El inicio de sesión abre el navegador
del sistema para entrar con Google y, mientras tanto, la app sondea
`/auth/estado` hasta que terminas (modo `polling` del contrato — ver
`docs/CONTRATO-API.md` en `../backend/`). La cuenta es obligatoria aquí: esta
app existe para sincronizar. El alta es abierta, así que entrar la primera vez
crea la cuenta.

- `.\usar-servidor.ps1` — apunta al backend compartido en
  `https://api.jmortiz.es`, sin necesidad de tener nada corriendo en este
  ordenador.
- `.\probar-local.ps1` — apunta a un backend levantado en local (requiere
  tener `backend/probar-local.ps1` corriendo aparte).

## Estructura

- `guardar_enlaces/modelo.py` — lógica pura de fusión de sincronización (sin
  red ni interfaz), con sus tests en `tests/test_modelo.py`.
- `guardar_enlaces/almacen_local.py` — caché local (SQLite) + cola de
  cambios pendientes de subir ("outbox").
- `guardar_enlaces/api_cliente.py` — llamadas HTTP al backend.
- `guardar_enlaces/credenciales.py` — guarda los tokens con `keyring`
  (Administrador de credenciales de Windows), nunca en fichero plano.
- `guardar_enlaces/ui/` — ventanas wxPython.

## Accesibilidad

Ver `docs/ACCESIBILIDAD-WXPYTHON.md` — hallazgos probados con NVDA real
(sin equivalente en `comun/`, que es solo para iOS/RN). Antes de añadir un
control nuevo, échale un vistazo: ahí está, por ejemplo, por qué el texto
de sugerencia de un campo de búsqueda no basta como su nombre accesible.
