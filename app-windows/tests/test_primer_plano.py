"""Traer la ventana delante cuando Windows no quiere.

Lo que de verdad hace esto solo se comprueba con una ventana de verdad en
Windows; aqui se cubren los bordes, que son los que se dan en la practica
(una ventana que ya no existe, o que ya esta delante).
"""

from __future__ import annotations

import wx

from guardar_enlaces.primer_plano import traer_al_frente


def test_sin_ventana_no_revienta_y_dice_que_no_pudo():
    assert traer_al_frente(0) is False


def test_una_ventana_de_verdad_no_hace_reventar_a_nadie(app):
    """No se comprueba que gane el primer plano: en una bateria de pruebas el
    foco lo tiene otra cosa y Windows esta en su derecho de negarlo. Lo que se
    comprueba es que devuelve un si o un no y no se queda colgado, que es lo
    que pasaria si se dejara enganchada la cola de entrada de otro hilo."""
    marco = wx.Frame(None, title="Prueba de primer plano")
    try:
        marco.Show()
        assert traer_al_frente(int(marco.GetHandle())) in (True, False)
    finally:
        marco.Destroy()
