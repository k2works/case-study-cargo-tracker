# イテレーション 7 ふりかえり（KPT）

| 項目 | 内容 |
|------|------|
| **イテレーション** | IT7（billingms 新設 / 精算、Phase 2 / 1） |
| **期間** | 2026-08-13 〜 2026-08-26（計画 2 週間）/ 2026-06-05（実績 1 日、Ralph Loop モード） |
| **実績** | 8/8 SP（コミット分 100%）、累計 68/76 SP（89%）、Release 2.1 完了 |
| **対象 US** | US21（輸送料金算出）/ US22（法人割引適用）/ US23（精算処理） |
| **コミット数** | 50+ 件（うち本体実装 + review 持ち越し対応 + ADR 起票 + ArchUnit 横展開）|
| **規模** | billingms 新設 + bookingms 拡張 + frontend で 50+ ファイル / 約 5,000 行追加 |

## サマリー

billingms（Billing Context）を新規立ち上げ、配送完了 → 輸送料金算出（US21）→ 法人割引適用（US22）→ 精算書発行・入金記録・督促（US23）の業務フローを完成させた。**Invoice 単一集約パターン**で domain-model.md L885-958 に準拠、`PENDING → CALCULATED → INVOICED → PAID / OVERDUE / CANCELLED` のステートマシンを `BillingStatus.canTransitionTo` 内に閉じ込めた。

bookingms cross-service で `PaymentRecordedEvent`（shared） を購読し、Cargo 集約を SETTLED に遷移させる完全な貫通フローを E2E で検証（cross-service.spec.ts US21/22/23 統合シナリオ）。

**Ralph Loop モード**を活用して IT 完了後も継続的改善を実施。マルチパースペクティブレビューで指摘された **review 高/中 持ち越し 7 件**（H1 二段イベント / M1 InvoiceProjection / M2 NumberSequenceRepository / M1 architect 決定論的 invoiceId / ハードコード除去 / Micrometer counter）を IT 内で即時対応。さらに **ADR-0017 / 0018 / 0019 を起票**し IT8 着手前準備を完了、**全 5 サービスに ArchUnit + Spring scan による構造防止網（15 件）**を展開。

billingms LINE カバレッジは review 対応で **85.4% → 89.87%** に向上。全モジュール check PASS。

## Keep（継続すること）

- **Invoice 単一集約パターン**：domain-model.md L885-958 準拠、BillingStatus ステートマシンを enum 内に閉じ込めた Tell-Don't-Ask 設計。状態遷移の不変条件が 1 箇所に集約され、変更を楽に安全にできる
- **shared/events による cross-service 契約の最小化**：cross-service に必要な情報のみ shared event に含め、内部運用情報は別 event で分離（ADR-0019 PaymentDetailRecorded の方針）。bookingms が知らなくてよい情報を契約に含めない
- **ShipperInfoAcl / BillingContextAcl の責務分離**：CargoDelivered 初回 fetch（BillingContextAcl）と ApplyDiscount オンデマンド fetch（ShipperInfoAcl）で異なる Stub / Rest 実装を分離（ADR-0015）
- **line_type 駆動設計**：BASIC / DISCOUNT / ADJUSTMENT / SURCHARGE のいずれかを invoice_line に追加し、`total_amount = basic_amount - discount_amount + adjustment_amount` を CHECK 制約でガード。将来の SURCHARGE 拡張に OCP で対応
- **Ralph Loop モードでの継続改善**：IT 完了後に review 持ち越し 7 件 + ADR 4 件起票 + ArchUnit 横展開を追加実施。「IT 内」と「IT8 持ち越し」の境界を Ralph Loop が押し広げた
- **ADR-0012 自己整合チェックリスト**：IT7 review H1 教訓を「設計時 C1-C4 / コミット前 R1-R3 / レビュー時 PR1-PR2」の 3 段階で文書化。次のサービス新設時に着手前検証可能

## Problem（問題点）

- **P1: TDD Red 先行が守られていない**：T4.1 / T4.3 / T4.6 で feat 単独コミット → T4.8 で test 後追い。実装とテストが同コミット同居するパターンが多発。Red コミット → Green コミット → Refactor コミットの 1 タスク 3 コミットを守れていない
- **P2: 設計初期に二段イベントを導入した（H1 教訓）**：`SharedPaymentRecordedEventPublisher`（内部 event → shared event 派生 publisher）を「内部 event の安定化」目的で導入したが、ADR-0012 §2 集約発火型違反だった。trackingms `CargoDeliveredEventPublisher`（IT6 廃止）と同型パターンの再導入を見落とした
- **P3: ドメインサービスが Mapper 直依存（M2 教訓）**：`InvoiceNumberGenerator` が `InvoiceSummaryMapper` を直接コンストラクタ注入。DIP 違反で Mapper API 変更がドメインに波及する構造になっていた。設計時に「ドメインサービス → 何のインターフェースを呼ぶか」の問いを通せていなかった
- **P4: ハードコード値の散在**：cron zone「Asia/Tokyo」、PaymentDuePolicy 30 日、DISCOUNT description「法人割引（%.0f%%）」が複数箇所に固定値で記述されていた。IT8 NET60 / 多言語化拡張時に複数箇所修正が必要だった
- **P5: 例外スキップが沈黙故障リスク**：OverdueScheduler の `AggregateNotFoundException` / `CommandExecutionException` を WARN ログのみでスキップ。本番でスキップが連続発生してもアラートが発火しない設計だった
- **P6: ArchUnit 1.4.0 が JDK 25 のクラスファイル major version 69 を完全サポートしていない**：一部の enum 等が読み込めず、@ProcessingGroup の value 検査が ArchUnit DSL では動作しない（Spring scan + リフレクションで代替）

