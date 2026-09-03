# ドキュメントポータル。静的ファイルを配るだけで、ビルド工程を持たない。
#
# docs/ と manual/ は生成物であり、リポジトリには入っていない。
# `npx gulp portal:artifacts` で配置してからイメージを作る。
# 配置前に作ると、リンクだけあって中身が 404 のポータルができる。
FROM nginx:1.29-alpine

COPY default.conf /etc/nginx/conf.d/default.conf
COPY . /usr/share/nginx/html/docs-portal/

EXPOSE 80
