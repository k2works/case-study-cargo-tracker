---
title: イテレーション 2 完了報告書
date: 2026-06-21
---

# イテレーション 2 完了報告書

## 概要

| 項目 | 内容 |
|------|------|
| イテレーション | IT2 |
| 期間 | 2026-07-06 〜 2026-07-19（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | 特殊貨物予約・経路設計引き渡し・航海スケジュール管理を完成させ Release 0.1 Internal Alpha リリース、US08 経路算出スパイクで Phase 2 最大リスクを早期検証 |
| 計画 SP | 12（基本 10 + スパイク別計上 2） |
| 実績 SP | 12 |
| 達成率 | 100% |

## ストーリー実績

| ID | ストーリー | 状態 | 計画 SP | 実績 SP |
|----|-----------|------|---------|---------|
| US05 | 危険物・冷凍貨物の予約を登録する | ✅ 完了 | 3 | 3 |
| US06 | 予約情報を経路設計者に引き渡す | ✅ 完了 | 2 | 2 |
| US24 | 航海スケジュールを新規登録する | ✅ 完了 | 3 | 3 |
| US25 | 既存航海スケジュールを更新する | ✅ 完了 | 2 | 2 |
| Spike US08 | 経路候補算出スパイク | ✅ 完了 | 2 | 2 |
| **合計** | | | **12** | **12** |

## バーンダウン

```mermaid
xychart-beta
    title "IT2 バーンダウン（実績）"
    x-axis ["開始", "Day 1"]
    y-axis "残 SP" 0 --> 12
    line "計画" [12, 0]
    line "実績" [12, 0]
```

## 成果物

### コミット履歴（34 件）

```text
8e8ac49a docs(it2): IT2 ふりかえりを実施（retrospective-2.md）
afd1a1ed docs: docs/index.md / mkdocs.yml / development/index.md を IT2 完了状態に更新
87743bb4 fix(it2): レビュー必須対応 5 件で IT2 完了承認ブロッカーを解消
4e0ab129 docs(review): IT2 実装のマルチパースペクティブレビュー結果を記録
c506ad2f docs(qt): SonarQube QG（Sonar way）確定とスキャン結果記録（IT2 タスク 0.8）
4ee3835a docs(release): Release 0.1.0 Internal Alpha の CHANGELOG とリリースゲート確認結果
e71ba68c feat(routing): US08 spike — DFS + 深さ制限の経路探索プロトタイプと ADR 0005
4834c5aa feat(routing): US24/25 タスク 3.7 — Voyage E2E + 形式バグ修正
70a6e28e feat(routing): US24/25 タスク 3.5+3.6 — VoyageController + Application Service + Twirl
c2010c5c feat(routing): US24/25 タスク 3.3+3.4 — Flyway V7 と ScalikeJdbcVoyageRepository
90f78905 feat(routing): US24/25 タスク 3.1+3.2 — Routing コンテキスト初期化とドメイン実装
202e5a08 test(e2e): US06 タスク 2.4 — 経路設計者引き渡しの Playwright E2E
7d9ddfa4 feat(booking): US06 タスク 2.3 — 経路設計者ダッシュボードに引き渡し済み一覧
86065aab feat(booking): US06 タスク 2.2 — 経路設計者引き渡しエンドポイント
7160accc feat(booking): US06 タスク 2.1 — Cargo.assignToRouting と BookingStatus 遷移ルール
174e5703 feat(booking): US05 UI 完成（Controller フォーム + Twirl + E2E POM）
c1567dee feat(booking): US05 BookingCommandService に冷凍貨物入力 + CargoSpec 必須検証を統合
a83ab520 feat(booking): US05 Flyway V6 + Cargo Repository に refrigeration マッピング追加
8e7c0bf4 feat(booking): US05 のドメイン基盤として RefrigerationSpec / CargoSpec.create を追加
5bce54c3 feat(ui): HomeController をロール別ダッシュボードに刷新（IT2 タスク 0.2）
99c47b61 test(infra): DbCleanupSupport trait を追加しテスト間の独立性を担保（IT2 タスク 0.6）
7cb62878 refactor(views): Twirl form テンプレートを formPage に改名（IT2 タスク 0.5）
00d1291a chore(hooks): pre-commit から scalafix を外し約 12 秒に短縮（IT2 タスク 0.4）
a0e40998 chore(coverage): scoverage 最低ゲートを 80% に復元（IT2 タスク 0.9 / IT1 H7）
77fdd4ee feat(persistence): 楽観ロック準備で version カラムを追加（IT2 タスク 0.11 / IT1 H5）
4f0a3211 feat(auth): 3 Controller の保護 Action に AuthenticatedAction を適用（IT2 タスク 0.1）
cabcaf53 fix(auth): admin シード資格情報を application.conf に外出し（IT1 H1 / IT2 タスク 0.10）
7a93ccd6 test(arch): ArchUnit ルール 4 で application 層の命名規約を強制
47738063 test(app): application 層 6 サービスのユニットテストを追加
4bad649b refactor(app): Controller の永続化・ビジネスロジックを application 層に移譲
f054e2c9 test(e2e): Playwright で IT1 機能の E2E ハッピーパスを追加
7d09ba70 docs(it2): タスク 0.7 ArchUnit ルール導入を完了マーク
93b13928 refactor(arch): ヘキサゴナル DDD パッケージ構成に統一し ArchUnit ルールを導入
c1a7d02d docs(it2): イテレーション 2 計画を作成し計画整合性検証を反映
```

