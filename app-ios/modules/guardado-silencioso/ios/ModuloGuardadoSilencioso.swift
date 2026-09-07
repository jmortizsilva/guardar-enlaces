import ExpoModulesCore

/**
 * Guarda el interruptor "modo silencioso" en el mismo App Group que ya
 * comparten la app y ShareExtension: la extension lo lee directamente con
 * UserDefaults(suiteName:), sin entitlements nuevas para esto. El grupo y
 * la clave deben coincidir exactamente con los que usa
 * plugins/plantillas/ShareViewController.swift.
 */
public class ModuloGuardadoSilencioso: Module {
  private let grupoApp = "group.com.jmortizsilva.guardarenlaces"
  private let clave = "modoSilencioso"

  public func definition() -> ModuleDefinition {
    Name("GuardadoSilencioso")

    Function("obtenerModoSilencioso") { () -> Bool in
      UserDefaults(suiteName: self.grupoApp)?.bool(forKey: self.clave) ?? false
    }

    Function("establecerModoSilencioso") { (valor: Bool) in
      UserDefaults(suiteName: self.grupoApp)?.set(valor, forKey: self.clave)
    }
  }
}
