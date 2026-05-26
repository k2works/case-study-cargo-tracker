# イテレーション 4 完了報告書

## プロジェクト概要

| 項目 | 内容 |
|------|------|
| **プロジェクト名** | 国際貨物輸送管理システム（take-5） |
| **イテレーション** | IT4（経路設計） |
| **期間** | 2026-07-02 〜 2026-07-15（2 週間） |
| **ゴール** | 経路設計者が経路候補を算出・選択・確定し、確定経路を予約に紐付けて荷主へ通知する。Phase 1 完了・Release 1.0 MVP を達成する。 |

### 要員

| 役割 | 担当 |
|------|------|
| 開発者 | k2works（AI ペアプログラミング） |

## 指標

### ベロシティ

| 項目 | 値 |
|------|-----|
| 計画 SP（コミット） | 11 |
| 完了 SP | 13（US08:5 / US09:2 / US11:2 / US12:2 + ストレッチ US10:2） |
| 達成率 | 118%（コミット 11 SP 達成 + ストレッチ US10 を達成） |
| 前回ベロシティ | 10 SP |

### バーンダウン（リリース）

```mermaid
xychart-beta
    title "リリースバーンダウン（実績）"
    x-axis ["開始", "IT1", "IT2", "IT3", "IT4"]
    y-axis "残 SP" 0 --> 80
    line "実績" [76, 66, 56, 46, 35]
```

Phase 1（41 SP）完了。累計 41/76 SP（54%）。

## テスト結果

### バックエンド

| サービス | 全体 LINE カバレッジ | 主要新規クラス |
|---------|---------------------|---------------|
| routingms | 91.4%（393/430） | OptimalRouteService 98.2% / RouteCalculationService・RouteSelectionService・RouteConfirmationService・RouteController 100% |
| bookingms | 84.4%（670/794） | CargoProjectionsEventHandler 100% / Cargo 88.0% / RouteConfirmedEventHandler 83.3% |

全サービスの `gradle check`（ユニット・Axon Test・統合・ArchUnit・JaCoCo）が PASS。

### テスト増分（IT4 新規追加）

| 区分 | テスト |
|------|--------|
| routingms ユニット | OptimalRouteServiceTest（8）・RouteCalculationServiceTest（3）・RouteSelectionServiceTest（3）・RouteConfirmationServiceTest（2）・RouteControllerTest（+4） |
| routingms 統合 | RouteDesignRequestMapperIntegrationTest（+1 状態更新） |
| bookingms Axon Test | CargoAggregateTest（+8：US11 経路割当 6・US12 通知 2）・BookingSagaManagerTest（+1） |
| bookingms ユニット | CargoProjectionsEventHandlerTest（+2）・RouteConfirmedEventHandlerTest（1）・CargoBookingControllerTest（+2） |
| bookingms 統合 | CargoLegMapperIntegrationTest（2） |
| フロント | routingApi（5）・RouteDesignListPage（3）・RouteDesignWorkbenchPage（3）・Navigation（+2）・BookingDetailPage（+2）。フロント合計 134 件 PASS |
| E2E | cross-service.spec.ts に US11（routingms→bookingms）を追加（env-gated、Playwright --list で検出確認） |

## 実施内容と評価

### ストーリー別完了状況

| ID | ストーリー | SP | 状態 |
|----|-----------|----|----|
| US08 | 経路候補を算出する | 5 | 完了 |
| US09 | 経路を選択・確定する | 2 | 完了 |
| US11 | 経路情報を予約に紐付ける | 2 | 完了 |
| US12 | 確定経路を荷主に通知する | 2 | 完了 |
| US10 | 経路条件を調整して再算出する（ストレッチ） | 2 | 完了（到着期限の上書き再算出） |

### US08 受入確認

- 航海スケジュールと出発地・目的地・期限を入力に経路候補を自動算出（OptimalRouteService）
- 寄港地の接続可能性を評価（直行便 + 1 経由までの探索、多段は IT8）
- 経路候補ごとに所要日数・経由港・費用・航海番号を表示
- 直行便を最優先に、所要日数→費用の推奨順でソート
- 期限内到達不可時は候補なし（空リスト）→ ワークベンチで条件緩和を警告

