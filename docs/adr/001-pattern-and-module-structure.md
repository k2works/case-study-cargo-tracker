---
type: ADR
title: "ADR-001: バックエンドのパターン・モジュール構成・Unit 間連携"
description: "U-02〜U-05 をイベント履歴式ドメインモデル + CQRS、U-01 をレイヤード 3 層とし、Unit を最上位モジュールにして契約（contract）で連携する。"
tags: [adr]
status: stable
generated: { by: claude-code/claude-sonnet-5-5, at: 2026-11-04T02:47:01Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-04T03:59:36Z }
---

# ADR-001: バックエンドのパターン・モジュール構成・Unit 間連携

U-02〜U-05 をイベント履歴式ドメインモデル + CQRS、U-01 をレイヤード 3 層とし、Unit を最上位モジュールにして契約（contract）で連携する。

日付: 2026-11-04

## ステータス

承認済み（2026-11-04、Bolt 1 の着手前ゲートとして）

> **承認の範囲：** Bolt 1 の着手に必要な範囲です。 以下は起草時の注記です。**元の注記：人の承認待ちです。** この ADR は設計書の「提案」（ADR 候補 BE-01・BE-02・BE-05）を決定記録の形にしたもので、AI が起草しました。リスク台帳 R-08（アーキテクチャ判断）・R-01（外部ライブラリ）・R-02（認証）に従い、**承認されるまで決定として扱わず、実装の根拠にしません**。承認されたら本文のステータスを「承認済み」にし、`apply-okf` の `verify` で文書を `stable` にします。決定期限：Bolt 1 の前

## コンテキスト

- 状態遷移（貨物予約 8 状態・貨物状態 9 状態）と履歴（例外・通関・キャンセル）が料金調整・監査の根拠になる。
- 5 つの Unit が独立して開発・デプロイできることが要る（Unit 定義）。パッケージの規約だけでは Unit 間の依存を強制できない。
- 参照実装（pekko-cqrs-es-example）は集約が 1 種類で、Unit 間連携・認証・外部ポートを持たない。そのまま拡大できない。
- R-08：リアルタイム反映の方式やコンテキスト分割を AI が既成事実化してはならない。

## 決定

**U-02〜U-05 はイベント履歴式ドメインモデル + CQRS、U-01 はレイヤード 3 層 + トランザクションスクリプト**とする。モジュールは **Unit を最上位**にし、各 Unit を `domain`／`use-case`／`contract`／`command-adapter`／`query-adapter`／`migration` に分ける。Unit 間は **`contract`（公開イベント・受け口のポート・読み取り専用の問い合わせ）だけ**を介して連携し、同期の問い合わせは読み取りモデルに対してだけ行う。連携は 17 本（バックエンド 7.2）。

### 変更箇所

- `modules/{booking,routing,tracking,billing,identity}` と `shared-kernel`・`shared-command`、`apps/{command-api,query-api,event-processor,identity-api}` の構成を採る。
- `use-case` が出力ポートを定義し、Pekko のアクターは `command-adapter` が実装する（参照実装と違い、use-case は `ActorRef` を持たない）。
- `contract` は `shared-kernel` にしか依存しない葉とし、Unit 間で双方向に参照してもモジュールは循環しない。
- `query-adapter` は自 Unit の `domain`（述語・ドメインサービス）に依存してよい（`available_commands` の計算、経路算出）。他 Unit の `domain` は参照しない。

### 代替案

- **U-03 を「ドメインモデル + ポートとアダプター」にして経路確定を Outbox で伝える**：金額も監査要求も無く根拠は最も弱いが、Unit 間連携を 1 種類の仕組みに揃えたほうが AI が規約どおりに生成しやすく検査も 1 組で済むため却下（人が覆してよい。BE-01 の確認ポイント）
- **参照実装どおり command／query を最上位にする**：`dependsOn` で Unit 間の依存を強制できないため却下
- **Unit 間を同期 API の呼び合いにする**：結合が強く、独立デプロイと障害の分離を損なうため却下

## 影響

### ポジティブ

- 履歴が残り、状態遷移を集約の中に閉じ込められる。ドメインを Pekko なしで検査できる。
- Unit 間の依存をコンパイル時に強制でき、検査（A1〜A3）に落とせる。

### ネガティブ

- イベントソーシングの学習コストと運用の複雑さ。規模（年 12,000 件の仮置き）に対して重い可能性がある。
- Unit 定義の依存 DAG に無い逆向きの連携（#10〜#14）が生じる。DAG の説明の更新が要る。
- 荷主・認証は CRUD の例外になり、永続化方式が 2 種類になる。

## コンプライアンス

- A1：別 Unit の内側を参照しない（sbt の `dependsOn`）。A2：依存が 7.2 の連携一覧と一致し循環しない。A3：`domain` が Pekko・Slick・GraphQL・ZIO を import しない（ArchUnit）。
- A9：連携の数（14）だけ往復テストを置く（テスト戦略 第 8 章）。

## 備考

- 著者: claude-code（AI による起草。承認者は未定）
- 出典: [バックエンド 3・5・7](../design/architecture_backend.md)、[ドメインモデル 2](../design/domain-model.md)
- 関連コミット: なし（未コミット）
