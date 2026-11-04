---
type: ADR
title: "ADR-002: イベントストアと配信方式"
description: "ウォーキングスケルトンでは参照実装どおり（DynamoDB Journal + Streams + Lambda）で始め、最初の Unit 間の反応（Bolt 4）の前に PoC で A′／B へ移るかを決める。"
tags: [adr]
status: draft
generated: { by: claude-code/claude-sonnet-5-5, at: 2026-11-04T02:47:01Z }
---

# ADR-002: イベントストアと配信方式

ウォーキングスケルトンでは参照実装どおり（DynamoDB Journal + Streams + Lambda）で始め、最初の Unit 間の反応（Bolt 4）の前に PoC で A′／B へ移るかを決める。

日付: 2026-11-04

## ステータス

提案中

> **人の承認待ちです。** この ADR は設計書の「提案」（ADR 候補 BE-03・BE-04・IF-04・NF-02）を決定記録の形にしたもので、AI が起草しました。リスク台帳 R-08（アーキテクチャ判断）・R-01（外部ライブラリ）・R-02（認証）に従い、**承認されるまで決定として扱わず、実装の根拠にしません**。承認されたら本文のステータスを「承認済み」にし、`apply-okf` の `verify` で文書を `stable` にします。決定期限：一次：Bolt 1 の前。二次：Bolt 4 の前

## コンテキスト

- イベント配信の購読者が、参照実装の 1 つ（読み取りモデル更新）から、Unit ごとの読み取りモデル更新・Unit 間の反応・通知へ増える。DynamoDB Streams は同一シャードの購読者数に目安がある。
- **最初の Unit 間の反応は Bolt 4**（`RouteDesignRequested` を U-03 が購読）。Bolt 1〜3 は読み取りモデル更新だけ。
- 反映遅延の目標（p95 5 s／p99 15 s、仮置き。NFR-P10）が方式の選択基準になる。
- 参照実装の永続化ライブラリ（`pekko-persistence-dynamodb-journal-v2`・`pekko-persistence-effector`）は個人製で、1.0 未満を含む。導入は外部ライブラリの導入にあたる（R-01）。
- Lambda の fat jar は実 AWS の zip 上限（50 MB／250 MB）を超える可能性がある（要確認）。

## 決定

**方式 A（参照実装どおり：DynamoDB Journal + DynamoDB Streams + Lambda）で Bolt 1 を始める**。Bolt 1 で反映遅延・再配信での冪等性・LocalStack での再現のしやすさを実測し、**Bolt 4 の前に購読者追加の PoC を行って A′（Streams → 振り分け → 購読者ごとの SQS FIFO）または B（PostgreSQL Journal + Pekko Projection）へ移るかを別の ADR で決める**。event-processor は Lambda（コンテナイメージ）で始め、同じ PoC で ECS 常駐へ移すかを見直す。永続化ライブラリは、まず参照実装のものを PoC で使い、**個人製の依存はラッパーで隔離**する。

### 変更箇所

- 読み取りモデル更新とイベント配信を `event-processor` に置き、イベントは再配信される前提で冪等に更新する（イベント ID または集約の版が「保存済み + 1」のときだけ適用。バックエンド 6.2）。
- 適用に失敗したイベントはリトライ後に待避先へ回し、件数を監視する（NFR-A13）。
- Lambda はコンテナイメージで配布し、fat jar の `sbt-assembly` は採らない。

### 代替案

- **最初から B（PostgreSQL Journal + Pekko Projection）**：個人製の DynamoDB プラグイン依存が無くなり購読者を独立に増やせるが、参照実装から離れる範囲が広い。Bolt 1 の時点で比較材料が無いため、A で始めて実測する
- **A′ を最初から**：キューの追加と監視が要る。Bolt 4 の前の PoC で必要性を確かめてから
- **標準の `EventSourcedBehavior`（+ `pekko-persistence-jdbc`）**：BE-04 の代替。B を選ぶ場合の自然な形で、同じ PoC で比較する

## 影響

### ポジティブ

- 参照実装を土台に素早く始められ、ウォーキングスケルトンで AI の出力品質を確かめられる。
- 移行判断を実測（反映遅延・冪等性）に基づけられる。
- 個人製の依存をラッパーで隔離し、将来の差し替えを可能にする。

### ネガティブ

- A のまま進めると Bolt 4 以降に配信方式の差し替えコストが発生しうる。
- LocalStack・Lambda・DynamoDB の運用負荷。コンテナイメージ配布への変更が要る。
- NFR-A17（同一集約への同時書き込み）の PoC 結果次第で、別の ADR（BE-12 クラスターモード）が要る。

## コンプライアンス

- A6：同じイベントを 2 回・順序を入れ替えて流しても読み取りモデルが同じ（テスト戦略 6.4）。
- 反映遅延を指標として計測し、Bolt 1 の完了時に p95・p99 を記録する（NFR-P10）。
- Bolt 4 の前に、PoC の結果と移行判断を新しい ADR として残す。

## 備考

- 著者: claude-code（AI による起草。承認者は未定）
- 出典: [バックエンド 6.3](../design/architecture_backend.md)、[インフラ 4.4](../design/architecture_infrastructure.md)、[非機能要件 3.2](../design/non_functional.md)
- 関連コミット: なし（未コミット）
