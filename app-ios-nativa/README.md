# Guárdalo — iOS nativo

Cliente iOS en Swift y SwiftUI. Sustituyó a la app de Expo/React Native en
septiembre de 2026; aquella sigue en el historial del repositorio, hasta el
commit que la retira. El motivo del cambio y lo que se decidió está en
[`PLAN.md`](PLAN.md).

El contrato de API no cambia: `../backend/docs/CONTRATO-API.md` sigue siendo
la fuente de la verdad.

## Desarrollo

```
./verificar                 # tipos + formato + pruebas. Si esto no pasa, no está terminado
cd Dominio && swift test    # solo la lógica pura: milisegundos, sin simulador
open Guardalo.xcodeproj     # para trabajar en la interfaz
```

`verificar` tarda algo más de un minuto, casi todo en las pruebas de interfaz:
cada una arranca la app en el simulador. Mientras se escribe código, `swift
test` en `Dominio/` o en `Fontaneria/` responde en milisegundos.

Requiere Xcode 27 y el runtime de simulador de iOS 27 (`xcodebuild
-downloadPlatform iOS`). El objetivo de despliegue es iOS 18.

`verificar` usa el simulador de iPhone 17 con iOS 27. Para otro:

```
SIMULADOR='platform=iOS Simulator,name=iPhone 18 Pro,OS=27.0' ./verificar
```

## Estructura

- `Dominio/` — paquete Swift con la lógica pura: sin SwiftUI, sin red y sin
  base de datos. Todo lo que decide algo vive aquí y se prueba en el propio
  Mac, sin simulador. Es la separación de la que habla la regla 8 del
  proyecto, y es lo que hace que las pruebas tarden milisegundos.
- `Fontaneria/` — paquete con lo que habla con el mundo: SQLite, HTTP y
  llavero. Depende de `Dominio`, nunca al revés.
- `Guardalo/` — la app: pantallas y cableado.
- `PruebasInterfaz/` — pruebas contra el árbol de accesibilidad de verdad, el
  mismo que lee VoiceOver. No pueden cubrirlo todo: las acciones del rotor no
  se pueden enumerar desde una prueba, así que ésas siguen siendo comprobación
  de oído.

## Lo que todavía no está

Fases 1 a 5 del plan. Ahora mismo esto es andamiaje: la app arranca y enlaza
con `Dominio`, nada más.
