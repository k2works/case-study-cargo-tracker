---
title: イテレーション 4 ふりかえり
description: IT4（経路設計後半・予約確定）の KPT ふりかえり。25 SP 完了、E2E 全通過、SonarQube PASS。
---

# イテレーション 4 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 4 |
| **期間** | Week 7-8（2026-05-16 〜 2026-05-18） |
| **ゴール** | 経路設計後半（US08〜US12）・予約確定（US13/US14）を完成させ Release 1.0 MVP を達成する |
| **計画 SP** | 25 |
| **実績 SP** | 25 |
| **達成率** | 100% |

---

## 結果サマリー

### 完了ストーリー（全 8 件）

| ID | ユーザーストーリー | SP | 結果 |
|----|------------------|----|----|
| TI03 | IT4 第 0 スプリント（TransitEdge 型安全化・ADR-0010/0011） | 2 | ✅ 完了 |
| US08 | 経路候補を算出する | 8 | ✅ 完了 |
| US09 | 経路を選択・確定する | 3 | ✅ 完了 |
| US10 | 経路条件を調整して再算出する | 3 | ✅ 完了 |
| US11 | 経路情報を予約に紐付ける | 2 | ✅ 完了 |
| US12 | 確定経路を荷主に通知する | 3 | ✅ 完了 |
| US13 | 予約を確定する | 3 | ✅ 完了 |
| US14 | 追跡番号を発行する | 1 | ✅ 完了 |
| **合計** | | **25** | **100%** |

### 品質メトリクス

| メトリクス | 結果 |
|-----------|------|
| バックエンド ユニットテスト | 211 件 PASS |
| フロントエンド ユニットテスト | 108 件 PASS |
| Playwright E2E テスト | 9/9 PASS（15.7s） |
| SonarQube Quality Gate | PASS（new_coverage 81.6%・violations 0） |

---

## KPT

### Keep（うまくいったこと）

#### K1: ADR 駆動の第 0 スプリントが IT4 全体の土台になった

IT3 ふりかえりの T1（PoC 処理方針の ADR 記録）を忠実に実施した。ADR-0010（OptimalRouteService 廃棄・RouteCandidateFinder 新設）と ADR-0011（CarrierMovement / TransitEdge 責務分離）を IT4 着手前に合意したことで、US08 本実装の方向が明確になり手戻りゼロで完了できた。

#### K2: フィーチャバッファ計画が機能した

25 SP は基準ベロシティ（14.7 SP）の 1.7 倍という高スコープを、「US10・US12 をバッファとして後回し可能」と事前に識別した。実際にはすべて完了できたが、リスク認識を共有しながら実装を進められた。

#### K3: SonarQube 違反をゼロで完了

`@Deprecated` 抑制・unnamed pattern・空 catch の 3 件を修正し、violations 0 を維持。Quality Gate PASS を DoD に組み込むことで品質基準が形式化された（IT2 から継続）。

#### K4: Axon 5 / Spring Boot 3.3 の動作パターンが確立した

`@TargetEntityId`・`sendAndWait()`・Saga レス手動フローという IT4 固有の Axon 運用パターンを確立。同パターンは IT5 以降の追跡系 Aggregate にも適用できる。

#### K5: E2E テストが統合テストとして機能した

Playwright E2E（routing-workbench.spec.ts）が実際のシステム統合バグ（ゲートウェイルーティング欠落・コマンドルーティング不整合・DB カラムオーバーフロー）を検出し、コード修正のドライバになった。「E2E は仕様の実行可能な記述」という原則が実証された。

---

### Problem（うまくいかなかったこと）

#### P1: ゲートウェイルーティングに `/api/v1/routing/**` が欠落していた

`gatewayms/application.yml` の routingms predicates に `/api/v1/routing/**` が含まれておらず、経路候補が常に 0 件を返していた。E2E テストで発覚するまで気付かなかった。

#### P2: Axon コマンドに `@TargetEntityId` が付与されていなかった

`AssignRouteToCargoCommand`・`ConfirmBookingCommand`・`IssueTrackingNumberCommand`・`NotifyRouteCommand` の 4 コマンドで `@TargetEntityId` が欠落。Axon がコマンドを正しい Aggregate にルーティングできず、状態遷移が発生しなかった。

#### P3: `commandGateway.send()` を使用したため例外が握り潰されていた

`assign-route`・`confirm`・`issue-tracking` で `send()`（fire-and-forget）を使用したため、Aggregate 内の例外がコントローラ層に伝播しなかった。`sendAndWait()` に変更して解決。

