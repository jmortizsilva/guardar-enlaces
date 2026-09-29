"""Diálogos de pregunta que hacen falta en más de un sitio.

Viven aparte porque los usan tanto el arranque (main.py) como el cambio de
cuenta desde la ventana principal, y duplicar el texto de una pregunta que
decide qué pasa con los datos de alguien es como acabas teniendo dos versiones
que dicen cosas distintas.
"""

from __future__ import annotations

import wx

from ..almacen_local import AlmacenLocal
from ..asentar_cuenta import EnlacesEnElEquipo, asentar_cuenta, identidad_dueno
from .campos import ESTILO_SOLO_LECTURA, con_etiqueta


def preguntar_importacion(enlaces: EnlacesEnElEquipo, padre: wx.Window | None = None) -> bool:
    """Qué hacer con los enlaces que ya hay en el equipo al entrar en una cuenta.

    Las dos salidas van nombradas por lo que hacen, no "Sí" y "No": borrar no se
    puede deshacer y hay que oírlo antes de elegir, no después.
    """
    cuenta = "1 enlace guardado" if enlaces.cuantos == 1 else f"{enlaces.cuantos} enlaces guardados"
    origen = "con otra cuenta" if enlaces.de_otra_cuenta else "sin cuenta"
    dialogo = wx.MessageDialog(
        padre,
        f"Hay {cuenta} en este equipo {origen}. ¿Quieres añadirlos a esta "
        "cuenta? Si eliges borrarlos, se quitan de este equipo y no se pueden "
        "recuperar.",
        "Enlaces en este equipo",
        wx.YES_NO | wx.ICON_QUESTION,
    )
    dialogo.SetYesNoLabels("&Añadirlos", "&Borrarlos")
    respuesta = dialogo.ShowModal()
    dialogo.Destroy()
    return respuesta == wx.ID_YES


def asentar_cuenta_contandolo(
    almacen: AlmacenLocal,
    url_servidor: str,
    email: str,
    padre: wx.Window | None = None,
) -> None:
    """asentar_cuenta, mas contarle a quien acaba de entrar lo que ha pasado
    cuando no habia nada que preguntarle.

    Entrar en la cuenta que ya era duena de esta cache no pregunta nada, y
    hace bien: esos enlaces ya eran suyos. Pero lo que se guardo sin sesion se
    sube igual, y eso pasaba **en silencio**. Parecia que la aplicacion no se
    habia enterado de los enlaces nuevos.

    El aviso es un cuadro y no un mensaje en la barra de estado a proposito:
    la barra no la lee NVDA cuando cambia (ver _decir_estado), asi que un
    aviso ahi es justo lo que no se entera quien mas falta le hace.
    """
    # Se cuentan ANTES: sin sesion, lo que este sin subir solo puede haberse
    # guardado sin cuenta.
    sin_subir = len(almacen.cargar_pendientes())
    pregunto = False

    def preguntar(enlaces: EnlacesEnElEquipo) -> bool:
        nonlocal pregunto
        pregunto = True
        return preguntar_importacion(enlaces, padre)

    asentar_cuenta(almacen, identidad_dueno(url_servidor, email), preguntar)

    # Si hubo pregunta ya se hablo de estos enlaces; un segundo cuadro sobra.
    if not sin_subir or pregunto:
        return

    if sin_subir == 1:
        texto = (
            "El enlace que guardaste sin haber iniciado sesión se va a subir "
            "a tu cuenta. También lo verás en el móvil."
        )
    else:
        texto = (
            f"Los {sin_subir} enlaces que guardaste sin haber iniciado sesión "
            "se van a subir a tu cuenta. También los verás en el móvil."
        )
    avisar(texto, "Enlaces guardados sin sesión", padre)


def avisar(texto: str, titulo: str, padre: wx.Window | None = None, grave: bool = False) -> None:
    """Un aviso con un solo botón, que dice Aceptar y no OK.

    `wx.MessageBox` no deja cambiar el texto de sus botones y sale en inglés; hay
    una prueba que impide usarlo.
    """
    dialogo = wx.MessageDialog(
        padre, texto, titulo, wx.OK | (wx.ICON_WARNING if grave else wx.ICON_INFORMATION)
    )
    dialogo.SetOKLabel("&Aceptar")
    dialogo.ShowModal()
    dialogo.Destroy()


