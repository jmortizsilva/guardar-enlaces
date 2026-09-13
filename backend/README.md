# Guárdalo — backend

Backend de "Guárdalo" (apps iOS y Windows con sincronización). Node +
Fastify + SQLite, proceso y contenedor **propios y separados** de
`servidor-notificaciones/`: no comparten base de datos ni proceso.

Ver `docs/CONTRATO-API.md` para el contrato de API completo (fuente de la
verdad para los dos clientes).

## Desarrollo

```
npm install
npm run dev          # servidor con recarga en caliente (tsx watch)
npm run verificar    # tsc --noEmit && eslint . && vitest
```

El alta es abierta: entrar con Google o Apple la primera vez crea la cuenta.
No hay lista de invitados ni ningún paso previo que dar en el servidor.

Para desplegar en el VPS (`api.jmortiz.es`), ver
[`docs/DESPLIEGUE.md`](docs/DESPLIEGUE.md).

## Variables de entorno

- `PORT` (por defecto 8081)
- `DB_PATH` (por defecto `./datos/enlaces.sqlite`)
- `ENLACES_TOKEN_SECRET` — secreto para firmar los tokens de acceso (HMAC)
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `APPLE_CLIENT_ID` (Services ID), `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`
- `URL_PUBLICA` — URL HTTPS pública del servidor, para construir las URIs de
  callback de OAuth (`${URL_PUBLICA}/auth/callback/google`, etc.)
- `PERMITIR_LOGIN_DEV` — **solo desarrollo**, nunca en un despliegue real.
  A `true` activa `POST /auth/dev-login {email}`, que crea la cuenta y da
  sesión sin pasar por Google/Apple. Pensado para construir y probar los
  clientes sin credenciales OAuth reales.
