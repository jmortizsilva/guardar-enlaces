/*!
 * Basado en la plantilla de expo-share-intent (ShareExtensionViewController.swift),
 * recortada: como app.json solo activa la extension para NSExtensionActivationSupportsWebURL/
 * WebPage, sobran por completo los casos de imagen/video/vCard/pkpass/pdf/fichero/texto del
 * original — no llegan nunca. Sobrescribe el fichero generado por expo-share-intent via
 * plugins/withGuardadoSilencioso.js (mismo mecanismo de plantilla con placeholders).
 *
 * Anade el modo silencioso: si esta activo (interruptor en Ajustes, compartido via
 * UserDefaults del App Group con modules/guardado-silencioso), intenta guardar el enlace
 * hablando con el servidor sin abrir nunca la app. Cualquier fallo cae al comportamiento
 * de siempre (abrir la app) para no perder el enlace compartido.
 */
import MobileCoreServices
import Security
import UIKit

class ShareViewController: UIViewController {
  let hostAppGroupIdentifier: String = "<GROUPIDENTIFIER>"
  let shareProtocol: String = "<SCHEME>"
  let sharedKey: String = "<SCHEME>ShareKey"
  let hideView: Bool = <HIDEVIEW>
  let apiBaseUrl: String = "<APIURL>"
  // Debe coincidir con CLAVE_TOKEN_REFRESCO en src/sesion/credenciales.ts
  let claveTokenRefresco = "guardar-enlaces-token-refresco"
  var sharedWebUrl: [WebUrl] = []
  let urlContentType: String = UTType.url.identifier
  let propertyListType: String = UTType.propertyList.identifier

  override func viewDidLoad() {
    super.viewDidLoad()
    if hideView {
      view.backgroundColor = .clear
      view.isOpaque = false
      handleViewLoad()
    }
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    if !hideView {
      handleViewLoad()
    }
  }

  private func handleViewLoad() {
    Task {
      guard let extensionContext = self.extensionContext,
        let content = extensionContext.inputItems.first as? NSExtensionItem,
        let attachments = content.attachments
      else {
        dismissWithError(message: "No content found")
        return
      }
      for (index, attachment) in attachments.enumerated() {
        if attachment.hasItemConformingToTypeIdentifier(propertyListType) {
          await handlePrepocessing(content: content, attachment: attachment, index: index)
        } else if attachment.hasItemConformingToTypeIdentifier(urlContentType) {
          await handleUrl(content: content, attachment: attachment, index: index)
        } else {
          NSLog("[ERROR] content type not handled !\(String(describing: content))")
          dismissWithError(message: "content type not handled \(String(describing: content))")
        }
      }
    }
  }

  private func handleUrl(content: NSExtensionItem, attachment: NSItemProvider, index: Int) async {
    Task.detached {
      if let item = try! await attachment.loadItem(forTypeIdentifier: self.urlContentType) as? URL {
        Task { @MainActor in
          self.sharedWebUrl.append(WebUrl(url: item.absoluteString, meta: ""))
          if index == (content.attachments?.count)! - 1 {
            await self.finalizarUrlCompartida()
          }
        }
      } else {
        NSLog("[ERROR] Cannot load url content !\(String(describing: content))")
        await self.dismissWithError(message: "Cannot load url content \(String(describing: content))")
      }
    }
  }

  private func handlePrepocessing(content: NSExtensionItem, attachment: NSItemProvider, index: Int)
    async
  {
    Task.detached {
      if let item = try! await attachment.loadItem(
        forTypeIdentifier: self.propertyListType, options: nil)
        as? NSDictionary
      {
        Task { @MainActor in
          if let results = item[NSExtensionJavaScriptPreprocessingResultsKey] as? NSDictionary {
            self.sharedWebUrl.append(
              WebUrl(url: results["baseURI"] as! String, meta: results["meta"] as! String))
            if index == (content.attachments?.count)! - 1 {
              await self.finalizarUrlCompartida()
            }
          } else {
            NSLog("[ERROR] Cannot load preprocessing results !\(String(describing: content))")
            await self.dismissWithError(
              message: "Cannot load preprocessing results \(String(describing: content))")
          }
        }
      } else {
        NSLog("[ERROR] Cannot load preprocessing content !\(String(describing: content))")
        await self.dismissWithError(
          message: "Cannot load preprocessing content \(String(describing: content))")
      }
    }
  }

