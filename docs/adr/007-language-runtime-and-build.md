---
type: ADR
title: "ADR-007: 言語・実行環境・ビルド（Scala 3 LTS・JDK 21・sbt、ZIO は採らない）"
description: "Scala 3.9 LTS + JDK 21（Temurin）+ sbt 1.12 を採り、実行時のエフェクトは ZIO でなく Future + Either とし、Protobuf は ScalaPB のみを使う。"
tags: [adr]
status: stable
generated: { by: claude-code/claude-sonnet-5-5, at: 2026-11-04T02:47:01Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-04T03:59:37Z }
---

# ADR-007: 言語・実行環境・ビルド（Scala 3 LTS・JDK 21・sbt、ZIO は採らない）

Scala 3.9 LTS + JDK 21（Temurin）+ sbt 1.12 を採り、実行時のエフェクトは ZIO でなく Future + Either とし、Protobuf は ScalaPB のみを使う。

日付: 2026-11-04

## ステータス

承認済み（2026-11-04、Bolt 1 の着手前ゲートとして）

> **承認の範囲：** Bolt 1 の着手に必要な範囲です。 以下は起草時の注記です。**元の注記：人の承認待ちです。** この ADR は設計書の「提案」（ADR 候補 TS-01・TS-02・TS-06・TS-12・BE-07）を決定記録の形にしたもので、AI が起草しました。リスク台帳 R-08（アーキテクチャ判断）・R-01（外部ライブラリ）・R-02（認証）に従い、**承認されるまで決定として扱わず、実装の根拠にしません**。承認されたら本文のステータスを「承認済み」にし、`apply-okf` の `verify` で文書を `stable` にします。決定期限：Bolt 1 の前

## コンテキスト

- リポジトリの Nix 開発シェルは `scala_3`・`sbt`・`metals`・`scala-cli` を提供する。参照実装は Scala 3.6.2・JDK 17・ZIO 2。
- LTS を優先し、サポート期限を把握する（技術スタック 6）。確認できた事実：Scala 3.9 LTS は 2029-09 以降まで、Temurin 21 は 2029-12-31 まで、Pekko 1.5.0（2026-03-23）。版番号は着手時に再確認する。
- ZIO は use-case 層で副作用を記述できるが、依存と学習の負担が増える。ドメインは Pekko・ZIO に依存しない（A3）。

## 決定

**Scala 3.9 LTS + JDK 21（Temurin）+ sbt 1.12.x** を採る。**ZIO を採らず、`Future` + `Either`**（業務エラーは sealed 型）で書く。Scala の構文は既定（インデント構文を許容）+ scalafmt で統一し、参照実装の `-no-indent`・`-rewrite`・`-experimental` は使わない。Protobuf は **sbt-protoc + ScalaPB のみ**（Pekko gRPC は採らない）。ベースイメージは `eclipse-temurin:21-jre` を**ダイジェストで固定**する。

### 変更箇所

- `PekkoInterop`（Future ↔ Task の橋渡し）を持ち込まない。
- 版は着手時に確認して固定する（Flyway・TanStack Query・Terraform など未確認のものは別途）。

### 代替案

- **Scala 3.3 LTS**：JDK 8 互換だが 2027-09 までと短い
- **JDK 25**：Temurin 25 は 2031-09-30 まで長いが、ライブラリの対応を確認する必要がある。まず 21
- **ZIO 2**：副作用の記述力が高いが、依存・学習負担と参照実装固有の橋渡しが増える

## 影響

### ポジティブ

- LTS で長く保守でき、ドメインを軽く保てる。

### ネガティブ

- エフェクトの合成を Future で手書きするため、複雑な非同期処理では冗長になる。
- JDK 21 は最新ではない（25 へ上げる計画を年次で見直す）。

## コンプライアンス

- A3：`domain` が Pekko・Slick・GraphQL・ZIO を import しない。
- サポート期限を年次で確認する（OPR-F40）。

## 更新（2026-11-04）：実行・開発の JDK を 25 にする

人の指示で、JDK を 21 から **25**（OpenJDK 25.0.2）に変更した。Scala 3.9.0・Pekko 1.5.0・Slick・Testcontainers・ScalaPB の組み合わせで、`sbt test` を全体で実行し、全モジュールが緑であることを確認した（JDK 25 の既定の `java` でそのまま動く。`JAVA_HOME` の指定は不要）。上の「JDK 21（Temurin）」は、起草時の判断として残す。

- 影響：イメージのベースは `eclipse-temurin:25-jre` を想定する（サポートは 2031-09-30 まで）。Scala のライブラリが出す `sun.misc.Unsafe` の警告は無害（将来の JDK で除去される際は、Scala 側の更新を待つ）。
- 残る確認：CI とコンテナのイメージを JDK 25 で組むときに、同じ検査（`sbt test`）を通す。

## 備考

- 著者: claude-code（AI による起草。承認者は未定）
- 出典: [技術スタック 2.1・5](../design/tech_stack.md)
- 関連コミット: なし（未コミット）
