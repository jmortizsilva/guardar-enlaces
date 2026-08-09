# Etapa de compilacion: instala dependencias (incluidas las nativas de better-sqlite3) y
# compila el TypeScript a dist/. Luego poda las dependencias de desarrollo.
FROM node:20 AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build && npm prune --omit=dev

# Etapa de ejecucion: solo el codigo compilado y las dependencias de produccion. Se reutiliza
# node_modules de la etapa anterior (misma base Debian/glibc) para no recompilar la parte nativa.
FROM node:20-slim AS runtime
ENV NODE_ENV=production
WORKDIR /app
COPY package*.json ./
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/dist ./dist
# Datos persistentes (SQLite) en /app/datos: montar ahi un volumen. Puerto y fichero propios,
# distintos de servidor-notificaciones, para que ambos contenedores convivan en el mismo host.
ENV DB_PATH=/app/datos/enlaces.sqlite
ENV PORT=8081
EXPOSE 8081
CMD ["node", "dist/index.js"]
