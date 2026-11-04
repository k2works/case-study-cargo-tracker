---
type: ADR
title: "ADR-010: クラウド・実行基盤・IaC・デプロイ戦略（AWS・ECS Fargate・Terraform・ローリング更新）"
description: "AWS の ECS Fargate を実行基盤にし、Terraform で ECS・RDS・DynamoDB 等を IaC 化、ECR とローリング更新（読む側を先に配る）で配備する。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-sonnet-5-5, at: 2026-11-04T02:47:01Z }
---

# ADR-010: クラウド・実行基盤・IaC・デプロイ戦略（AWS・ECS Fargate・Terraform・ローリング更新）

AWS の ECS Fargate を実行基盤にし、Terraform で ECS・RDS・DynamoDB 等を IaC 化、ECR とローリング更新（読む側を先に配る）で配備する。

日付: 2026-11-04

## ステータス

提案中

> **人の承認待ちです。** この ADR は設計書の「提案」（ADR 候補 IF-01・IF-02・IF-03・IF-05・TS-09）を決定記録の形にしたもので、AI が起草しました。リスク台帳 R-08（アーキテクチャ判断）・R-01（外部ライブラリ）・R-02（認証）に従い、**承認されるまで決定として扱わず、実装の根拠にしません**。承認されたら本文のステータスを「承認済み」にし、`apply-okf` の `verify` で文書を `stable` にします。決定期限：ステージング構築の前（IF-01 は Bolt 1 の前）

## コンテキスト

- 実行物は command-api・query-api・event-processor・identity-api の 4 つ。デプロイ頻度が高く、Unit ごとの独立スケールは今は不要。
- テンプレートが AWS のステージング・本番の手順書を持つ。参照実装は DynamoDB・Lambda を使う。
- デプロイ設定・環境変数・本番環境は確認必須で、AI は接続情報の値を読まない（R-10）。
- イベントソーシングでは新旧の版が同時に動く時間にイベントの版が食い違いうる。
- Terraform はライセンスが BSL（OpenTofu が代替）。版は着手時に確認。

## 決定

**AWS を前提に、実行基盤は ECS Fargate**（EKS は採らない）、IaC は **Terraform**、レジストリは **ECR**、デプロイは**ローリング更新（読む側を先にデプロイ）**とする。ステージングと本番は**同じ IaC から作る**（差は変数だけ）。本番の変更は差分を提示して**人が承認**する。「開発」環境をクラウドに置くかは別に決める。ローカル環境（Docker Compose）は Bolt 1 の前に整える。

### 変更箇所

- 公開パスは別のターゲットグループ（ADR-006）。Secrets Manager に接続情報を置き、リポジトリとイメージに入れない。
- イメージはダイジェストで指定し、同じタグの作り直しでタスクが入れ替わらない事故を防ぐ。
- 運用タスクは `operating-script` で足す。

### 代替案

- **EKS（Kubernetes）**：実行物が 4 つには運用負荷が過剰
- **他社クラウド**：手順書のテンプレートが AWS。BE-03 で B を選べば再評価できる
- **CloudFormation／Pulumi／OpenTofu**：Terraform を推奨。ライセンスの懸念が強ければ OpenTofu（互換）
- **ブルーグリーン**：リソースが 2 倍になるため当面採らない

## 影響

### ポジティブ

- 運用負荷が小さく、IaC で環境の差を変数に絞れる。

### ネガティブ

- AWS 固有の部品に依存する（BE-03 が A のとき）。
- ローリング更新ではイベントの版の順序（読む側が先）を守る規律が要る。

## コンプライアンス

- I1：秘密情報の走査。I2：ステージングと本番が同じ IaC。I5：新しい版のイベントを旧版が読める。I8：実行中のイメージが意図した版。

## 備考

- 著者: claude-code（AI による起草。承認者は未定）
- 出典: [インフラ 3〜6・11](../design/architecture_infrastructure.md)
- 関連コミット: なし（未コミット）
