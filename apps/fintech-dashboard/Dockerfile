# =============================================================================
# Stage 1: deps — instala apenas dependências de produção
# =============================================================================
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# =============================================================================
# Stage 2: builder — compila o Next.js com output standalone
# =============================================================================
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# standalone: gera bundle auto-suficiente sem node_modules completo
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# =============================================================================
# Stage 3: runner — imagem final mínima (~150MB vs ~1GB)
# =============================================================================
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3001

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copia apenas o output standalone e os assets estáticos
COPY --from=builder --chown=appuser:appgroup /app/.next/standalone ./
COPY --from=builder --chown=appuser:appgroup /app/.next/static ./.next/static

USER appuser
EXPOSE 3001

CMD ["node", "server.js"]
