"""Icono de bandeja basico: abrir, sincronizar ahora, salir. El atajo de
teclado global + captura de portapapeles (equivalente Windows al "Compartir"
de iOS) queda para la Fase 2, ver README.md."""

from __future__ import annotations

import wx
import wx.adv


class IconoBandeja(wx.adv.TaskBarIcon):
    def __init__(self, ventana_principal: wx.Frame):
        super().__init__()
        self._ventana = ventana_principal
        # Icono generico de sistema por ahora: sustituir por uno propio antes de distribuir.
        bitmap = wx.ArtProvider.GetBitmap(wx.ART_TIP, wx.ART_OTHER, (16, 16))
        icono = wx.Icon()
        icono.CopyFromBitmap(bitmap)
        self.SetIcon(icono, "Guardar enlaces")
        self.Bind(wx.adv.EVT_TASKBAR_LEFT_DCLICK, self._al_abrir)

    def CreatePopupMenu(self) -> wx.Menu:
        menu = wx.Menu()
        item_abrir = menu.Append(wx.ID_ANY, "&Abrir")
        item_sincronizar = menu.Append(wx.ID_ANY, "Si&ncronizar ahora")
        menu.AppendSeparator()
        item_salir = menu.Append(wx.ID_ANY, "&Salir")
        self.Bind(wx.EVT_MENU, self._al_abrir, item_abrir)
        self.Bind(wx.EVT_MENU, self._al_sincronizar, item_sincronizar)
        self.Bind(wx.EVT_MENU, self._al_salir, item_salir)
        return menu

    def _al_abrir(self, evento: wx.Event) -> None:
        self._ventana.Show()
        self._ventana.Raise()

    def _al_sincronizar(self, evento: wx.CommandEvent) -> None:
        self._ventana.sincronizar_en_segundo_plano(manual=True)

    def _al_salir(self, evento: wx.CommandEvent) -> None:
        self._ventana.Close(force=True)
