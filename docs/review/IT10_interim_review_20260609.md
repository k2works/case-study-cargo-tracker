# IT10 中間レビュー（AI Agent 単独完結部分）

## サマリ

**対象**: IT10 AI Agent 単独完結部分（a17f3299..70fefe38、28 コミット、約 950 行 + 11 ドキュメント更新）

**実施日**: 2026-06-09

**形式**: 中間（self-review）— staging 実機完了後に `developing-review` スキルで正式マルチパースペクティブレビューを再実施する前提

**範囲**:
- A1 認可深層強化（US30 / 2 SP）
- A2 fallback UX 改善（US31 / 1 SP）
- A4 Flyway × enum 同期検証（US33 / 1 SP）
- A3 staging タスクのうち AI 単独完結分（3.6 / 3.7 / 3.8 / 3.9a / 3.10a）
- A5 ドキュメント整備（5.1 / 5.4 / 5.5）
- ADR-0023 起票

**範囲外**: A3.1-A3.5 / A3.9b / A3.10b（staging 実機）+ A5.2-A5.3（v1.1.0 GitHub Release tag + 本番デプロイ宣言）

**総評**: IT9 マルチパースペクティブレビュー指摘 12 件中 9 件（H3 / H4 / H5 / H6 / H7 / H8 / H9 / H10 / M3 / M8 / L5）を IT10 内で構造的に解消。ADR-0023 起票で運用ルールの永続化も達成。Backend 変更を最小化する判断（A2 で frontend のみで完結、A3.10a で MeterRegistry 注入のみ）は YAGNI と整合的。一方、staging 実機完了で初めて発覚しうる **PreAuthFilter 単独テストの統合カバレッジ不足**、**境界値テストの実 SDK 互換性未確認**、**メトリクス命名規則の Prometheus alert manager 適合性未確認** が残課題。

---

## 高優先度（staging E2E 前に解消推奨、3 件）

| ID | 観点 | 指摘 | 該当ファイル |
|----|------|------|------------|
| H1 | programmer | `PreAuthFilter` が 5 ms にコピペ。`shared` モジュール `BasePreAuthFilter` への抽出は Rule of Three 到達済み（IT9 review M1 のような cross-ms shared コード化が CHECK 制約テストでも候補化）。staging で動作確認後、IT11 で抽出推奨 | 5 ms × `PreAuthFilter.java` |
| H2 | tester | `PreAuthFilter` 単体テストは header 解釈のみで、**SecurityFilterChain 全体での @PreAuthorize 統合動作**は @WebMvcTest 別ファイル（`*ControllerAuthorizationTest`）に分散。staging E2E で「PreAuthFilter → SecurityContext → @PreAuthorize ROLE check」のフルチェインが期待通り動くかを A3.2 で実機確認必須 | 5 ms × `PreAuthFilterTest.java` + 5 ms × `*ControllerAuthorizationTest.java` |
| H3 | architect | `PaymentGatewayWebhookController` の tolerance 二段判定（前段自前 + 後段 Stripe SDK）が **二重実装**: SDK 側 tolerance も `properties.toleranceSeconds()` 同値を渡しているため、SDK の挙動と自前判定の挙動が乖離した場合に検知しづらい。前段で OK → SDK で NG だと 401 が「invalid signature」（自前は通っていたのに紛らわしい）。SDK 側 tolerance を `Long.MAX_VALUE` に近い値で実質無効化し HMAC 検証のみ任せる方が単純 | `PaymentGatewayWebhookController.java:96-108` |

---

## 中優先度（IT10 staging 中 or IT11 早期、4 件）

| ID | 観点 | 指摘 | 該当ファイル |
|----|------|------|------------|
| M1 | programmer | `*CheckConstraintTest` が 3 ms にコピペ。`shared` モジュールに `EnumCheckConstraintVerifier`（migration_dir + check_pattern + enum class を受け取る）抽出は ADR-0023 Cons 緩和策として既に予約済み。IT11 で実施 | 3 ms × `*CheckConstraintTest.java` |
| M2 | tester | `PaymentGatewayWebhookToleranceBoundaryTest` は Controller 前段のみ検証。実 Stripe SDK 内部の HMAC 検証 + tolerance との結合は `PaymentGatewayWebhookIntegrationTest` で実時刻ベースの「now - 280s（安全圏）」しか実証していない。実 SDK 境界値 300s ぴったりでの挙動は SDK 仕様変更に追随する仕組みが必要（SDK upgrade 時に走る contract test） | `PaymentGatewayWebhookToleranceBoundaryTest.java` |
| M3 | architect | `AwsSecretsManagerTrackingTokenSecretProvider` の Counter 命名 `tracking.public_token.refresh.success` / `.failure` / `.consecutive_failures` は Micrometer 規約（小文字 + ドット区切り）に従う。一方、Prometheus 出力時は `tracking_public_token_refresh_success_total` に変換される。Grafana / alert manager 側のクエリ表記は別チームへの周知が必要（A3.10b で確認、operation.md は Micrometer 名で記述しているのみ） | `AwsSecretsManagerTrackingTokenSecretProvider.java:67-91` + `operation.md` |
| M4 | technical-writer | CHANGELOG `[1.1.0] — 2026-06-09` 内で IT10 解消の IT9 レビュー指摘 12 件中 9 件を列挙しているが、解消テーブルの「H11-H12 相当」行は実体がなく混乱を招く。staging 実機タスクに対応する正式な ID 体系（IT9 review にあれば）への置換、または「staging 実機残作業」と明示する | `CHANGELOG.md` [1.1.0] セクション末尾 |

