// Configuracion leida del entorno (docker-compose la inyecta; en local, un .env con
// `node --env-file=.env` o variables exportadas).

export const config = {
  puerto: Number(process.env.PORT ?? 8081),
  rutaBd: process.env.DB_PATH ?? './datos/enlaces.sqlite',

  // URL publica HTTPS de este servidor (sin barra final), usada para construir las URIs de
  // callback de OAuth que se registran en Google/Apple. En local, apuntar a un tunel (Cloudflare
  // Tunnel/ngrok): ni Google ni Apple aceptan localhost como callback real.
  urlPublica: process.env.URL_PUBLICA ?? 'http://localhost:8081',

  // Firma los tokens de acceso (HMAC, sin estado). Sin definir, el servidor no arranca en
  // produccion (ver comprobacion en index.ts); en tests se usa un valor fijo.
  tokenSecreto: process.env.ENLACES_TOKEN_SECRET,

  google: {
    clientId: process.env.GOOGLE_CLIENT_ID,
    clientSecret: process.env.GOOGLE_CLIENT_SECRET,
  },
  apple: {
    // "Services ID" de Sign in with Apple (hace de client_id en el flujo OAuth).
    clientId: process.env.APPLE_CLIENT_ID,
    teamId: process.env.APPLE_TEAM_ID,
    keyId: process.env.APPLE_KEY_ID,
    // Clave privada ES256 descargada una vez del portal de Apple Developer, en formato PEM.
    privateKey: process.env.APPLE_PRIVATE_KEY,
  },

  // SOLO PARA DESARROLLO LOCAL: activa POST /auth/dev-login, que crea sesion sin pasar por
  // Google/Apple (util para construir y probar los clientes antes de tener credenciales OAuth
  // reales). Debe estar SIEMPRE apagado fuera de la maquina de desarrollo: sin esta variable a
  // 'true' explicitamente, la ruta ni se registra.
  permitirLoginDev: process.env.PERMITIR_LOGIN_DEV === 'true',
};
