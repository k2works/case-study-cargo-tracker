# エンタープライズ Java における実践的ドメイン駆動設計（draft-2）

## 概要

国際貨物輸送管理システム（Cargo Tracker）を題材に、**DDD の概念が Java と Spring のコードとしてどこに現れるか**を追うシリーズです。

扱うのは 1 つの実装 —— `docs/article/source/java-2/` に収録された Spring Boot 実装です。**この実装は書籍『Practical Domain-Driven Design in Enterprise Java』の構造を実際に適用したもの**であり、設計ドキュメントは「Practical DDD in Enterprise Java (Chapter 3) のパッケージ構造に準拠する」と明記し、ADR は同書の `bookingms` を参照実装として名指ししています。

**したがって本シリーズは書籍の要約ではなく、適用した結果の報告です。** 報告である以上、次の 2 つを同じ比重で扱います。

- **書籍の構成を写したまま使って壊れた箇所** —— 識別子の置き場を写したために、分割が狙った利得が最初の 1 回で失われた（第 2 章）
- **設計ドキュメントが実装から離れた箇所** —— 正典が規定する `interfaces/rest/` は全 BC に存在せず、ドメインモデル図は 6 か所ずれている（第 2 章・第 3 章）

## 章一覧

| 章 | タイトル | 扱うこと |
| :--- | :--- | :--- |
| [第 1 章](01-ddd-fundamentals.md) | ドメイン駆動設計 — 概念と、この実装での対応物 | DDD の語彙とパッケージ・型の対応表。**対応物が無いもの**（サガ・イベントストア・REST）の一覧 |
| [第 2 章](02-cargo-domain-model.md) | Cargo Tracker のドメインモデル | 集約・識別子・値オブジェクト・ドメインサービス。**境界を分けて払った代金** |
| [第 3 章](03-spring-modular-monolith.md) | Spring Platform 上のモジュラーモノリス | Spring 上の配置と、**境界を守っている検査**。結果整合の取りこぼしの扱い |

## 読む順序

**第 3 章から読んでも構いません。** 実装の現物を先に見たい場合はそちらが入口として適しています。本シリーズは実装を先に確定させてから概念の章を書いており、第 1 章は最後に書かれています。

| 目的 | 入口 |
| :--- | :--- |
| DDD の語彙と実装の対応を先に押さえたい | [第 1 章](01-ddd-fundamentals.md) |
| モデルの設計判断とその代償を見たい | [第 2 章](02-cargo-domain-model.md) |
| Spring 上の配置と検査の実物を見たい | [第 3 章](03-spring-modular-monolith.md) |

## 参照元

| 種別 | パス |
| :--- | :--- |
| 実装 | [`docs/article/source/java-2/apps/cargo-tracker/`](../../source/java-2/apps/cargo-tracker) |
| 設計ドキュメント | [`docs/article/source/java-2/docs/design/`](../../source/java-2/docs/design) |
| ADR（25 本） | [`docs/article/source/java-2/docs/adr/`](../../source/java-2/docs/adr) |

**記事中のコードはすべて上記の実ファイルから転記しています。** 本体を `{ ... }` で省略した引用はありません。設計ドキュメントからの引用と実コードからの引用は、記事中で区別して示しています。

## このシリーズが扱わないこと

| 対象外 | 理由 |
| :--- | :--- |
| DDD の用語解説・パターンカタログ | 既刊書籍が扱う。本シリーズは適用結果の側から書く |
| 20 イテレーションの時系列 | [実践 DDD in Spring Boot](../../practical-ddd-spring-boot/index.md) が扱う |
| XP プラクティスとモデルの関係 | [XP によるドメイン駆動設計の実践](../../xp-domain-driven-design/index.md) が扱う |
| 多言語比較 | [モノリスアーキテクチャ実装比較](../../monolith-architecture/index.md) が扱う |
| **Axon Framework / Event Sourcing** | **参照元実装に存在しない。** 構想で章を埋めることはしない |

## 現在の範囲

**本稿は第 3 章までです。** 実装アプローチの比較（EDA・CQRS/ES）は、対応する実装を参照元に収録できた時点で扱います。

| 章 | 状態 | 着手条件 |
| :--- | :--- | :--- |
| 第 4 章（EDA） | 保留 | メッセージングを使う実装の収録 |
| 第 5 章（CQRS / Event Sourcing） | 保留 | Event Sourcing 実装の収録 |
| 第 6 章（比較と選択指針） | 保留 | 第 4 章・第 5 章のいずれかの成立 |

保留の理由は [アウトライン](outline.md) §5 に記録しています。

## 前稿

[draft-1](../draft-1/index.md) は本稿の前の版です。書籍の目次をそのまま章立てに採用したため参照元と噛み合わず、Axon の章が構想の記述になっていました。何をどう変えたかは [アウトライン](outline.md) §2 にまとめています。
