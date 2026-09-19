# Guárdalo

Ver [`PROYECTO.md`](PROYECTO.md) para la descripción completa del proyecto y
cómo arrancar cada parte — este README es solo el resumen de estructura.

App para guardar contenidos (enlaces, vídeos) accesible con lector de
pantalla en iPhone y en Windows, sincronizada entre ambos. Un solo
repositorio (para tenerlo centralizado en GitHub), tres proyectos
independientes por dentro — no comparten código, cada uno con su propio
gestor de paquetes y su propio ciclo de vida:

- `backend/` — backend (Node + Fastify + SQLite). **Proceso y contenedor
  Docker propios y separados** de los clientes y de `servidor-notificaciones`
  (otro proyecto del repo padre): eso es una decisión de despliegue, no de
  dónde vive el código. Su `docs/CONTRATO-API.md` es la fuente de la verdad
  del API para los dos clientes.
- `app-windows/` — cliente de escritorio, Python + wxPython.
- `app-ios-nativa/` — cliente iOS, Swift y SwiftUI.

El cliente de iOS fue una app de Expo hasta septiembre de 2026. Por qué se
reescribió en nativo, qué cambió y qué se descartó por el camino está en
[`app-ios-nativa/PLAN.md`](app-ios-nativa/PLAN.md); el código de la de Expo
sigue en el historial, hasta el commit que la retira.