### 規模

- 変更ファイル: **138 ファイル**
- 追加行数: **+5,354 行**
- 削除行数: **-422 行**

### 新規 ADR

- ADR 0005: 経路探索アルゴリズム選定（DFS + 深さ制限、IT2 spike → IT3 US08 で再評価）

### Flyway マイグレーション

| バージョン | 内容 |
|-----------|------|
| V5 | shipper / estimate / cargo に `version INTEGER NOT NULL DEFAULT 0` 追加（楽観ロック準備） |
| V6 | cargo に `refrigeration_min_temp` / `refrigeration_max_temp` / `refrigeration_unit` 追加 + CHECK 制約「3 カラム全 NULL / 全 NOT NULL」 |
| V7 | voyage（BIGSERIAL PK + voyage_number UK + version）+ carrier_movement（FK ON DELETE CASCADE + UNIQUE(voyage_id, seq_number) + CHECK 制約 2 種）+ 3 インデックス |

### コードベース構成（IT2 末）

```
app/cargotracker/
├── auth/                # IT1 既存 + IT2 application 層追加
│   ├── domain/model/{aggregates,valueobjects,repositories}/
│   ├── application/commandservices/AuthCommandService.scala
│   ├── infrastructure/{repositories,services}/
│   └── interfaces/web/{AuthController,AuthFilter,AuthenticatedAction}.scala
├── shared/              # IT1 既存 + IT2 拡張
│   ├── domain/{Money,Location,CargoType,Weight,ShipperId,ShipperType,OptimisticLockException}.scala
│   ├── domain/pricing/{PricingService,InMemoryPricingService}.scala
│   └── interfaces/web/{HealthController,HomeController}.scala
├── shipper/             # IT1 既存 + IT2 application 層追加
│   ├── domain/model/{aggregates,valueobjects,repositories}/
│   ├── application/{commandservices/ShipperCommandService,queryservices/ShipperQueryService}.scala
│   ├── infrastructure/repositories/
│   └── interfaces/web/ShipperController.scala
├── estimation/          # IT1 既存 + IT2 application 層追加
│   ├── domain/model/{aggregates,valueobjects,repositories}/
│   ├── application/{commandservices/EstimateCommandService,queryservices/EstimateQueryService}.scala
│   ├── infrastructure/repositories/
│   └── interfaces/web/EstimateController.scala
├── booking/             # IT1 既存 + IT2 拡張（US05 / US06）
│   ├── domain/model/{aggregates/Cargo,valueobjects/{BookingId,CargoSpec,HazardousDeclaration,RefrigerationSpec,RouteSpecification},repositories,acl/ShipperExistenceChecker}/
│   ├── application/{commandservices/BookingCommandService,queryservices/BookingQueryService}.scala
│   ├── infrastructure/{repositories,services}/
│   └── interfaces/web/BookingController.scala
└── routing/             # IT2 新設（US24/US25 + Spike US08）
    ├── domain/model/{aggregates/Voyage,valueobjects/{VoyageNumber,Schedule,CarrierMovement},repositories/VoyageRepository}/
    ├── application/{commandservices/VoyageCommandService,queryservices/VoyageQueryService,RouteCandidateSearchSpike}.scala
    ├── infrastructure/repositories/ScalikeJdbcVoyageRepository.scala
    └── interfaces/web/VoyageController.scala
```

## 品質メトリクス

