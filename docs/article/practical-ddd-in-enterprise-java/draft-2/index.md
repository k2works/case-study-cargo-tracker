# エンタープライズ Java における実践的ドメイン駆動設計（draft-2）

## 概要

国際貨物輸送管理システム（Cargo Tracker）を題材に、**DDD の概念が Java と Spring のコードとしてどこに現れるか**を追うシリーズです。

主に扱うのは 1 つの実装 —— `docs/article/source/java-2/` に収録された Spring Boot 実装です（第 4 章と第 5 章のみ、それぞれプロセスを越える配送とイベントソーシングを扱うために別実装 `docs/article/source/java-3/`・`docs/article/source/java-4/` を引きます）。**この実装は書籍『Practical Domain-Driven Design in Enterprise Java』の構造を実際に適用したもの**であり、設計ドキュメントは「Practical DDD in Enterprise Java (Chapter 3) のパッケージ構造に準拠する」と明記し、ADR は同書の `bookingms` を参照実装として名指ししています。

**したがって本シリーズは書籍の要約ではなく、適用した結果の報告です。** 報告である以上、次の 2 つを同じ比重で扱います。

- **書籍の構成を写したまま使って壊れた箇所** —— 識別子の置き場を写したために、分割が狙った利得が最初の 1 回で失われた（第 2 章）
- **設計ドキュメントが実装から離れた箇所** —— 正典が規定する `interfaces/rest/` は全 BC に存在せず、ドメインモデル図は 6 か所ずれている（第 2 章・第 3 章）

## 章一覧

| 章 | タイトル | 扱うこと |
| :--- | :--- | :--- |
| [第 1 章](01-ddd-fundamentals.md) | ドメイン駆動設計 — 概念と、この実装での対応物 | DDD の語彙とパッケージ・型の対応表。**対応物が無いもの**（サガ・イベントストア・REST）の一覧 |
| [第 2 章](02-cargo-domain-model.md) | Cargo Tracker のドメインモデル | 集約・識別子・値オブジェクト・ドメインサービス。**境界を分けて払った代金** |
| [第 3 章](03-spring-modular-monolith.md) | Spring Platform 上のモジュラーモノリス | Spring 上の配置と、**境界を守っている検査**。結果整合の取りこぼしの扱い |
| [第 4 章](04-spring-eda.md) | プロセスを越えるイベント — マイクロサービス版の Cargo Tracker | BC をプロセスに分けたときイベント駆動が**新しく要求するもの**。契約・到達・冪等・コミット順序と、それを守る検査 |
| [第 5 章](05-spring-cqrs-es-axon.md) | イベントを正典にする — CQRS / Event Sourcing 版の Cargo Tracker | 集約の保存をイベント列に替えたとき**判断の材料でなくなるもの**。**守りが外れてなお緑になる**欠陥と、それを検査に落とす方法 |
| [第 6 章](06-conclusion.md) | 3 つの実装を同じ尺度で比べる | 配置・**境界を直す費用**・**検知の手段**の 3 軸。軸にしなかったものと、**この比較が確かめていないこと** |

## 読む順序

**第 3 章から読んでも構いません。** 実装の現物を先に見たい場合はそちらが入口として適しています。本シリーズは実装を先に確定させてから概念の章を書いており、第 1 章は最後に書かれています。

| 目的 | 入口 |
| :--- | :--- |
| DDD の語彙と実装の対応を先に押さえたい | [第 1 章](01-ddd-fundamentals.md) |
| モデルの設計判断とその代償を見たい | [第 2 章](02-cargo-domain-model.md) |
| Spring 上の配置と検査の実物を見たい | [第 3 章](03-spring-modular-monolith.md) |
| プロセスを越えるイベント連携の実物を見たい | [第 4 章](04-spring-eda.md) |
| イベントソーシングの代金と、その検知の仕方を見たい | [第 5 章](05-spring-cqrs-es-axon.md) |
| 3 方式の違いと、選ぶときの順序を知りたい | [第 6 章](06-conclusion.md) |