#### P4: `tracking_number` カラムサイズが不足していた

`TRK-YYYYMMDD-XXXXXXXX`（22 文字）に対してカラムが `VARCHAR(20)` だったため、`DataIntegrityViolationException` が発生。Flyway V005 で `VARCHAR(25)` に拡張して解決。ストーリー設計時にフォーマット長を検証する習慣が必要。

#### P5: E2E テストのナビゲーション記述がコンポーネント構造に依存していた

ダッシュボードカードとナビバーの両方に「荷主管理」リンクが存在するため `strict mode violation` が発生。また「予約管理」（カード）と「予約」（ナビ）のラベル不一致で `TimeoutError` が発生。ロケーター設計が脆弱だった。

---

### Try（次に試すこと）

#### T1: ゲートウェイルーティングをチェックリストに追加する

新しい Microservice エンドポイントを追加する際、`gatewayms/application.yml` の predicates 更新を DoD チェックリストに明示的に含める。IT5 以降の追跡系 API（`/api/v1/tracking/**`）追加時に適用する。

#### T2: Axon コマンド作成時の `@TargetEntityId` を必須化する

`BookCargoCommand` 以外のコマンドにも `@TargetEntityId` が必要であることをコーディング規約（または ADR）として明記する。コマンドクラス新規作成時に自動的に確認できるよう、テンプレートまたはアーキテクチャテストを IT5 で導入する。

#### T3: 非同期 vs 同期コマンドの指針を ADR に記録する

`send()`（fire-and-forget）は副作用のない通知系のみに使用し、状態遷移を伴うコマンドは `sendAndWait()` を使用する。この判断基準を ADR に記録し、コードレビューでチェックする。

#### T4: カラムサイズを設計フェーズで検証する

新しいフィールド（特に生成 ID・フォーマット付き文字列）のカラムサイズを、イテレーション計画の設計セクションで明示的に見積もる。`TRK-YYYYMMDD-XXXXXXXX` のようなフォーマットは事前に文字数を数える。

#### T5: E2E ロケーターにデータ属性を活用する

`data-testid` 属性を重要な UI 要素に付与することで、コンポーネント構造変更に耐えるロケーターを作成する。`getByRole('link', { name: ... })` はナビバーとカードの両方にマッチするリスクがあるため、`getByTestId` を優先する。IT5 の追跡系 UI 実装時に適用する。

---

## ベロシティ実績

| イテレーション | 計画 SP | 実績 SP | 達成率 |
|--------------|---------|---------|--------|
| IT1 | 16 | 14 | 88% |
| IT2 | 14 | 14 | 100% |
| IT3 | 16 | 16 | 100% |
| IT4 | 25 | 25 | 100% |
| **平均（IT2-4）** | | **18.3** | **100%** |

**IT5 推奨ベロシティ**: 14〜16 SP（IT4 は特例スコープのため IT1-3 実績 14.7 SP を基準に設定）

---

## IT5 への申し送り事項

### 持越しタスク

| タスク | 元 ID | 優先度 |
|--------|--------|--------|
| US04-r1 荷主 ID マスタ検索 | IT3 繰越し | 低（IT5 以降） |
| US05-r1 IMO クラスドロップダウン化 | IT3 繰越し | 低（IT5 以降） |
| US24-r1 出発日 < 到着日チェック強化 | IT3 繰越し | 低（IT5 以降） |
| TI03: US08/09/10/11/12/13/14 GitHub Issues クローズ | IT4 完了処理 | 要対応 |

### IT5 で注意すべきリスク

1. **追跡系 Aggregate の Axon 設計**: `@TargetEntityId` と `sendAndWait()` を最初から正しく適用する（P2/P3 の再発防止）。
2. **追跡系 API のゲートウェイ登録**: `/api/v1/tracking/**` を gateway に追加することを DoD に含める（P1 の再発防止）。
3. **trackingms 新規サービス起動**: IT5 で trackingms を新設する場合、Spring Boot 起動設定・ポート設定・gateway 登録を一体で実施する。

### 申し送りメモ

- IT4 は 25 SP と過去最大スコープだったが、ADR 駆動の第 0 スプリントと E2E テストのドライバ活用により 100% 達成。
- Release 1.0 MVP（Phase 1: 予約・経路設計）が IT4 で完成。
- IT5〜IT8 は Phase 2（追跡・精算）。新しい Bounded Context（trackingms・billingms）の設計判断を IT5 計画前に ADR として記録することを推奨。

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-18 | 初版作成（IT4 完了後） | AI Agent（XP PM） |
