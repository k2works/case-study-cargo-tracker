---
title: IT7 実装レビュー (XP マルチパースペクティブ)
date: 2026-06-23
---

# IT7 実装レビュー

## レビュー対象

- **コミット範囲**: `aff2405d^..08229ee1` (29 コミット、73 ファイル、+3504/-550 行)
- **主要追加**: US19 遅延例外 + US20 破損・紛失例外 + IT6 申し送り 16 件全消化
- **新規 ADR**: 0014 (Snapshot ADT)、0015 (Money 統一)
- **新規 Flyway**: V18-V22 (5 件)
- **テスト数**: 354 → 371 (+17、67 → 68 suites)

## 総合評価

IT7 は **ADR 0014/0015 適用 + ACL ポート分離 + HandlingOrchestrator 抽出** によりヘキサゴナル境界が機械的に強制され、IT6 の主要なコード Smell・申し送りを構造的に解消した。一方で **(a) 楽観ロック try/catch の重複、(b) BookingAdapter の application 層直接依存、(c) HandlingOrchestrator のトランザクション分割、(d) TrackingExceptionEvent の PK 暗黙仕様、(e) 公開追跡画面への例外波及、(f) Lost エスカ後の MasterAdmin 動線、(g) 通知ペイロード仮値** が業務 / 設計両面の最大リスクとして残る。テストはユニット 371 件で底辺は厚いが、Repository IT 不在・Playwright E2E スキップ・TDD コミット規律の弱さで「ドメインは緑だが本番で取り出せない / 偽陽性」のリスクが残存。

## 改善提案 (重要度順)

### 高優先度 (IT8 着手前に対応推奨)

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | 楽観ロック try/catch を `withOptimisticLock[A](label)` ヘルパに抽出 | TrackingCommandService.scala:87-94 / 110-115 / 128-136 | xp-programmer | 3 箇所重複。今後の例外型追加・メッセージ統一を 1 箇所で吸収。エラーメッセージのドリフト防止 |
| H2 | ExceptionType.Lost と NotificationType.LossEscalated / escalateLoss の命名不統一を解消 | 全 Tracking/Booking 境界 | xp-programmer | ユビキタス言語に「Lost (状態) / Loss (事象名詞)」と注記、または `LostEscalated` / `escalateLost` に統一 |
| H3 | BookingAdapter が `BookingCommandService` に直接依存している ACL 越境を解消 | handling/infrastructure/acl/BookingAdapter.scala:3 | xp-architect | Booking 側に公開 Port (`BookingPublicApi` trait) を切り、Adapter はそれを呼ぶ。現状は ArchUnit ルール 3 をすり抜けているだけで、Booking の内部実装変更が Handling に波及 |
| H4 | HandlingOrchestrator の tx 境界 ADR 起票 (T2 / ふりかえり P3) | HandlingOrchestrator.scala:23-60 | xp-architect | `recordEvent` 成功後 `logHandling` 失敗時のデータ不整合リスク。単一 DB.localTx OR Outbox + Domain Events の選択を ADR 0016 で記録 |
| H5 | TrackingExceptionEvent に PK ID を付与 (V20 修正 + 値オブジェクト追加) | tracking/domain/model/entities/TrackingExceptionEvent.scala | xp-architect | 現状 `(type + occurred_at)` 複合キー UPDATE。同一 type/time での競合と「ドメインは緑だが本番で取り出せない」リスク |
| H6 | TDD サイクル遵守 (コミット粒度を Red→Green 分離) | コミット 46d604ff 等 | xp-tester | feat: の前に test: を分離、もしくはコミットメッセージに「Red→Green を経た」事実を明記。CLAUDE.md TDD 規律 |
| H7 | ExceptionType 4 種の同値クラス代表値テスト追加 (CustomsHold / Damage 単独) | TrackingExceptionSpec.scala | xp-tester | 現状 Delay/Lost/Damage(解決のみ) は登場するが、CustomsHold → InException と Damage デフォルト escalationFlag=false の検証なし |
| H8 | 公開追跡画面 `/public/tracking/...` への例外表示方針決定 | tracking/PublicTrackingController + views | xp-user-representative | 荷主にとって「自分の貨物が破損・遅延しているか」は最重要関心事。表示する/別経路で通知の業務ルール明文化 |
| H9 | 解決済例外の取消し / 補足コメント追記 / 誤記録是正の動線 | views/tracking/detail.scala.html | xp-user-representative | 現場では「対応報告後に問題再発」「種別を誤って Lost で記録」が必ず起きる。是正できないと監査ログ汚染 |
| H10 | newEstimatedArrival="未確定" 等の仮値解消 (T7) | TrackingController.recordException + UI | xp-user-representative | 業務通知として不十分。最低でも +24h/+72h 暫定 ETA + responsePlan の定型文選択を Tracker に提供 |
| H11 | README.md (トップレベル) を IT2 以降の進捗反映 | README.md | xp-technical-writer | Release 1.0 MVP 機能完了 + Phase 4 着手の重要マイルストーンが未反映。docs/development/index.md へのリンク委譲が望ましい |
| H12 | `recordException` 戻り値の `: @unchecked` パターン不在 | TrackingCommandServiceSpec.scala:161-169 | xp-tester | Left になっていても次行 `resolveException` で偽陽性 PASS。`val Right(_) = ...: @unchecked` で受ける |

