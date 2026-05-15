---
title: イテレーション 2 完了報告書
description: 貨物予約 (Cargo Aggregate)、危険物・冷凍貨物、航海スケジュール (Voyage Aggregate)、IT1 持越し (アカウントロック・ログアウト・E2E) の完了報告
published: true
date: 2026-05-15T00:00:00.000Z
---

# イテレーション 2 完了報告書

## 1. プロジェクト概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 2 / 8 |
| **期間** | 2026-05-13 〜 2026-05-15（計画 2026-05-28〜06-10 を前倒し完了） |
| **ゴール** | bookingms に `Cargo` Aggregate（Axon 5.1 Event Sourcing）を導入し貨物予約を実装。routingms を新規起動し航海スケジュール新規登録を実装。IT1 持越し（アカウントロック・ログアウト・E2E）を完了 |
| **チーム** | 開発者 1 名 + AI エージェント（Claude Code） |

---

## 2. 指標

### ベロシティ

| 項目 | 値 |
|------|-----|
| 計画 SP | 14 |
| 実績 SP | 14 |
| 達成率 | 100% |
| 持越し | 0 SP（PIT 75% / data-model.md 同期はバッファ枠で IT3 へ正式持越し） |

### バーンダウンチャート

```mermaid
xychart-beta
    title "リリースバーンダウン（IT2 完了反映）"
    x-axis ["開始", "IT1", "IT2", "IT3", "IT4", "IT5", "IT6", "IT7", "IT8"]
    y-axis "残 SP" 0 --> 106
    line "計画" [106, 90, 76, 60, 35, 24, 19, 13, 0]
    line "実績" [106, 92, 78]
```

### ベロシティチャート

```mermaid
xychart-beta
    title "ベロシティ推移"
    x-axis ["IT1", "IT2"]
    y-axis "SP" 0 --> 20
    bar "計画" [16, 14]
    bar "実績" [14, 14]
```

---

## 3. テスト結果

| カテゴリ | テスト数 | 成功 | 失敗 | スキップ |
|---------|---------|------|------|---------|
| authms（ユニット + 統合 + Smoke）| 81 | 81 | 0 | 0 |
| bookingms（ユニット + 統合 + Smoke）| 53 | 53 | 0 | 0 |
| routingms（ユニット + 統合 + Smoke）| 14 | 14 | 0 | 0 |
| **バックエンド合計** | **148** | **148** | **0** | **0** |
| フロントエンド（Vitest）| 79 | 79 | 0 | 0 |
| E2E（Playwright）| 3 | 3 | 0 | 0 |
| **総合計** | **230** | **230** | **0** | **0** |

- バックエンド: `./gradlew :authms:test :bookingms:test :routingms:test` BUILD SUCCESSFUL
- フロントエンド: `vitest run` 15 テストファイル / 79 テストケース ALL PASSED
- E2E: `login-shipper.spec.ts` / `login-booking.spec.ts` / `login-voyage.spec.ts` 全 GREEN（10.5 秒）

### コード品質メトリクス（SonarQube）

| プロジェクト | Quality Gate | new_coverage | new_violations | Bug | Vulnerability | LOC |
|------------|-------------|-------------|----------------|-----|---------------|-----|
| Backend (cargo-tracker-backend)  | **PASS** ✅ | **88.6%** | 0 | 0 | 0 | 3,243 |
| Frontend (cargo-tracker-frontend) | **PASS** ✅ | **83.8%** | 0 | 0 | 0 | 1,764 |

CI - Backend / CI - Frontend / CI - E2E すべて success（commit 30c91659）。

---

## 4. 実施内容と評価

### ストーリー別完了状況

| ID | ユーザーストーリー | SP | 状態 | 備考 |
|----|-------------------|----|------|------|
| US00-r1 | アカウントロックを有効化する | 1 | 完了 | 5 回連続失敗で 30 分ロック、`users.lock_until` 管理 |
| US00-r2 | ログアウトを実装する | 1 | 完了 | `user_sessions.revoked = TRUE`、無効化トークンで 401 |
| US-UI-r | フロントエンド E2E テストを整備する | 1 | 完了 | Playwright で「ログイン → 荷主登録」、CI 統合済み |
| US04 | 貨物予約を登録する | 5 | 完了 | `Cargo` Aggregate（Axon 5.1）+ POST/GET API + UI |
| US05 | 危険物・冷凍貨物の予約を登録する | 3 | 完了 | `HazardInfo` / `TemperatureCondition` 値オブジェクト、境界値テスト含む |
| US24 | 航海スケジュールを新規登録する | 3 | 完了 | routingms 新規起動、`Voyage` Aggregate + POST/GET API + UI |
| **合計** | | **14** | | 全件完了 |

