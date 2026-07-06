---
title: Rust 版設計ドキュメントレビュー
description: docs/design 配下の Rust 版設計ドキュメント 10 件に対する XP 5 エージェントによるマルチパースペクティブレビュー結果。
date: 2026-07-06
tags: review, design, rust
---

# レビュー結果

## レビュー対象

- docs/design/ 配下の Rust 版設計ドキュメント 10 件
  - architecture_backend.md / architecture_frontend.md / architecture_infrastructure.md
  - domain-model.md / data-model.md
  - ui_design.md
  - test_strategy.md / non_functional.md / operation.md
  - tech_stack.md
- レビュー実施日: 2026-07-06
- レビュアー: xp-product-manager / xp-architect / xp-interaction-designer / xp-tester / xp-user-representative（並列レビュー）

## 総合評価

Java/Spring 版のドメイン資産（ビジネスルール・6〜8 コンテキスト・CQRS・ヘキサゴナル）を Rust の型システムと cargo workspace に翻案する設計思想は 5 視点すべてで高評価であり、特に「ArchUnit の事後検証をコンパイラの事前強制へ置き換える」「不正状態を enum で表現不可能にする」判断は本質的に正しい。一方で、**状態機械（BookingStatus / TransportStatus / Money）の定義がドキュメント間で三者三様**である点、**CQRS 読み取り側のクレート配置と依存制約の矛盾**、**Dockerfile の sqlx オフラインビルド欠落**という「実装が物理的に成立するか」に直結する不整合が複数エージェントから独立に指摘されており、開発着手前の解消が必須である。

## 改善提案（重要度順）

### 高

| # | 提案 | 指摘元 | 理由 |
|---|------|--------|------|
| 1 | BookingStatus / TransportStatus / Money の定義を domain-model.md を正典として統一する | tester, architect, PM, user-rep | 3 ドキュメントで enum 構成が異なり、テスト例が存在しないバリアント（`Misrouted`, `IN_PORT`）を参照。TDD のレッドが書けない |
| 2 | コンテキスト数の不一致（backend: 6 / domain・data: 8）を解消し、Estimation・Shipper のスコープ方針を明記する | PM | 開発チームが「結局いくつ作るのか」を判断できず手戻りする |
| 3 | CQRS 読み取り側（`query_as!` / FromRow DTO）の配置を infra-persistence に統一し、app 層はクエリポート trait のみ持つと明記する | architect | app クレートに sqlx 依存が入り「Application は Domain のみ依存」ルールと正面衝突。実装初日に破綻する |
| 4 | Dockerfile / CI に `SQLX_OFFLINE=true` と `.sqlx` キャッシュ（`cargo sqlx prepare --check`）を組み込む | architect, tester | 現状の Dockerfile はビルドステージに DB がなく必ずビルド失敗する |
| 5 | 予約登録フォームに荷主・荷受人の指定欄を追加する | user-rep | ドメインは `shipper_id` 必須なのに UI に入力欄がない。業務が成立しない |
| 6 | 追跡管理者向けの例外登録・解決画面（RegisterException / ResolveException）を追加する | user-rep | 破損・通関保留・紛失の記録は日常業務だが閲覧系画面しかない |
| 7 | 経路割り当ての担当アクター（UI: 営業担当者 / ドメイン: 経路設計者）の食い違いを解消する | user-rep | 業務の受け渡し境界が画面フローに現れていない |
| 8 | ステータス用語体系（表示ラベル・コード）を付録の対応表を Single Source として全画面で統一し、PaymentStatus 定義表を追加する | designer, user-rep | 同一状態が画面ごとに別ラベルで現れ、誤った心理モデルを形成する |
| 9 | ワイヤーフレームのナビゲーションをロール別メニュー構成（8 メニュー）と整合させる | designer | 共通レイアウト定義と全画面の 4 項目固定ヘッダーが食い違い、導線検証ができない |
| 10 | data-model.md 内の `cargo.shipper_id` 型（BIGINT / UUID）を統一する | PM | FK 制約エラーに直結する型不整合 |

### 中

