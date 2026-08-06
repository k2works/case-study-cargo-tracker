# 記事：モノリスアーキテクチャ実装比較

同一の題材（国際貨物輸送管理システム = Cargo Tracker）を 10 の言語・スタックでモノリスとして実装した実績をもとに、DDD・ヘキサゴナルアーキテクチャ・CQRS という同じ設計方針が、型システムとエコシステムの違うランタイムでどう分かれるかをイテレーション単位で追います。

比較の対象は言語の文法ではありません。**同じ設計方針を違うランタイムに載せたとき、どこが同じでどこが分かれるか**です。

## シリーズ概要

| 項目 | 内容 |
| :--- | :--- |
| 題材 | 国際貨物輸送管理システム（予約・経路設計・追跡・精算） |
| 対象言語 | Java / C# / F# / Scala / Haskell / Flix / Rust / Go / Ruby / TypeScript（10 言語） |
| アーキテクチャ | モジュラーモノリス（DDD + ポートとアダプター + CQRS） |
| 開発プロセス | XP（TDD・イテレーション・ふりかえり・マルチパースペクティブレビュー） |
| 章構成 | 導入 1 章 + イテレーション 10 章 + まとめ 1 章 |

軸となるのは Java（Spring Boot）実装の IT1〜IT10 です。各イテレーションで下した設計判断を実コードで追い、章末で他 9 言語が同じユーザーストーリーをどう実装したかを比較します。

詳しい前提と対象言語のスタック一覧は [シリーズ概要](monolith-architecture/index.md) を参照してください。

## 要件

各章が扱うユーザーストーリー（US01〜US23）と受入基準は、[要件](../requirements/index.md) を参照してください。記事側では要件の全文を再掲せず、イテレーションごとに対象ストーリーの ID と要点だけを示しています。

| ドキュメント | 内容 |
| :--- | :--- |
| [要件定義書](../requirements/requirements_definition.md) | システム価値・外部環境・境界・内部構造（RDRA 2.0） |
| [ビジネスユースケース](../requirements/business_usecase.md) | 業務視点のユースケース |
| [システムユースケース](../requirements/system_usecase.md) | システム視点のユースケース |
| [ユーザーストーリー](../requirements/user_story.md) | 各ストーリーの本文と受入条件（US01〜US27） |

10 実装はいずれもこの要件を正典としており、**ストーリー ID が共通であることが言語横断比較の接続点**になっています。イテレーション数は言語ごとに 7〜12 と異なりますが、同じ US を軸に突き合わせられるのはこのためです。

本シリーズが扱うのは US01〜US23 です。認証・航海マスタ管理にあたる US24 以降は、実装ごとに採否と割り当てイテレーションが分かれるため、比較の軸には含めていません。

## 章構成

### 導入

- [第 1 章：モノリスアーキテクチャの全体像](monolith-architecture/01-architecture.md) — DDD・ヘキサゴナル・CQRS をどう組み合わせたか

### 第 1 部：予約・荷主管理の基盤（Phase 1）

- [第 2 章：IT1 荷主登録と貨物予約の基盤](monolith-architecture/02-iteration-01.md) — US02 / US03 / US04
- [第 3 章：IT2 特殊貨物と予約確定](monolith-architecture/03-iteration-02.md) — US05 / US13

### 第 2 部：経路設計（Phase 2 前半）

- [第 4 章：IT3 輸送見積と経路設計への引き渡し](monolith-architecture/04-iteration-03.md) — US01 / US06
- [第 5 章：IT4 航海スケジュール検索と経路候補算出](monolith-architecture/05-iteration-04.md) — US07 / US08
- [第 6 章：IT5 経路の選択・確定・紐付け](monolith-architecture/06-iteration-05.md) — US09 / US10 / US11

### 第 3 部：精算（Phase 3 前半）

- [第 7 章：IT6 法人割引と精算処理](monolith-architecture/07-iteration-06.md) — US22 / US23

### 第 4 部：追跡（Phase 2 後半）

- [第 8 章：IT7 追跡番号発行と荷役作業記録](monolith-architecture/08-iteration-07.md) — US14 / US15
- [第 9 章：IT8 引取記録・追跡照会・状態手動更新](monolith-architecture/09-iteration-08.md) — US16 / US17 / US18

### 第 5 部：例外処理と料金算出（Phase 3 後半）

- [第 10 章：IT9 遅延・破損・紛失の例外処理](monolith-architecture/10-iteration-09.md) — US19 / US20
- [第 11 章：IT10 輸送料金算出とリリース 2.0](monolith-architecture/11-iteration-10.md) — US21

### まとめ

- [第 12 章：10 言語横断まとめ](monolith-architecture/12-comparison.md) — 型システム・テスト・境界防御・運用の総括

## 読み方

イテレーション章はすべて同じ構成です。

1. **このイテレーションのゴール** — 何を動く状態にするか
2. **扱うユーザーストーリー** — 受入基準と見積
3. **Java 実装** — 実際のコードと、そこで下した設計判断
4. **他言語ではどう書いたか** — 同じストーリーの実装を 9 言語で比較
5. **このイテレーションの学び** — ふりかえりで浮かんだ問題と対処

通読する時間がない場合は、[第 1 章](monolith-architecture/01-architecture.md) で全体像を掴み、[第 12 章](monolith-architecture/12-comparison.md) の横断まとめを読むだけでも要点は追えます。

## 前提知識

- オブジェクト指向またはいずれかの静的型付き言語の基礎
- DDD の用語（エンティティ・値オブジェクト・集約・境界づけられたコンテキスト）の概要
- テスト駆動開発の Red-Green-Refactor サイクル

用語の詳細は [ドメインモデル設計ガイド](../reference/ドメインモデル設計ガイド.md)、アーキテクチャパターンの選択基準は [アーキテクチャ設計ガイド](../reference/アーキテクチャ設計ガイド.md) を参照してください。
