# Guardar enlaces

App para guardar contenidos (enlaces, vídeos) accesible con lector de
pantalla en iPhone y en Windows, sincronizada entre ambos. Contiene los dos
clientes:

- `app-windows/` — Python + wxPython.
- `app-ios/` — Expo/React Native (pendiente).

El backend (`servidor-guardar-enlaces`, repo aparte, proceso y contenedor
propios) define el contrato de API en su carpeta `docs/CONTRATO-API.md` —
es la fuente de la verdad para ambos clientes, que no comparten código entre
sí.

Ver `CLAUDE.md` (raíz del repo padre `desarrollo-ios-rn`) para las
convenciones generales de desarrollo iOS/RN sin Mac.
