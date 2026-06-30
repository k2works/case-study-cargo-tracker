# IT4 マルチパースペクティブコードレビュー

## レビュー対象

- 期間: 2026-06-30 (Ralph Loop 18 反復)
- スコープ: `38aced80..ebafc130` (IT4 全 commit、49 ファイル / +4319 / -25 LOC)
- 本体: US08b / US09 / US11 / US13 (Domain + Application + UI 全レイヤ)
- IT3 繰越: U-04 arch-check Phase 2 + Phase 3 (T-01/T-02/T-03)
- 拡張: U-15 HPC ゲート段階引き上げ

## 総合評価

**Domain + Application 層は production-ready 水準** に達した。CancellationPolicy / RouteEvaluator など純粋関数の凝集度・hedgehog プロパティテストの規律・ADR-0004 Cross-BC 規約の徹底は IT3 までの設計判断を確実に継承している。

一方、5 つの並列レビューで以下の **本質的な技術的負債** が浮き彫りになった:

1. **状態遷移ルールの Single Source of Truth 違反** (`canTransitionTo` がテストでしか使われず、Cargo.hs に重複した状態判定が散在)
2. **5 Application Command の execute 同型重複** (新 Command 追加コストが線形)
3. **ADR-0007/0008/0009 未起票** (`iteration_plan-4.md` で参照済みだが本体不在 = リンク切れ)
4. **HTTP ハンドラ未結線** (Application/View は揃っているが業務フロー全体は動かない)
5. **ALLOWLIST 5 件に sunset 期限なし** (恒久例外化リスク)

IT4 は機能面では受入条件達成済みだが、IT5 では **結線 + 負債解消** が必須。

---

## 改善提案（重要度順）

### 高（IT5 着手前または初期スプリントで対応）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H-01 | 状態遷移ルールを `canTransitionTo` に集約し、Cargo の各遷移関数からこれを呼ぶ形に統一 | `Cargo.hs:74-159` + `BookingStatus.hs:43-54` | xp-programmer | 真実が 2 箇所に分散。新状態追加時に確実に乖離する |
| H-02 | HTTP ハンドラ Servant 接続 (4 ストーリー × 6+ エンドポイント) | `Booking.Interfaces.*` / `Estimation.Interfaces.*` (未存在) | xp-user-representative | Application + View は完成済だが業務フローが動かない。受入条件の Gherkin 全シナリオが手動検証不能 |
| H-03 | ADR-0007 (CancellationPolicy) / 0008 (Itinerary+Leg) / 0009 (Booking 状態機械) を起票 | `docs/adr/` | xp-technical-writer + xp-architect | iteration_plan-4.md からの参照が空中浮遊。設計判断の事後追認 |
| H-04 | hspec-wai 統合テスト追加 (最低 5 本: Cancel Free/Partial/Full + Confirm 成功/失敗) | `test/integration/` (未存在) | xp-tester | 結線バグ検知不能。HX-Trigger ヘッダ / HTTP status 4xx の検証ゼロ |
| H-05 | `CancelBookingInput.inputDepartureTime :: Maybe UTCTime` を sum type に置き換え | `CancelBookingCommand.hs:51-56` | xp-programmer | Confirmed なのに `Nothing` を渡せば Free になる runtime バグの温床。Itinerary 永続化後に削除 |