### 中優先度

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | BookingCommandService の 4 通知ログメソッドを `logNotification(bookingId, payload)` に統一 | BookingCommandService.scala:188-252 | xp-programmer | 4 メソッドが構造同一。NotificationPayload sealed trait に `notificationType` を持たせて集約 |
| M2 | Itinerary.voyageNumbers を `legs.map(_.voyageNumber)` の派生プロパティ化 | Itinerary.scala:13, 28-42 | xp-programmer | 不変条件「legs 非空 → 一致」が型で保証されていない。CSV と legs の二重表現を解消 |
| M3 | HandlingOrchestrator の Claim 分岐で `recipientConfirmation.getOrElse("")` 防御を `Option[String]` 受けに変更 | HandlingOrchestrator.scala:55-60 | xp-programmer | register 成功時に空文字に落ちる経路は不変条件矛盾。意図明確化 |
| M4 | TrackingActivity.resolveException の `exceptions(index)` 安定順序保証 | TrackingActivity.scala | xp-programmer | UI 表示順と DB `ORDER BY occurred_at` の一致前提に依存。同時刻ソートの安定性検証 |
| M5 | `updateExceptionResolution` の `WHERE resolved_at IS NULL` で 0 件更新時の例外化 | ScalikeJdbcTrackingActivityRepository.scala:215-219 | xp-programmer | 二重対応時に黙って通過する。UI 表示と DB の乖離リスク |
| M6 | TrackingExceptionSpec エッジケース追加 (解決済再解決 / description=None / OutOfOrder) | TrackingExceptionSpec.scala | xp-tester | P10 並行解決リスクに直結。最低でも (a) AlreadyResolved or 上書き仕様化テスト必須 |
| M7 | HandlingOrchestratorSpec に補償シナリオ追加 (Tracking/Booking 失敗時の Handling rollback 契約) | HandlingOrchestratorSpec.scala | xp-tester | 現状 Right フローのみ。Fake ポートに「失敗を返すモード」を追加 |
| M8 | Repository IT 追加 (tracking_exception_event の永続化往復) | test/cargotracker/tracking/infrastructure/ | xp-tester | InMemoryRepo と本番 SQL の挙動乖離検知不能。Testcontainers IT を 1 本 |
| M9 | TrackingActivity の exceptions リストを未解決のみロードに変更 (CQRS Read 分離) | TrackingQueryService.scala | xp-architect | 件数増大時の性能リスク |
| M10 | ADR 0014 と 0015 の相互参照追加 + ステータス行と適用順序 (L213) の整合 | docs/adr/0014 / 0015 | xp-technical-writer | 「承認・適用済」と SonarQube 実機未実施記載の矛盾を解消 |
| M11 | 設計ドキュメント (data-model / domain-model / ui_design) に「IT7 差分未反映」WARNING 注記 | docs/design/*.md | xp-technical-writer | T6 IT8 持ち越し期間中の「コードとドキュメントどちらが正？」を防ぐ |
| M12 | 状態手動更新の遷移制約 UI/サーバ両面チェック (Claimed → Received 禁止) | views/tracking/detail.scala.html L62-68 | xp-user-representative | 誤操作で監査ログ汚染 |
| M13 | 対応報告 input を textarea に昇格 | detail.scala.html L40 | xp-user-representative | 500 文字許可なのに見た目で 20 文字程度しか書けない |
| M14 | 例外発生場所の prefill (現在位置をデフォルト) | detail.scala.html L113 | xp-user-representative | 業務的に例外は現在位置で起きることが多い |
| M15 | 例外発生時刻表示を `YYYY/MM/DD HH:mm (JST)` に整形 | detail.scala.html L31 | xp-user-representative | ISO 文字列は現場で読みづらい |
| M16 | iteration_report-7.md のコミット数記載を範囲明示 (26 → 29) | docs/development/iteration_report-7.md L18 | xp-technical-writer | 範囲が曖昧 (GitHub Project 同期含むか) |
| M17 | ADR 0014 で TrackingActivity 適用判断の追記 | docs/adr/0014-aggregate-snapshot-adt.md L131 | xp-technical-writer | 「現状未適用、IT8 以降の追加フィールド発生時に検討」と明記 |

### 低優先度

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| L1 | `Orchestrator` 命名は CQRS/Process Manager 的響き、要件次第で `HandlingRegistrationFlow` 等の検討 | HandlingOrchestrator.scala | xp-programmer | プロジェクトの CommandService 命名慣習との並列 |
| L2 | TrackingCommandService.scala:103-107 の完全修飾名を import に上げる | TrackingCommandService.scala | xp-programmer | 可読性 |
| L3 | Snapshot 内の `isCorporate` / `isDelivered` 派生値フラット化見直し | BillingCargoSnapshot.scala | xp-architect | 真実の源の二重化。集約 method で算出推奨 |
| L4 | ItineraryLeg と CSV `voyageNumbers` の deprecation 期限を ADR 化 | Itinerary.scala | xp-architect | 移行期負債の永続化防止 |
| L5 | ScalikeJdbcTrackingActivityRepository.scala:48 のサイレントフォールバック解消 | リポジトリ | xp-architect | `ExceptionType.fromName(...).getOrElse(Delay)` は DB 不正値を隠蔽 |
| L6 | Playwright E2E US19/US20 シナリオを IT8 に計画明記 (T4) | iteration_plan-8 | xp-tester | 自動回帰の代表 1 シナリオ追加 |
| L7 | EitherValues 移行で `: @unchecked` パターンを削減 | test 全般 | xp-tester | 失敗時の MatchError 回避 |
| L8 | ADR 0015 L83 の `4 箇所、IT7 0.4 で対応済` を commit ハッシュ参照のみに簡素化 | ADR 0015 | xp-technical-writer | DRY (数値は commit diff から取れる) |
| L9 | iteration_report-7.md 実績期間「1 日」と計画「2 週間」乖離の脚注追記 | iteration_report-7.md L13 | xp-technical-writer | Ralph Loop 自律実行特殊事情の補足 |
| L10 | 荷受人確認「値」のラベル動的化 (種別に応じて placeholder/label 変更) | handling/newForm.scala.html | xp-user-representative | 種別と値の不整合防止 |
| L11 | 例外の重複記録防止ルール定義 (同一貨物 Delay 2 回など) | 業務ルール | xp-user-representative | 対応報告との紐付け混乱回避 |
| L12 | 法人バッジ自動判定の境界条件 (Shipper 属性変更時のスナップショット vs 動的参照) | views/billing/detail.scala.html | xp-user-representative | 会計監査上はスナップショット必須 |
| L13 | Lost エスカ後の MasterAdmin 到達手段 (メール？画面通知センター？) | 業務運用 | xp-user-representative | 「Lost にしたが上長が気づかない」最悪シナリオ防止 |
| L14 | ロケーションマスタ連動 (UN/LOCODE 直入力からプルダウンへ) | 各画面 | xp-user-representative | IT2 から繰返し指摘、IT8 で一括対応推奨 |
| L15 | ArchUnit ルール 4 拡張時の Orchestrator 乱立防止ガイド (1 コンテキスト 1 Orchestrator) | テストガイド | xp-architect | 緩和方向への暴走防止 |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| C1 | xp-architect: ACL は公開 Port を呼ぶべき (H3) | xp-programmer: BookingAdapter が `BookingCommandService` 直接呼出は ACL 範囲内 | Booking 公開 Port の必要性 | xp-architect 採用。`BookingPublicApi` trait 化推奨だが IT8 への持ち越し可 (短期は許容、ADR 0017 候補) |
| C2 | xp-tester: TDD コミット規律を厳格化 (H6) | xp-programmer: 機能完成優先で Red→Green 痕跡なしも許容 | コミット粒度 | xp-tester 採用。CLAUDE.md TDD 規律と整合 |

## 高優先度対応方針

| # | 提案 | 対応方針 | 期限 |
|---|------|---------|------|
| H1 | withOptimisticLock 抽出 | 修正する | IT8 0.1 |
| H2 | Lost/Loss 命名統一 | 修正する (ユビキタス言語注記 or 改名) | IT8 0.2 |
| H3 | BookingAdapter ACL 越境 | ADR 0017 起票 + IT8 で修正 | IT8 中期 |
| H4 | HandlingOrchestrator tx 境界 ADR | 修正する (ADR 0016 起票) | IT8 着手前 |
| H5 | TrackingExceptionEvent PK ID | 修正する (V23 で id 追加 + 値オブジェクト) | IT8 0.3 |
| H6 | TDD コミット規律 | 規律として CLAUDE.md に追記 + 今後遵守 | 即時 |
| H7 | ExceptionType 同値クラステスト | 修正する (TrackingExceptionSpec 拡張 2 件) | IT8 0.4 |
| H8 | 公開追跡画面の例外表示方針 | ADR or 業務ルールメモで決定 → IT8 で実装 | IT8 着手前 |
| H9 | 解決済例外の取消し動線 | 修正する (UI + Controller) | IT8 中期 |
| H10 | newEstimatedArrival 仮値解消 | 修正する (T7 で IT8 計画済) | IT8 中期 |
| H11 | README.md 更新 | 修正する | 即時 |
| H12 | recordException 戻り値パターン | 修正する (TrackingCommandServiceSpec) | 即時 |

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer (高: 2 / 中: 4 / 低: 2)</summary>

評価: ADR 0014/0015 適用、ACL ポート分離、HandlingOrchestrator 抽出により SOLID 観点で大きな前進。一方、楽観ロック try/catch の重複、ExceptionType 命名の不統一 (Lost/Loss)、Itinerary の voyageNumbers/legs 二重表現が「シンプルな設計」の観点で改善余地。

- 良い点: Snapshot ADT 適用一貫性、ACL ポート分離徹底、TrackingActivity 不変条件 3 の require 明示、HandlingOrchestrator の 4 段フロー可読性、lockedTrackingId/bumpVersion ヘルパ抽出
- 改善: H1, H2, M1-M3, L1-L2
- 懸念: tx 境界、resolveException の index アクセス順序前提、updateExceptionResolution の 0 件更新黙過
- スコープ外: `.claude/ralph-loop.local.md` 未コミット (これは IT7 完了後に削除済 / `4c25f664`)、完全修飾名の import 整理

</details>

<details>
<summary>xp-tester (高: 2 / 中: 3 / 低: 2)</summary>

評価: 17 件のユニットテストは US19/US20 のハッピーパスと主要失敗系を確実に押さえており、AtomicReference Fake ポート / 楽観ロック例外の Left 変換テストなど質高。一方、例外系の境界網羅、TDD コミット規律、永続化往復の Repository IT 不在に改善余地。

- 良い点: HandlingOrchestratorSpec の Fake ポート設計、OptimisticLockException 匿名サブクラスでの仕様化、TrackingExceptionSpec の「複数件のうち 1 件解決で InException 維持」、Receive/Claim 両側からの decisional 検証
- 改善: H6, H7, H12, M6-M8, L6-L7
- 懸念: 偽陰性リスク (InMemoryRepo と本番 SQL 乖離)、並行解決リスク (P10)、`: @unchecked` 多用
- スコープ外: addException 戻り型契約の型注釈テスト追加、P10 を iteration_plan-8 H 項目登録

</details>

<details>
<summary>xp-architect (高: 2 / 中: 1 / 低: 3)</summary>

評価: ヘキサゴナル境界の明示化と Snapshot ADT による集約再構築 API 統一の方向性は正しく、ArchUnit ルール 3 拡張で機械的に守られている点高評価。一方、BookingAdapter の application 層依存、HandlingOrchestrator の tx 分割、TrackingExceptionEvent PK 暗黙仕様に看過できない設計負債が残存。

- 良い点: ADR 0014/0015 が「変更を楽に」を実現、ArchUnit ルール 3 の 8 コンテキスト網羅、Port 名がドメイン語彙、HandlingOrchestrator のテスト容易性
- 改善: H3-H5, M9, L3-L5, L15
- 懸念: HandlingOrchestrator が Saga になりつつある、ArchUnit ルール 4 緩和方向への暴走
- スコープ外: サイレントフォールバック (L5)、ADR 0014/0015 の「結果 (トレードオフ)」セクション要確認

</details>

<details>
<summary>xp-technical-writer (高: 1 / 中: 3 / 低: 2)</summary>

評価: ADR 0014/0015 は意思決定の WHY・代替案・帰結・適用結果が一級品。申し送り 16 件と新規 12 SP の追跡可能性も極めて高い。一方、トップレベル README が IT2 以降の進捗未反映、docs/index.md と設計ドキュメントの接続が弱い。

- 良い点: ADR 0014 の「動作するきれいなゴミ」予兆引用、ADR 0015 の業務的前提明記、iteration_report-7 の commit ハッシュ紐付け、HandlingOrchestrator の WHY 明記
- 改善: H11, M10, M11, M16, M17, L8, L9
- 懸念: T6 期間中の「コードとドキュメントどちらが正？」、ADR 0014/0015 相互参照欠落
- スコープ外: iteration_plan-7.md PlantUML state 構文は IT6 修正の教訓踏襲で良好

</details>

<details>
<summary>xp-user-representative (高: 4 / 中: 4 / 低: 5)</summary>

評価: Tracker の追跡詳細画面で「記録→通知→対応報告→履歴確認」までワンストップで完結する設計になっており、IT6 の主要懸念を大幅解消。一方で公開追跡画面 (荷主向け) への波及、Lost エスカ後の MasterAdmin 動線、newEstimatedArrival 等の仮値の業務妥当性が実運用に不安。

- 良い点: 更新理由必須化、例外履歴行内対応報告、Lost 自動エスカヘルプ文、料金内訳テーブル、法人バッジ Shipper 連動、Claim 時のみ荷受人確認
- 改善: H8-H10, M12-M15, L10-L14
- 懸念: Lost エスカ後の MasterAdmin 到達手段、例外重複記録防止、法人バッジ判定の境界条件 (スナップショット vs 動的)
- スコープ外: SonarQube 実機未実施 (T5)、UN/LOCODE 直入力 (IT2 から継続指摘)

</details>

## 関連ドキュメント

- IT7 計画: [iteration_plan-7.md](../development/iteration_plan-7.md)
- IT7 ふりかえり: [retrospective-7.md](../development/retrospective-7.md)
- IT7 完了報告書: [iteration_report-7.md](../development/iteration_report-7.md)
- ADR 0014 Snapshot ADT: [0014-aggregate-snapshot-adt.md](../adr/0014-aggregate-snapshot-adt.md)
- ADR 0015 Money 統一: [0015-billing-money-shared-domain.md](../adr/0015-billing-money-shared-domain.md)

## 更新履歴

| 日付 | 変更内容 | 著者 |
|------|---------|------|
| 2026-06-23 | IT7 マルチパースペクティブレビュー初版 (高 12 / 中 17 / 低 15) | XP 5 エージェント並列レビュー (xp-programmer / xp-tester / xp-architect / xp-technical-writer / xp-user-representative) |
