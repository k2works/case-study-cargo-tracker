---
type: Plan
title: "Bolt 計画 1 - ウォーキングスケルトン（荷主登録から予約登録まで）"
description: "Bolt 1（U-02・US02・US04 の基本形）のゴール・仮説・着手前の承認ゲート・チェックボックス付きステップ計画。人の承認待ちの草案。"
tags: [plan, bolt]
status: stable
generated: { by: claude-code/claude-sonnet-5-5, at: 2026-11-04T03:44:39Z }
verified:
  - { by: human:kakimomokuri, at: 2026-11-04T03:59:38Z }
---

# Bolt 計画 1 - ウォーキングスケルトン

[リリース計画](release_plan.md)の Bolt 1。**この計画は 2026-11-04 に人（プロジェクトの担当者）が承認しました**（`/goal Bolt1` と、着手前ゲートの承認：ADR-001〜005・007〜009・011 と ADR-006 の API 部分、仮置き Q-1〜Q-3、対象外、AI の仮定をそのまま採用）。確認が要るステップ（新規ファイル・外部ライブラリ・スキーマ変更・認証）では、その都度停止して承認を得ます。

## 1. Bolt の概要

| 項目 | 内容 |
| :--- | :--- |
| Unit | U-02 貨物予約 |
| ストーリー | US02（AC-01・02）、US04（AC-01・02） |
| ゴール | 荷主を登録し、その荷主の貨物予約を「仮受付」で登録するまでを、UI・API・ドメイン・DB に最小の縦割りで通す |
| **確認したい仮説** | 選んだアーキテクチャ（Unit を最上位にしたモジュール、純粋なドメイン + イベントソーシング + CQRS）で、**AI が規約どおりの縦割りを TDD で生成できるか**。ゲート密度をどこまで下げられるか（リリース計画「ゲート密度」） |
| テスト戦略 | Standard（U-02。[テスト戦略 4](../design/test_strategy.md)） |
| ゲート密度 | 既定：各ステップでゲート。確認必須の操作（新規ファイル・構造・外部ライブラリ・スキーマ）を含むステップは必ず停止 |
| 測るもの | 承認ゲート通過数、変更依頼数、手戻り率、リードタイム（リリース計画「進捗の測り方」）。反映遅延の**計測点が働くこと**（実測の確定は別のスパイクとステージング） |

### 1.1 対象と対象外

| 対象 | 対象外（理由） |
| :--- | :--- |
| US02 AC-01：荷主を個人で登録し荷主 ID が発行される | US02 AC-02 のうち「登録を続ける」の選択は、メール重複の扱い（Q2）の確認後に確定。重複の**警告表示**までを対象にする |
| US02 AC-02：同じメールの荷主が登録済みなら既存の荷主を示す | US03（法人）：Bolt 13 |
| US04 AC-01：荷主を選択する | **US04 AC-03**（経路設計者への通知）：通知ポートのスタブが要り、US06 の通知と二重にならないか未確認（US06 の確認ポイント）。Bolt 4 で扱う |
| US04 AC-02：貨物仕様・輸送条件を入力して登録し、予約番号が発行され「仮受付」になる（貨物種別は一般貨物のみ） | **US04 AC-04**（見積との整合性）：判定不能（テスト戦略 11.3）。Bolt 9 |
| | US05（危険物・冷凍・冷蔵）：Bolt 13 |
| | 認証・認可の実装：Bolt 2（R-02）。この Bolt では `Principal` を差し込めるスタブにとどめる |

## 2. 着手前の承認ゲート

次が**未承認のあいだは、ステップ 1 以降を始めません**。レビューの指摘 H1・H6・H7 に当たります。

### 2.1 承認する ADR（提案中のものを人が承認する）

| ADR | 承認する内容 | この Bolt で効く場所 |
| :--- | :--- | :--- |
| ADR-001 | パターン・モジュール構成・Unit 間連携 | モジュールの切り方、検査 A1〜A3 |
| ADR-002 | イベントストアと配信（**一次判断のみ**：方式 A で始める。見直しは Bolt 4 の前） | 永続化・投影の実装 |
| ADR-003 | 冪等性キーと識別子 | `registerShipper`・`registerBooking` |
| ADR-004 | 個人情報の扱い（荷主は CRUD） | `booking.shipper` |
| ADR-005 | Clock ポート（**業務タイムゾーンの基準は Bolt 1 では仮置き**。US04 の基本形に期限と到着の比較は出てこない） | `Clock` |
| ADR-007 | Scala 3.9 LTS・JDK 21・sbt、ZIO は採らない | ビルド |
| ADR-008 | スキーマ構成と規約（**R-03：スキーマ変更の前に承認**） | マイグレーション |
| ADR-009 | React + TypeScript + Vite、TanStack Query ほか | フロントエンド |
| ADR-011 | テスト戦略 | 全ステップ |