  /// Ultimo paso una vez se tiene la URL compartida: intenta el guardado
  /// silencioso si esta activado, y si no (o si falla), el comportamiento de
  /// siempre — dejar la URL para la app y abrirla.
  @MainActor
  private func finalizarUrlCompartida() async {
    guard let url = sharedWebUrl.last?.url else {
      dismissWithError(message: "No url found")
      return
    }

    let silencioso =
      UserDefaults(suiteName: hostAppGroupIdentifier)?.bool(forKey: "modoSilencioso") ?? false
    if silencioso, await guardarEnSilencio(url: url) {
      extensionContext!.completeRequest(returningItems: [], completionHandler: nil)
      return
    }

    let userDefaults = UserDefaults(suiteName: hostAppGroupIdentifier)
    userDefaults?.set(toData(data: sharedWebUrl), forKey: sharedKey)
    userDefaults?.synchronize()
    redirectToHostApp(type: .weburl)
  }

  // MARK: - Guardado silencioso

  private func guardarEnSilencio(url: String) async -> Bool {
    guard let tokenRefresco = leerTokenRefresco() else {
      NSLog("[guardado-silencioso] sin sesion guardada")
      return false
    }
    guard let renovacion = await renovarSesion(tokenRefresco: tokenRefresco) else {
      NSLog("[guardado-silencioso] no se pudo renovar la sesion")
      return false
    }
    // Rotacion: igual que aplicarTokens() en sesion.ts, hay que reescribir el
    // token de refresco nuevo o la siguiente renovacion (de la app o de aqui) falla.
    guardarTokenRefresco(renovacion.tokenRefresco)
    guard await guardarElemento(url: url, tokenAcceso: renovacion.tokenAcceso) else {
      NSLog("[guardado-silencioso] no se pudo guardar el elemento")
      return false
    }
    UIAccessibility.post(notification: .announcement, argument: "Enlace guardado")
    return true
  }

  private func consultaKeychain() -> [String: Any] {
    // Sin kSecAttrAccessGroup a proposito: al ser el unico grupo de Keychain
    // propio tanto de la app como de esta extension (misma entitlement en
    // ambos targets), iOS lo usa como grupo por defecto en los dos sitios sin
    // tener que conocer el Team ID en ningun lado.
    [
      kSecClass as String: kSecClassGenericPassword,
      kSecAttrService as String: "app:no-auth",
      kSecAttrGeneric as String: Data(claveTokenRefresco.utf8),
      kSecAttrAccount as String: Data(claveTokenRefresco.utf8),
    ]
  }

  private func leerTokenRefresco() -> String? {
    var consulta = consultaKeychain()
    consulta[kSecMatchLimit as String] = kSecMatchLimitOne
    consulta[kSecReturnData as String] = true
    var resultado: CFTypeRef?
    let estado = SecItemCopyMatching(consulta as CFDictionary, &resultado)
    guard estado == errSecSuccess, let datos = resultado as? Data else { return nil }
    return String(data: datos, encoding: .utf8)
  }

  private func guardarTokenRefresco(_ valor: String) {
    let actualizacion: [String: Any] = [kSecValueData as String: Data(valor.utf8)]
    SecItemUpdate(consultaKeychain() as CFDictionary, actualizacion as CFDictionary)
  }

  private struct Renovacion {
    let tokenAcceso: String
    let tokenRefresco: String
  }

