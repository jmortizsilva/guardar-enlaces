import Dominio
import SwiftUI

/// Un resultado que hay que poder volver a leer, con un único botón.
///
/// Es el equivalente del `DialogoAvisoLegible` de Windows, y sale de lo que se
/// pidió al probar allí la importación: una frase que solo se oye se pierde, y
/// un aviso del sistema se lee entero pero no deja volver a una cifra. Aquí el
/// texto es un elemento propio que VoiceOver lee al abrirse la hoja y que se
/// puede recorrer por palabras o caracteres con el rotor.
struct HojaResultado: View {
    let titulo: String
    let texto: String
    let alAceptar: () -> Void

    @AccessibilityFocusState private var enElTexto: Bool

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(texto)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    // Para quien quiera copiar una cifra o pegarla en otro
                    // sitio; y no cambia lo que dice VoiceOver.
                    .textSelection(.enabled)
                    .accessibilityFocused($enElTexto)
                    .padding()
            }
            .navigationTitle(titulo)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(Textos.aceptar, action: alAceptar)
                }
            }
        }
        .presentationDetents([.medium])
        .onAppear {
            // El foco al texto y no al título de la barra, que es lo primero
            // que elegiría VoiceOver: el título ya se sabe, lo que importa es
            // el resultado.
            enElTexto = true
        }
    }
}
