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
(por defecto `http://localhost:8081`). Contra un servidor local con
`PERMITIR_LOGIN_DEV=true`, la pantalla de inicio de sesión pide solo un
correo ya invitado (sin pasar por Google/Apple) — ver
`docs/CONTRATO-API.md` en `servidor-guardar-enlaces/`.

## Estructura

- `guardar_enlaces/modelo.py` — lógica pura de fusión de sincronización (sin
  red ni interfaz), con sus tests en `tests/test_modelo.py`.
- `guardar_enlaces/almacen_local.py` — caché local (SQLite) + cola de
  cambios pendientes de subir ("outbox").
- `guardar_enlaces/api_cliente.py` — llamadas HTTP al backend.
- `guardar_enlaces/credenciales.py` — guarda los tokens con `keyring`
  (Administrador de credenciales de Windows), nunca en fichero plano.
- `guardar_enlaces/ui/` — ventanas wxPython.