| # | 提案 | 指摘元 | 理由 |
|---|------|--------|------|
| 11 | 段階的実装計画（Phase 1: Booking + Shipper → Phase 2: Routing + Tracking → Phase 3: Handling + Billing → Phase 4: Estimation）を独立節として構造化する | PM | 優先順位の根拠（ビジネス価値・依存関係）が crate コメントに埋没している |
| 12 | shared-kernel の `DomainEvent` メガ enum を廃し、コンテキスト固有イベント型 + エンベロープ（トピック + ペイロード）方式にする | architect | 1 コンテキストのイベント追加が全コンテキストを再結合させ、クレート分割の境界を縫い戻す |
| 13 | tokio broadcast のイベント消失（NoSubscribers / Lagged）時の意味論（業務エラーにしない・補償方針）を定義する | architect, tester | commit 済みなのに publish 失敗でエラー伝播すると一貫性が崩れる。at-least-once 保証がない |
| 14 | 消費税の端数処理（丸め規則・適用順序）をエッジケースとしてテスト設計に明示し、ドメインの料金計算と UI の請求明細（サーチャージ・消費税）を整合させる | tester, user-rep | 金額計算のバグは法的リスク。UI とドメインで金額モデルが不一致 |
| 15 | MISROUTED 判定のデシジョンテーブルを境界値付きで網羅し（Customs 常時 valid の抜け穴、複数 Leg 部分一致、VoyageNumber 欠落）、荷役登録・一覧 UI に誤送警告を追加する | tester, user-rep | `any()` によるルート順序無視の論理漏れの可能性。現場が誤送に気づけない |
| 16 | 通関業務の画面（CustomsStatus の一覧・更新導線）を追加する | user-rep | 「Cleared まで引取不可」ルールをどの画面で操作するか不明 |
| 17 | カバレッジ目標（85%/80%）を Rust 実測でキャリブレーションする注記を追加する | tester | 型で排除した分岐により分母が変わり、JaCoCo と cargo-llvm-cov で数え方が異なる |
| 18 | CI の DB 供給方式（testcontainers-rs / GitHub Actions サービスコンテナ）を一本化する | architect | 二重起動・ポート競合の恐れ |
| 19 | インラインバリデーションのトリガー（blur / change）を 2 ドキュメント間で統一する | designer | フィードバックタイミングの体感が大きく異なる |
| 20 | ダッシュボードに見積への導線（サマリーカード・クイックリンク）を追加し、見積 → 予約の引き継ぎ導線の優先度を上げる | designer, user-rep | 営業の主要業務起点が UI にない。手入力の二度手間が発生する |
| 21 | 荷役の識別子（追跡番号 / 貨物 ID）と荷役種別表記（CUSTOMS_CLEARANCE / CUSTOMS）の主従・用語を統一し、日本語ラベルを併記する | designer, user-rep | 現場作業員の入力ミスと混乱を生む |
| 22 | Transactional Outbox への移行トリガー（SLA 違反率等）を条件として明記する | PM, architect | 規約（commit 後 publish）は破られる。意思決定条件の事前定義が必要 |

### 低

| # | 提案 | 指摘元 | 理由 |
|---|------|--------|------|
| 23 | non_functional.md の「anyhow コンテキスト」記述を thiserror 統一方針に合わせて修正する | architect | tech_stack.md の方針と矛盾 |
| 24 | 30 秒ポーリングをタブ非アクティブ時に停止し、aria-live の過剰読み上げ対策を注記する | designer | 無駄な通信と支援技術での過剰通知 |
| 25 | 請求書フィルタに REFUNDED を追加し、返金業務の導線を定義する | user-rep | ドメインに IssueRefundCommand があるが UI に導線がない |
| 26 | 輸送開始後の予約キャンセルを UI で抑止または確認強化する | user-rep | 誤操作は返金トラブルの元 |
| 27 | htmx ポーリング E2E のフレイキー対策（ポーリング間隔の設定注入・決定論的ステータス注入）を明記する | tester | 時間依存アサーションは必ず不安定化する |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| 1 | PM: 8 コンテキスト全設計はゴールドプレーティングリスク。Estimation は作らない判断も一案 | user-rep: 見積 → 予約の引き継ぎは営業実務の自然な流れであり優先度を上げるべき | Estimation Context の投資時期 | 初期リリースは「予約 → 経路割当 → 追跡 → 配達」の一気通貫に絞り、Estimation は見積 → 予約導線とセットで Phase 4 として計画に明記する（作らないのではなく時期を決めて後置） |
| 2 | architect: CI はサービスコンテナ、ローカルは testcontainers の使い分けも可 | tester: testcontainers を統合テストの標準とする | CI での DB 供給方式 | テストコード自身が DB を起動する testcontainers に一本化し、CI はDocker-in-Docker 前提とする（テストがどこでも同じに動くことを優先） |