ADR-006（GraphQL の API 方式）は、この Bolt で GraphQL を使うため、**API 部分だけ**承認する（公開照会の分離は Bolt 8）。ADR-010（クラウド）・012・013 は、この Bolt を止めない。

### 2.2 事業側の確認（Bolt 1 を止める 3 点）

| # | 論点 | 仮置きで進める場合 |
| :--- | :--- | :--- |
| Q-1 | 荷主のメールアドレスの重複：警告にとどめるか、一意制約にするか（domain-model Q2、data-model Q1） | **警告にとどめる**（US02 AC-02 の文面どおり）。一意制約にはしない |
| Q-2 | 予約番号の形式（data-model Q3） | 採番は `platform.booking_number_seq`、形式は `BK-` + 連番 6 桁の**仮**。整形は 1 箇所に隔離 |
| Q-3 | 「希望着日」と「希望期限」は同じか（domain-model 3.1） | **同じ**として扱う（用語集は「希望期限」）。列名は `desired_deadline` |

### 2.3 時間制限つきスパイク（Bolt 1 の外で、前に行う）

| # | スパイク | 時間の上限（案） | 目的 |
| :--- | :--- | :--- | :--- |
| SP-1 | **同一集約への同時書き込み**（NFR-A17、レビュー H1） | 半日〜1 日 | 2 つのタスクで同じ集約にコマンドを送ったときの Journal の挙動を確かめ、クラスターモードの要否（BE-12）を判断する材料にする。結果は ADR-001・002・007 と NFR-A07 に反映 |
| SP-2 | **反映遅延の実測の準備**（NFR-P10） | 半日 | LocalStack（方式 A）での測り方を決める。確定値はステージングで測る（ローカルでは SLO を確定できない） |

SP-1 の結果で、ステップ 4（アクター）の構成が変わる可能性があります。**SP-1 は Bolt 1 の開始前に結論を出す**。

## 3. 入力となる成果物

- 要件：[US02・US04](../requirements/user_story.md)、[Unit 定義 U-02](../requirements/units.md)、[リスク台帳](../requirements/risk_register.md)、[用語集](../design/glossary.md)
- 設計：[ドメインモデル 5](../design/domain-model.md)（`Shipper`・`Booking`）、[データモデル 7](../design/data-model.md)（`booking` スキーマ）、[UI 設計](../design/ui_design.md)（SC-20〜22・25〜27）、[バックエンド](../design/architecture_backend.md)、[フロントエンド](../design/architecture_frontend.md)、[技術スタック](../design/tech_stack.md)、[テスト戦略](../design/test_strategy.md)
- ADR：[001〜011](../adr/index.md)

## 4. ステップ計画

各ステップは **Red → Green → Refactor** で進め、完了はテストで判定します。「**確認**」は人が停止して承認するステップです。承認前に実行しません。チェックボックスは完了で更新します。

### 4.1 準備（環境・構造）

