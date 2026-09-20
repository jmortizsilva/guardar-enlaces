"""Elegir las etiquetas de un enlace de entre las que ya existen, y crear
alguna que todavia no exista.

Mismo trato que PantallaEtiquetas.swift en el iPhone, y por el mismo motivo:
antes esto era un campo de texto separando por comas, y escribir a mano una
etiqueta que ya existe es como acabas teniendo "Podcast" y "Podcasts". Con
una lista, ademas, se puede saber cuales hay sin acordarse de memoria.

Es un wx.ListCtrl con casillas y no un wx.CheckListBox: aquel envuelve el
SysListView32 nativo de Windows, que es el control que NVDA, JAWS y el
Narrador anuncian de forma consistente y el mismo que usa la lista de la
ventana principal (ver su cabecera). El CheckListBox no es nativo por dentro,
lo dibuja wx, y no hay experiencia en este proyecto de como se anuncia.

Vive aparte porque lo usan el dialogo de anadir y el de detalle. Cuando esto
eran dos campos de texto, ya eran dos sitios donde tocar lo mismo.
"""

from __future__ import annotations

import unicodedata

import wx

from .campos import con_etiqueta

#: Teclas de acceso: Q, V y D. Estan libres en los dos dialogos que lo usan,
#: que es la condicion para que un control compartido no choque con nada.
_ETIQUETA_LISTA = "Eti&quetas:"
_ETIQUETA_NUEVA = "Etiqueta nue&va:"
_BOTON_ANADIR = "Aña&dir"


class SelectorEtiquetas(wx.Panel):
    def __init__(
        self,
        padre: wx.Window,
        disponibles: list[str] | tuple[str, ...] = (),
        elegidas: list[str] | tuple[str, ...] = (),
    ):
        super().__init__(padre)
        # Las que ya lleva el enlace entran en la lista aunque no las tenga
        # nadie mas: si no, editarlo se las borraria sin decir nada.
        iniciales = set(elegidas)
        self._todas: list[str] = sorted(set(disponibles) | iniciales, key=_orden)

        sizer = wx.BoxSizer(wx.VERTICAL)

        etiqueta_lista, self.lista = con_etiqueta(
            self,
            _ETIQUETA_LISTA,
            lambda p: wx.ListCtrl(
                p,
                style=wx.LC_REPORT | wx.LC_NO_HEADER | wx.LC_SINGLE_SEL,
                size=(420, 150),
            ),
        )
        self.lista.EnableCheckBoxes()
        self.lista.InsertColumn(0, "Etiqueta", width=400)
        sizer.Add(etiqueta_lista, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(self.lista, 1, wx.EXPAND | wx.ALL, 12)

        fila = wx.BoxSizer(wx.HORIZONTAL)
        # TE_PROCESS_ENTER para que Enter aqui cree la etiqueta en vez de
        # disparar el boton por defecto del dialogo, que seria Guardar.
        etiqueta_nueva, self.campo_nueva = con_etiqueta(
            self,
            _ETIQUETA_NUEVA,
            lambda p: wx.TextCtrl(p, style=wx.TE_PROCESS_ENTER),
        )
        self.boton_anadir = wx.Button(self, label=_BOTON_ANADIR)
        fila.Add(self.campo_nueva, 1, wx.ALIGN_CENTER_VERTICAL | wx.RIGHT, 8)
        fila.Add(self.boton_anadir, 0, wx.ALIGN_CENTER_VERTICAL)
        sizer.Add(etiqueta_nueva, 0, wx.LEFT | wx.RIGHT | wx.TOP, 12)
        sizer.Add(fila, 0, wx.EXPAND | wx.ALL, 12)

        self.SetSizer(sizer)

        self.boton_anadir.Bind(wx.EVT_BUTTON, lambda evento: self.anadir_nueva())
        self.campo_nueva.Bind(wx.EVT_TEXT_ENTER, lambda evento: self.anadir_nueva())

        self._pintar(iniciales)

    # --- lo que preguntan los dialogos ---

    def etiquetas_elegidas(self) -> tuple[str, ...]:
        """Las marcadas, ordenadas.

        Se leen de las propias casillas y no de un conjunto aparte: con dos
        sitios donde vive lo mismo, tarde o temprano dicen cosas distintas.
        Aqui manda el control, que es lo que la persona ve y oye.
        """
        return tuple(sorted(self._marcadas(), key=_orden))

    def anadir_nueva(self) -> str | None:
        """Crea la etiqueta escrita y la deja MARCADA, como en el iPhone:
        quien se molesta en escribirla la quiere para este enlace. Devuelve su
        nombre, o None si no habia nada que crear.

        Si ya existia no se duplica la fila, solo se marca."""
        nombre = self.campo_nueva.GetValue().strip()
        if not nombre:
            return None

        # Leer las casillas ANTES de repintar, que las borra.
        elegidas = self._marcadas()
        elegidas.add(nombre)
        if nombre not in self._todas:
            self._todas = sorted([*self._todas, nombre], key=_orden)
        self.campo_nueva.SetValue("")
        self._pintar(elegidas)

        # El foco va a la etiqueta recien creada, no se queda en el campo. Es
        # la unica confirmacion que recibe quien no ve la lista: el lector
        # lee su nombre y que esta marcada. Callarse aqui no se distingue de
        # que no haya pasado nada.
        fila = self._todas.index(nombre)
        self.lista.Focus(fila)
        self.lista.Select(fila)
        self.lista.SetFocus()
        return nombre

    # --- interior ---

    def _marcadas(self) -> set[str]:
        return {
            nombre
            for fila, nombre in enumerate(self._todas)
            if self.lista.IsItemChecked(fila)
        }

    def _pintar(self, elegidas: set[str]) -> None:
        self.lista.DeleteAllItems()
        for fila, nombre in enumerate(self._todas):
            self.lista.InsertItem(fila, nombre)
            self.lista.CheckItem(fila, nombre in elegidas)


def _orden(nombre: str) -> tuple[str, str]:
    """Para que "Ávila" no acabe detras de "Zamora".

    Ordenar por el numero de cada caracter pone las vocales con tilde despues
    de la z, porque la 'á' es U+00E1 y la 'z' es U+007A. Asi que se descompone
    el acento y se tira la tilde solo PARA COMPARAR; lo que se ve sigue
    llevandola.

    No es la ordenacion perfecta del espanol --la 'ñ' acaba empatada con la
    'n'-- y por eso el nombre original desempata, para que el orden al menos
    sea siempre el mismo. El iPhone usa localizedCompare, que si sabe de
    idiomas; aqui no hace falta tanto, que esto solo decide en que fila se ve
    cada etiqueta.
    """
    sin_tildes = "".join(
        letra
        for letra in unicodedata.normalize("NFD", nombre.casefold())
        if not unicodedata.combining(letra)
    )
    return (sin_tildes, nombre)
