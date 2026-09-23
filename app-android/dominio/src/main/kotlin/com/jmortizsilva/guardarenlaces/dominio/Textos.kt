package com.jmortizsilva.guardarenlaces.dominio

/**
 * Todo lo que la app dice o lee en voz alta, junto y en un solo sitio.
 *
 * Aquí, en lógica pura, para revisarlo de una vez y probarlo en la JVM. Lo que se lee en voz alta
 * es contrato: al cambiar una frase se cambia su prueba en el mismo commit.
 *
 * No es una copia de los textos de iOS: TalkBack añade por su cuenta cosas distintas de las que
 * añade VoiceOver, y lo que en iOS dice «iPhone» aquí dice «teléfono». Se revisan pantalla a
 * pantalla antes de escribirla.
 */
object Textos {
    const val tituloApp = "Guárdalo"
}