- [x] **S1 ローカル環境の確認**（2026-11-04 完了：JDK 21 は Corretto 21.0.x が利用可能（のち 2026-11-04 に JDK 25 へ変更。ADR-007 の更新を参照。イメージのベースは Temurin 21 の想定）、sbt は `/usr/local/bin/sbt`、Docker デーモンは稼働中。**このマシンの既定の `java` は JDK 25（IntelliJ JBR）なので、sbt 実行時は JDK 21 を `JAVA_HOME` に指定する**。PostgreSQL のコンテナは S9 のスキーマ承認後に compose で起動する。値は読んでいない）：Nix の Scala 開発シェル（`scala_3`・`sbt`）と Docker で PostgreSQL が起動することを確認する（方式 A の場合は LocalStack も）。環境変数・接続情報の**値は読まない**（R-10）。手順書に無い操作は `operating-script` でタスクにする　**確認：環境操作**
- [x] **S2 sbt のマルチモジュールの骨格**（2026-11-04 完了：承認済みの標準構成。Scala 3.9.0・sbt 1.12.15・sbt-scalafmt 2.6.2・scalatest 3.2.20・archunit 1.5.1。`modules/architecture-tests` の `ModuleBoundariesSpec`（A1 と層の向き。100 件）を先に書いて Red（build.sbt なし）→ Green。**壊して赤**：contract に domain への依存と参照を足すと「booking の contract は自 Unit の内側を参照しない」が FAILED、元に戻すと緑）：`shared-kernel`・`booking`（`domain`／`use-case`／`contract`／`command-adapter`／`query-adapter`）を空で作り、`dependsOn` で依存の向きを固定する。**テストが先**：A1（別 Unit の内側を参照しない）の検査が、わざと違反させると赤になる。ADR-007 の版を `build.sbt` に固定する　**確認：新規ファイル・構造・外部ライブラリの導入（R-01）**
- [x] **S3 適合性の最小セット**（2026-11-04 完了：`DomainPurenessSpec`（A3）。Red（`ArchRules` 未定義）→ Green。**メタ検査**で、禁止パッケージの型を持つフィクスチャの domain を渡すと検査が失敗し、触れないフィクスチャなら通ることを固定。全体 103 件緑）：A3（`domain` が Pekko・Slick・GraphQL・ZIO を import しない）を ArchUnit で検査する。壊して赤になることを確認し、記録する（テスト戦略 7.4）

### 4.2 ドメイン（L1・L2：Pekko も DB も無し）

- [x] **S4 共有カーネル**（2026-11-04 完了：`Clock`・`Ulid`（自前実装。新しい外部ライブラリを入れない）・`PortCode`（UN/LOCODE の形式のみ。実在の確認は reference.port）・`InvalidValue`。16 件）：`Clock`、ULID の集約 ID、`PortCode`（UN/LOCODE の形式検証）、業務エラーの型。実在する UN/LOCODE だけを使う（R-09）。形式・境界値のテスト
- [x] **S5 `Shipper`**（2026-11-04 完了：個人荷主のみ。氏名・住所・連絡先・メールの値オブジェクト、重複メールは警告（承知して続ければ登録できる）。7 件）（US02）：氏名または社名・住所・連絡先・メール（正規化）・荷主種別 `INDIVIDUAL`。登録のコマンドと拒否理由。**メールの正規化と重複の判定は仕様どおり**（Q-1 の仮置き）。テストで境界を押さえる
- [x] **S6 `Booking`**（2026-11-04 完了：`RegisterBooking` → `BookingRegistered`、状態は仮受付だけ。B1（遷移表の網羅。表に載っていない組み合わせは失敗）、B11（出発地≠目的地、引渡日≦期限、重量・個数・寸法）、NFR-S22（イベントに個人情報の型を持たない）を検査。`-Werror` を有効化。18 件）（US04）：`RegisterBooking` → `BookingRegistered_V1`、状態は仮受付だけ。不変条件 B11（出発地≠目的地、希望引渡日≦希望期限、重量は正、個数は 1 以上）、B12（貨物仕様は型で守る：一般貨物のみ）。**状態 × コマンドの遷移表**から検査を生成する（テスト戦略 6.2）。イベントから状態を組み立てる形式の Given/When/Then（6.1）

### 4.3 ユースケース（L3：出力ポートはフェイク）

- [x] **S7 `RegisterShipper`**（2026-11-04 完了：認可（営業担当者のみ。6 ロールを全部検査）→ 冪等性 → 入力検証（不備をまとめて返す）→ 重複の確認 → 登録。キー再利用の拒否、失敗したキーは使い捨てにならない、別利用者の同じキーは別扱い。11 件）：認可（営業担当者）を入力検証より先に。重複の警告。冪等性キー（同じキーの再送は最初の結果）。`Principal` のフェイク
- [x] **S8 `RegisterBooking`**（2026-11-04 完了：認可 → 冪等性 → 入力検証 → 荷主の存在確認 → 番号の発行 → 集約。応答に ID・番号・最後のイベントの版。再送で番号が飛ばない。集約が拒んだ InvalidTransition を返す。12 件。use-case 計 23 件）：荷主の存在確認（外部の事実を集めてコマンドに載せる）、予約番号の採番（出力ポート）、`Clock`、冪等性キー。応答に集約 ID と**最後のイベントの版**を含める

