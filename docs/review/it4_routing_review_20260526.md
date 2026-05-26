# コードレビュー結果 — IT4 経路設計（US08/US09/US11/US12）

| 項目 | 内容 |
|------|------|
| レビュー日 | 2026-05-26 |
| 対象 | IT4 経路設計の実装（`git diff 772c66e6..HEAD`、9 コミット・約 3,170 行） |
| 手法 | developing-review（XP 5 エージェント並列レビュー） |

## レビュー対象

- routingms: OptimalRouteService・RouteSearchSpecification・RouteCandidate・RouteLeg、RouteCalculationService・RouteSelectionService・RouteConfirmationService、RouteController + dto
- bookingms: Cargo（経路割当・荷主通知）、RouteConfirmedEventHandler、CargoProjectionsEventHandler、BookingSagaManager、CargoLeg + Mapper、Flyway V6/V7
- shared: RouteConfirmedEvent
- frontend: routing feature（待ちリスト・ワークベンチ S14）、BookingDetailPage、Navigation
- E2E: cross-service.spec.ts（US11 追加）
- docs: API カタログ・data-model・iteration_report-4・retrospective-4

## 総合評価

TDD（Red-Green-Refactor、インサイドアウト）が全レイヤで徹底され、ドメインの不変条件を算出側（OptimalRouteService）と受理側（Cargo）の双方で多重検証する堅実な設計。ADR-0009 の cross-service 方針（shared 配置・tracking 購読・processor 束ね・冪等受信・自己完結イベント）にも忠実で、新規コードのカバレッジは 83〜100% と高い。一方、(1) 計画記述（Saga 経由でコマンド発行）と実装（ハンドラ直接発行・Saga は受動観測）の乖離、(2) 冪等スキップ分岐・費用タイブレークの未テスト、(3) US12 荷主通知画面の通知内容欠落（所要日数・料金概算）、(4) ワークベンチの操作順序の分かりにくさ、が改善点として挙がった。

## 改善提案（重要度順）

### 高（マージ前に対応すべき）

| # | 提案 | 箇所 | 指摘元 | 理由 | 対応 |
|---|------|------|--------|------|------|
| H1 | 計画/報告書の「Saga 経由でコマンド発行」を実装（ハンドラ直接発行・Saga は CargoRoutedEvent を受動観測）に整合 | iteration_plan-4.md L149/L250、RouteConfirmedEventHandler | programmer, architect | event-source 競合回避のための妥当な実装。設計記述とのズレが保守者を混乱させる | 対応（計画・報告書に判断理由を明記） |
| H2 | 冪等スキップ分岐（CommandExecutionException を握りつぶす）の自動テストを追加 | RouteConfirmedEventHandler L51-57 | programmer, tester, architect | 重複配信・再処理対策の中核が未カバー。将来のリファクタで静かに壊れる | 対応（例外が伝播しないテストを追加） |
| H3 | 推奨順ソートの「日数同・費用差」タイブレークのテストを追加 | OptimalRouteServiceTest | tester | 費用比較分岐がミューテーションで生き残る | 対応（タイブレークのテストを追加） |
| H4 | US12 荷主通知画面に通知内容（所要日数・料金概算）を表示。受入条件 2 欠落 | BookingDetailPage | user-representative | 営業が荷主に説明する主要項目が画面に出ていない | 部分対応（所要日数を追加。料金概算は bookingms 未保持のため次イテレーション課題として記録） |
| H5 | API カタログ /confirm 行に route_design_request → ASSIGNED 遷移を追記 | architecture_backend.md L1128-1129 | technical-writer | /select は副作用記載済みだが /confirm は未記載で非対称 | 対応 |
| H6 | ワークベンチの「確定」「紐付け」の操作順序を明確化 | RouteDesignWorkbenchPage | user-representative | 並列ボタンで手順が不明、押し忘れ事故を招く | 対応（番号付けとガイド文を追加） |

### 中（対応推奨）

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| M1 | select/confirm の非トランザクション境界（publish→updateStatus の非原子性、再算出依存）を明示またはトランザクション化 | RouteSelectionService・RouteConfirmationService | programmer, architect |
| M2 | 冪等スキップが状態ガード由来の正当な重複と旅程整合性違反（設計バグ）を区別できず後者を握り潰す | RouteConfirmedEventHandler L53 | programmer, architect |
| M3 | OptimalRouteService の費用計算（区間×20万 + 日数×4万）の根拠を Javadoc/ADR に明記し、費用算出の独立テストを追加 | OptimalRouteService L30-31 | programmer |
| M4 | estimatedDays の日付丸め仕様をテストで固定 or LocalDateTime 比較を検討 | OptimalRouteService L116-118 | programmer |
| M5 | cross-service 結合経路（RouteConfirmedEventHandler→Cargo）の Testcontainers Kafka 統合テストを CI 常時実行で追加 | bookingms | tester, architect |
| M6 | RouteController クラス Javadoc の未来形（「本 Controller に追加する」）を現在形へ更新 | RouteController L24-25 | technical-writer |
| M7 | GET /route の応答が Leg のリストである旨を API カタログに明示 | architecture_backend.md L1106 | technical-writer |
| M8 | ArchUnit テストが 0 件。依存方向を機械的に固定する安全網を追加 | 全サービス | architect |

### 低（改善の余地あり）

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| L1 | transshipmentCandidates は O(n²) 全探索。件数前提を Javadoc に明記 | OptimalRouteService L64-88 | programmer |
| L2 | select/confirm の sequence は呼び出し時点の推奨順依存。@param に注意書きを追加 | RouteController・各サービス | programmer, technical-writer |
| L3 | RouteCandidate.departureTime() がデッドコードの可能性 | RouteCandidate | tester |
| L4 | confirm の 202 Accepted（非同期紐付け）を API カタログに注記 | architecture_backend.md | technical-writer |
| L5 | 「旅程（itinerary）」と「経路（route）」の用語をユビキタス言語で統一 | 全般 | technical-writer |
| L6 | fetchRouteDesignRequests のクライアント側ソートは件数増大時サーバー側へ | routingApi.ts L41 | programmer |

## 矛盾事項

なし（各視点の指摘は相補的で、相反する提案はなかった）。

## 対応方針

- 本レビューで H1・H2・H3・H5・H6 を即時対応、H4 は所要日数表示を追加（料金概算は bookingms 未保持のため M3 と合わせて次イテレーション課題）。
- M1・M2・M5・M8（トランザクション境界・例外判別・Testcontainers Kafka 統合テスト・ArchUnit）は IT8 品質改善 / retro Try で対応する技術的負債として記録。
- 低（L1-L6）は余力で対応。

## エージェント別サマリー

| エージェント | 高 | 中 | 低 | 主要指摘 |
|------------|----|----|----|---------|
| xp-programmer | 2 | 4 | 3 | 冪等スキップ未テスト・計画乖離・費用マジックナンバー |
| xp-tester | 2-3 | 1 | - | cross-service 結合の CI 未カバー・費用タイブレーク未検証・冪等分岐未テスト |
| xp-architect | 2 | 4 | 1 | Saga 非経由の事実と記述乖離・select/confirm 二重再算出・ArchUnit 不在 |
| xp-technical-writer | 1 | 2 | 2 | /confirm の ASSIGNED 遷移記載漏れ・Javadoc 未来形・/route 応答粒度 |
| xp-user-representative | 2 | - | - | 荷主通知の通知内容欠落（料金・所要日数）・ワークベンチ操作順序 |