## Try（次に試すこと）

- **T1: TDD Red/Green/Refactor 分離コミット運用化**（review 高、IT8）。pre-commit hook で「Red コミットには失敗テストが含まれる」を簡易検証（grep ベース）
- **T2: H1 教訓を ADR-0012 自己整合チェックリストとして文書化** ✅ IT 内対応完了（commit eadd6683）。次のサービス新設時の着手前ガイドとして活用
- **T3: InvoiceProjection 抽出リファクタ** ✅ IT 内対応完了（commit 43270c3e）。state 遷移ごとの updateForXxx 増殖を SRP / DRY で解消
- **T4: NumberSequenceRepository ポート抽出** ✅ IT 内対応完了（commit c9fa9a1c）。テストはインメモリ Fake で完結
- **T5: ShedLock 採用 ADR（ADR-0017）起票** ✅ IT 内対応完了（commit c4a913be）。IT8 で実装
- **T6: OverdueScheduler に Micrometer counter** ✅ IT 内対応完了（commit c3c2fe0e）。billing.overdue.fired / skipped[reason] / candidates を発行、Heroku metrics で監視可能
- **T7: ArchUnit + Spring scan で ADR-0012/0014/0016 CI 検知** ✅ IT 内対応完了（commit bf020c3e / afe31e86）。全 5 サービスに 15 件展開
- **T8: SendGrid ADR-0018 起票** ✅ IT 内対応完了（commit c4a913be）。IT8 で実装
- **T9: PaymentDetailRecorded ADR-0019 起票** ✅ IT 内対応完了（commit 43d87dd1）。H1 修正の副作用を IT8 で解消する設計道筋
- **T10: ArchUnit 1.5+ または ASM 更新後の DSL 統一**（IT8）。@ProcessingGroup value 検査を ArchUnit ベースに統一
- **T11: ADR-0016 完全移行**（IT8）。全サービスの旧名 @ProcessingGroup を新規約準拠に改名、ArchUnit の prefix soft warning を hard assertion に変更

## 数値指標（KPT 補完）

| メトリクス | 値 | 目標 | 評価 |
|-----------|-----|------|------|
| 計画 SP 達成率 | 100%（8/8） | 100% | ✅ |
| billingms LINE カバレッジ | 89.87% | 80%+ | ✅（review 対応で +4.47pt） |
| billingms テスト件数 | 71 件（IT 完了時点） | - | ✅ |
| bookingms 追加テスト | 5 件（cross-service SETTLED） | - | ✅ |
| frontend テスト件数 | 229 件 / 35 ファイル | - | ✅ |
| E2E spec | billing.spec.ts 7 件 + cross-service.spec.ts 1 件 | - | ✅ |
| review 高/中 IT 内消化率 | 7/7（100%） | 高は IT 内、中は IT8 | ✅（超過達成） |
| ADR 起票・更新 | 5 件（0012 chk / 0015 / 0017 / 0018 / 0019） | - | ✅ |
| ArchUnit 構造ガード | 15 件 / 全 5 サービス | - | ✅ |

## イテレーションを終えての考察

IT7 は **Ralph Loop モードと Code Review プラクティスの組合せが品質を底上げした iteration** だった。当初の計画は「8 SP の実装 + review 高/中 持ち越し」だったが、Ralph Loop が IT 完了後も継続的改善を促し、review 持ち越しを 1 つずつ消化しながら ADR 起票・ArchUnit 横展開まで完遂した。

H1 二段イベントの教訓は ADR-0012 自己整合チェックリストとして体系化され、ArchUnit で CI 検知可能になった。これにより「次のサービス新設時に同じ過ちを繰り返さない」構造が完成した。

IT8 では ADR-0015 / 0017 / 0018 / 0019 の実装に集中することで、Phase 2 を完全に閉じる。残 8 SP + Buffer で本番デプロイ可能な状態に仕上げる。

---

**作成日**: 2026-06-05
**作成者**: k2works（AI ペアプログラミング、Ralph Loop モード）
