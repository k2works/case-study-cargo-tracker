# フロントエンドのイメージ。ビルドコンテキストは apps/cargo-tracker/frontend。
FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# build は tsc -b を通ってから vite build に入る（package.json）。
# 型が壊れたイメージを作らないための順序であり、-x で飛ばさない。
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=6 --start-period=10s \
  CMD ["sh", "-c", "wget -qO- http://localhost:8080/healthz || exit 1"]
