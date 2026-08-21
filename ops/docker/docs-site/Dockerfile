# ドキュメントサイト配信イメージ（Heroku Container Runtime）
#
# ドキュメントポータルと生成物を 1 つの静的サイトとして配信する。
#   /            ドキュメントポータル（apps/www。手書きの入口）
#   /docs/       MkDocs（設計・要件・運用ドキュメント）
#   /jig/        JIG（コードから生成した設計ドキュメント。サービス単位）
#   /jig-erd/    jig-erd（実 DB スキーマから生成した ER 図。サービス単位）
#   /manual/     ユーザーマニュアル（apps/www/manual。manual:build が生成し、
#                ポータルごと配信される。専用の COPY は要らない）
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

# ドキュメントポータル（手書き。リポジトリで管理する）と、
# その配下に生成したユーザーマニュアル（apps/www/manual。追跡しない）。
# manual:build を先に走らせておくこと（deploy:docs:artifacts が実施する）。
COPY apps/www/ /usr/share/nginx/html/

# MkDocs サイトは `mkdocs:build` が apps/www/docs に同期する。
# 上の `COPY apps/www/` でポータルと同じ単位として配信する。

# JIG と jig-erd も生成タスクが apps/www/jig / apps/www/jig-erd に同期する。
# 上の `COPY apps/www/` でポータルと同じ単位として配信する。

# ユーザーマニュアルは `manual:build` が docs/manual から HTML へ変換し、
# apps/www/manual に出力する。上の `COPY apps/www/` でそのまま配信されるため、
# ここでの追加の COPY は要らない。
#
# 以前はダミーを別途 COPY していたが、`apps/manual/` という存在しない場所を
# 参照していたため、書いたマニュアルが配信されず「まだ作成していません」が
# 出続けた。置き場所と配信先を離さないこと。

EXPOSE 8080