### 中（IT5 中盤までに対応）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M-01 | 5 Application Command の `execute` を `withCargo` 汎用ヘルパに抽出 | `Booking.Application.*` 5 ファイル | xp-programmer | 65 行の機械的重複。永続化方式や監査ログ追加時の修正箇所が 5 → 1 |
| M-02 | ALLOWLIST_RULE6 / ALLOWLIST_T01_T02 (計 5 件) に sunset 日付コメント必須化 | `scripts/arch-check.sh:43-60` | xp-architect | 期限なし ALLOWLIST は恒久例外化する |
| M-03 | ADR-0004 を改訂し「BC 境界のみ Text、BC 内部は型化」を明示 | `docs/adr/0004-cross-bc-shipper-ref.md` | xp-architect | RouteEvaluator/EvaluateRouteCandidatesCommand が BC 内部まで Text 化されており型安全性を失っている |
| M-04 | Lucid view テストの `T.isInfixOf` から `hasClass` / `hasAttr` ヘルパまたは html-conduit パースへ移行 | `test/unit/Booking/Views/*` + `Estimation/Views/*` | xp-tester | リファクタ耐性が低い (Tailwind クラス変更や属性順入替で false positive) |
| M-05 | キャンセル料ラベル「30% (一部料金)」を「30% (¥XX,XXX)」など金額併記に拡張 | `CancellationFeeView.hs:30` | xp-user-representative | 業務ユーザーには料率より金額がわかりやすい |
| M-06 | iteration_report-4.md / retrospective-4.md を作成 | `docs/development/` | xp-technical-writer | IT1-IT3 で確立されたベロシティ実績データの蓄積が途切れる |
| M-07 | CancellationFee VO 単体テストを追加 (5-6 件、smart constructor の境界) | `test/unit/Booking/Domain/Model/Value/CancellationFeeSpec.hs` (未作成) | xp-tester | 現状 Service spec 経由の間接カバーのみ。VO は不変条件の砦 |
| M-08 | 状態バッジを業務日本語化 (「ROUTE_ASSIGNED」→「経路紐付け済」) | `RouteConfirmView.hs:badgeClass` | xp-user-representative | 大文字スネークは DB CHECK 整合のためであり、UI 表示は業務ラベルにすべき |

### 低（余裕があれば / IT5-IT6 で着手）

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L-01 | `cancelBooking` の `elem [Submitted, RouteProposed, RouteAssigned, Confirmed]` を `cancellableStatuses` 名前付き定数に抽出 | `Cargo.hs:135` | xp-programmer | 業務概念「キャンセル可能状態」が無名リストで埋め込まれている |
| L-02 | `CancellationPolicy.hs` 定数 (7d / 1d) に ADR-0007 参照コメント追加 | `CancellationPolicy.hs:24` | xp-programmer | ドメイン規約の trace ability |
| L-03 | 5 Command の共通パターンを `docs/development/application-command-pattern.md` に集約 | 新規 | xp-technical-writer | 6 個目追加時の説明コストが下がる |
| L-04 | BookingStatus 49 ペア網羅テストを `forAll allStatusPairs` で property 化 | `BookingStatusSpec.hs` | xp-tester | 将来 status 追加時の N² 爆発を防ぐ |
| L-05 | arch-check Rule 番号の整理 (Rule 5 欠番の意図明示 or 連番化) | `scripts/arch-check.sh` | xp-technical-writer | 番号体系の一貫性 |
| L-06 | `iteration_plan-4.md` (1034 行) を `docs/design/it4/*.md` に分割、計画書はスコープと受入条件に専念 | `docs/development/` | xp-technical-writer | 保守性 |
| L-07 | 港コード「HKHKG」表示に港名併記 (例「香港 (HKHKG)」) | `RouteEvaluationView.hs:reasonLabel` | xp-user-representative | 業務ユーザーは UnLocode より港名がわかりやすい |
| L-08 | キャンセル操作の二段階確認 (textarea で理由入力 + チェックボックス確認) | `CancellationFeeView.hs:cancelConfirmButton` | xp-user-representative | `data-confirm` のみではキャンセル誤操作を防げない |
| L-09 | 全候補除外時の制約緩和ヒント表示 (「冷凍制約を外すと N 件採用可能」) | `RouteEvaluationView.hs:acceptedSection` | xp-user-representative | UX 改善 |