### 4.4 永続化（L5・L4）

- [x] **S9 マイグレーションの案を作る（2026-11-04 完了・承認済み：`modules/platform-migration`（reference・platform：V1 スキーマとドメイン型、V2 実在の UN/LOCODE 7 件、V3 冪等性キー・待避・採番）と `modules/booking/migration`（V1 shipper・booking_view・booking_history）。data-model との差分：法人の列は Bolt 13、`port_cargo_kind` は Bolt 3、経路・追跡番号・キャンセルの列は後の Bolt。Flyway の場所は `db/migration/<platform|booking>` に分け、履歴は Unit のスキーマごとに持つ）**（実行しない）：`reference.port`・`port_cargo_kind`（実在コードの最小の部分集合。取扱い貨物種別は**架空**と明記）、`platform.idempotency_key`・`dead_letter_event`・`booking_number_seq`、`booking.shipper`・`booking_view`・`booking_history`。**法人の列は Bolt 13 のマイグレーションで足す**（レビュー L1。data-model 15 は「先に作る」としているため、この計画では YAGNI を優先し、承認時に確認する）。差分を人に提示する　**確認：スキーマ変更（R-03）**
- [x] **S10 マイグレーションの検査**（2026-11-04 完了：`modules/migration-tests`。Flyway 13.12.0 + PostgreSQL 18.6（Testcontainers の実物）。空の DB と前の版の DB の両方（I7）、実在の UN/LOCODE、ドメイン型、メール重複を許す（US02 AC-02）、B11 の違反を DB でも拒む、履歴の冪等、スキーマ間の外部キーは reference のみ。11 件。**拒否は SQLState 23xxx に限定**して、SQL の書き間違いが偽の緑にならないようにした。注：tech_stack の Flyway 12.x は古く、13.12.0 を採用）：Testcontainers（PostgreSQL の実物）で、空の DB と前の版の DB の両方に通る（I7）。スキーマをまたぐ外部キーが `reference` 以外に無い
- [x] **S11 イベントのシリアライザ**（2026-11-04 完了・承認済み：sbt-protoc 1.0.8・ScalaPB 0.11.21。Envelope（型名・版・ペイロード）+ `BookingRegistered_V1`。A5：イベントの一覧から見本の有無を検査（載せ忘れは失敗）、往復、**固定した V1 のバイト列を読める**（proto の項目番号を変えると、往復は緑のままこのテストだけが赤になることを確認して戻した）、未知の型・版は `UnknownEvent`、壊れた入力は `Malformed`、NFR-S22（個人情報の項目が無い。品名 `item_name` には誤反応しない）。7 件）：Protobuf の Envelope（`BookingRegistered_V1`）。**往復テストと旧版のバイト列のテスト**（A5）。イベントの一覧から検査を生成し、載っていないイベントは失敗にする。**個人情報をイベントに載せない**（荷主 ID のみ。NFR-S22）　**確認：外部ライブラリ（ScalaPB など。R-01）**
- [x] **S12 集約アクター**（承認済みの方針：Pekko 標準の `EventSourcedBehavior`（1.5.0）+ persistence-testkit のインメモリ Journal。個人製の effector は入れない。**SP-1（同時書き込みのスパイク）は S17 の前に LocalStack + DynamoDB プラグインで行い**、結果でクラスターモードの要否を決める）
  - 2026-11-04 完了（Pekko 1.5.0）：`BookingActor`（EventSourcedBehavior。**永続化してから返信**、拒否は永続化しない、返信に最後のイベントの版）、`PekkoBookingAggregates`（`BookingAggregates` ポートの実装。ローカルモード：予約 ID ごとに子アクター）、`BookingEventPekkoSerializer`（Protobuf の Envelope。Java シリアライズは禁止）。persistence-testkit のインメモリ Journal で、登録・版・拒否・復元・独立・8 並行で 1 件だけ成功を検査（6 件）。**壊して赤**：シリアライザのバインディングを外すと 6 件が失敗、戻すと緑。注：同一集約への複数タスクからの同時書き込み（NFR-A17）はこのローカルの検査では確かめられない。SP-1 で確かめる：`Booking` のアクター（永続化してから返信）。pekko-persistence-testkit（インメモリの Journal）で復元・拒否を検査。**SP-1 の結論に従う**。個人製の永続化ライブラリは使うならラッパーで隔離する（ADR-002）　**確認：外部ライブラリ、SP-1 の結論**