---

## 低優先度（IT11 以降検討、4 件）

| ID | 観点 | 指摘 | 該当ファイル |
|----|------|------|------------|
| L1 | programmer | `extractTimestamp` が `Optional<Long>` を返すが、`PaymentGatewayWebhookController.receive()` 内で `timestampOpt.isEmpty()` を確認した直後 `timestampOpt.get()` を呼んでいる。`orElseGet` / pattern matching での書き換えで NullPointer 安全性を構造化可 | `PaymentGatewayWebhookController.java:111-120` |
| L2 | tester | `*CheckConstraintTest` の正規表現が IF EXISTS の DROP CONSTRAINT には反応しない設計だが、リファクタリングで `DROP CONSTRAINT chk_xxx; ADD CONSTRAINT chk_xxx CHECK (...)` のような順序を変えた場合の挙動が未テスト | 3 ms × `*CheckConstraintTest.java` |
| L3 | user-representative | A3.9a の skipped 動作で `markFailed("unsupported event type or missing metadata")` という同一 reason が「対象外 event type」と「対象 type だが metadata 不足」の 2 ケースで混在。経理担当者が `webhook_processed` テーブルを直接見たときに区別不可。reason を分けると業務追跡しやすい | `PaymentGatewayWebhookController.java:124-127` + `StripeEventTranslator` |
| L4 | technical-writer | README 主要機能表で「Release 1.1 / IT10 時点」と書いているが、IT10 staging 実機未完了。staging 検証完了までは「Release 1.1 候補」または「Release 1.1（実装完了、staging 検証中）」が正確 | `README.md` 主要機能セクション |

---

## 良い点（積極評価、6 件）

| ID | 観点 | 内容 |
|----|------|------|
| G1 | programmer | A2 アプローチ変更判断（Backend null fallback → Frontend Circuit Breaker check）が YAGNI 原則を体現。ドメイン不変条件への侵襲を避けつつ業務要件（経理担当者への警告）を満たした |
| G2 | architect | A3.7 で外部 SDK 制約（Stripe SDK が System.currentTimeMillis 固定）を Controller 前段の Clock 注入で吸収する設計パターンは、他の時刻依存 SDK（AWS SDK / Google API client 等）にも転用可能な普遍的解 |
| G3 | tester | A4 で Testcontainers Postgres ではなく migration SQL 直接パース方式を採用した判断が秀逸。0.1s / no-deps で実行可能、CI 既存パイプラインへの加算ゼロ。ADR-0023 で「検討した 4 方式と却下理由」を記録した点も透明性が高い |
| G4 | technical-writer | ADR-0023 の構成（コンテキスト → 検討した 4 方式の比較表 → 決定 5 項目 → Pros 4 + Cons 2 + 緩和策）が ADR テンプレートの理想形に近い。特に Cons とその緩和策を明示している点が将来の見直しを容易にする |
| G5 | user-representative | US26 受入基準に「対象外イベントの受入動作」セクションを追加し、charge.refunded / charge.dispute.created の現状仕様（skipped 200 + markFailed）+ 将来 US28 / US29 候補を予告した。業務担当者が「これは IT10 段階の仕様か / 将来対応か」を明確に区別可能 |
| G6 | programmer | A3.6 の 1 巨大メソッド → 4 メソッド分割で各テストが独立 UUID で Invoice ID 採番。Spring Context 共有上でも相互干渉せず、`arrangeInvoicedInvoice() / sendWebhook() / InvoicedFixture record` で重複も解消。リファクタリング設計の模範 |

---

## staging 実機完了後に再検証すべき項目（A3 残タスク連動）

