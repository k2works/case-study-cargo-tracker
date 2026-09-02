FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# 動作確認用ログインの事前入力。既定は空（無効）。
# ローカル統合（kind）と開発環境（Heroku）のイメージをビルドするときだけ true を渡す。
# 既定を有効にすると、本番のイメージにも認証情報が入った画面が焼き込まれる。
ARG VITE_DEMO_LOGIN_ENABLED=""
ENV VITE_DEMO_LOGIN_ENABLED=$VITE_DEMO_LOGIN_ENABLED

# API の宛先。既定は空（同一オリジンの /api を叩く）。
# kind は Ingress が /api を Gateway に振り分けるため空でよいが、Heroku は
# フロントと Gateway が別オリジンのため、ここで Gateway の URL を焼き込む。
# 渡し忘れると画面は「サーバーに接続できませんでした」としか言えない。
ARG VITE_API_BASE_URL=""
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

# Heroku は $PORT を注入する。kind では既定の 80 で listen する。
ENV PORT=80
# envsubst が nginx 自身の変数（$uri 等）を空に潰さないよう、置換対象を PORT に限定する
ENV NGINX_ENVSUBST_FILTER="^PORT$"

EXPOSE 80