- [x] **S13 荷主の DAO**（2026-11-04 完了：`SlickShipperRepository`（手書きの Slick 3.6.1）+ 共通部品 `modules/db-test-support`（PostgreSQL 18 を JVM あたり 1 回起動し、テストごとに空の DB へ全マイグレーション）。保存→取得、正規化メールでの重複検出（大文字小文字を区別しない）、同じメールを 2 件保存でき先の荷主を示す、**同じ ID の保存は更新（行が増えず、作成の日時・者は不変）**、論理削除は見えない。6 件。ポート `ShipperRepository.save` に操作者・日時（`AuditStamp`）を足した。気づき：Slick 標準の `OffsetDateTime` は PostgreSQL に文字列で束縛されて `timestamptz` に入らないため、`Timestamp` 経由の変換を明示した）：`booking.shipper`（論理削除）。重複メールの検索。L5 で実 DB に対して検査

### 4.5 読み取り（L5・L6）

- [x] **S14 投影**（2026-11-04 完了：`BookingProjection`（`BookingRegistered` → `booking_view`・`booking_history`。7 件、PostgreSQL の実物）。A6 冪等（同じイベントを 2 回→`AlreadyApplied`、行は増えない）、版が飛んだら適用せず `Gap`（欠けを埋めない）、読み取りモデルを空にして流し直すと同じ内容、外部キー違反は Future の失敗で一部の行も残さない、反映遅延の計測点（`LagRecorder`。適用時だけ記録）、`available_commands` はドメインの述語（`Booking.availableCommands`）。あわせて **イベントに実行者（`EventMeta.actor`、NFR-S10）を追加**（proto は V1 のまま項目を追加：旧版の固定バイト列は実行者なしとして読める））：`BookingRegistered` → `booking_view`・`booking_history`。**冪等**（同じイベントを 2 回・順序を入れ替えて流しても同じ。A6）。`available_commands` は自 Unit の `domain` の述語で計算（ADR-001）。反映遅延の**計測点**（イベントの発生時刻と反映時刻）を指標として出す
- [x] **S15 Query**（2026-11-04 完了：`ShipperQueries`・`BookingQueries`（読み取り）、`QueryService`（**認可 → 入力検証 → 問い合わせ**。A8：荷主の個人情報は営業担当者だけ、予約は営業・経路設計者・追跡管理者）、`BookingGraphQL`（Sangria 4.2.20 + circe。`shippers`・`shipper`・`bookings`・`booking`。**予約の版（version）と実行できる操作・履歴（実行者）を含む**）。業務エラーは `extensions.code`（FORBIDDEN・NOT_FOUND・INVALID_INPUT）で返し、文法や検証の誤りも応答の `errors` にする（500 にしない。A4）。`%` `_` は文字として検索。10 件。query-adapter 計 17 件）：荷主一覧・検索、荷主詳細、予約一覧、予約詳細（SC-20・22・25・27 の読み取り）。応答に読み取りモデルの版を含める。**画面から送った不正な値が業務エラーになる**ことを GraphQL から検査する（A4）

### 4.6 API（L8）