## 高重要度指摘への対応方針

| # | 指摘 | 対応方針 | 状態 |
|---|------|----------|------|
| 1 | 状態機械の不統一 | **修正する** — domain-model.md を正典とし、architecture_backend.md / test_strategy.md / ui_design.md を参照・追従させる | 対応済（2026-07-06） |
| 2 | コンテキスト数 6/8 の不一致 | **修正する** — 8 コンテキストに統一し、backend に Estimation / Shipper の実装フェーズを明記 | 対応済（2026-07-06） |
| 3 | CQRS 読み取り側の配置矛盾 | **修正する** — Read Model 実装は infra-persistence、app 層はポート trait のみ。ADR-0001 起票済 | 対応済（2026-07-06） |
| 4 | Dockerfile の sqlx オフラインビルド | **修正する** — SQLX_OFFLINE + .sqlx コミット + prepare --check を Dockerfile / CI に反映 | 対応済（2026-07-06） |
| 5-7 | UI の業務欠落（荷主欄・例外画面・経路割当アクター） | **修正する** — ui_design.md に画面・入力欄・アクター境界を追加（21 画面に拡張） | 対応済（2026-07-06） |
| 8-9 | ステータス用語・ナビゲーション不整合 | **修正する** — 付録対応表を Single Source 化、ロール別ナビ例を追加 | 対応済（2026-07-06） |
| 10 | shipper_id 型不整合 | **修正する** — shipper.id / cargo.shipper_id を UUID に統一 | 対応済（2026-07-06） |

## 中重要度指摘への対応状態（2026-07-06 対応済）

| # | 指摘 | 対応内容 |
|---|------|----------|
| 11 | 段階的実装計画 | architecture_backend.md に「段階的実装計画」節を新設（Phase 1: Booking + Shipper + Shared → Phase 4: Estimation、価値根拠・依存関係付き） |
| 12 | DomainEvent メガ enum | shared-kernel は `EventEnvelope`（topic + payload）+ `EventPublisher` trait のみ。イベント型は各コンテキスト固有クレートで定義（backend / domain-model 両方更新） |
| 13 | イベント消失の意味論 | NoSubscribers / commit 後失敗はログ + メトリクスに留める規約、Lagged は定期リコンシリエーションで補償 |
| 14 | 消費税・金額モデル整合 | Invoice に明細（InvoiceLineItem）+ TaxRate を追加。計算順序（サーチャージ → 割引上限 30% → 税 10% 切り捨て 1 回）をドメイン・テスト両方で固定 |
| 15 | MISROUTED デシジョンテーブル | 11 ケースに拡充（Customs は itinerary 上の港制約、Load/Unload の VoyageNumber None はバリデーションエラー、順序検証は将来拡張と記録） |
| 16 | 通関業務画面 | 通関一覧 `/customs` + 状態更新画面を新設。「Cleared まで Claim 不可」を画面仕様に明記 |
| 17 | カバレッジのキャリブレーション | 型による分岐排除・cargo-llvm-cov と JaCoCo の計測差の注記、実測 2〜3 IT でキャリブレーション |
| 18 | CI の DB 供給一本化 | testcontainers-rs に一本化。`sqlx-check` ジョブのみサービスコンテナ使用（test_strategy / infra 両方整合） |
| 19 | バリデーショントリガー | `hx-trigger="blur changed"` に統一（ui_design / architecture_frontend） |
| 20 | 見積導線 | ダッシュボードに見積カード + クイックリンク、見積詳細に「この見積で予約する」（Phase 4） |
| 21 | 識別子・用語統一 | 追跡番号を主・貨物 ID 併記、HandlingType 正典 + 日本語ラベル対応表を付録に追加 |
| 22 | Outbox 移行トリガー | 移行判断 3 条件（不整合インシデント / SLO 継続違反 / プロセス分離）を明記 |

### 追加対応（レビュー外・ユーザー指摘）

- **経路設計フローの欠落**: docs/requirements の US06〜US14 / UC04〜UC12 を ui_design.md に反映。経路設計・割り当て画面をステップ型（貨物仕様確認 → 航海検索 → 候補算出 → 選択 / 条件調整 → 予約紐付け）に拡張し、予約詳細に経路設計依頼・荷主通知・予約確定・追跡番号発行の導線を追加。画面数は 24 画面に拡張