### 受入条件の達成状況

| 成功基準 | 状態 |
|---------|------|
| `POST /api/v1/auth/login` で 5 回連続失敗するとアカウントが 30 分ロックされる | 達成 |
| `POST /api/v1/auth/logout` で `user_sessions.revoked = TRUE` に更新され、以降の認証で 401 を返す | 達成 |
| `POST /api/v1/bookings` で貨物予約を登録でき、`bookingId` と「PRELIMINARY」状態が発行される | 達成 |
| 貨物種別「HAZARDOUS」を選択すると `HazardInfo` の入力が必須となる（API + UI） | 達成 |
| 貨物種別「REFRIGERATED」を選択すると `TemperatureCondition` の入力が必須となる（API + UI） | 達成 |
| routingms サービスが起動し、Swagger UI / Actuator が応答する | 達成 |
| `POST /api/v1/voyages` で `Voyage` Aggregate を登録でき、寄港地が複数登録できる | 達成 |
| `Cargo` Aggregate が Axon Event Sourcing で実装され、`CargoBookedEvent` がイベントストアに永続化される | 達成 |
| `CargoProjectionsEventHandler` が `cargo_summary` Read Model を更新する | 達成 |
| Playwright で「ログイン → 荷主登録」シナリオの E2E テストが GREEN | 達成（3 シナリオ） |
| ADR-0007（Event Sourcing 導入方針）が作成される | 達成（ADR-0008 も追加） |
| PIT カバレッジ（バックエンド集約）が CI で計測される | **IT3 持越し** |
| 行カバレッジが副指標として計測される | 達成（Backend 88.6% / Frontend 83.8%） |
| Checkstyle / SpotBugs が CI で自動チェックされ、PR 単位でブロックされる | 達成（SonarQube Quality Gate PASS で実質的に達成） |

### IT2 中に発生した重要な技術的発見

| 発見 | 影響 | 対応 |
|------|------|------|
| Axon 5.1 `@EventSourcedEntity` 単独では Spring Boot で Command Handler が CommandBus に登録されない | bootRun / bootJar で `NoHandlerForCommandException`、E2E で発覚 | ADR-0008 を作成し `@EventSourced` (Spring stereotype) + `@Profile("!springboot-integration-test")` を採用 |
| `PooledStreamingEventProcessor` が `TOKENENTRY` テーブル不在で起動失敗 | local-h2 / local-docker / heroku の 3 環境で発生 | `application-{local-h2,local-docker,heroku}.yml` で `subscribing` モードに切替 |
| 統合テストでの `@MockitoBean CommandGateway` が Aggregate 登録の死角を生んでいた | E2E で初めて発覚、テスト戦略の改善が必要 | `BootSmokeTest` を追加し SpringBootTest と異なる Bean 解決順を構造的にカバー |

---

## 5. 追加タスク（SP 外）

イテレーション期間中に計画外で実施した作業です。

| タスク | 内容 |
|--------|------|
| ADR-0008 作成 | Axon 5.1 + Spring Boot 4 統合パターンの具体決定 |
| local-docker プロファイル全サービス対応 | apps/docker-compose.yml に authms/routingms/gatewayms 追加、init-databases.sh の psql `--dbname` 修正、flyway-database-postgresql 追加 |
| Spring Boot DevTools の有効化 | 4 マイクロサービスで bootRun 自動再起動（実測 485ms） |
| gulp タスクの整理 | Docker 系を `local-docker:*` 5 タスクに統一（build / up / down / clean / smoke） |
| deploy:dev に routingms 追加 | Dockerfile.heroku 新規 + push/release/logs/config タスク追加 |
| 全違反 136 件解消 + カバレッジ向上 | SonarQube Quality Gate PASS のため Backend 27件 + Frontend 4件 + Security Hotspot 1件 + テスト追加 |
| CI - E2E 修正 | npm ci 不一致 2 段階対応（macOS → Docker Linux で lock 再生成） |
| Heroku PooledStreamingEventProcessor 対応 | bookingms / routingms の application-heroku.yml に subscribing モード設定 |
| XP 5 エージェント並列レビュー実施 | 41 件の改善提案（高 13 / 中 19 / 低 9）と矛盾事項 2 件を統合 |

---

## 6. フェーズ・累計進捗

### イテレーション進捗

| イテレーション | 計画 SP | 実績 SP | 達成率 | 状態 |
|---------------|---------|---------|--------|------|
| IT1 | 16 | 14 | 88% | 完了 |
| IT2 | 14 | 14 | 100% | 完了 |
| IT3 | 16 | - | - | 未着手（US25 繰越し含む） |
| IT4 | 25 | - | - | 未着手 |
| IT5 | 11 | - | - | 未着手 |
| IT6 | 5 | - | - | 未着手 |
| IT7 | 6 | - | - | 未着手 |
| IT8 | 13 | - | - | 未着手 |
| **累計** | **106** | **28** | **26%** | |