| ID | staging で検証 | 関連 IT10 改善箇所 |
|----|---------------|-------------------|
| S1 | PreAuthFilter → SecurityContext → @PreAuthorize の full chain が staging で期待通り 401/403 を返すか | H2 |
| S2 | charge.refunded / charge.dispute.created を Stripe Test Mode から実送信した時、skipped 動作が webhook_processed に期待通り記録されるか | A3.9b / L3 |
| S3 | AwsSecretsManagerTrackingTokenSecretProvider の連続失敗を staging で再現したとき、Counter / Gauge が Prometheus に正しく export され Grafana / PagerDuty に届くか | A3.10b / M3 |
| S4 | tolerance 境界値の実 Stripe webhook での挙動（Stripe ダッシュボードから 5 分以上前のイベントを再送）が IT10 自前判定と一致するか | H3 / M2 |
| S5 | SonarQube Quality Gate が staging code で PASS、IT10 で追加した認可 / 監視 / 検証コードのカバレッジが目標値を満たすか | A3.5 |

---

## 統合所感

IT10 AI 単独完結部分は、IT9 review 指摘の「コード変更 / ドキュメント変更で完結可能な 9 件」を確実に消化した。各タスクで「YAGNI を意識した最小スコープ」「ADR / 受入基準 / 監視設計の運用ドキュメント化」「テスト追加によるリグレッション保護」が一貫している。

一方、本中間レビューでは **「コピペ 3 ms / 5 ms の共通化遅延」**（H1 / M1、Rule of Three 到達済みだが staging 実機で安定性確認を優先）と **「実 SDK / 実時刻との結合保証」**（H3 / M2 / S4）と **「Prometheus / Grafana / PagerDuty への接続実証」**（M3 / S3）の 3 領域が残課題として浮上した。これらは全て **A3 staging 実機タスクで自然に検証される** ため、IT10 の本来スコープと整合する。

IT11 では、staging で安定動作確認後に:

1. `BasePreAuthFilter` / `EnumCheckConstraintVerifier` の shared 共通化（M1 + H1）
2. SDK upgrade 検知の contract test 仕組み（M2 + IT9 M4 と同種）
3. Prometheus alert rule の YAML 化 + alert manager 接続（M3）
4. US28（返金処理）/ US29（申し立て管理）の本格スコープ確定（L3 + IT10 A3.9a 続編）

の 4 項目を優先候補として持ち越し検討する。

## 解消状況追跡（中間レビュー後の修正履歴）

中間レビュー後に AI Agent 単独完結可能な範囲で解消した指摘の追跡。staging 実機完了後の正式 `developing-review` 時点で再確認する。

| ID | 解消方法 | コミット |
|---|---|---|
| L4 | README 主要機能セクション見出しを「Release 1.1 候補 / IT10 進行中：実装完了、staging 検証中」に修正、CHANGELOG `[Unreleased]` セクションの v1.1.0 正式タグ化記述と整合化 | 1c4ba54e |
| L1 | `PaymentGatewayWebhookController.receive()` の `Optional<Long> timestampOpt = extractTimestamp(...)` + `if (isEmpty())` + `timestampOpt.get()` を `Long timestamp = extractTimestamp(...).orElse(null)` + `if (timestamp == null)` に書き換え。null チェック直後の `Long` 直接利用で `.get()` 呼び出しが消え、Optional の二重抽出が解消。境界値 6 件 + 既存 9 件全 PASS | 04943b3a |
| L3 | `markFailed` reason を「`unsupported_event_type`」と「`missing_metadata`」の 2 値に分離。Controller 側で `event.getType()` を判定して reason を選択（StripeEventTranslator 変更不要）。新規テスト 1 件追加（payment_intent.succeeded だが metadata 不足 → missing_metadata）、既存 2 件の assertion 更新。US26 受入基準も 2 reason 体系に更新（業務監査時に「対象外イベント率」と「データ不正率」を分離可能） | 1c7ef1c0 |
| L2 | 3 ms（billingms / handlingms / trackingms）の `*CheckConstraintTest` にリテラル SQL を渡す「複数 ADD CONSTRAINT 順序ロバスト性テスト」を 1 件ずつ追加。`DROP CONSTRAINT IF EXISTS chk_xxx; ADD CONSTRAINT chk_xxx CHECK (... 新値リスト)` のような再定義パターンを SQL 文字列で再現し、Pattern が最新の ADD のみを採用することを実証。3 ms × 10 件全 PASS（既存 7 + 新規 3）。将来の migration リファクタリングで DROP/ADD の順序を変えてもパース挙動が崩れないリグレッション保護 | （本ターン） |

残 H1 / H2 / H3 / M1-M4 は中間レビューの判定通り（H1 / M1 は staging 安定確認後、H3 / M2 / M3 は staging 実機タスクに連動、M4 は CHANGELOG 表記）。L 優先度は全件解消。

## 関連

- [IT9 開発成果物レビュー](./IT9_review_20260606.md) — IT10 で解消した 9 件の出典
- [iteration_plan-10.md](../development/iteration_plan-10.md) — タスク状態
- [journal-it10.md](../development/journal-it10.md) — IT10 中間サマリ
- [ADR-0023](../adr/0023-flyway-enum-sync-verification.md) — A4 由来