## エージェント別フィードバック詳細

<details>
<summary>xp-product-manager（高: 2 / 中: 2 / 低: 0）</summary>

### 評価サマリー
Java/Spring 版のドメイン資産（8 コンテキスト・ビジネスルール・料金/割引ロジック）をほぼ完全に保持しつつ、Rust の型システムで不変条件をコンパイル時強制する翻案として質が高い。ただし「コンテキストの数」がドキュメント間で 6 と 8 に食い違っており、段階的実装計画が構造化された形で明示されていない点が、ビジネス価値の観点で最大の懸念。

### 良い点
- 中核ドメインのビジネスルール（料金計算式、法人割引上限 30%、Hazardous → 危険物申告必須、MISROUTED 判定、CLEARED まで CLAIM 不可、LOST 強制エスカレーション）が失われず翻案されている
- 型による不変条件の表現（`BookingStatus` enum、`ShipperKind::Corporate(CorporateProfile)`、newtype）が実ビジネス価値に直結
- クレート分割によるコンテキスト境界のコンパイラ強制は境界侵食を機械的に防ぐ
- 外部連携 5 ポートの ACL trait 抽象化
- Billing のイベントソーシング不採用等「今やらない」判断の明示（YAGNI）

### 改善提案
- 【高】コンテキスト数の不一致（backend: 6 / domain・data: 8）を解消する
- 【高】Estimation Context のスコープ方針を明確化する（backend のクレート構成に `domain-estimation` がない）
- 【中】段階的実装計画を独立節として構造化する（Phase 1 = Booking + Shipper + Shared → Phase 2 = Routing + Tracking → Phase 3 = Handling + Billing → Phase 4 = Estimation）
- 【中】Transactional Outbox 移行トリガーを条件として明記する

### 懸念事項
- 8 コンテキスト設計完備によるゴールドプレーティング/へろへろリスク。初期リリースは「予約 → 経路割当 → 追跡 → 配達」一気通貫に絞ることを強く推奨
- Shipper Context はほぼ CRUD であり独立コンテキストとして厚く作る必然性が弱い。要件の裏付け確認が必要
- 見積 → 予約の引き継ぎが「将来」のままでは Estimation は価値の薄い機能になる

### スコープ外の発見
- data-model.md 内で `cargo.shipper_id` の型が不整合（FromRow 例: i64 / 論理モデル: BIGINT / DDL: UUID）。実装時に FK 制約エラーになる

</details>

<details>
<summary>xp-architect（高: 2 / 中: 3 / 低: 2）</summary>

### 評価サマリー
DDD + ヘキサゴナル + CQRS を Rust の型システムと cargo workspace へ翻案する設計思想は一貫して優れている。一方で CQRS 読み取り側と依存制約の両立、sqlx コンパイル時検証と Docker ビルドの整合、DomainEvent の共有カーネル集中、状態機械の定義不統一に、看過できない不整合と技術的リスクが残る。

### 良い点
- 依存制約のコンパイラ強制（domain-* クレートに axum/sqlx を宣言しない）は ArchUnit より強力かつ低コスト。cargo-deny との二段構え
- 不正状態を表現不可能にする enum 設計（状態固有データを持つバリアント）
- テストピラミッドとヘキサゴナル層の一対一対応、proptest 適用
- GC レス・サブ秒起動・distroless の運用への正しい波及
- 外部連携 5 件の ACL ポート trait 抽象化

### 改善提案
- 【高】CQRS 読み取り側の配置が依存制約と矛盾（app-booking に `query_as!` を置くと sqlx 依存が入る）。Read Model 実装は infra-persistence、app 層はポート trait のみに統一
- 【高】Dockerfile が sqlx コンパイル時検証と不整合（ビルドステージに DB がなく必ず失敗）。SQLX_OFFLINE + .sqlx コミット + prepare --check の 3 点を反映
- 【中】DomainEvent メガ enum が全コンテキストを再結合。コンテキスト固有型 + エンベロープ方式へ
- 【中】tokio broadcast の消失（NoSubscribers / Lagged）意味論を定義
- 【中】状態機械の定義がドキュメント間で食い違い。domain-model.md を正典に一本化
- 【低】anyhow 記述の矛盾を修正
- 【低】CI の DB 供給方式（testcontainers / サービスコンテナ）を統一

