"""Punto de entrada: python -m guardar_enlaces"""

from __future__ import annotations

import os
from pathlib import Path

import wx

from .almacen_local import AlmacenLocal
from .api_cliente import ClienteApi
from .actualizaciones import NOMBRE_MARCA_VERSION, estrena_version
from .asentar_cuenta import identidad_dueno
from .sesion import Sesion
from .version import VERSION
from .ui.dialogo_login import DialogoLogin
from .ui.preguntas import asentar_cuenta_contandolo
from .ui.ventana_principal import VentanaPrincipal


#: Lo que hay que esperar a que la ventana termine de activarse antes de
#: traerla delante. Medido: con cero --un CallAfter-- no se queda.
_ESPERA_PARA_HACERSE_NOTAR_MS = 1500


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
        # Antes que nada: si la version que arranca no es la que se vio la
        # ultima vez, es que acaban de actualizarla y nadie esta delante
        # esperandola. Se mira aqui para que quede constancia aunque luego
        # falle cualquier otra cosa.
        recien_actualizada = estrena_version(
            VERSION, _ruta_datos().parent / NOMBRE_MARCA_VERSION
        )

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
        if recien_actualizada:
            # CallLater y no CallAfter, y esto esta medido: con CallAfter la
            # ventana se quedaba detras igual. Se ejecuta en cuanto hay un
            # hueco, antes de que la ventana termine de activarse, y el
            # primer plano que se gana ahi no se queda. Con esta espera si.
            #
            # Va despues de enfocar_lo_primero a proposito: aquello mueve el
            # foco DENTRO de la ventana, y esto mueve la ventana.
            # Guardado en la aplicacion a proposito: un wx.CallLater sin
            # referencia puede llevarselo el recolector ANTES de que salte,
            # y entonces no pasa nada y nadie se entera de por que.
            self._aviso_actualizada = wx.CallLater(
                _ESPERA_PARA_HACERSE_NOTAR_MS, ventana.hacerse_notar_tras_actualizar
            )
        self.SetTopWindow(ventana)
        return True


def main() -> None:
    app = AplicacionGuardarEnlaces()
    app.MainLoop()


if __name__ == "__main__":
    main()