  private func renovarSesion(tokenRefresco: String) async -> Renovacion? {
    guard
      let respuesta = await peticionJson(
        ruta: "/auth/renovar", cuerpo: ["tokenRefresco": tokenRefresco], tokenAcceso: nil)
    else { return nil }
    guard let nuevoAcceso = respuesta["tokenAcceso"] as? String,
      let nuevoRefresco = respuesta["tokenRefresco"] as? String
    else { return nil }
    return Renovacion(tokenAcceso: nuevoAcceso, tokenRefresco: nuevoRefresco)
  }

  private func guardarElemento(url: String, tokenAcceso: String) async -> Bool {
    let ahora = Int64(Date().timeIntervalSince1970 * 1000)
    let elemento: [String: Any] = [
      "id": UUID().uuidString,
      "url": url,
      "titulo": NSNull(),
      "descripcion": NSNull(),
      "imagenUrl": NSNull(),
      "tipo": "enlace",
      "etiquetas": [],
      "creadoEn": ahora,
      "actualizadoEn": ahora,
      "borrado": false,
    ]
    let respuesta = await peticionJson(
      ruta: "/sincronizar", cuerpo: ["elementos": [elemento]], tokenAcceso: tokenAcceso)
    return respuesta != nil
  }

  private func peticionJson(ruta: String, cuerpo: [String: Any], tokenAcceso: String?) async
    -> [String: Any]?
  {
    guard let url = URL(string: apiBaseUrl + ruta) else { return nil }
    var solicitud = URLRequest(url: url)
    solicitud.httpMethod = "POST"
    solicitud.setValue("application/json", forHTTPHeaderField: "Content-Type")
    if let tokenAcceso {
      solicitud.setValue("Bearer \(tokenAcceso)", forHTTPHeaderField: "Authorization")
    }
    // Corto a proposito: una extension tiene poco tiempo de vida, mejor caer
    // pronto al modo "abrir la app" que dejar que el sistema mate la extension
    // a medio guardar (justo el bug de "no pasa nada" que costo arreglar).
    solicitud.timeoutInterval = 8
    guard let datos = try? JSONSerialization.data(withJSONObject: cuerpo) else { return nil }
    solicitud.httpBody = datos

    do {
      let (respuestaDatos, respuesta) = try await URLSession.shared.data(for: solicitud)
      guard let http = respuesta as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
        return nil
      }
      return try? JSONSerialization.jsonObject(with: respuestaDatos) as? [String: Any]
    } catch {
      NSLog("[guardado-silencioso] fallo de red: \(error)")
      return nil
    }
  }

  // MARK: - Comportamiento existente (abrir la app)

  private func dismissWithError(message: String? = nil) {
    DispatchQueue.main.async {
      NSLog("[ERROR] Error loading application ! \(message!)")
      let alert = UIAlertController(
        title: "Error", message: "Error loading application: \(message!)", preferredStyle: .alert)

      let action = UIAlertAction(title: "OK", style: .cancel) { _ in
        self.dismiss(animated: true, completion: nil)
        self.extensionContext!.completeRequest(returningItems: [], completionHandler: nil)
      }

      alert.addAction(action)
      self.present(alert, animated: true, completion: nil)
    }
  }

  private func redirectToHostApp(type: RedirectType) {
    let nonce = UUID().uuidString
    let url = URL(string: "\(shareProtocol)://dataUrl=\(sharedKey)?nonce=\(nonce)#\(type)")!
    var responder = self as UIResponder?

    while responder != nil {
      if let application = responder as? UIApplication {
        if application.canOpenURL(url) {
          application.open(url)
        } else {
          NSLog("redirectToHostApp canOpenURL KO: \(shareProtocol)")
          self.dismissWithError(
            message: "Application not found, invalid url scheme \(shareProtocol)")
          return
        }
      }
      responder = responder!.next
    }
    extensionContext!.completeRequest(returningItems: [], completionHandler: nil)
  }

  enum RedirectType {
    case weburl
  }

  class WebUrl: Codable {
    var url: String
    var meta: String

    init(url: String, meta: String) {
      self.url = url
      self.meta = meta
    }
  }

  func toData(data: [WebUrl]) -> Data? {
    let encodedData = try? JSONEncoder().encode(data)
    return encodedData
  }
}
