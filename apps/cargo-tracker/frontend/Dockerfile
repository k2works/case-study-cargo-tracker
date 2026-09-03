# フロントエンドのイメージ。ビルドコンテキストは apps/cargo-tracker/frontend。
FROM node:22-alpine AS builder
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

# 動作確認用の利用者を事前入力するか（ADR-0004）。**既定は無効。**
# SPA の設定はビルド時に成果物へ焼き込まれ、実行時には取り消せないので、
# 渡し忘れたら安全側（無効）に倒れるようにしてある。有効にするのは
# ローカルの kind クラスタ向けイメージだけ（k8s:images）。
ARG VITE_DEMO_LOGIN_ENABLED=false
ENV VITE_DEMO_LOGIN_ENABLED=$VITE_DEMO_LOGIN_ENABLED

# build は tsc -b を通ってから vite build に入る（package.json）。
# 型が壊れたイメージを作らないための順序であり、-x で飛ばさない。
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=6 --start-period=10s \
  CMD ["sh", "-c", "wget -qO- http://localhost:8080/healthz || exit 1"]