### 懸念事項
- axum 0.8 / sqlx 0.8 / tower-sessions / axum-login はすべて 0.x で破壊的変更リスク。認証周りのマイナー更新でも ADR 起票を推奨
- CSRF・同時セッション 1・パスワード履歴は axum-login が標準提供せず自作が必要。設計段階で確定しないとリリース前に実装不能と判明するリスク
- 起動時マイグレーションの並行実行（2〜6 タスク同時起動）。独立 ECS 一回タスクでの先行実行を既定に
- RDS 接続数の天井（10 タスク × 20 接続 = max_connections）はピーク前に枯渇。RDS Proxy を設計時点の既定に

### スコープ外の発見
- 監視閾値が architecture_infrastructure.md と non_functional.md で別値（CPU 80% vs 70%/90%）の二重管理

</details>

<details>
<summary>xp-interaction-designer（高: 2 / 中: 3 / 低: 1）</summary>

### 評価サマリー
OOUX を土台にオブジェクト中心の一貫した骨格が組まれ、htmx の適用箇所・PRG・ARIA まで踏み込んだ良質な UI 設計。ただしステータス用語体系の画面間ゆらぎと、ワイヤーフレームのナビゲーションが実際のロール別メニュー構成を反映していない点が、心理モデルとの整合を崩す最大のリスク。

### 良い点
- OOUX の徹底（一覧 → 詳細 → アクションの一貫性）
- 状態のバッジ可視化と WCAG 1.4.1 準拠（色のみに依存しない）
- ARIA 属性マッピング表・スキップリンク・キーボード操作・htmx 部分更新への aria-live 適用
- フラッシュメッセージの一元化と PRG による二重送信防止
- HX-Request ヘッダーによるフラグメント/全ページ出し分けと Askama Template 構造体分離の正しい対応

### 改善提案
- 【高】ステータス用語体系を付録対応表を Single Source として統一。PaymentStatus 定義表を追加
- 【高】全ワイヤーフレームのナビゲーションをロール別メニュー構成（8 メニュー）に合わせる
- 【中】ダッシュボードに見積への導線を追加
- 【中】荷役の識別子（追跡番号 / 貨物 ID）の主従を統一
- 【中】インラインバリデーションのトリガー（blur / change）を統一（blur 推奨）
- 【低】30 秒ポーリングのタブ非アクティブ時停止と aria-live 過剰読み上げ対策

### 懸念事項
- Askama テンプレート式（`if let`、メソッド呼び出し）のバージョン依存。エラー整形は Rust 側 DTO で済ませる方針を明記すべき
- 公開追跡と認証追跡の二重動線で認証境界の心理モデルが曖昧
- htmx フラグメント URL 直リンク時の全ページ描画フォールバック記述が必要

### スコープ外の発見
- ステータス定義と US・状態遷移のトレーサビリティ欠落
- 請求書「新規発行」ボタンの対応 US・遷移先画面が未定義

</details>

<details>
<summary>xp-tester（高: 3 / 中: 4 / 低: 1）</summary>

### 評価サマリー
テストピラミッド・ツール選定・ArchUnit 代替の設計は Rust の型システムを活かした優れた翻案で、テスト戦略単体の完成度は高い。ただし BookingStatus・TransportStatus・Money 型の定義が 3 ドキュメント間で食い違っており、テストコード例が存在しない enum バリアントを参照しているため、レッドを書き始めた瞬間にコンパイルエラーになる。

### 良い点
- テストレベルとヘキサゴナル境界の対応表が明確
- 「コンパイラを最初のテストとして扱う」方針の一貫性、cargo-deny 併用
- testcontainers-rs による実 PostgreSQL 統一（mock/prod 乖離の回避）、wiremock 契約テストの正常・異常ペア列挙
- proptest による Money 演算則・UN/LOCODE 全域検証
- US トレーサビリティ表

### 改善提案
- 【高】BookingStatus の定義を 3 ドキュメントで統一（`Misrouted` はテスト例のみに存在しコンパイル不能）
- 【高】TransportStatus / TrackingStatus の値集合を統一（E2E 期待値 `IN_PORT` はどの enum にも存在しない）
- 【高】Money の内部表現（i64 / rust_decimal::Decimal）を統一し、オーバーフロー挙動を確定
- 【中】消費税の端数処理と適用順序（割引 → 税）をエッジケースとして明示
- 【中】MISROUTED 判定のデシジョンテーブルを境界値付きで網羅（Customs 常時 valid、複数 Leg 部分一致、VoyageNumber 欠落）
- 【中】カバレッジ目標を Rust 実測でキャリブレーション
- 【中】CI に SQLX_OFFLINE / .sqlx キャッシュの扱いを明記（ユニットジョブはオフライン、統合ジョブは testcontainers）
- 【低】htmx ポーリング E2E のフレイキー対策（間隔の設定注入・決定論的ステータス注入・明示的アサート）

