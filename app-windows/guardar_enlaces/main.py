"""Punto de entrada: python -m guardar_enlaces"""

from __future__ import annotations

import os
from pathlib import Path

import wx

from .almacen_local import AlmacenLocal
from .api_cliente import ClienteApi
from .sesion import Sesion
from .ui.dialogo_login import DialogoLogin
from .ui.ventana_principal import VentanaPrincipal


def _ruta_datos() -> Path:
    appdata = os.getenv("APPDATA")
    base = Path(appdata) / "GuardarEnlaces" if appdata else Path.home() / ".guardar-enlaces"
    return base / "cache.sqlite"


def _url_base() -> str:
    return os.getenv("GUARDAR_ENLACES_API", "http://localhost:8081")


class AplicacionGuardarEnlaces(wx.App):
    def OnInit(self) -> bool:
        cliente = ClienteApi(_url_base())
        sesion = Sesion(cliente)
        almacen = AlmacenLocal(_ruta_datos())

        if not sesion.restaurar():
            dialogo = DialogoLogin(None, sesion, cliente)
            resultado = dialogo.ShowModal()
            dialogo.Destroy()
            if resultado != wx.ID_OK:
                return False

        ventana = VentanaPrincipal(almacen, cliente, sesion)
        ventana.Show()
        self.SetTopWindow(ventana)
        return True


def main() -> None:
    app = AplicacionGuardarEnlaces()
    app.MainLoop()


if __name__ == "__main__":
    main()
