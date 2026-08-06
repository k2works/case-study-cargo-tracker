# 運用

開発環境構築・デプロイ・運用に関するドキュメントです。

## ドキュメント一覧

### 環境セットアップ

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [アプリケーション開発環境セットアップ手順書](アプリケーション開発環境セットアップ手順書.md) | ローカルアプリケーション開発環境の構築手順（Java 25 / Gradle / Docker / Testcontainers） | 作成済（環境構築は未完了） |
| [開発環境セットアップ手順書](開発環境セットアップ手順書.md) | Heroku Container Registry へのデプロイ手順（dev プロファイル / H2） | 作成済・デプロイ済 |
| AWS ステージング環境セットアップ手順書 | ステージング環境の構築手順 | 未作成 |
| AWS プロダクション環境セットアップ手順書 | 本番環境の構築手順 | 未作成 |

### 運用コマンド

Gulp タスクとして `ops/scripts/` に実装しています。各カテゴリのヘルプは `npx gulp <カテゴリ>:help` で確認できます。

| カテゴリ | スクリプト | 主なタスク |
| :--- | :--- | :--- |
| アプリケーション | `ops/scripts/app.js` | `app:start` / `app:test` / `app:tdd` / `app:check` / `app:jig` / `app:jig-erd`（`app:help`） |
| 開発環境デプロイ | `ops/scripts/deploy.js` | `deploy:dev`（アプリ）/ `deploy:docs`（ドキュメントサイト）（`deploy:dev:help`） |
| ユーザーマニュアル | `ops/scripts/manual.js` | `manual:build` |
| ドキュメント | `ops/scripts/mkdocs.js` | `mkdocs:serve` / `mkdocs:build` / `mkdocs:stop` |
| コード品質 | `ops/scripts/sonar_local.js` | `sonar-local:setup` / `sonar-local:check`（`sonar-local:help`） |
| ジャーナル | `ops/scripts/journal.js` | `journal:generate` |
| シークレット | `ops/scripts/vault.js` | `vault:encrypt` / `vault:decrypt` / `vault:view` |
| SSH | `ops/scripts/ssh.js` | SSH 接続・トンネル |
| リリース | `ops/scripts/release.js` | `release:patch` / `release:minor` / `release:major` |

#### ユーザーマニュアルの生成（`manual:build`）

`docs/manual/` の Markdown を `apps/manual/` の静的 HTML サイトに変換します。

```bash
npx gulp manual:build
# または
npm run manual:build
```

| 機能 | 内容 |
| :--- | :--- |
| PlantUML | ` ```plantuml ` フェンスを PlantUML サーバの SVG 画像に置換する |
| 相互リンク | 同一フォルダの `.md` リンクを `.html` に書き換える（`../` の外部相対リンクは対象外） |
| 見出しアンカー | 本文中の `#43-...` 形式のリンクと一致する `id` を見出しに付与する |
| アセット | `docs/manual/assets/` の画像（png / jpg / gif / svg）をコピーする |

- 出力先の `apps/manual/` は**毎回クリーンして再生成**します。Git 管理外です
- 設定は `.env` の `MANUAL_TITLE` / `MANUAL_COPYRIGHT` / `MANUAL_PORTAL_URL` / `PLANTUML_SERVER_URL` で上書きできます
- **`docs/manual/` はまだ作成していません。** マニュアルの執筆は UI 実装後（`creating-manual` スキル）に行います。ソースが無い状態で実行すると、その旨のメッセージで停止します

### 公開環境

| 環境 | URL | 内容 |
| :--- | :--- | :--- |
| アプリケーション（開発環境） | `https://cargo-tracker-take-6-b878c5d99300.herokuapp.com/` | Spring Boot（dev プロファイル / H2） |
| **ドキュメントポータル** | `https://cargo-tracker-take-6-docs-340bd48cb9d2.herokuapp.com/` | 入口。以下へ移動できる |
| ├ ドキュメント | `/docs/` | MkDocs（戦略・要件・設計・開発・運用・レビュー・ADR） |
| ├ JIG | `/jig/` | コードから生成した設計ドキュメント |
| ├ ER 図 | `/jig-erd/` | 実スキーマから生成した ER 図 |
| └ ユーザーマニュアル | `/manual/` | UI 実装後に作成 |

### インフラ

インフラ構成の一覧を追加予定です。

## 環境構築の進捗

環境は「アプリケーション開発環境 → 開発環境 → ステージング → 本番」の順に段階的に構築します。**前段が構築済みであることを確認してから次に進みます。**

| 段階 | 環境 | 手順書 | 構築状況 |
| :--- | :--- | :--- | :--- |
| 1 | アプリケーション開発環境 | 作成済 | **完了** |
| 2 | 開発環境（Heroku Container Registry） | 作成済 | **完了**（`cargo-tracker-take-6` にデプロイ済み） |
| 3 | AWS ステージング環境 | 未作成 | 未着手 |
| 4 | AWS 本番環境 | 未作成 | 未着手 |

段階 1 の詳細な整備状況は [アプリケーション開発環境セットアップ手順書 > 現在の整備状況](アプリケーション開発環境セットアップ手順書.md#現在の整備状況) を参照してください。

## 補足

- 手順書は「手順」であり「実施状況」ではありません。各手順書の末尾に整備状況を記録します。
- テンプレートは [template/アプリケーション開発環境セットアップ手順書.md](../template/アプリケーション開発環境セットアップ手順書.md)、[template/開発環境セットアップ手順書.md](../template/開発環境セットアップ手順書.md)、[template/AWSステージング環境セットアップ手順書.md](../template/AWSステージング環境セットアップ手順書.md)、[template/AWSプロダクション環境セットアップ手順書.md](../template/AWSプロダクション環境セットアップ手順書.md) を利用できます。
