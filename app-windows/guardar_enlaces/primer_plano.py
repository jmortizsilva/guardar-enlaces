"""Traer una ventana delante cuando Windows no quiere dejarla.

Windows solo deja ponerse en primer plano a un proceso que tenga "derecho de
primer plano": ser quien ya esta delante, o haber recibido la ultima pulsacion
del usuario. Un proceso al que lanza el relevo --un cmd desprendido y sin
consola-- no tiene ninguna de las dos, asi que SetForegroundWindow falla, y
falla EN SILENCIO: devuelve cero y no pasa nada.

Medido tras actualizar: la ventana existia, era visible y no estaba
minimizada, pero no estaba al frente. El foco no se movia, el lector no
anunciaba nada, y desde fuera era exactamente igual que si la aplicacion no
hubiera vuelto a abrirse.

El unico modo fiable de saltarlo es engancharse a la cola de entrada del hilo
que SI esta delante: mientras dos hilos estan enganchados, Windows los trata
como uno, asi que el permiso del otro vale para nosotros. Se desengancha
inmediatamente despues; dejarlo enganchado cuelga las dos aplicaciones.

Esto es una esquina rara del sistema y podria dejar de colar. Por eso quien
llama NO debe fiarse solo de esto para avisar de que la ventana ya esta ahi:
devuelve si lo consiguio, precisamente para poder decirlo por otro medio.
"""

from __future__ import annotations

import ctypes

_SW_RESTORE = 9


def traer_al_frente(ventana: int) -> bool:
    """`ventana` es el identificador de Windows (el de wx.Window.GetHandle()).
    Devuelve si la ventana acabo de verdad en primer plano."""
    if not ventana:
        return False
    try:
        usuario = ctypes.windll.user32
        nucleo = ctypes.windll.kernel32
    except AttributeError:  # no es Windows: aqui no hay nada que hacer
        return False

    if usuario.IsIconic(ventana):
        usuario.ShowWindow(ventana, _SW_RESTORE)

    delante = usuario.GetForegroundWindow()
    if delante == ventana:
        return True

    hilo_de_delante = usuario.GetWindowThreadProcessId(delante, None)
    hilo_propio = nucleo.GetCurrentThreadId()
    enganchado = False
    if hilo_de_delante and hilo_de_delante != hilo_propio:
        enganchado = bool(usuario.AttachThreadInput(hilo_propio, hilo_de_delante, True))
    try:
        usuario.BringWindowToTop(ventana)
        usuario.SetForegroundWindow(ventana)
    finally:
        # Siempre, aunque lo de arriba falle: dos hilos enganchados para
        # siempre es peor que una ventana detras.
        if enganchado:
            usuario.AttachThreadInput(hilo_propio, hilo_de_delante, False)

    return usuario.GetForegroundWindow() == ventana
