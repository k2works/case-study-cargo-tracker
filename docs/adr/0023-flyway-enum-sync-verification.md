# ADR-0023: Flyway migration × enum 同期検証ルール（CI 検証）

IT9 マルチパースペクティブレビューで V5 バグ（`chk_invoice_status` CHECK 制約に `PARTIALLY_PAID` 値が含まれておらず、Stripe webhook 経由の部分入金記録が実行時に CHECK 制約違反で失敗）が発覚した。本 ADR では IT10 A4 で導入した「Flyway migration の CHECK 制約値リスト ⊃ Java enum 値」を CI で構造的に検証するルールを確定する。

日付: 2026-06-09

## ステータス

採用済み（実装完了）

- 2026-06-08: 提案 / iteration_plan-10 で「ADR-0023（起票候補）」として A4 完了後に起票判断と明示
- 2026-06-09: IT10 A4 完遂（billingms / handlingms / trackingms の 3 ms × 7 件の同期検証テスト）に伴い起票・採用済みへ昇格

## コンテキスト

### IT9 V5 バグの根本原因

billingms IT9 A1.5（部分入金対応）で `BillingStatus` enum に `PARTIALLY_PAID` を追加した際、V5 migration では `paid_so_far` カラム追加と `chk_invoice_paid_so_far` 制約追加に注力した結果、既存 `chk_invoice_status` の値リスト更新が漏れた。テスト環境（H2 + Flyway 適用）では検知できず、A1.6 統合テスト（Stripe webhook → Invoice 集約 → Projection 反映）で初めて Postgres CHECK 制約違反が発覚した。

### 同種バグの再発リスク

DB CHECK 制約と Java enum の同期は、以下の場合に容易に乖離する:

- enum に新値を追加したが migration を忘れる（A1.5 と同型）
- migration で CHECK 制約に値を追加したが enum 側に追加し忘れる（孤児値）
- 既存値の名称を変更する際に片方のみ変更（タイポ含む）
- 複数 ms に同名 enum がある場合（HandlingType / TransportStatus 等）の片方だけ更新

これらは静的型システムでは検知できず、実行時に「DB CHECK 制約違反」「INSERT 失敗」「Projection 反映不可」として顕在化する。Postgres 本番 / staging では確実に再現するが、CI 環境（H2 + Flyway 既定値）では再現しにくいパターンもある。

### 当初検討した方式と却下理由

| 方式 | 検証範囲 | 速度 | 却下理由 |
|---|---|---|---|
| `@MybatisTest` + Testcontainers Postgres | 実 CHECK 制約適用 | 約 30s | enum 全値で INSERT を試す必要があり、ノイズが多い。コンテナ起動コストが高い |
| ArchUnit カスタムルール | enum 値の文字列リテラル収集 | 1-2s | migration SQL から CHECK 制約を取得する手段がない |
| Flyway API + Postgres 接続 | CHECK 制約メタデータ取得 | 約 15s | Flyway API は migration の adopted 履歴を返すが「現行制約値」は SQL クエリが必要、結局 Testcontainers と同等 |
| **migration SQL 直接パース**（採用） | CHECK 制約値リスト × enum 値 | 約 0.1s | DB なし、純粋なファイル I/O + 正規表現、依存少 |

## 決定

### 1. 検証パターン

各 ms の Test ディレクトリ配下に `infrastructure/migration/<Enum>CheckConstraintTest.java` を配置し、以下の不変条件を検証する：

- **enum 値 ⊂ CHECK 制約値**: enum に新値追加 → migration 漏れを検知
- **CHECK 制約値 ⊂ enum 値**: migration に不要値が混入 → 孤児値を検知（タイポ含む）

### 2. SQL パース戦略

```java
private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
private static final Pattern CHECK_PATTERN = Pattern.compile(
        "ADD\\s+CONSTRAINT\\s+chk_<column>\\s+CHECK\\s*\\(\\s*"
                + "<column>\\s+IN\\s*\\(([^)]+)\\)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
```

- `V*.sql` をバージョン順に読み、`ADD CONSTRAINT chk_xxx` 行から値リスト抽出
- 同名制約が複数 migration で再定義されている場合は最後（最新）を採用（Flyway 適用順と同じ振る舞い）
- NULL 許容カラム（例: `tracking_event.transport_status`）は `IS NULL OR ... IN (...)` 形式に対応する別パターンで処理

### 3. 適用対象（IT10 時点）

| ms | enum | CHECK 制約名 | テスト数 |
|---|---|---|---|
| billingms | `BillingStatus`（7 値） | `chk_invoice_status` | 2 |
| handlingms | `HandlingType`（5 値） | `chk_handling_type` | 2 |
| trackingms | `TransportStatus`（9 値） | `chk_tracking_summary_current_status` + `chk_tracking_event_transport_status` | 3 |

### 4. CI での実行

通常の `:check` で実行される（タグ分離なし）。実行時間は各テスト ~0.1s で、CI 既存パイプラインへの加算は無視可能。

### 5. 新規 enum 追加時のチェックリスト

- [ ] enum 値を追加 / 削除 / 改名する PR では、対応 Flyway migration（新 V_N__add_xxx.sql）を同 PR に含める
- [ ] migration では `ALTER TABLE ... DROP CONSTRAINT IF EXISTS` + `ADD CONSTRAINT ... CHECK (<column> IN (...))` パターンで再定義（冪等性確保）
- [ ] 該当 `<Enum>CheckConstraintTest` を実行し、Green であることを確認
- [ ] 既存制約名と一致するパターンが SQL に無ければ、テスト側 `CHECK_PATTERN` を新規制約名に合わせて追加

## 結果

### Pros

- **CI で構造的に検知**: enum × DB 値域の同期漏れは静的型検査では捕捉できないが、本テストで PR レビュー前に検知される
- **高速 / 依存少**: Testcontainers 不要、ファイル I/O のみで約 0.1s
- **横展開しやすい**: 同型テストを他 ms / 他 enum にも転用可能（同 IT 内で 3 ms に展開済み）
- **再発防止の文書化**: ADR + テスト + チェックリストで「同型バグを防ぐ」運用ルールが揃う

### Cons

- **SQL パース脆弱性**: コメント内の `chk_invoice_status` 文字列や複雑な CHECK 式（複数 OR 結合等）でパースが失敗する可能性。現状は単純な `IN (...)` 形式のみ対応
- **新規 enum 追加時の手動作業**: 新規 ms に同種テストを横展開する際、`CHECK_PATTERN` を手動コピー / カスタマイズする必要あり（ヘルパー抽出は IT11 以降）

### Cons の緩和策

- IT11+ で共通ヘルパー `EnumCheckConstraintVerifier`（shared モジュール）に抽出する候補
- パース失敗時は明確なエラーメッセージ（"対象 CHECK 制約が migration から見つからない: " + pattern）で開発者に通知

## 関連

- [ADR-0020: 決済機関 webhook 受信設計](./0020-payment-gateway-webhook.md)（IT9 V5 バグの原因となった `PARTIALLY_PAID` 追加 PR の親）
- [iteration_plan-10.md A4](../development/iteration_plan-10.md) (A4.1 / A4.2a / A4.2b 実装タスク)
- [iteration_report-9.md](../development/iteration_report-9.md) (V5 バグ発覚経緯)