- [x] **S16 GraphQL の Mutation**（2026-11-04 完了：`BookingMutationGraphQL`（`registerShipper`・`registerBooking`。冪等性キー必須）。業務エラーは `extensions.code`（FORBIDDEN・VALIDATION_FAILED（不正な項目名の一覧つき）・DUPLICATE_EMAIL（既存の荷主 ID つき）・SHIPPER_NOT_FOUND・IDEMPOTENCY_KEY_REUSED・INVALID_TRANSITION）で、500 にしない（A4）。認可は入力検証より先（数値・日付の変換より前に判定）。認証は `Principal` を渡すスタブで、実装は Bolt 2。11 件。command-adapter 計 30 件）：`registerShipper`・`registerBooking`（冪等性キー必須）。業務エラーをエラーコードに写す。認可は `Principal` スタブで、**認証の実装は Bolt 2**　**確認：認証・認可に触れるため、スタブの範囲を確認（R-02）**
- [x] **S17 結線**（承認済みの方針 2026-11-04：**インメモリ Journal で結線**し、HTTP は pekko-http 1.4.2。個人製プラグイン・LocalStack・docker-compose は使わない。**SP-1（同時書き込み）と A／B の比較は Bolt 1 の直後**に行い、ADR-002 を見直す。永続化は切れる（再起動で消える）が、縦割りの結線と E2E は確かめられる）
  - 2026-11-04 完了：`apps/local-server`（pekko-http 1.4.2）。`POST /command/graphql`（Mutation）と `/query/graphql`（Query）は別のエンドポイント、`GET /health` は認証・制限の対象外。**認証は開発用スタブ（ヘッダー `X-Debug-*`）で、設定で明示的に有効にしたときだけ使え、既定は無効（401）**。設定は環境変数から（値はリポジトリに置かない。R-10）。**マイグレーションは自動では適用しない**（R-03。未適用なら起動時に何をすべきか示して失敗）。イベント処理（`EventProcessor`）：リトライ → 待避先（`platform.dead_letter_event`）に記録して**次へ進む**、落ちたら先頭から読み直す（冪等）。E2E（HTTP → DB）：荷主登録 → 予約登録 → 応答の版に追いつくまで待って一覧に現れる、履歴に実行者、同じキーの再送で 1 件、認可、400/401。計 11 件。
  - **気づき（SP-1 に引き継ぐ）**：インメモリ読み取り Journal（pekko-persistence-testkit）は、ライブ追尾の `eventsByTag` が 2 件目以降のイベントを配信せず（20 秒待っても届かない）、`offset` も全体の位置でなく集約ごとの連番だった。そのためポーリング（集約ごとの処理済みの連番）にした。**これはローカル専用の割り切りで、実 Journal では全体のオフセットで続きから読む方式に置き換える**：`apps/command-api`・`query-api`・`event-processor` を結線し、ローカルで起動する。`/health` はヘルスチェック。実際にコマンド→イベント→投影→Query の流れが通ることを確認する　**確認：デプロイ設定に触れない範囲であること**

### 4.7 UI（フロントエンド）

- [x] **S18 `apps/web` の骨格**（2026-11-04 完了・承認済み：Vite 8.3.5・React 19.3.1・TS 6.0.3（typescript-eslint が <6.1 を要求するため下げた）・graphql 16.14.2（graphql-request 7.4.0 が 14〜16 を要求）。F1・F2 は ESLint、F3 は Scala のスキーマ固定ファイル + codegen。自作の lint 規則はメタテストつき。msw 3 の未処理要求オプションは `onUnhandledFrame`（旧名は黙って無視されるので注意））：React + TypeScript + Vite、`features/booking`、`lib`（GraphQL クライアント、冪等性キー、Clock）、ESLint で F1・F2（feature 同士の import 禁止、画面から GraphQL を直接呼ばない）。`graphql-codegen` で型生成、スキーマ差分でビルド失敗（F3）　**確認：新規ファイル・外部ライブラリ（R-01）**
- [x] **S19 荷主登録（SC-21）と一覧・詳細（SC-20・22）**（2026-11-04 完了：荷主の登録・一覧・詳細。重複の警告の表示。Vitest + Testing Library + MSW）：部品・フックの検査（Vitest + Testing Library + MSW。モックはスキーマで検証し、本物より甘くしない。F4）。重複の警告の表示
- [x] **S20 予約登録（SC-26）と一覧・詳細（SC-25・27）**（2026-11-04 完了：受付後に予約番号を先に見せ、版が追いつくまで再取得（500ms→2s、15s で「反映待ち」）。二重押下は同じキー）：成功後は「受け付けました（予約番号）」を先に見せ、一覧への反映を版が追いつくまで待つ（上限 15 秒。NFR-P11・P12）。二重押下では同じ冪等性キーを使う
- [x] **S21 ナビゲーション**（2026-11-04 完了：役割 × 画面の表 `ROUTE_ACCESS` から、ナビ・403・到達性テストを生成（F5）。ホームの件数は絞り込み済み一覧へのリンク。全 106 件緑、tsc -b・lint・vite build 通過）：営業担当者のホーム（作業入口）から SC-20・25 へ。件数は絞り込み済み一覧へのリンク（U1・U2）。到達性のテストを表から生成する（F5）