## 参照元

| 種別 | パス |
| :--- | :--- |
| 実装（第 1〜3 章） | [`docs/article/source/java-2/apps/cargo-tracker/`](../../source/java-2/apps/cargo-tracker) |
| 設計ドキュメント（第 1〜3 章） | [`docs/article/source/java-2/docs/design/`](../../source/java-2/docs/design) |
| ADR（第 1〜3 章・25 本） | [`docs/article/source/java-2/docs/adr/`](../../source/java-2/docs/adr) |
| 実装（第 4 章） | [`docs/article/source/java-3/apps/backend/`](../../source/java-3/apps/backend) |
| 設計ドキュメント（第 4 章） | [`docs/article/source/java-3/docs/design/`](../../source/java-3/docs/design) |
| ADR（第 4 章・32 本） | [`docs/article/source/java-3/docs/adr/`](../../source/java-3/docs/adr) |
| 実装（第 5 章） | [`docs/article/source/java-4/apps/cargo-tracker/backend/`](../../source/java-4/apps/cargo-tracker/backend) |
| 設計ドキュメント（第 5 章） | [`docs/article/source/java-4/docs/design/`](../../source/java-4/docs/design) |
| ADR（第 5 章） | [`docs/article/source/java-4/docs/adr/`](../../source/java-4/docs/adr) |

**記事中のコードはすべて上記の実ファイルから転記しています。** 本体を `{ ... }` で省略した引用はありません。設計ドキュメントからの引用と実コードからの引用は、記事中で区別して示しています。

## このシリーズが扱わないこと

| 対象外 | 理由 |
| :--- | :--- |
| DDD の用語解説・パターンカタログ | 既刊書籍が扱う。本シリーズは適用結果の側から書く |
| 20 イテレーションの時系列 | [実践 DDD in Spring Boot](../../practical-ddd-spring-boot/index.md) が扱う |
| XP プラクティスとモデルの関係 | [XP によるドメイン駆動設計の実践](../../xp-domain-driven-design/index.md) が扱う |
| 多言語比較 | [モノリスアーキテクチャ実装比較](../../monolith-architecture/index.md) が扱う |
| ~~**Axon Framework / Event Sourcing**~~ | **第 5 章で扱うようになった。** `source/java-4/` を収録し、実装から書き直した。**構想で章を埋めない**という判断は変えていない |

## 現在の範囲

**本稿は第 6 章で完結します。**

| 章 | 状態 | 着手条件 |
| :--- | :--- | :--- |
| 第 4 章（EDA） | **執筆済** | RabbitMQ で連携するマイクロサービス実装を `source/java-3/` に収録して成立 |
| 第 5 章（CQRS / Event Sourcing） | **執筆済** | Axon Framework 5 による Event Sourcing 実装を `source/java-4/` に収録して成立 |
| 第 6 章（3 実装の比較） | **執筆済** | 比較の尺度（配置・境界を直す費用・検知の手段）を先に定めて成立 |

**第 6 章は推奨を書きません。** 書けるのは「何が違うか」と「どの順序で問うか」までです。3 本は制御実験ではなく（別の時期・同じ作り手・本番運用なし）、方式の差と学習の差を分離できません。**その限界も同じ章に置いています。**

保留の理由は [アウトライン](outline.md) §5 に記録しています。

## 前稿

[draft-1](../draft-1/index.md) は本稿の前の版です。書籍の目次をそのまま章立てに採用したため参照元と噛み合わず、Axon の章が構想の記述になっていました。何をどう変えたかは [アウトライン](outline.md) §2 にまとめています。

**その Axon の章は、本稿の第 5 章として実装から書き直しました。** 構想版が書けたのは構成要素の一覧までで、実装版の中心（タグの付け忘れ・単体テストの限界・復元演習の落とし穴・検査の書き方）はどれも出てきません。両方を読み比べると、**P1 の再発防止が何を守ったのか**が分かります。
