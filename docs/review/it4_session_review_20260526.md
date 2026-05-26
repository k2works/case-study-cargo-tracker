# コードレビュー結果 — IT4 セッション後続変更（cross-service 堅牢化 / H4 予約化プリセット / Code Smell 解消）

| 項目 | 内容 |
|------|------|
| レビュー日 | 2026-05-26 |
| 対象 | `git diff c1f9f3c3..HEAD -- apps/`（コミット a85c28c0 / 8c151dea / 773a3f8d、9 ファイル・約 160 行） |
| 手法 | developing-review（XP 5 エージェント並列レビュー） |

## レビュー対象

- **(1) bookingms 孤児イベント冪等スキップ**（a85c28c0）: `RouteConfirmedEventHandler` に `AggregateNotFoundException` の catch を追加し WARN + スキップ（US11 cross-service / ADR-0009・0010）+ ユニットテスト。
- **(2) frontend H4 見積→予約化プリセット**（8c151dea）: `QuotationDetailPage` の「予約化」で見積情報を React Router navigation state（`fromQuotation`）で渡し、`BookingFormPage` が `presetToFormValues` で初期化 + テスト。
- **(3) Code Smell 解消**（773a3f8d）: `OptimalRouteService.transshipmentCandidates` を Stream + `connects()` 抽出にリファクタ、frontend 3 件（`.at(-1)`・否定条件・JSX span）。

## 総合評価

3 つの変更はいずれも TDD を遵守し、振る舞いを変える変更にテストが併走している。`OptimalRouteService` のリファクタは**振る舞い不変性が保たれた模範的なリファクタ**（旧 `isBefore→continue` を新 `!isBefore→filter` に正しく反転、集約境界・ドメインサービス責務も維持）。SonarQube は backend/frontend 両 Quality Gate PASS・Code Smell 0。マージをブロックする欠陥はない。

一方、テスト技法（**ネガティブパス・境界値・無効同値**）に穴があり、tester が 3 件を高指摘とした。設計面では `CommandExecutionException` 一律 catch が旅程整合性違反（設計バグ）を握り潰す既存負債（IT4 レビュー M2）が未解決のまま、今回さらに失敗種別（`AggregateNotFoundException`）が増えた点を architect が高指摘。利用者視点では H4 の「初期入力された状態で開く」と現場体感（数量・品名は手入力が残る）のギャップを user-representative が高指摘とした。

## 改善提案（重要度順）

### 高（マージ前 / 次着手前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 | 対応方針 |
|---|------|------|--------|------|---------|
| H1 | catch 対象外の真の例外（汎用 `RuntimeException` 等）が**伝播する**ネガティブテストを追加。握りつぶし範囲を仕様固定する | RouteConfirmedEventHandler.java:51-63 | tester, architect | 現状は catch を広げても全テスト Green。冪等スキップ対象（2 種）と「伝播すべき真のエラー」の境界が未固定 | **修正（本セッション）** |
| H2 | `connects()` の接続境界「接続便の到着時刻＝乗り継ぎ便の出発時刻（等値・境界±0）」のテストを追加 | OptimalRouteServiceTest | tester | `!isBefore` → `isAfter` のミューテーションが生き残る（境界で候補に含めるか否かが未検証） | **修正（本セッション）** |
| H3 | H4 プリセットの無効同値クラス（`weightKg=null` で重量欄が空になる等）のテストを追加 | BookingFormPage.test.tsx | tester | 正常系のみ検証。null 等の欠損プリセットでフォームが壊れないことが未固定 | **修正（本セッション）** |
| H4 | `CommandExecutionException` 一律 catch が「状態ガード由来の正当な重複」と「旅程整合性違反（設計バグ）」を区別せず握り潰す。失敗種別を区別し、設計バグは可視化する | RouteConfirmedEventHandler.java:60-63 | architect | IT4 レビュー M2 の継承。今回 `AggregateNotFoundException` も増え、握りつぶし範囲が拡大 | **保留（IT8 品質改善 / retro M2 と統合）** |
| H5 | 予約化直後に営業が必ず手入力する「数量」「品名」が空欄で残ることを画面で明示（残り入力項目の明示・「※要入力」表示・カーソル初期位置） | 予約フォーム（見積からの予約化） | user-representative | 見積は数量・品名を持たないため引き継げない。「初期入力された状態で開く」期待とのギャップで、登録時に入力チェックではじかれ戸惑う | **保留（次フロントストーリー: 予約化 UX 改善）** |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 | 対応方針 |
|---|------|------|--------|---------|
| M1 | `QuotationDetailPage`→`BookingFormPage` 間の navigation state 契約（`fromQuotation`）を共有型に集約、または相互参照コメントを付す（送信側 `cargoType:string` と受信側 `CargoType` union の型不一致がコンパイルエラーにならない） | QuotationDetailPage.tsx:117 / BookingFormPage.tsx:44 | programmer, technical-writer, architect | **部分対応（本セッション：相互参照コメントを付与）。共有型化は次フロントストーリーへ** |
| M2 | `AggregateNotFoundException` 冪等スキップを監視可能にする（メトリクスカウンタ / 件数閾値アラート）。「正常な再生」と「データ不整合の恒常スキップ」を切り分け、経路紐付け取りこぼしを業務担当者が画面でも気づける導線（予約一覧で経路提案中へ未進行を識別等） | RouteConfirmedEventHandler.java:54-59 / 経路設計ワークベンチ・予約一覧 | programmer, architect, user-representative | **保留（IT8 監視整備 / 取りこぼし導線は新ストーリー）** |
| M3 | 見積詳細画面に「貨物種別」を表示（予約化のプリセットに使うのに詳細の項目一覧に出ていない＝画面と引き継ぎ情報の食い違い） | QuotationDetailPage.tsx 詳細 dl | user-representative | **保留（次フロントストーリー、軽微）** |
| M4 | UI 設計書（ui_design.md シナリオ A）の「予約情報を補完」が実際の自動プリセット挙動とずれて読める。設計書を実挙動に整合 | ui_design.md | technical-writer | **保留（設計ドキュメント整合タスク）** |
| M5 | ADR-0009 の「Saga 経由でコマンド発行」記述と実装（ハンドラ直発行・コレオグラフィ）の設計ドリフトが継続。**実装ではなくドキュメントを直す** | AssignRouteToCargoCommand.java:11 / ADR-0009 | architect | **保留（ADR-0009 追記。iteration_plan-4 に実装注は記載済み）** |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| L1 | 所要日数算出（積込日〜荷降し日の暦日数）が frontend `estimatedDays` と backend `toCandidate` で重複実装（言語境界のため即統合は不要） | BookingDetailPage.tsx:39 | programmer |
| L2 | `BookingDetailPage.estimatedDays` の空配列ガードは呼び出し元が `legs.length > 0` で保護済みのため到達不能。削除でシンプル化 or 到達テスト | BookingDetailPage.tsx:36-40 | tester |
| L3 | H4 navigation state はリロードで揮発（直接 URL アクセス時はプリセットなし）。許容仕様ならコメント | BookingFormPage.tsx:127 | architect |
| L4 | 品名がプリセットされない根拠（`Quotation` が品名を持たない）をコメントで明示 | QuotationDetailPage.tsx:120 | programmer |
| L5 | プリセット値が「見積から引き継いだ値」と分かる軽い表示で営業の安心感向上 | 予約フォーム | user-representative |
| L6 | 危険物・冷凍の追加情報（IMO/温度）も引き継ぎたい（要・見積側でのデータ保持の業務判断） | 見積→予約化 | user-representative |