### 4.8 検証

- [x] **S22 E2E（1 本）**（2026-11-04 完了：Playwright 1.64.1（chromium）。バックエンドはテスト用の起動口 `E2eServer`（Testcontainers の使い捨て PostgreSQL・開発用認証つき・固定ポート）を `webServer` で起動し、接続情報は持たない（R-10）。ホーム → 荷主 → 荷主を登録 → 予約 → 予約を登録 → 受け付けました（予約番号）→ 一覧で番号が見える、を 1 本で通す。**E2E で見つかった欠陥 2 件**：(1) 一覧に登録画面への導線が無かった（画面はあるが辿れない）→ 「荷主を登録」「予約を登録」を追加し導線のテストを追加 (2) ブラウザでは相対 URL を graphql-request が解釈できず「通信に失敗」→ 基底 URL を省くと開いているページの origin にし、単体テストで再現してから直した。日付は共有ヘルパ `e2e/support/dates.ts`（Asia/Tokyo。F9））：営業担当者のホームから**一覧で荷主を選んで**始め、荷主登録→予約登録→一覧に見える（F8）。識別子を握って API を叩かない。日時は共有ヘルパで作る（F9）
- [x] **S23 AC の突き合わせ**（2026-11-04 完了：`ops/scripts/ac-trace.cjs`（`npm run ac:trace -- US02=AC-01+AC-02,US04=AC-01+AC-02`）。テスト名の `USxx-ACyy` / `USxx AC-yy`（「・」区切り可）を拾い、対象 AC にテストが無ければ終了コード 1。対象外の AC（US04 AC-03・04）は一覧に出すが不足とは数えない。対象 4 件はすべて対応あり。テストの無い AC を渡すと終了コード 1 になることを確認）：US02 AC-01・02、US04 AC-01・02 のテストが、テスト名の AC 番号で対応づいていることを確認するスクリプト（TST-06）。テストの無い AC を一覧にする
- [x] **S24 全体の検査**（2026-11-04 完了：`sbt test` を全体で実行して 240 件緑（shared-kernel 16・domain 19・use-case 24・command-adapter 103・query-adapter 11・migration 17・local-server 14・architecture 36）。フロントエンドは vitest 109 件・tsc -b・eslint・vite build が通る。`npm audit`（本番依存）は高以上なし）：`sbt test` を**モジュールを絞らず全体で**通す。フロントエンドの lint・型生成・テスト。A1・A3・A4・A5・A6・F1〜F5・F8・I7 が緑
- [x] **S25 守りを壊して確認**（2026-11-04 完了：(1) 遷移の拒否：`-Werror` が到達不能パターンを先に赤にする。テストで赤にするため、既に登録済みの状態でも `RegisterBooking` を受け入れる分岐を足すと B1 の 2 件が赤 (2) 冪等性：キーの検索を空にすると冪等性の 4 件が赤（荷主・予約それぞれ 再送・内容違い） (3) 認可：`canRegisterShipper/Booking` を常に true にすると A8 と「認可は検証より先」の 4 件が赤。いずれも戻して緑を確認）：Standard の Unit だが、ゲート密度を決める材料として、3 つの守り（遷移の拒否、冪等性キー、認可）を壊して赤を確認し、記録する
- [x] **S26 レビュー**（2026-11-04 統合済み・保存先 `docs/review/Bolt1実装_review_20261104.md`：高 11 / 中 19 / 低 多数。**高の対応方針は「推奨案で良い」と承認**（2026-11-04）。H1・H3・H4・H6・H7・H8・H10・H11 は修正済み（下記）。H2（冪等性の事前予約）は ADR-003 に Bolt 1 の制約として記録し SP-1 の観点へ、H5（荷主の検索型選択・件数 Query。一覧は 100 件上限）と H9（起動・マイグレーション適用のタスク化）は既知の制約として次の Bolt 前に扱う。中・低は S27 / SP-1 / 次の Bolt。修正後の確認：sbt 全体、vitest 115 件、tsc・lint・build、E2E が緑。修正の内訳：H1 港の実在確認（`PortCatalog`）と重量・寸法の桁・上限を列に合わせた、H4 項目名を origin / destination / bookingId / shipperId に分けた、H3 承知用の冪等性キーを別に保持、H6 荷主詳細に予約登録の導線、H7 重複警告に既存の荷主、H8 待避先の一意制約（platform V4）、H10 権限のないロールに登録リンクを出さない、H11 個数は整数のみ）：`developing-review`（5 視点）を実施し、結果を保存する　**確認：結果の統合**
- [x] **S27 設計書の反映**（2026-11-04 完了：tech_stack の版（Flyway 13.12.0・TypeScript 6.0.3・graphql 16.14.2、Tailwind/Radix/Prettier は未導入）、ADR-002 に Bolt 1 の割り切りと SP-1 での見直し、ADR-003 に冪等性の Bolt 1 の制約、data-model の港の実在・待避の一意制約・Q1/Q3、domain-model の Q2 と法人の列。Q-1〜Q-3 は確定）：実装で変わった設計判断を `docs/design/` と ADR に反映する（SP-1 の結論、Q-1〜Q-3 の確定、法人の列の扱い）。OKF の検査を通す
- [x] **S28 ふりかえりとゲート密度の見直し**（2026-11-04 完了・人の判断：**U-02・U-04 のゲート密度を、Bolt 2 以降は「Green 完了時のみ」に下げる**。外部ライブラリ・スキーマ変更・認証の確認ゲート（R-01〜R-03）は残す。根拠：Bolt 1 の確認ゲート 7 回（S2・S9・S11・S12・S17・S18・TS の版）はすべて推奨案で承認され、変更依頼は 0 件。欠陥の多くは人の承認ではなく、レビュー（高 11 件）・E2E（導線の欠落、相対 URL の解釈の 2 件）・壊して赤の確認で見つかった。見直しの条件：Bolt 2 の認証で変更依頼が出たら戻す）：変更依頼の数と手戻り率から、U-02・U-04 のゲート密度を「Green 完了時のみ」に下げるかを決める　**確認：人の判断**

