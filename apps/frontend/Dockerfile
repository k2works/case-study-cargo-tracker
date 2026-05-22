# frontend コンテナイメージ（Heroku 用）
#
# マルチステージビルド:
#   1. builder: Node.js で React SPA をビルド
#   2. runtime: nginx で静的ファイルを配信し /api/ を gatewayms にプロキシ

# ===== Stage 1: builder =====
FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ===== Stage 2: runtime =====
FROM nginx:1.27-alpine

RUN apk add --no-cache gettext

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.heroku.conf /etc/nginx/templates/default.conf.template

EXPOSE 80

CMD ["/bin/sh", "-c", "envsubst '${PORT} ${GATEWAY_URL} ${GATEWAY_HOST}' < /etc/nginx/templates/default.conf.template > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'"]
