"""Completar la direccion escrita a mano, contra los casos compartidos.

Como las de importar, estas pruebas no traen sus casos: leen
pruebas-compartidas/direcciones/casos.json, el mismo que leen iOS y Android.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from guardar_enlaces.direcciones import completar

CASOS = Path(__file__).resolve().parents[2] / "pruebas-compartidas" / "direcciones" / "casos.json"


def _casos() -> list[dict]:
    return json.loads(CASOS.read_text(encoding="utf-8"))["casos"]


@pytest.mark.parametrize("caso", _casos(), ids=lambda caso: repr(caso["escrito"]))
def test_completa_como_dicen_los_casos_compartidos(caso):
    resultado = completar(caso["escrito"])
    if caso["direccion"] is None:
        assert resultado is None
    else:
        assert resultado is not None
        assert resultado.direccion == caso["direccion"]
        assert resultado.alternativa == caso["alternativa"]
