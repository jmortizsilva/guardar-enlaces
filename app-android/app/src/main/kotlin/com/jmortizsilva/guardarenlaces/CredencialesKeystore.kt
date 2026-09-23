package com.jmortizsilva.guardarenlaces

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.jmortizsilva.guardarenlaces.fontaneria.AlmacenCredenciales
import java.io.File
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * El token de refresco, cifrado con una clave que vive dentro del Android Keystore y no sale de él.
 * El fichero cifrado se guarda en la carpeta privada de la app; sin la clave no sirve de nada.
 *
 * No se usa `EncryptedSharedPreferences`: la librería entera está marcada como obsoleta desde junio
 * de 2025, y su propia nota recomienda usar el Keystore directamente, que es esto.
 *
 * La clave NO pide el teléfono desbloqueado (`setUnlockedDeviceRequired`), aunque iOS sí lo hace
 * con `kSecAttrAccessibleWhenUnlocked`. Se probó en el teléfono: con esa restricción, bloqueado, la
 * clave se niega a cifrar («Device locked»). Si la pantalla se bloquea mientras se renueva la
 * sesión, el servidor ya ha revocado el token viejo y el nuevo no se puede guardar: en el siguiente
 * arranque solo queda el revocado, y la sesión se pierde. La clave sigue sin poder salir del
 * Keystore y el fichero sigue en la carpeta privada de la app.
 *
 * El token se descifra una vez y se queda en memoria mientras la app viva.
 *
 * @param fichero dónde va el token cifrado. Tiene que quedar fuera de la copia de seguridad (ver
 *   `res/xml/reglas_copia.xml`): restaurado en otro teléfono, sin su clave, es basura.
 */
class CredencialesKeystore(private val fichero: File, private val alias: String = ALIAS) :
    AlmacenCredenciales {
    @Volatile private var enMemoria: String? = null
    @Volatile private var leido = false

    override fun guardarTokenRefresco(token: String) {
        synchronized(this) {
            val cifrador = Cipher.getInstance(TRANSFORMACION)
            cifrador.init(Cipher.ENCRYPT_MODE, clave())
            val cifrado = cifrador.doFinal(token.toByteArray(Charsets.UTF_8))
            // Primero a un fichero aparte y luego se cambia de nombre: si la app muere a mitad de
            // escribir, queda el token anterior entero en vez de uno a medias. Con la rotación, un
            // token a medias es una sesión perdida.
            fichero.parentFile?.mkdirs()
            val temporal = File(fichero.path + ".nuevo")
            temporal.writeBytes(byteArrayOf(cifrador.iv.size.toByte()) + cifrador.iv + cifrado)
            check(temporal.renameTo(fichero)) { "no se pudo guardar el token de sesión" }
            enMemoria = token
            leido = true
        }
    }

    override fun tokenRefresco(): String? {
        if (leido) return enMemoria
        synchronized(this) {
            if (leido) return enMemoria
            enMemoria = leerDelFichero()
            leido = true
            return enMemoria
        }
    }

    override fun borrarTokenRefresco() {
        synchronized(this) {
            fichero.delete()
            enMemoria = null
            leido = true
        }
    }

    private fun leerDelFichero(): String? {
        if (!fichero.exists()) return null
        val clave = claveExistente()
        val contenido = fichero.readBytes()
        return try {
            checkNotNull(clave) { "el token está cifrado con una clave que ya no existe" }
            val largoIv = contenido[0].toInt()
            val iv = contenido.copyOfRange(1, 1 + largoIv)
            val cifrador = Cipher.getInstance(TRANSFORMACION)
            cifrador.init(Cipher.DECRYPT_MODE, clave, GCMParameterSpec(BITS_ETIQUETA, iv))
            String(
                cifrador.doFinal(contenido, 1 + largoIv, contenido.size - 1 - largoIv),
                Charsets.UTF_8,
            )
        } catch (irrecuperable: Exception) {
            // Solo se tira el fichero cuando no hay forma de leerlo nunca más: la clave ya no
            // existe o dejó de valer, o el fichero está roto. Cualquier otro fallo (el teléfono
            // bloqueado, por ejemplo) se deja pasar sin borrar nada, o se perdería la sesión por
            // algo que se arregla solo.
            if (esIrrecuperable(irrecuperable)) fichero.delete()
            null
        }
    }

    private fun esIrrecuperable(fallo: Exception) =
        fallo is IllegalStateException ||
            fallo is KeyPermanentlyInvalidatedException ||
            fallo is AEADBadTagException ||
            fallo is IndexOutOfBoundsException ||
            fallo is IllegalArgumentException

    private fun almacen() = KeyStore.getInstance(PROVEEDOR).apply { load(null) }

    private fun claveExistente(): SecretKey? = almacen().getKey(alias, null) as? SecretKey

    private fun clave(): SecretKey =
        claveExistente()
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVEEDOR)
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                alias,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build()
                    )
                }
                .generateKey()

    /** Para las pruebas en el teléfono: la clave propia de la prueba, y fuera. */
    internal fun borrarClave() = almacen().deleteEntry(alias)

    private companion object {
        const val ALIAS = "guardalo-token-refresco"
        const val PROVEEDOR = "AndroidKeyStore"
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val BITS_ETIQUETA = 128
    }
}
