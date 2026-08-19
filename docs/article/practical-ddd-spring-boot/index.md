# 実践 DDD in Spring Boot：20 イテレーションの実装記録

## 概要

本記事では、国際貨物輸送管理システム（Cargo Tracker）を **Spring Boot でドメイン駆動設計に沿って実装した 20 イテレーション**を、イテレーション単位でそのまま辿ります。

扱うのは「DDD とは何か」ではありません。**集約・値オブジェクト・リポジトリ・ドメインイベント・腐敗防止層を、Spring Boot のコードとして具体的にどう書いたか**です。そして、それらが最初から正しかったわけではないこと — 境界が動き、共有カーネルが縮み、同期の越境が結果整合に反転した過程を、20 回分そのまま残します。

各章は 1 イテレーションに対応します。第 1 章から順に読めば、**この順で作れば同じものが立つ**という経路になります。

## 題材

| 項目 | 内容 |
| :--- | :--- |
| システム | 国際貨物輸送管理システム（貨物の見積・予約・経路設計・追跡・荷役・精算） |
| Bounded Context | Shipper / Booking / Estimation / Routing / Tracking / Handling / Billing（＋共有カーネル・Security サブドメイン） |
| ユーザーストーリー | US01〜US36（117SP） |
| アーキテクチャ | モジュラーモノリス（DDD ＋ ポートとアダプター ＋ CQRS） |
| 開発プロセス | XP（TDD・イテレーション・ふりかえり・マルチパースペクティブレビュー） |
| 実績 | 20 イテレーション／ADR 25 本／実装 553 ファイル・テスト 167 ファイル／v2.1.0 出荷済み |

## 技術スタック

| 領域 | 採用 | 備考 |
| :--- | :--- | :--- |
| 言語 / フレームワーク | Java 25 / Spring Boot 4.0 | ADR-001 |
| 永続化 | MyBatis（手書き SQL） | JPA を採らない判断は ADR-004 |
| データベース | PostgreSQL（ローカル補助に H2） | ADR-003 |
| 画面 | Thymeleaf ＋ htmx | サーバサイドレンダリング |
| マイグレーション | Flyway | `common` / `postgresql` / `h2` の 3 系統 |
| 検査 | ArchUnit / Checkstyle / SpotBugs / JaCoCo | 宣言した規則を検査に落とす（ADR-024 ほか） |

## 記事構成

### 導入

- [第 1 章：Spring Boot で DDD を書く土台](01-overview.md) — レイヤ構成・パッケージ規約・技術選定・検査

### 第 1 部：予約基盤（Release 0.1）

- [第 2 章：IT1 ウォーキングスケルトンを 1 本通す](02-iteration-01.md) — US26 / US27 / US31 / US02
- [第 3 章：IT2 Cargo 集約と最初の ACL ポート](03-iteration-02.md) — US04 / US32

### 第 2 部：経路設計（Release 0.2）

- [第 4 章：IT3 航海スケジュールと経路設計への引き渡し](04-iteration-03.md) — US24 / US07 / US06
- [第 5 章：IT4 経路候補算出](05-iteration-04.md) — US08
- [第 6 章：IT5 経路の確定と予約への紐付け](06-iteration-05.md) — US09 / US11 / US33

### 第 3 部：一気通貫（Release 1.0）

- [第 7 章：IT6 予約確定・追跡番号・荷役記録](07-iteration-06.md) — US13 / US14 / US15

### 第 4 部：実運用への補完（Release 1.1）

- [第 8 章：IT7 追跡照会・引取・法人荷主](08-iteration-07.md) — US18 / US16 / US03
- [第 9 章：IT8 うまくいかなかったときを扱う](09-iteration-08.md) — US10 / US12 / US17
- [第 10 章：IT9 荷主セルフサービスと特殊貨物](10-iteration-09.md) — US34 / US05 / US25
- [第 11 章：IT10 遅延・破損・紛失の例外処理](11-iteration-10.md) — US19 / US20
- [第 12 章：IT11 誤配の再設計と通関申告](12-iteration-11.md) — US29 / US28
- [第 13 章：IT12 引取確認コードと引取記録の訂正](13-iteration-12.md) — US35 / US36

### 第 5 部：精算（Release 2.0）

- [第 14 章：IT13 Billing Context の立ち上げと金額の丸め](14-iteration-13.md) — US21 / US22
- [第 15 章：IT14 請求から入金確認までを閉じる](15-iteration-14.md) — US23
- [第 16 章：IT15 輸送中キャンセルの承認](16-iteration-15.md) — US30

### 第 6 部：整流（IT16〜IT17）

- [第 17 章：IT16 宣言した規則を検査に落とす](17-iteration-16.md)
- [第 18 章：IT17 数え上げた負債を返す](18-iteration-17.md)

### 第 7 部：出荷と是正（Release 2.1〜）

- [第 19 章：IT18 Estimation Context の立ち上げ](19-iteration-18.md) — US01
- [第 20 章：IT19 正典に届いていない実装を返す](20-iteration-19.md)
- [第 21 章：IT20 育つ負債を止める](21-iteration-20.md)

### 総括

- [第 22 章：20 イテレーションで積み上がったもの](22-conclusion.md)

## 読み方

- **通しで読む** — 第 1 章から順に。各イテレーションが前のイテレーションの返済から始まるため、時系列がそのまま依存順になっています
- **設計判断だけを追う** — 各章の「設計判断」節と、[ADR 一覧](../source/java-2/docs/adr/index.md) を突き合わせる
- **特定のパターンを探す** — 集約は第 3 章（`Cargo`）、値オブジェクトは第 3・14 章、ドメインイベントは第 7・12 章、ACL は第 3・14 章、CQRS は第 8 章が中心です

## 関連シリーズ

同じ実装（`java/take-6`）を別の軸で扱うシリーズがあります。**本シリーズが時系列、他は横串**です。

| シリーズ | 軸 |
| :--- | :--- |
| [実践 AI 駆動開発](../ai-driven-development/index.md) | 誰が・どうやって書いたか（開発プロセス） |
| [XP によるドメイン駆動設計の実践](../xp-domain-driven-design/index.md) | どの規律がモデルを動かしたか |
| [エンタープライズアーキテクチャの 4 観点](../enterprise-architecture/index.md) | 完成した構造の断面 |

## 参照元

- 実装: [`docs/article/source/java-2/apps/`](../source/java-2/apps)
- 一次資料: [`docs/article/source/java-2/docs/`](../source/java-2/docs)（計画 20・完了報告 20・ふりかえり 20・ADR 25）
- 要件: [ユーザーストーリー](../../requirements/user_story.md)（US 採番の正典）
- 執筆計画: [アウトライン](outline.md)
