import sys
from pathlib import Path

# Sin instalar el paquete (no hay setup.py/build backend), asi que hay que anadir app-windows/ al
# path explicitamente para que "import guardar_enlaces" funcione sea cual sea el cwd desde el que
# se invoque pytest.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
