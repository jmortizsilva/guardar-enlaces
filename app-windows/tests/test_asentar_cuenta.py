"""Mismos casos que app-ios/src/almacen/asentarCuenta.test.ts."""

from unittest.mock import MagicMock

from guardar_enlaces.asentar_cuenta import (
    EnlacesEnElEquipo,
    asentar_cuenta,
    identidad_dueno,
)

CUENTA = identidad_dueno("https://api.ejemplo.com", "persona@ejemplo.com")
OTRA_CUENTA = identidad_dueno("https://api.ejemplo.com", "otra@ejemplo.com")


def _almacen(dueno: str | None, cuantos: int) -> MagicMock:
    almacen = MagicMock()
    almacen.dueno_actual.return_value = dueno
    almacen.contar_elementos.return_value = cuantos
    return almacen


def test_entrar_en_la_cuenta_de_siempre_no_pregunta_ni_toca_nada():
    almacen = _almacen(CUENTA, 12)
    decidir = MagicMock()

    asentar_cuenta(almacen, CUENTA, decidir)

    decidir.assert_not_called()
    almacen.vaciar.assert_not_called()
    almacen.adoptar_con_ids_nuevos.assert_not_called()
    almacen.fijar_cursor.assert_not_called()


def test_con_el_equipo_vacio_no_pregunta():
    almacen = _almacen(None, 0)
    decidir = MagicMock()

    asentar_cuenta(almacen, CUENTA, decidir)

    decidir.assert_not_called()
    almacen.fijar_dueno.assert_called_once_with(CUENTA)


def test_lo_guardado_sin_cuenta_se_importa_si_el_usuario_dice_que_si():
    almacen = _almacen(None, 3)
    decidir = MagicMock(return_value=True)

    asentar_cuenta(almacen, CUENTA, decidir)

    decidir.assert_called_once_with(EnlacesEnElEquipo(cuantos=3, de_otra_cuenta=False))
    almacen.adoptar_con_ids_nuevos.assert_called_once()
    almacen.vaciar.assert_not_called()
    # Sin esto, el primer pull se saltaria lo anterior al cursor de antes.
    almacen.fijar_cursor.assert_called_once_with(0)
    almacen.fijar_dueno.assert_called_once_with(CUENTA)


def test_si_dice_que_no_se_borra_lo_que_habia():
    almacen = _almacen(None, 3)
    decidir = MagicMock(return_value=False)

    asentar_cuenta(almacen, CUENTA, decidir)

    almacen.vaciar.assert_called_once()
    almacen.adoptar_con_ids_nuevos.assert_not_called()
    almacen.fijar_dueno.assert_called_once_with(CUENTA)


def test_al_cambiar_de_cuenta_se_avisa_de_que_lo_de_antes_era_de_otra():
    almacen = _almacen(OTRA_CUENTA, 5)
    decidir = MagicMock(return_value=False)

    asentar_cuenta(almacen, CUENTA, decidir)

    decidir.assert_called_once_with(EnlacesEnElEquipo(cuantos=5, de_otra_cuenta=True))


def test_el_mismo_correo_en_otro_servidor_es_otra_biblioteca():
    assert identidad_dueno("https://api.ejemplo.com", "a@b.com") != identidad_dueno(
        "http://localhost:8081", "a@b.com"
    )