## 欠けている業務考慮（user-representative、新ストーリー候補）

| # | 業務考慮 | 重要度 | 対応方針 |
|---|---------|--------|---------|
| B1 | **失効・草稿の見積を予約化できてしまう**。本筋は「受諾済み見積を予約化」。失効見積の予約化は古い条件で輸送を引き受ける事故につながる。予約化を受諾済みに絞る or 警告 | 高（業務リスク） | **保留（新ストーリー: 予約化の業務ルール）** |
| B2 | **同一見積の二重予約の歯止めが無い**。予約化が手軽になったぶん、再押下・別担当による重複登録リスクが上がる。「予約化済み」状態表示 | 中 | **保留（新ストーリー）** |

## 矛盾事項

なし（各視点の指摘は相補的で、相反する提案はなかった）。OptimalRouteService リファクタは全視点で高評価。

## 対応方針サマリー

- **本セッションで即対応**: H1・H2・H3（テスト技法の穴をふさぐネガティブ/境界値/無効同値テスト）、M1（navigation state 契約の相互参照コメント）。
- **IT8 品質改善 / retro へ**: H4（catch の失敗種別区別、M2 継承）、M2（監視可能性）。
- **新ストーリー候補**: H5（予約化 UX：未入力項目の明示）、M3（見積詳細に貨物種別）、B1（失効見積の予約化抑止）、B2（二重予約の歯止め）。
- **設計ドキュメント整合**: M4（ui_design.md）、M5（ADR-0009 Saga 記述ドリフト）。
- **スコープ外（継続観察）**: US12 料金概算の確定経路カード表示、経路設計者ロールの遷移先導線。

## エージェント別サマリー

| エージェント | 高 | 中 | 低 | 主要指摘 |
|------------|----|----|----|---------|
| xp-programmer | 0 | 2 | 2 | navigation state 型契約の保証なし・冪等スキップの監視可能性・所要日数 DRY |
| xp-tester | 3 | 1 | 1 | ネガティブパス未検証・接続境界（到着=出発）未検証・H4 無効同値未検証・空配列ガードのデッドコード |
| xp-architect | 1 | 1 | 2 | `CommandExecutionException` 握りつぶしの負債拡大・ADR-0009 設計ドリフト（ドキュメント修正推奨）・リファクタは模範的 |
| xp-technical-writer | 0 | 2 | 1 | UI 設計書と自動プリセット挙動のズレ・navigation state 契約が暗黙的・用語/API ドキュメントは指摘なし |
| xp-user-representative | 1 | 2 | 2 | 予約化後の数量・品名空欄の明示・取りこぼし導線・見積詳細に貨物種別・失効/重複予約の歯止め |

## 備考

- 著者: k2works（developing-review / XP 5 エージェント）
- 関連コミット: a85c28c0・8c151dea・773a3f8d
- 関連 ADR: ADR-0009（cross-service イベント連携と Saga）、ADR-0010（local-h2 トピック初期化・孤児イベント冪等スキップ）
- 関連ドキュメント: iteration_plan-4.md（Section 5 / 実装注）、it4_routing_review_20260526.md（IT4 本体レビュー・M2 の出所）
