# servidor-guardar-enlaces

Backend de "Guardar enlaces" (apps iOS y Windows con sincronización). Node +
Fastify + SQLite, proceso y contenedor **propios y separados** de
`servidor-notificaciones/`: no comparten base de datos ni proceso.

Ver `docs/CONTRATO-API.md` para el contrato de API completo (fuente de la
verdad para los dos clientes).

## Desarrollo

```
npm install
npm run dev          # servidor con recarga en caliente (tsx watch)
npm run verificar    # tsc --noEmit && eslint . && vitest
npm run crear-invitacion -- correo@ejemplo.com
```

## Variables de entorno

- `PORT` (por defecto 8081)
- `DB_PATH` (por defecto `./datos/enlaces.sqlite`)
- `ENLACES_TOKEN_SECRET` — secreto para firmar los tokens de acceso (HMAC)
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `APPLE_CLIENT_ID` (Services ID), `APPLE_TEAM_ID`, `APPLE_KEY_ID`, `APPLE_PRIVATE_KEY`
- `URL_PUBLICA` — URL HTTPS pública del servidor, para construir las URIs de
  callback de OAuth (`${URL_PUBLICA}/auth/callback/google`, etc.)
