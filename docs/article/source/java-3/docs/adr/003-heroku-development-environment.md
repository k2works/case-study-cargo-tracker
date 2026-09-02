# ADR-003: 開発環境（結合テスト）に Heroku Container Registry / Runtime を採用

結合テスト用の開発環境は AWS ではなく Heroku Container Registry / Runtime で運用し、各サービスを個別の Heroku アプリとしてデプロイする。

日付: 2026-08-19

## ステータス

承認済み

## コンテキスト

- ローカル（kind）と本番（AWS ECS）の間に、常時参照できる結合テスト環境が必要
- AWS ECS + RDS + Amazon MQ を開発環境にも用意するとコストが高い（本番相当で月額 $1,000 超）
- take-3 で Heroku Container Registry による同一構成（8 アプリ + CloudAMQP）の運用実績と手順書がある

## 決定

- 開発環境は **Heroku Container Registry / Runtime**（container stack）で運用する
- 各マイクロサービスとフロントエンドを個別の Heroku アプリ（`{prefix}-{service}` 命名）としてデプロイする
- DB は **H2（インメモリ）**、メッセージブローカーは **CloudAMQP**（Heroku アドオン、AMQPS/TLS）とする
- バックエンドは `SPRING_PROFILES_ACTIVE=product` で動作させ、ポートは Heroku 注入の `$PORT` を使用する

### 代替案

- **AWS ECS を開発環境にも使用**: 本番と同一構成で検証精度は高いが、コストが結合テスト用途に見合わない。ステージングで本番同等の検証を行うため二重投資になる
- **開発環境を持たない（ローカル + ステージングのみ）**: 常時稼働の URL がなく、フロントエンド・外部関係者との結合確認がしづらい
- **Heroku buildpack デプロイ**: Docker イメージの明示的管理ができない。ローカル・本番とイメージビルドの経路を揃えるため container stack を採用する

## 影響

### ポジティブ

- 低コストで常時稼働の結合テスト環境を維持できる（低コスト dyno + CloudAMQP 無料プラン）
- イメージのビルド・push・release が Heroku CLI で完結し、CI からも自動化しやすい
- サービス個別デプロイのため、マイクロサービスの独立デプロイを開発環境でも実践できる

### ネガティブ

- H2 インメモリ DB のため再起動でデータが消える。PostgreSQL 固有機能（方言）の検証はできない
- 本番（PostgreSQL / Amazon MQ）との構成差分があり、方言差・接続方式差はステージングで検証する必要がある
- Heroku Container Runtime は x86_64 のみのため、Apple Silicon では `linux/amd64` ビルドが必要

## コンプライアンス

- セットアップ手順・Config Vars・デプロイコマンドは `docs/operation/開発環境セットアップ手順書.md` に記録し、環境操作は手順書のタスクのみで行う
- 全マイグレーション SQL は方言差検出のため H2 / PostgreSQL の両方で実行できることを CI で確認する

## 備考

- 著者: k2works
- 参考: take-3 `docs/operation/開発環境セットアップ手順書.md`
- 関連ドキュメント: [インフラストラクチャアーキテクチャ設計](../design/architecture_infrastructure.md)
- 関連 ADR: ADR-001、ADR-002