---

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| C-01 | xp-architect: ItineraryRepository 分離は早すぎる抽象化、BookingRepository に統合すべき | 現状実装: ConfirmRouteCommand が両 Repository を引数で受け取る | Aggregate 境界 (Booking が Itinerary を所有) vs Port 分離による Tx 境界明示 | ADR-0008 (Itinerary+Leg 集約境界) 起票で集約境界を確定してから判断。それまでは現状維持。 |
| C-02 | xp-architect: ADR-0004 Text 化は BC 境界のみとし内部は型化すべき | 現状実装: Estimation BC 内部まで全面 Text | 型安全性 vs Cross-BC 結合度 | M-03 で改訂 ADR を起票。Estimation BC 内に EstimationVoyageNumber 型を導入する移行を IT5 で計画化 |
| C-03 | xp-programmer: `0ccfffc8` (Cargo 4 メソッド一括 commit) は本来 4 commit に分けるべき | Ralph Loop の高速反復前提 | TDD 1 サイクル粒度 vs Ralph Loop の効率 | 将来は Domain 関数を 1 関数 = 1 Red-Green-Refactor commit に分割。本件は教訓として記録 |

---

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer (高: 2 / 中: 2 / 低: 1)</summary>

### 評価サマリー
Domain 純粋関数の凝集度と ADR-0004 (Cross-BC) の徹底は良。根本課題は状態遷移の SSoT 違反と Application Command の同型重複。

### 良い点
- CancellationPolicy / RouteEvaluator の純粋関数設計
- ADR-0004 を Estimation 側で徹底
- hedgehog プロパティテスト 12 件追加
- `transitionFromTo` ヘルパで一部 DRY

### 改善提案
- 【高】canTransitionTo がテスト以外で未使用、Cargo の遷移関数がパターンマッチで状態判定を二重定義
- 【高】5 Application Command の execute が 65 行重複 → `withCargo` 汎用ヘルパに抽出
- 【中】`CancelBookingInput.inputDepartureTime :: Maybe UTCTime` は型強制不足 → sum type 化
- 【中】`cancelBooking` の elem リスト → `cancellableStatuses` に命名
- 【低】CancellationPolicy 定数に ADR-0007 参照コメント

### TDD 評価
Red→Green→Property のフローは明確。ただし `0ccfffc8` (Cargo 4 メソッド一括) は粒度大。
</details>

<details>
<summary>xp-tester (高: 2 / 中: 4 / 低: 1)</summary>

### 評価サマリー
Domain/Application 層は production-ready 水準。最大リスクは View 層の文字列依存と統合テスト不在による結線バグ。

### 良い点
- CancellationPolicy 4 境界の BVA を Property + Example の両輪
- BookingStatus 49 ペア完全網羅
- G-01 (コンストラクティブ生成) を 18 プロパティで貫徹
- IORef ベース mock の並列実行可能性

### 改善提案
- 【高】Lucid view テストの `T.isInfixOf` 依存 → ヘルパ / html-conduit 化
- 【高】hspec-wai 統合テスト不在 → IT5 で最低 5 本
- 【中】CancellationFee VO 単体テスト追加
- 【中】カバレッジ 74.89% / 75% 未達は View 統合テストで回収
- 【低】49 ペア網羅を property 化で N² 爆発回避
</details>

<details>
<summary>xp-architect (高: 1 / 中: 3 / 低: 0)</summary>

### 評価サマリー
ヘキサゴナル境界は概ね遵守。技術的負債として ADR 不在、ALLOWLIST 期限なし、Text 化過剰、Command 重複。

### 良い点
- Domain 純粋関数中心、Application orchestrate、Interfaces 分離
- arch-check Phase 2/3 を shell で即座に CI gate 化
- ALLOWLIST 運用で新規違反のみ fail

### 改善提案
- 【高】ADR-0007/0008/0009 未起票で設計判断が事後追認
- 【中】ADR-0004 改訂: BC 境界のみ Text、内部は型化
- 【中】ALLOWLIST に sunset 期限必須化
- 【中】ItineraryRepository は ADR-0008 起票後に統合判断
- 【中】arch-check shell → AST 化 ADR を IT5 冒頭で起票

