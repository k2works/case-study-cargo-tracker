# イテレーション 6 ふりかえり（KPT）

- 実施日: 2026-07-14
- 対象: IT6（2026-09-14 〜 09-25）／US19・US20（6 SP）+ IT5 レビュー高優先（H1-H4）の先行消化 + 通知記録基盤（H2）

## サマリー

| 指標 | 計画 | 実績 |
|------|------|------|
| ストーリーポイント | 6 SP | 6 SP（達成率 **100%**） |
| ベロシティ | 6 SP + Release 1.0 フィードバック | **6 SP/IT**（IT1=13・IT2=10・IT3=14・IT4=12・IT5=17・IT6=6、平均 12.0 SP/IT） |
| テスト | ドメイン 85% 目標 | **255 件全パス**（Domain 116 / App 3 / Arch 8 / Web 63 / E2E 4 / Infra 61） |
| 品質 | 警告 0・format 準拠 | 達成（全コミット pre-commit 通過・ビルド警告 0・ドメイン被覆 96-98%） |
| マイグレーション | - | tracking_exception（0013）・exception_notification（0014）を追加 |
| ADR | - | ADR-0009（post-commit 連鎖の結果整合性方針）を起票、ADR-0002/0006 に相互参照追記 |

局面（開発戦略）: **終盤 = アウトサイドインの初回イテレーション**（IT6-7）。中盤（IT3-5）で作り込んだ Tracking BC 中核を再利用し、追跡例外対応（遅延/破損/紛失）を業務シナリオ起点で結合。IT5 レビュー高優先 H1-H4 を先行消化し、機能実装は全層で完結。品質ゲート（Playwright・CI カバレッジ・SonarQube）は環境操作前提で繰り越し。

## Keep（継続すること）

### 技術的成功

- **IT5 レビュー高優先 H1-H4 を Week 1 前半で先行消化した**。例外イベント（`TrackingExceptionDetectedEvent`）追加の前提として、H1（ADR-0009 post-commit 結果整合性）・H3（ArchUnit Tracking/Handling ルール 5/6）・H4（CLAIM/UNLOAD 状態同期 E2E）を新規実装の前に完了。技術的負債・回帰穴を持ち込まなかった。
- **例外の状態遷移・エスカレーション境界・状態復帰をドメインに凝集した**。`TrackingActivity.AddException`/`HasActiveException`/`ResolveException`、`TrackingExceptionEvent`（Lost のみ EscalationFlag 導出）、未解決時 Exception 導出と解決による前状態復帰（BR5）を集約に凝集。ドメインユニットテスト（被覆 96-98%）で網羅。
- **post-commit イベント連鎖に結果整合性方針（ADR-0009）を確立した**。1 イベントが複数ハンドラを各独立 UoW で処理する部分適用リスクに対し、冪等・失敗 WARN ログ・状態導出による手動修復・Outbox 移行の 4 段方針を明文化。例外通知ハンドラで失敗握り潰し＋ログを ADR どおり実装。
- **単一未解決例外の不変条件で設計曖昧点を解消した**。self-review（xp-programmer/xp-tester）が指摘した「複数未解決例外の解決挙動の曖昧さ」を、UI の単一対応報告フォームと整合する「同時未解決は 1 件まで」の集約ガードで確定。二重登録・通知重複も同時に防止。
- **ArchUnit 基準線を Tracking/Handling BC へ拡張した**。他 BC の `.Domain.Model` 非依存をルール 5/6 で固定し、ドメインイベント購読・CargoSnapshot ACL を正規チャネルとして許容。回帰を CI で検出可能に。

### プロセス的成功

- **計画着手前の 2 段階検証（validating-iteration-plan → validating-design）が再び有効**。ExceptionType・RegisterException/ResolveExceptionCommand・単一未解決不変条件の乖離を開発着手前に確定し docs/design を正に修正してから実装した。
- **Ralph Loop による層単位の反復消化**（ドメイン→永続化→アプリ→イベント→通知→UI）で、各コミット全テスト緑・警告 0 を担保しながら 6 SP＋H1-H4＋通知基盤を着実に消化した。
- **中間 self-review（Ralph Loop 内 1 ターン）で高優先指摘を即消化**。単一未解決不変条件・escalation_flag の真実源明確化・破損正常系テスト追加をイテレーション内で反映し、正式 developing-review へ持ち込む前に品質を底上げした。

## Problem（問題点）

