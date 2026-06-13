# 運用

開発環境構築・デプロイ・運用に関するドキュメントです。

## ドキュメント一覧

### 環境セットアップ

段階的構築フロー（アプリケーション開発環境 → 開発環境 → ステージング → 本番）に従って整備します。

| ドキュメント | 概要 | 状況 |
| :--- | :--- | :--- |
| [アプリケーション開発環境セットアップ手順書](./dev_app_instruction.md) | ローカルアプリケーション開発環境（JDK 25 / sbt / Play / Docker PostgreSQL）の構築手順 | 作成済 |
| [Playwright E2E テストセットアップ手順書](./dev_e2e_instruction.md) | Playwright による E2E テスト環境の構築手順 | 作成済 |
| [コントローラー E2E テストセットアップ手順書](./dev_e2e_api_instruction.md) | ScalaTestPlus-Play + Testcontainers によるコントローラー E2E テストの構築手順 | 作成済 |
| [開発環境セットアップ手順書](./dev_infra_instruction.md) | Heroku コンテナ + Heroku Postgres を使った開発環境の構築手順 | 作成済 |
| [Kubernetes 開発環境セットアップ手順書](./dev_k8s_instruction.md) | Kustomize + Docker Desktop による Kubernetes デプロイ・運用手順 | 作成済 |
| AWS ステージング環境セットアップ手順書 | ステージング環境の構築手順 | 未作成 |
| AWS プロダクション環境セットアップ手順書 | 本番環境の構築手順 | 未作成 |

### 運用コマンド

アプリケーション開発タスク（`ops/scripts/develop.js`）を Gulp で提供しています。

| コマンド | 説明 |
| :--- | :--- |
| `npm run start` | アプリ + ドキュメントサーバー一括起動（PostgreSQL / MkDocs / Play） |
| `npx gulp dev` | 開発サーバー起動（PostgreSQL 起動込み） |
| `npx gulp tdd` | TDD モード（`sbt ~test`） |
| `npx gulp dev:db:start` / `dev:db:stop` | PostgreSQL の起動 / 停止 |
| `npx gulp dev:test` / `dev:coverage` | テスト実行 / カバレッジレポート |
| `npx gulp dev:format` / `dev:check` | フォーマット適用 / 品質チェック（CI と同一） |
| `npx gulp dev:help` | 開発コマンドの一覧を表示 |

デプロイ（`deploy_*`）・プロビジョニング（`provision_*`）のスクリプトは各環境の構築時に追加します。

### インフラ

インフラ構成の一覧を追加予定です。運用要件は [設計 > 運用要件](../design/operation.md) を参照してください。

## 補足

- AWS 環境の手順書はテンプレート [template/AWSステージング環境セットアップ手順書.md](../template/AWSステージング環境セットアップ手順書.md)、[template/AWSプロダクション環境セットアップ手順書.md](../template/AWSプロダクション環境セットアップ手順書.md) を基に、環境構築フェーズで作成します。
