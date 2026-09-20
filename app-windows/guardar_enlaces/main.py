"""Punto de entrada: python -m guardar_enlaces"""

from __future__ import annotations

import os
from pathlib import Path

import wx

from .almacen_local import AlmacenLocal
from .api_cliente import ClienteApi
from .asentar_cuenta import identidad_dueno
from .sesion import Sesion
from .ui.dialogo_login import DialogoLogin
from .ui.preguntas import asentar_cuenta_contandolo
from .ui.ventana_principal import VentanaPrincipal


def _ruta_datos() -> Path:
    appdata = os.getenv("APPDATA")
    base = Path(appdata) / "GuardarEnlaces" if appdata else Path.home() / ".guardar-enlaces"
    return base / "cache.sqlite"


def _url_base() -> str:
    """El servidor real por defecto. Antes era localhost porque siempre se
    arrancaba con un script que fijaba la variable; el ejecutable no tiene
    script, asi que lo razonable es que funcione al abrirlo. La variable sigue
    sirviendo para apuntar a un servidor local cuando se prueba."""
    return os.getenv("GUARDAR_ENLACES_API", "https://api.jmortiz.es")


class AplicacionGuardarEnlaces(wx.App):
    def OnInit(self) -> bool:
        cliente = ClienteApi(_url_base())
        sesion = Sesion(cliente)
        almacen = AlmacenLocal(_ruta_datos())

        restaurada = sesion.restaurar()
        if not restaurada:
            # Se entre o no, la aplicacion se abre. Sin cuenta funciona entera
            # contra este equipo y lo unico que no hace es sincronizar; entrar
            # mas tarde esta en el menu Archivo. Antes, no entrar aqui cerraba
            # la aplicacion.
            dialogo = DialogoLogin(None, sesion, cliente)
            dialogo.ShowModal()
            dialogo.Destroy()

        correo = (sesion.usuario or {}).get("email")
        if correo:
            dueno = identidad_dueno(cliente.url_base, correo)
            if restaurada:
                # Sesion que ya venia de antes: lo que hay en la cache es de esta
                # misma cuenta, se sincronizo bajo ella. Se marca sin preguntar
                # --las instalaciones anteriores a esto no tienen dueno
                # guardado--; preguntar aqui haria que la primera vez tras
                # actualizar se ofreciera importar enlaces que YA estan en el
                # servidor, y aceptar habria creado duplicados con ids nuevos.
                if almacen.dueno_actual() is None:
                    almacen.fijar_dueno(dueno)
            else:
                asentar_cuenta_contandolo(almacen, cliente.url_base, correo)

        ventana = VentanaPrincipal(almacen, cliente, sesion)
        ventana.Show()
        ventana.Raise()
        # Con CallAfter: justo despues de Show() la ventana aun no esta activa, y
        # al activarse el foco iba al primer control, el buscador.
        wx.CallAfter(ventana.enfocar_lo_primero)
        self.SetTopWindow(ventana)
        return True


def main() -> None:
    app = AplicacionGuardarEnlaces()
    app.MainLoop()


if __name__ == "__main__":
    main()