### US09 受入確認

- 経路候補一覧（経由港・所要日数・費用・航海番号）を確認し 1 件選択
- 選択で route_design_request の状態が ROUTE_SELECTED に遷移
- 候補なし時は条件調整（US10）へ誘導する警告を表示

### US11 受入確認

- 確定経路を予約に紐付ける操作（POST /routes/{id}/confirm）で RouteConfirmedEvent を Kafka 発行
- bookingms が tracking 購読 → BookingSagaManager → AssignRouteToCargoCommand → Cargo が CargoRoutedEvent 適用
- 予約状態が ROUTE_PROPOSED、経路設定状態が ROUTED に更新され、cargo_leg を確定
- IT3 の bookingms→routingms と対になる routingms→bookingms 逆方向 cross-service（ADR-0009）

### US12 受入確認

- 確定旅程（経由港・到着予定日）を予約詳細で確認（GET /bookings/{id}/route）
- 経路提案中の予約のみ「荷主に経路を通知」可能（NotifyRouteToShipperCommand）
- 通知送信記録（cargo_summary.route_notified_at）を登録

### 実装内容の要約

#### routingms（経路候補算出・選択・確定）

- `OptimalRouteService`（ドメインサービス）・`RouteSearchSpecification`・`RouteCandidate`・`RouteLeg`
- `RouteCalculationService` / `RouteSelectionService` / `RouteConfirmationService`
- `RouteController`：POST /calculate・GET /candidates・POST /select・POST /confirm
- `RouteConfirmedEvent` を EventGateway へ発行し Axon Kafka Publisher で cargo-events へ転送

#### bookingms（経路割当・荷主通知）

- `Cargo`：AssignRouteToCargoCommand（旅程検証 → CargoRoutedEvent）・NotifyRouteToShipperCommand
- `RouteConfirmedEventHandler`（route-confirmed プロセッシンググループ、Kafka tracking、冪等）
- `CargoProjectionsEventHandler`：CargoRoutedEvent で状態更新 + cargo_leg 確定、RouteNotifiedToShipperEvent で通知日時更新
- `BookingSagaManager`：CargoRoutedEvent で経路提案中フェーズへ継続
- Flyway V6（cargo_leg）・V7（route_notified_at）

#### shared / フロント

- `RouteConfirmedEvent`（routingms→bookingms の cross-service 契約）
- routing feature（待ちリスト・経路設計ワークベンチ S14）・ナビゲーション導線（H3）・予約詳細の確定経路表示と荷主通知（US12）

## 品質ゲート

- JaCoCo：新規コードの行カバレッジは全クラス 80% 以上（routingms 91.4% / bookingms 84.4%）
- SonarQube Quality Gate のライブスキャンはローカル SonarQube 起動が前提のため、本報告では JaCoCo を代理指標とする（運用時に `operating-qt scan` で確認する）

## ADR

| ADR | タイトル | IT4 での適用 |
|-----|---------|-------------|
| [ADR-0009](../adr/0009-cross-service-event-saga.md) | cross-service イベント連携と Axon Saga | US11 の routingms→bookingms 逆方向 cross-service に適用 |

## フェーズ・累計進捗

### Phase 1 進捗

| イテレーション | 計画 SP | 実績 SP | 状態 |
|---------------|---------|---------|------|
| IT1 | 10 | 10 | 完了 |
| IT2 | 10 | 10 | 完了 |
| IT3 | 10 | 10 | 完了 |
| IT4 | 11 | 11 | 完了 |
| **Phase 1 計** | **41** | **41** | **完了（Release 1.0 MVP）** |

## 残課題（環境依存・次アクション）

- SonarQube Quality Gate のライブスキャン（ローカル SonarQube 起動が前提）
- cross-service E2E（US06/US11）のライブ実行（Kafka + 全サービスの再ビルド・起動が前提。spec は parse 検証済み）
- US10 の経由地追加・貨物種別変更による条件調整（本 IT は到着期限の上書きのみ実装。多段経由は IT8）

## ふりかえり

詳細は [retrospective-4.md](./retrospective-4.md) を参照。

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-26 | 初版作成（IT4 経路設計・Phase 1 完了） | k2works |