### フェーズ進捗

| フェーズ | 計画 SP | 完了 SP | 進捗率 | 状態 |
|---------|---------|---------|--------|------|
| 認証基盤（Phase 0） | 8 | 8 | 100% | 完了（IT2 で US00-r1/r2 完了し全体充足） |
| Phase 1（予約・経路設計） | 57 | 20 | 35% | 進行中（US02/US03/US04/US05/US24 + US-UI-r 完了） |
| Phase 2（追跡・精算） | 35 | 0 | 0% | 未着手 |

### IT3 への持越し事項

| タスク | 理由 |
|--------|------|
| PIT 75% 主指標導入（IT2 タスク 7.2） | テスト戦略の主指標達成のため。`pitest` プラグイン + CI 統合 |
| `data-model.md` に `users.lock_until` / `users.failed_attempts` 反映 | 実装と設計ドキュメントの同期 |
| `apps/frontend/e2e/README.md` / 運用手順書 §7 の最新化 | レビュー指摘 H8（IT2 完了処理の延長で対応可） |
| ADR-0007 のクロスリンク（ADR-0008 への置換注記） | レビュー指摘 H9 |
| 業務的入力検証の改善ストーリー | レビュー指摘 H10-H13。荷主インクリメンタルサーチ、IMO/UN 番号バリデーション、日付・寄港地検証 |
| ArchUnit によるパッケージ間依存検証 | レビュー指摘 H6。trackingms / handlingms 着手前必須 |
| Controller の Assembler 分離 / Form の useReducer or react-hook-form 化 | レビュー指摘 H1/H2 |

---

## 7. レビューと品質保証

### XP 5 エージェント並列レビュー結果

| エージェント | 評価サマリー |
|------------|------------|
| xp-programmer | ADR-0007/0008 に忠実で責務分離は明快。Controller DTO→VO 変換肥大化と Form 重複に技術的負債 |
| xp-tester | 値オブジェクト境界・整合性検証は徹底。PIT 未稼働、Aggregate ファクトリ側検証薄い、@MockitoBean 死角が課題 |
| xp-architect | 承認可（条件付き）。ADR 連鎖機能、CQRS 境界清潔、routingms 追加容易。ArchUnit 不在・Spring 依存漏洩が負債 |
| xp-technical-writer | ADR / 計画書は非常に高水準。e2e/README と運用手順書 §7 に実装追随ラグ |
| xp-user-representative | 「歩ける状態」到達は高評価。業務利用にはマスタ連動・整合性チェック・エラーメッセージ改善が必須 |

詳細: [IT2 実装成果物レビュー](../review/IT2_実装成果物_review_20260515.md)

### Quality Gate 通過

- 両プロジェクト（cargo-tracker-backend / cargo-tracker-frontend）で SonarQube Quality Gate **PASS**
- new_coverage / new_violations / new_duplicated_lines_density / new_security_hotspots_reviewed すべて OK

---

## 8. ふりかえり

[イテレーション 2 ふりかえり](./retrospective-2.md) を参照。

KPT サマリー:
- **Keep**: ADR ベースの意思決定 / E2E 駆動の死角発見 / 計画外バッファ活用 / Clean as You Code 正面突破 / CI/Heroku 安定稼働 / スコープ判断の透明性
- **Problem**: 計画と実機の乖離（Axon Event Processor） / 試行錯誤コミット多発 / PIT 主指標未達 / data-model.md 更新漏れ / 業務利用に耐える入力検証欠如 / ドキュメント陳腐化
- **Try**: クロスプラットフォーム前倒し検証 / ADR リスク欄活用 / PIT を IT3 タスク 1 で実行 / 業務的受入チェックリスト化 / ドキュメント連動コミット規律 / 計画外タスク分類記録

---

## 9. 関連ドキュメント

- [リリース計画](./release_plan.md)
- [イテレーション 2 計画](./iteration_plan-2.md)
- [イテレーション 2 ふりかえり](./retrospective-2.md)
- [IT2 実装成果物レビュー](../review/IT2_実装成果物_review_20260515.md)
- [ADR-0007 Axon 5.1 Event Sourcing API](../adr/0007-axon-5-event-sourcing-api.md)
- [ADR-0008 Axon 5.1 Spring Boot 統合パターン](../adr/0008-axon-5-spring-boot-integration-pattern.md)
- [テスト戦略](../design/test_strategy.md)

---

## 10. 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-15 | 初版作成 | AI Agent（XP PM） |
