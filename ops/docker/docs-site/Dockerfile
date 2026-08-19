# ドキュメントサイト配信イメージ（Heroku Container Runtime）
#
# ドキュメントポータルと生成物を 1 つの静的サイトとして配信する。
#   /            ドキュメントポータル（apps/www。手書きの入口）
#   /docs/       MkDocs（設計・要件・運用ドキュメント）
#   /jig/        JIG（コードから生成した設計ドキュメント。サービス単位）
#   /jig-erd/    jig-erd（実 DB スキーマから生成した ER 図。サービス単位）
#   /manual/     ユーザーマニュアル
#
# **成果物はビルド前にホスト側で生成しておくこと。**
# このイメージは配信するだけで、生成は行わない
# （JIG は JDK、jig-erd は Docker と Graphviz を必要とするため）。
#
# 本 take はマイクロサービス構成のため、JIG は 8 モジュール分、
# jig-erd は専用 DB を持つ 6 サービス分をそれぞれサービス名のディレクトリに配置する。
FROM nginx:1.29-alpine

# nginx の公式イメージは /etc/nginx/templates/*.template を
# envsubst で処理してから起動する。$PORT の展開にこれを使う。
COPY ops/docker/docs-site/default.conf.template /etc/nginx/templates/default.conf.template

# ドキュメントポータル（手書き。リポジトリで管理する）
COPY apps/www/ /usr/share/nginx/html/

# MkDocs サイト（生成物）
COPY site/ /usr/share/nginx/html/docs/

# JIG（サービスごと）
COPY apps/backend/gatewayms/build/jig/  /usr/share/nginx/html/jig/gatewayms/
COPY apps/backend/authms/build/jig/     /usr/share/nginx/html/jig/authms/
COPY apps/backend/bookingms/build/jig/  /usr/share/nginx/html/jig/bookingms/
COPY apps/backend/routingms/build/jig/  /usr/share/nginx/html/jig/routingms/
COPY apps/backend/trackingms/build/jig/ /usr/share/nginx/html/jig/trackingms/
COPY apps/backend/handlingms/build/jig/ /usr/share/nginx/html/jig/handlingms/
COPY apps/backend/billingms/build/jig/  /usr/share/nginx/html/jig/billingms/
COPY apps/backend/shared/build/jig/     /usr/share/nginx/html/jig/shared/

# JIG のサービス選択ページ。サービス単位に分かれるため一覧が必要。
COPY ops/docker/docs-site/jig-index.html /usr/share/nginx/html/jig/index.html

# jig-erd（専用 DB を持つサービスごと）
COPY apps/backend/authms/build/jig-erd/     /usr/share/nginx/html/jig-erd/authms/
COPY apps/backend/bookingms/build/jig-erd/  /usr/share/nginx/html/jig-erd/bookingms/
COPY apps/backend/routingms/build/jig-erd/  /usr/share/nginx/html/jig-erd/routingms/
COPY apps/backend/trackingms/build/jig-erd/ /usr/share/nginx/html/jig-erd/trackingms/
COPY apps/backend/handlingms/build/jig-erd/ /usr/share/nginx/html/jig-erd/handlingms/
COPY apps/backend/billingms/build/jig-erd/  /usr/share/nginx/html/jig-erd/billingms/

# ER 図の一覧ページ。SVG を直接置くだけでは一覧できないため用意する。
COPY ops/docker/docs-site/jig-erd-index.html /usr/share/nginx/html/jig-erd/index.html

# ユーザーマニュアル。未生成でもビルドを止めないため、
# ダミーを同時に指定して「少なくとも 1 つは存在する」状態にする。
COPY ops/docker/docs-site/manual-placeholder/ apps/manua[l]/ /usr/share/nginx/html/manual/

EXPOSE 8080
