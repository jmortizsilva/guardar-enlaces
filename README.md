# Guardar enlaces

App para guardar contenidos (enlaces, vídeos) accesible con lector de
pantalla en iPhone y en Windows, sincronizada entre ambos. Un solo
repositorio (para tenerlo centralizado en GitHub), tres proyectos
independientes por dentro — no comparten código, cada uno con su propio
gestor de paquetes y su propio ciclo de vida:

- `servidor/` — backend (Node + Fastify + SQLite). **Proceso y contenedor
  Docker propios y separados** de los clientes y de `servidor-notificaciones`
  (otro proyecto del repo padre): eso es una decisión de despliegue, no de
  dónde vive el código. Su `docs/CONTRATO-API.md` es la fuente de la verdad
  del API para los dos clientes.
- `app-windows/` — cliente de escritorio, Python + wxPython.
- `app-ios/` — cliente iOS, Expo/React Native (pendiente).

Ver `CLAUDE.md` (raíz del repo padre `desarrollo-ios-rn`) para las
convenciones generales de desarrollo iOS/RN sin Mac.