## 5. AI の仮定

| # | 仮定 | 確認 |
| :--- | :--- | :--- |
| 1 | US04 AC-03（通知）は対象外とし、Bolt 4 で US06 と合わせて扱う | 要確認（リリース計画の「基本形」の範囲） |
| 2 | メール重複は警告にとどめ、一意制約にしない（Q-1） | 要確認 |
| 3 | 予約番号の形式は仮（`BK-` + 連番 6 桁）で、整形を 1 箇所に隔離する（Q-2） | 要確認 |
| 4 | 希望着日＝希望期限（Q-3） | 要確認 |
| 5 | 業務タイムゾーンの基準は Bolt 1 では仮置き（JST） | 要確認（ADR-005） |
| 6 | 認証はスタブ（Bolt 2 で実装）。画面のロールは営業担当者だけ | 要確認 |
| 7 | 貨物種別は一般貨物のみ。危険物・冷凍・冷蔵は Bolt 13 | リリース計画どおり |
| 8 | 法人の列は Bolt 13 で足す（data-model 15 の「先に作る」を変える） | 要確認（承認時に data-model を直す） |
| 9 | 方式 A（DynamoDB + Streams + Lambda）で始め、見直しは Bolt 4 の前 | ADR-002 の承認 |

## 6. 完了条件（Deployment Unit としての検証）

- [x] 対象の受入条件（US02 AC-01・02、US04 AC-01・02）のテストがすべて緑で、AC と対応づいている
- [x] 荷主登録→予約登録→一覧に見えるの E2E が、ローカルの全体構成で緑
- [x] `sbt test`（全体）とフロントエンドの検査が緑。適合性の検査 A1・A3〜A6・F1〜F5・F8・I7 が緑
- [x] 仮説の検証結果が残っている：AI が規約どおりの縦割りを生成できたか、変更依頼数、ゲート密度の判断
- [x] 設計書・ADR が実装と一致している。OKF の検査を通る

## 7. 承認

- [x] Bolt ゴールと確認したい仮説を確認した
- [x] 着手前の承認ゲート（第 2 章）を確認し、ADR の承認と事業側の確認の方針を決めた
- [x] ステップ計画を検証した（順序、確認ポイント、スコープ、参照、仮定）
- [x] 承認ゲートに立ち会うモブの集中時間を確保した
- [x] 人が計画を承認した（または 3 回以上の変更依頼後に現状で受け入れた）
