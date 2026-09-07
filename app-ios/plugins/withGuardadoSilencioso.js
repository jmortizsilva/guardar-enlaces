const fs = require('fs');
const path = require('path');
const { withEntitlementsPlist, withXcodeProject } = require('@expo/config-plugins');
const plist = require('@expo/plist').default;

// Debe coincidir con "group.<bundleIdentifier>" que ya usa expo-share-intent
// (app.json, parametro iosAppGroupIdentifier del plugin).
const GRUPO_APP = 'group.com.jmortizsilva.guardarenlaces';
// Nombre por defecto del target que genera expo-share-intent (no se ha
// puesto iosShareExtensionName en app.json, asi que es el que usa la
// libreria de fabrica: ver expo-share-intent/plugin/build/ios/constants.js).
const NOMBRE_EXTENSION = 'ShareExtension';

/**
 * Añade Keychain Sharing (un unico grupo, el mismo en los dos targets) para
 * que ShareExtension pueda leer/rotar el token de refresco que guarda la app
 * via expo-secure-store. Al ser el UNICO grupo de Keychain de cada target,
 * iOS lo usa como grupo por defecto sin tener que conocer el Team ID en
 * ningun sitio (ni aqui en el plugin, ni en Swift, ni en credenciales.ts).
 */
function withEntitlementsAppPrincipal(config) {
  return withEntitlementsPlist(config, (config) => {
    const existente = config.modResults['keychain-access-groups'];
    config.modResults['keychain-access-groups'] = [
      `$(AppIdentifierPrefix)${GRUPO_APP}`,
      ...(Array.isArray(existente) ? existente : []),
    ];
    return config;
  });
}

/**
 * El fichero ShareExtension.entitlements y ShareViewController.swift los
 * escribe expo-share-intent dentro de un mod "xcodeproj" (ver su
 * withIosShareExtensionXcodeTarget.js) — por eso este plugin usa tambien
 * withXcodeProject, para quedar despues del suyo en la misma categoria.
 *
 * OJO al orden en app.json: los mods del mismo tipo se ejecutan en orden
 * INVERSO al de la lista "plugins" (cada withMod nuevo envuelve al
 * anterior y llama a su propia accion ANTES de delegar en el `nextMod` —
 * ver @expo/config-plugins/build/plugins/withMod.js, la funcion `action`
 * de withMod). Comprobado en la practica: con este plugin DESPUES de
 * "expo-share-intent" fallaba con ENOENT porque corria ANTES que ellos.
 * Por eso "./plugins/withGuardadoSilencioso" va ANTES que "expo-share-intent"
 * en la lista de app.json, aunque logicamente "dependa" de el.
 */
function withExtensionSobrescrita(config) {
  return withXcodeProject(config, async (config) => {
    const carpetaExtension = path.join(config.modRequest.platformProjectRoot, NOMBRE_EXTENSION);

    // 1. Keychain Sharing en el target de la extension, mismo grupo unico.
    const rutaEntitlements = path.join(carpetaExtension, `${NOMBRE_EXTENSION}.entitlements`);
    const entitlements = plist.parse(fs.readFileSync(rutaEntitlements, 'utf8'));
    const existente = entitlements['keychain-access-groups'];
    entitlements['keychain-access-groups'] = [
      `$(AppIdentifierPrefix)${GRUPO_APP}`,
      ...(Array.isArray(existente) ? existente : []),
    ];
    fs.writeFileSync(rutaEntitlements, plist.build(entitlements));

    // 2. Sustituye el ShareViewController.swift generado por el propio de
    //    este proyecto (guardado silencioso + el mismo comportamiento de
    //    siempre como respaldo). Ver plugins/plantillas/ShareViewController.swift.
    const apiUrl = process.env.EXPO_PUBLIC_API_URL;
    if (!apiUrl) {
      throw new Error(
        '[withGuardadoSilencioso] Falta EXPO_PUBLIC_API_URL en el entorno de compilación ' +
          '(en eas.json, perfil de build) — ShareViewController.swift lo necesita para el ' +
          'guardado silencioso.',
      );
    }
    const scheme = Array.isArray(config.scheme) ? config.scheme[0] : config.scheme;
    if (!scheme) {
      throw new Error('[withGuardadoSilencioso] Falta "scheme" en app.json.');
    }
    const rutaPlantilla = path.join(__dirname, 'plantillas', 'ShareViewController.swift');
    const contenido = fs
      .readFileSync(rutaPlantilla, 'utf8')
      .replaceAll('<SCHEME>', scheme)
      .replaceAll('<GROUPIDENTIFIER>', GRUPO_APP)
      .replaceAll('<HIDEVIEW>', 'true')
      .replaceAll('<APIURL>', apiUrl);
    const rutaSwift = path.join(carpetaExtension, 'ShareViewController.swift');
    fs.writeFileSync(rutaSwift, contenido);

    return config;
  });
}

module.exports = function withGuardadoSilencioso(config) {
  config = withEntitlementsAppPrincipal(config);
  config = withExtensionSobrescrita(config);
  return config;
};
