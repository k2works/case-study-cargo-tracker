# ドキュメントサイト配信イメージ（Heroku Container Runtime）
#
# ドキュメントポータルと 4 種類の成果物を 1 つの静的サイトとして配信する。
#   /          ドキュメントポータル（apps/www。手書きの入口）
#   /docs/     MkDocs（設計・要件・運用ドキュメント）
#   /jig/      JIG（コードから生成した設計ドキュメント）
#   /jig-erd/  jig-erd（実 DB スキーマから生成した ER 図）
#   /manual/   ユーザーマニュアル
#
# **成果物はビルド前にホスト側で生成しておくこと。**
# このイメージは配信するだけで、生成は行わない
# （JIG は JDK、jig-erd は Docker と Graphviz を必要とするため）。
FROM nginx:1.29-alpine

# nginx の公式イメージは /etc/nginx/templates/*.template を
# envsubst で処理してから起動する。$PORT の展開にこれを使う。
COPY ops/docker/docs-site/default.conf.template /etc/nginx/templates/default.conf.template

# ドキュメントポータル（手書き。リポジトリで管理する）
COPY apps/www/ /usr/share/nginx/html/

# MkDocs サイト（生成物）
COPY site/ /usr/share/nginx/html/docs/

# JIG（必須）
COPY apps/cargo-tracker/build/jig/ /usr/share/nginx/html/jig/

# jig-erd（必須）
COPY apps/cargo-tracker/build/jig-erd/ /usr/share/nginx/html/jig-erd/

# ER 図の一覧ページ。SVG を直接置くだけでは一覧できないため用意する。
COPY ops/docker/docs-site/jig-erd-index.html /usr/share/nginx/html/jig-erd/index.html

# ユーザーマニュアル。未生成でもビルドを止めないため、
# ダミーを同時に指定して「少なくとも 1 つは存在する」状態にする。
COPY ops/docker/docs-site/manual-placeholder/ apps/manua[l]/ /usr/share/nginx/html/manual/

EXPOSE 8080