### 懸念事項
- 状態機械が 3 ドキュメントで別物である限り TDD のレッドが書けない
- backend のデータ付き enum と domain-model のフラット enum で永続化マッピングがまるで変わる。早期確定が必要
- フィクスチャ（CargoFixture 等）の配置クレートが未定義でクレート分割方針と衝突し得る
- 追跡 API 1,000 RPS の性能テストシナリオが未定義

### スコープ外の発見
- `CargoType` の重複定義が「共有カーネルは Location のみ」方針と矛盾
- 認証系ストーリーがトレーサビリティ表にない（パスワードポリシー等のセキュリティ要件がテスト計画に未接続）
- tokio broadcast の at-least-once 保証欠如はテスト設計上の穴。Outbox 移行前提のテスト観点を残すべき

</details>

<details>
<summary>xp-user-representative（高: 3 / 中: 6 / 低: 2）</summary>

### 評価サマリー
業務フロー（見積 → 予約 → 経路割当 → 荷役 → 追跡 → 請求）を素直に画面化しており、追跡・状態可視化・アクセシビリティ配慮は現場で十分使えるレベル。ただし「予約に荷主・荷受人を入れる場所がない」「例外（LOST・CUSTOMS_HOLD・MISROUTED）を現場が登録・対応する画面がない」という、日常業務が止まりかねない欠落がある。

### 良い点
- 追跡詳細画面が実務ニーズ（今どこ？いつ着く？）を的確に吸収
- 公開追跡ページ（認証不要・QR・個人情報非表示）が実際の使い方に合致
- 荷役登録のカメラスキャン入力（手がふさがる現場への配慮）
- ステータスバッジのテキスト併記（屋外・逆光対応）
- LOST 自動エスカレーション・Cleared まで Claim 不可等の業務ルールが型で担保

### 改善提案
- 【高】予約登録フォームに荷主・荷受人の指定欄を追加
- 【高】例外の登録・解決画面（ExceptionType 選択・発生場所・エスカレーション表示）を追加
- 【高】経路割り当ての担当（UI: 営業 / ドメイン: 経路設計者）の食い違いを解消
- 【中】荷役登録・一覧で MISROUTED 警告を表示
- 【中】通関業務の画面（CustomsStatus 一覧・更新）を追加
- 【中】荷役種別・輸送ステータスの英語コード表記ゆれを統一し日本語ラベル併記
- 【中】公開追跡のステータス体系混在（BookingStatus / TransportStatus）を統一
- 【中】請求の金額モデル（UI: サーチャージ・消費税 / ドメイン: 基本料金のみ）を整合
- 【中】見積 → 予約の引き継ぎ導線の優先度を上げる
- 【低】請求書フィルタに REFUNDED を追加
- 【低】輸送開始後の予約キャンセル抑止

### 懸念事項
- 例外発生時の荷主・荷受人への通知運用が operation.md に未定義（技術メトリクス中心）
- 荷主自身のログイン導線と自社貨物への絞り込み（他社予約が見えない制御）が未明示
- 荷役実施日時の未来日許可は実績記録の正確性を損なう恐れ

### スコープ外の発見
- 状態語彙の統一ガイドの必要性（全ドキュメント共通）
- ユビキタス言語の統一表（コード名 ↔ 業務用語）を 1 か所で正典化する運用の提案

</details>

## 次のアクション

重要度「高」の 10 件から着手する。推奨順序:

1. **状態機械・用語の正典化**（#1, #8）: domain-model.md に BookingStatus / TransportStatus / Money / 荷役種別の正典定義とユビキタス言語対応表を置き、他ドキュメントを追従させる
2. **構造の整合**（#2, #3, #10）: コンテキスト数の統一、CQRS Read Model の infra 配置（ADR 起票）、shipper_id 型統一
3. **ビルド成立性**（#4）: Dockerfile / CI への SQLX_OFFLINE 反映
4. **UI の業務欠落**（#5, #6, #7, #9）: ui_design.md への画面・入力欄・アクター境界の追加