| メトリクス | 目標 | 実績 | 評価 |
|-----------|------|------|------|
| ベロシティ | 10-12 SP | 12 SP | ✅ |
| テスト pass 率 | 100% | 100%（158/158 + E2E 14/14） | ✅ |
| ScalafmtCheck | pass | pass | ✅ |
| ScalafixAll | pass | pass | ✅ |
| ArchUnit | pass | 5 ルール全 pass | ✅ |
| ステートメントカバレッジ | 80% | 78.67% | ⚠️ IT3 で復元 |
| ブランチカバレッジ | - | 72.11% | 計測のみ |
| SonarQube Quality Gate | PASS | ⚠️ 条件付き（new_coverage 68.9% 以外は PASS） | ⚠️ IT3 で復元 |

## テスト実績

### 総数: ユニット/統合/Arch **158 件**（IT1 末 70 → +88）

主な追加スペック:

| 区分 | Spec | テスト数 |
|------|------|---------|
| Application 層 | AuthCommandServiceSpec / ShipperCommandServiceSpec / ShipperQueryServiceSpec / EstimateCommandServiceSpec / EstimateQueryServiceSpec / BookingCommandServiceSpec / BookingQueryServiceSpec | 29 |
| ArchUnit | HexagonalArchitectureSpec（5 ルール） | 5 |
| Booking US05 | RefrigerationSpecSpec / CargoSpecValidationSpec / CargoAssignToRoutingSpec / ScalikeJdbcCargoRepositorySpec | 18 |
| Routing US24/25 | VoyageNumberSpec / ScheduleSpec / VoyageSpec / ScalikeJdbcVoyageRepositorySpec | 18 |
| Routing Spike | RouteCandidateSearchSpikeSpec | 7 |
| サポート | DbCleanupSupportSpec / ShipperVersionColumnSpec | 3 |

### E2E（Playwright）: **14 件**（IT1 末 0 → +14）

| Spec | テスト数 | 内容 |
|------|---------|------|
| auth.spec.ts | 4 | US26 認証フロー（リダイレクト / 成功 / 失敗 / ログアウト） |
| it1-flow.spec.ts | 4 | US02 / US03 / US01 / US04 ハッピーパス |
| us06-assign-routing.spec.ts | 2 | US06 引き渡しフロー / 冪等性 |
| us24-25-voyage.spec.ts | 4 | US24 登録 / US24 重複エラー / US25 編集 / ナビバーロール別表示 |

## リリース計画への影響

| 項目 | 状況 |
|------|------|
| Phase 1（IT1-IT2）目標 22 SP | ✅ **24 SP 完了**（IT1 12 + IT2 12） |
| Release 0.1 Internal Alpha | ✅ ゲート確認済み（[release-0.1.0-gate-check.md](./release-0.1.0-gate-check.md)）、v0.1.0 タグ付け待ち |
| 総スコープ 91 SP | 24 SP 完了（26%） |

## 申し送り事項

詳細は [retrospective-2.md](./retrospective-2.md) を参照。重要項目（IT3 着手者向け）:

1. **Controller / Twirl 統合テスト追加**: new_coverage を 80% に復元（高優先）
2. **集約 version フィールド活性化**: Cargo / Estimate / Shipper / Voyage に `version: Int` を持たせ、`Either[DomainError.ConcurrentModification, A]` で完全な競合検出を活性化（高優先）
3. **VoyageCommandService / Controller の重複抽出**: `upsert(vn, build)` 共通骨格を private 抽出（中優先）
4. **scoverage + Twirl + coverage モード偶発エラー調査**: clean coverage 時の `NoClassDefFoundError` 再現条件特定（中優先）
5. **Spike `RouteCandidateSearchSpike` を domain 格上げ**: IT3 US08 で `routing.application.RouteCandidateSearch` に格上げ、料金スコアリング・対応貨物種別フィルタ・上位 N 件選定・P95 < 3 秒（ADR 0005 申し送り）

## 関連ドキュメント

- [リリース計画](./release_plan.md)
- [イテレーション 2 計画](./iteration_plan-2.md)
- [イテレーション 2 ふりかえり](./retrospective-2.md)
- [Release 0.1.0 ゲート確認](./release-0.1.0-gate-check.md)
- [IT2 実装レビュー](../review/it2_implementation_review_20260621.md)
- [CHANGELOG](../../apps/cargo-tracker/CHANGELOG.md)
- [ADR 0005 経路探索アルゴリズム](../adr/0005-route-search-algorithm.md)
