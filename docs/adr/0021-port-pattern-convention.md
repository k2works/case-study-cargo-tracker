# 0021 Port パターン規約: 公開 / 入力 / 出力 Port の配置と ArchUnit 強制

Context 間連携で利用される 3 種類の Port (公開 / 入力 / 出力) の配置規約を定め、ArchUnit ルールで強制する。これにより「どこに何の Port を置くか」「他 Context は何に依存して良いか」が一意に決まり、ヘキサゴナル境界の劣化を防ぐ。

日付: 2026-06-25

## ステータス

2026-06-25 承認・適用 (IT9 タスク 0.6)。ArchUnit ルール 6 を新規追加し、IT9 完了時点で全 8 Context が本規約に準拠する。

## コンテキスト

IT8 マルチパースペクティブレビュー H6 (architect) で「公開 Port vs 入力 Port の非対称」が指摘された。具体的には:

- **BillingCargoQueryPort** (IT7 / ADR 0014 系): Billing が Booking から Cargo 情報を取得するための **入力 Port** で、Billing 配下 (`billing.domain.model.repositories`) に定義
- **BookingPublicApi** (IT8 ADR 0017): Booking が他 Context に対して公開する **公開 Port** で、Booking 配下 (`booking.application.api`) に定義
- **MailNotificationPort** (IT8 ADR 0018): Billing が外部メールサーバーに送信するための **出力 Port** で、Billing 配下 (`billing.domain.model.ports`) に定義

これら 3 種類の Port が異なるパッケージ規約で配置されており、新規開発者が「どこに何を置くか」を判断するために既存実装を毎回 grep する必要があった。さらに ArchUnit ルール 3 (IT8 6fe0b22c で修正) も Port 種別を識別せず一律ルールを適用していた。

### 3 種類の Port の業務的違い

| Port 種類 | 視点 | 定義者 | 使用者 | 例 |
| :--- | :--- | :--- | :--- | :--- |
| **公開 Port** (Public API) | 自 Context **が外に公開する** API | 自 Context | 他 Context | `BookingPublicApi` |
| **入力 Port** (Query/Repository Port) | 自 Context **が外から取得したい** 情報の窓口 | 自 Context | 自 Context infrastructure (ACL Adapter 実装) | `BillingCargoQueryPort` |
| **出力 Port** (Notification/External) | 自 Context **が外部に送信する** 副作用の窓口 | 自 Context | 自 Context infrastructure (Adapter 実装) | `MailNotificationPort` |

「使用者」と「定義者」の関係が異なるため、配置規約も異なるべきである。

## 決定

**3 種類の Port の配置を以下に統一し、ArchUnit ルール 6 で強制する。**

### 配置規約

| Port 種類 | 配置パッケージ | 命名サフィックス | 他 Context 依存可否 |
| :--- | :--- | :--- | :--- |
| **公開 Port** | `<ctx>.application.api.*` | `*Api` または `*PublicApi` | ✅ 他 Context から直接依存可 |
| **入力 Port** | `<ctx>.domain.model.ports.*` または `<ctx>.domain.model.repositories.*` | `*Port` または `*Repository` | ❌ 自 Context 内のみ (infrastructure ACL Adapter で実装) |
| **出力 Port** | `<ctx>.domain.model.ports.*` | `*Port` | ❌ 自 Context 内のみ (infrastructure Adapter で実装) |

### 入力 Port vs 出力 Port の区別

両者とも `domain.model.ports` 配下に置くが、命名と用途で区別:
- **入力 Port** (`*QueryPort` 等): 戻り値が情報 (Option / Seq / DTO)、副作用なし
- **出力 Port** (`*NotificationPort` / `*MailPort` 等): 戻り値が `Either[String, Unit]`、外部システムへの副作用

### ArchUnit ルール 6 (新規追加)

```scala
test("ルール 6: Port パターン規約 (ADR 0021): application.api.* への外部依存は許容、commandservices/queryservices/notifications への外部依存は禁止 (ルール 3 既存)、domain.model.ports は自 Context 内のみ"):
  val contexts = Seq("auth", "billing", "booking", "estimation", "handling", "routing", "shipper", "tracking")
  contexts.foreach { ctx =>
    // 他 Context の domain.model.ports は禁止
    val otherPorts = contexts.filter(_ != ctx).map(o => s"..cargotracker.$o.domain.model.ports..")
    val rule = noClasses()
      .that().resideInAnyPackage(s"..cargotracker.$ctx..")
      .should().dependOnClassesThat().resideInAnyPackage(otherPorts*)
      .because(s"他 Context の入力/出力 Port (domain.model.ports) への直接依存は禁止 (ADR 0021)")
    rule.check(classes)
  }
```

## 影響

### IT9 内変更

- ADR 0021 起票・承認 (本書)
- `HexagonalArchitectureSpec` にルール 6 追加 (IT9 0.6)
- 既存 Port の配置確認: BookingPublicApi (`booking.application.api`) ✅ 規約準拠、BillingCargoQueryPort (`billing.domain.model.repositories`) ✅ 規約準拠 (入力 Port)、MailNotificationPort (`billing.domain.model.ports`) ✅ 規約準拠 (出力 Port)
- US30 で新設する AuditLogPort は `shared.audit.domain.AuditLogPort` (shared kernel 配下、出力 Port 扱い)
- CLAUDE.md に Port 配置規約セクション追記

### IT9 以降

- 新規 Port 起票時は本 ADR の表で配置先を一意に決定
- Port 命名チェックリスト: `*PublicApi` → 公開、`*QueryPort` / `*Repository` → 入力、`*NotificationPort` / `*MailPort` → 出力
- ArchUnit ルール 6 が CI で常時検証

### 帰結

- **境界の明示**: 公開 Port (api 配下) と入力/出力 Port (domain.model 配下) が物理的に分離、grep で識別可能
- **依存方向の自動検証**: ArchUnit ルール 6 で「他 Context の Port に直接依存していないか」を CI 検出
- **新規開発者の学習コスト削減**: 本 ADR 表 1 つで配置判断完結
- **既存コードへの影響なし**: 既に IT8 時点で 3 種類の Port が本規約通り配置されているため、リファクタ不要

## コンプライアンス

- `HexagonalArchitectureSpec` ルール 6 が CI で全 PR 検証
- ADR 起票時に「3 種類の Port のうちどれか」を ADR 本文に明示
- Port 命名サフィックスを違反した場合は ArchUnit ルール 7 (将来追加) でブロック

## 備考

- 起票者: AI Agent (IT9 タスク 0.6、IT8 H6 / R3 解消)
- 関連 ADR: 0014 (BillingCargoQueryPort 系)、0017 (BookingPublicApi)、0018 (MailNotificationPort)、0022 (監査ログ、AuditLogPort)
- 関連レビュー: `docs/review/it8_implementation_review_20260624.md` H6 / R3