class DialogoAvisoLegible(wx.Dialog):
    """Un aviso cuyo texto se puede recorrer con las flechas.

    `avisar` usa el cuadro de mensaje del sistema, que el lector lee entero al
    abrirse pero no deja volver sobre una palabra ni releer una cifra: si se
    escapa, no hay manera de oirla otra vez. Aqui el texto va en un campo de
    solo lectura, que tiene el foco al abrirse, y detras un unico boton.
    """

    def __init__(self, padre: wx.Window | None, texto: str, titulo: str, etiqueta: str):
        super().__init__(padre, title=titulo)
        panel = wx.Panel(self)
        sizer = wx.BoxSizer(wx.VERTICAL)

        texto_etiqueta, self.campo = con_etiqueta(
            panel,
            etiqueta,
            lambda padre: wx.TextCtrl(
                padre, value=texto, style=ESTILO_SOLO_LECTURA, size=(420, 80)
            ),
        )
        sizer.Add(texto_etiqueta, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.campo, 1, wx.EXPAND | wx.ALL, 12)

        self.boton = wx.Button(panel, wx.ID_OK, "&Aceptar")
        self.boton.SetDefault()
        sizer.Add(self.boton, 0, wx.ALIGN_RIGHT | wx.LEFT | wx.RIGHT | wx.BOTTOM, 12)
        # Sin boton de Cancelar, Escape no sabe que boton pulsar.
        self.SetEscapeId(wx.ID_OK)

        panel.SetSizer(sizer)
        marco = wx.BoxSizer(wx.VERTICAL)
        marco.Add(panel, 1, wx.EXPAND)
        self.SetSizerAndFit(marco)

        # El foco en el campo y el cursor al principio: asi el lector dice el
        # nombre y el texto al abrirse, y las flechas empiezan por la primera
        # palabra y no por el final.
        self.campo.SetInsertionPoint(0)
        self.campo.SetFocus()


def avisar_legible(
    texto: str, titulo: str, padre: wx.Window | None = None, etiqueta: str = "&Resultado:"
) -> None:
    dialogo = DialogoAvisoLegible(padre, texto, titulo, etiqueta)
    dialogo.ShowModal()
    dialogo.Destroy()


def confirmar_eliminacion(titulo: str, padre: wx.Window | None = None) -> bool:
    """La pregunta antes de eliminar un enlace, desde la lista o desde su detalle.

    Aceptar y Cancelar y no Sí y No: un cuadro de Sí y No no tiene botón de
    cancelar, y sin él Escape no lo cierra. Cancelar es el botón de partida
    porque eliminar no se puede deshacer y un Enter de más no debería bastar.
    """
    dialogo = wx.MessageDialog(
        padre,
        f"¿Eliminar «{titulo}»? Esta acción no se puede deshacer.",
        "Confirmar eliminación",
        wx.OK | wx.CANCEL | wx.CANCEL_DEFAULT | wx.ICON_QUESTION,
    )
    dialogo.SetOKCancelLabels("&Eliminar", "&Cancelar")
    respuesta = dialogo.ShowModal()
    dialogo.Destroy()
    return respuesta == wx.ID_OK


def confirmar_guardar_duplicado(titulo: str, padre: wx.Window | None = None) -> bool:
    """La pregunta al anadir un enlace que ya esta guardado: no se duplica,
    se ACTUALIZA ese mismo enlace con la comprobacion de ahora."""
    dialogo = wx.MessageDialog(
        padre,
        f"Ya tienes guardado «{titulo}». ¿Actualizarlo con la comprobación de ahora?",
        "Enlace repetido",
        wx.OK | wx.CANCEL | wx.CANCEL_DEFAULT | wx.ICON_QUESTION,
    )
    dialogo.SetOKCancelLabels("&Actualizar", "&Cancelar")
    respuesta = dialogo.ShowModal()
    dialogo.Destroy()
    return respuesta == wx.ID_OK


def confirmar_eliminar_etiqueta(etiqueta: str, cuantos: int, padre: wx.Window | None = None) -> bool:
    """La pregunta antes de eliminar una etiqueta de TODOS los enlaces que la
    llevan, desde el gestor de etiquetas."""
    cuenta = "1 enlace" if cuantos == 1 else f"{cuantos} enlaces"
    dialogo = wx.MessageDialog(
        padre,
        f"¿Eliminar la etiqueta «{etiqueta}»? Se quitará de {cuenta}. "
        "Esta acción no se puede deshacer.",
        "Eliminar etiqueta",
        wx.OK | wx.CANCEL | wx.CANCEL_DEFAULT | wx.ICON_QUESTION,
    )
    dialogo.SetOKCancelLabels("&Eliminar", "&Cancelar")
    respuesta = dialogo.ShowModal()
    dialogo.Destroy()
    return respuesta == wx.ID_OK
