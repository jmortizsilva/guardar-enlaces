# Lo que hemos aprendido probando con VoiceOver

Cosas comprobadas en un iPhone de verdad, no leídas en ningún sitio. Ninguna
la detecta una prueba automática: las pruebas leen el árbol de accesibilidad,
pero no lo oyen.

## Las acciones del rotor: una sola fuente

Medido en un iPhone con cuatro filas, cada una con una combinación distinta y
las mismas acciones. Esto es lo que se oyó en el rotor de cada una:

| Declarado en la fila | Oído en el rotor |
|---|---|
| 2 propias (`accessibilityActions`) | **2**, en orden inverso al declarado |
| 2 gestos (`swipeActions`) | **4**: cada gesto, dos veces |
| 2 gestos + 4 en el menú (`contextMenu`) | **4**: solo los gestos, otra vez duplicados |
| 2 gestos + 2 propias | **5**: las dos fuentes sumadas, con los gestos repetidos |

De ahí salen tres reglas, y ninguna está escrita en la documentación de Apple:

1. **Cada acción puesta en un gesto de deslizar se ofrece dos veces.** No hay
   forma de evitarlo desde la app. Por eso este proyecto **no usa
   `swipeActions`**, aunque deslizar para eliminar sea la costumbre en iOS.
2. **El menú contextual no llega al rotor.** Puede repetir las mismas acciones
   sin ensuciarlo, así que sirve para quien usa la pantalla mirando.
3. **Las acciones propias salen una vez y en un orden que se controla.** Son
   la única fuente fiable para el rotor.

Resultado: el rotor se sirve **solo** desde `accessibilityActions`, el menú
contextual repite esas mismas para el tacto, y nada de gestos.

## El orden se declara al revés

VoiceOver lee las acciones de `accessibilityActions` **en orden inverso al que
se declaran**. Para que se oigan «ver detalles, editar etiquetas, copiar URL,
eliminar», hay que escribirlas empezando por eliminar.

## Un anuncio al abrir una pantalla se pierde

Un aviso lanzado en `onAppear` no llega a sonar: VoiceOver está leyendo la
pantalla recién abierta y uno que espera turno nunca lo consigue.

Lo que funciona: esperar un momento (unos 900 ms) y emitirlo con prioridad
alta. Así se oye sin pisar la lectura de la pantalla.

## Un control que aparece y desaparece hay que contarlo

El botón de pegar solo aparece cuando hay una dirección en el portapapeles.
Quien mira la pantalla lo ve aparecer; quien no, no tiene forma de saber que
está ahí salvo recorriendo la pantalla entera por si acaso.

Por eso, cuando hay algo copiado, la app lo dice: «Hay un enlace copiado,
puedes pegarlo». La regla general: **si un control aparece por su cuenta,
anúncialo o es como si no existiera**.