### リスク
ALLOWLIST 期限管理を仕組み化しないと arch-check が形骸化する。
</details>

<details>
<summary>xp-technical-writer (高: 1 / 中: 2 / 低: 3)</summary>

### 評価サマリー
モジュール冒頭 Haddock の質は高水準。最大欠落は ADR 不在によるリンク切れと iteration_report-4/retrospective-4 未作成。

### 良い点
- CancellationPolicy / RouteEvaluator / Itinerary の Haddock が「なぜ/不変条件/境界値」を明示
- T-03 規約の理由 (呼び出し側が `now` を渡す) を明文化
- arch-check.sh の Phase 1/2/3 ヘッダコメント

### 改善提案
- 【高】ADR-0007/0008/0009 + iteration_report-4 + retrospective-4 + v0.2.0 CHANGELOG (リンク切れ・履歴断絶)
- 【中】5 Command 共通パターンの集約ドキュメント
- 【低】arch-check Rule 5 欠番の意図明示
- 【低】iteration_plan-4.md の分割
- 【低】「過去 (= 既に出航済)」のような業務的補足
</details>

<details>
<summary>xp-user-representative (高: 1 / 中: 3 / 低: 3)</summary>

### 評価サマリー
業務観点での受入は**条件付き合格**。Domain ロジック (キャンセル料 3 段階・状態遷移) は荷主・営業担当者の業務に合致するが、UI 表示の業務日本語化と HTTP 結線未完が IT5 で必須対応。

### 良い点
- キャンセル料 3 段階 (Free/Partial/Full) のラベル方針
- 除外理由 (HazardousPortViolation) の日本語変換
- htmx + Bootstrap 色分けによる視認性

### 改善提案 (IT5 必須)
- 【高】HTTP ハンドラ結線 (ブロッカー)
- 【中】状態バッジ「ROUTE_ASSIGNED」→「経路紐付け済」業務日本語化
- 【中】港コード表示に港名併記 (「香港 (HKHKG)」)
- 【中】キャンセル料ラベルに金額併記 (「30% (¥XX,XXX)」)
- 【低】キャンセル操作の二段階確認 (textarea + checkbox)
- 【低】全候補除外時の制約緩和ヒント表示
</details>

---

## サマリーメトリクス

| 重要度 | 件数 | 内訳 |
|--------|-----|------|
| 高 | 5 | SSoT 違反 / Command 重複 / HTTP 結線 / 統合テスト / ADR 不在 |
| 中 | 8 | sunset / Text 化 / View テスト / ラベル / 報告書 / VO テスト / 日本語化 |
| 低 | 9 | 命名 / コメント / ドキュメント分割 / 港名併記 / 二段階確認 / 緩和ヒント |
| **計** | **22** | |

### 全エージェント合意点

- **Domain 純粋関数の品質は高水準** (5 エージェント中 4 が言及)
- **HTTP 結線 + ADR 起票 + iteration_report-4 作成** が IT5 着手前の必須事項

### 対応方針

| 重要度 | 推奨対応 |
|--------|---------|
| 高 5 件 | **IT5 計画策定時に全件取り込む**。H-01 + M-01 は同根 (Cargo 集約 + Command 重複) のため一括リファクタ可能 |
| 中 8 件 | IT5 中盤までに対応。M-03/M-06 はドキュメント系で即着手可能 |
| 低 9 件 | IT5-IT6 で順次。L-07/L-08/L-09 は UX レビュー結果と合わせて優先度再評価 |

---

## 関連ドキュメント

- [IT4 計画](../development/iteration_plan-4.md)
- [IT3 マルチパースペクティブレビュー](it3_post_nav_review_20260629.md)
- [IT2 マルチパースペクティブレビュー](it2_code_review_20260627.md)
- [リリース計画](../development/release_plan.md)