- **対応報告（ResolveException）の荷主通知が記録されていない**。例外検知時の荷主/管理職通知は実装したが、対応報告（解決）時の荷主通知が欠落。US19 AC4/US20 AC5「対応内容を入力して荷主に対応報告を送信できる」が記録どまり（正式レビュー高 H1）。
- **変換ヘルパ重複負債（IT4 M1）が本 IT でも未返済・拡大した**。`ToDatabaseTimestamp`・`ToDbStatus`・`ToPascalCase` が Shared 未集約で 8 箇所に拡散。IT4 レビューで提起以来 IT5・IT6 と 3 イテレーション連続で先送り（正式レビュー高 H2）。
- **ADR-0009 のコンプライアンス（冪等性統合テスト）が未整備**。ADR 自身が「同一イベント 2 回処理で二重適用されないこと」の統合テストを要求するが未作成。通知テーブルに一意制約もなく at-least-once 再送で二重記録され得る（正式レビュー中 M1）。
- **設計ドキュメントのドリフトが継続**。data-model に exception_notification が未定義、ui_design の例外登録ワイヤーが実装と乖離（CustomsHold 提示・DELAYED 表記・対応方針欄）、「Booking が消費」の文書が実装（Tracking BC 内完結）より広い連携を約束。IT5 でも同種の通知記録テーブルドリフトが指摘されており繰り越しパターン化。
- **品質ゲートが 4 イテレーション連続で繰り越し**。Playwright E2E・カバレッジ CI ハードゲート・SonarQube SQ-3/SQ-2 が IT3-IT6 まで持ち越し。いずれも環境操作（`operating-qt`・CI・ブラウザ）が前提で Ralph Loop 内では着手できなかった（ドメイン被覆 96-98% は実測確認済み）。

## Try（次に試すこと）

| # | 改善アクション | 責任者 | 期限 | 期待効果 |
|---|--------------|--------|------|---------|
| T1 | **対応報告の荷主通知を実装**する。`ResolveExceptionCommand` に解決通知の append-only 記録（Recipient.Shipper・種別=対応報告）を追加し US19 AC4/US20 AC5 を完全充足 | 開発チーム | IT7 着手時 | 受入基準の完全達成・荷主への証跡確保 |
| T2 | **変換ヘルパ（DatabaseTimestamp・EnumDbCodec）を Shared に集約**する。IT5 Try T1 の ADR 化とあわせ、Booking/Routing/Tracking の 8 箇所を一括巻き取り（部分適用禁止）。Row DTO 重複方針も ADR 検討 | 開発チーム | IT7 冒頭 | DRY 負債の返済・往復整合の保証 |
| T3 | **ADR-0009 の冪等性統合テストと通知の冪等キー**を整備する。同一イベント 2 回処理で二重記録されないテスト、または exception_notification に一意制約導入 or 「append-only 再送許容」の明文化 | 開発チーム | IT7 | ADR コンプライアンス達成・二重通知防止 |
| T4 | **通知記録テーブルの data-model 一括整備と ui_design 追随**を独立タスク化する。exception_notification 追加・例外登録ワイヤーの実装追随・対応報告画面の画面一覧追記。DoD の「ui_design 整合」を enum 値/選択肢/フォーム項目レベルの突合に厳格化 | 開発チーム | IT7 | ドキュメントドリフトの解消・繰り越しパターンの打破 |
| T5 | **品質ゲート（Playwright・カバレッジ CI・SonarQube）を IT7 で環境ごと決着**する。IT5 Try T3/T4 から連続繰り越し。`operating-qt`・`operating-cicd` で環境を起動し 4 IT 跨ぎの積み残しを止める | 開発チーム | IT7 | 品質の定量ゲート化（積み残し解消） |
| T6 | **単一未解決例外制約の業務妥当性を PO 確認**する。「遅延中に破損」等の複数同時例外シナリオの要否を確認し、必要なら複数同時例外対応を改善バックログへ | PO・開発チーム | IT7 計画時 | 業務要求との一致確認 |

## ベロシティと計画への反映

- IT6 実績 **6 SP**（計画どおり）。US19/US20（6 SP）に加え IT5 レビュー高優先 H1-H4 と通知記録基盤の消化を伴い、実質的な負荷は例外 2 ストーリー＋負債返済。IT1=13・IT2=10・IT3=14・IT4=12・IT5=17・IT6=6 で平均 **12.0 SP/IT**。
- 残 SP: **13 SP**（Release 1.1 まで IT7）。バーンダウンは計画線に追従。
- 終盤（アウトサイドイン）初回として、中盤で作り込んだ Tracking BC 中核を再利用し例外フローを業務シナリオ起点で結合できた。IT7（請求・精算 US21-23）は同じアウトサイドインで、Delivered 後の `InvoiceRequested` 起点（H4 で担保）から精算フローを結合する。

## 次のステップ

1. `creating-iteration-report`（`--report`）で IT6 完了報告書を作成（本ふりかえりとセット）
2. `syncing-github-project --sync` で US19/US20 の Issue を Done 同期
3. `planning-releases --iteration 7` で IT7（請求・精算・終盤アウトサイドイン）計画を作成し、Try（T1・T2・T3・T4・T5）を先頭タスクに反映
4. `operating-qt`・`operating-cicd` で品質ゲート（SonarQube・カバレッジ・Playwright）を環境ごと決着（T5）
5. IT7 計画時に単一未解決例外制約の業務妥当性を PO 確認（T6）

## 関連ドキュメント

- [イテレーション 6 計画](./iteration_plan-6.md)
- [イテレーション 5 ふりかえり](./retrospective-5.md)
- [開発戦略](./development_strategy.md)
- [リリース計画](./release_plan.md)
- [ADR-0009 post-commit イベント連鎖の結果整合性方針](../adr/0009-post-commitイベント連鎖の結果整合性方針.md)
- [開発成果物レビュー（IT6）](../review/開発成果物_IT6_review_20260714.md)
