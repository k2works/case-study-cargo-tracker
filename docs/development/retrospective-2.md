---
title: イテレーション 2 ふりかえり
date: 2026-06-21
---

# イテレーション 2 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| 期間 | 2026-07-06 〜 2026-07-19（計画）/ 1 日（AI ペアプロ実績） |
| ゴール | 特殊貨物予約・経路設計引き渡し・航海スケジュール管理を完成させ Release 0.1 Internal Alpha をリリース、US08 経路算出スパイクで Phase 2 最大リスクを早期検証 |
| 計画 SP | 12（基本 10 + スパイク別計上 2） |
| 実績 SP | 12（100%） |

## 達成事項

- **US05（3 SP）**: RefrigerationSpec / TemperatureUnit 値オブジェクト、CargoSpec.create 条件付き必須バリデーション、Flyway V6 で cargo に refrigeration 3 カラム
- **US06（2 SP）**: BookingStatus.canTransitionTo（完全状態遷移マトリクス）、Cargo.assignToRouting、POST `/bookings/:id/assign-routing`、経路設計者ダッシュボードに RouteProposed 一覧
- **US24（3 SP）**: Routing コンテキスト新設、Voyage / Schedule / CarrierMovement 集約、VoyageNumber opaque type、Flyway V7、ScalikeJdbcVoyageRepository（楽観ロック対応）
- **US25（2 SP）**: 編集画面で既存値 fill、CarrierMovement 全削除 + 再挿入による更新
- **Spike US08（2 SP）**: RouteCandidateSearchSpike（純関数 DFS + 深さ制限）+ ADR 0005

### 成果物

- ADR 0005 経路探索アルゴリズム選定
- Flyway マイグレーション V5（version カラム）/ V6（refrigeration）/ V7（voyage + carrier_movement）
- 新規 Routing コンテキスト（domain/model/{aggregates,valueobjects,repositories,acl}、application/commandservices/queryservices、infrastructure/repositories、interfaces/web）
- application 層 7 サービス（Auth/Shipper/Estimate/Booking/Voyage の Command/Query）
- 12 新画面・拡張（ロール別ダッシュボード、航海一覧 / 登録 / 編集差分確認、危険物・冷凍フィールド付き予約フォーム、引き渡しボタン付き予約詳細）
- ヘキサゴナル DDD パッケージ構成への全面再構成（29 ファイル移動）
- ArchUnit 5 ルール（domain 純粋性 / application 境界 / コンテキスト分離 / 命名規約 / Repository 実装方向）
- DbCleanupSupport trait + OptimisticLockException
- CHANGELOG / Release 0.1.0 ゲート確認結果

## メトリクス

| メトリクス | 目標 | 実績 |
|-----------|------|------|
| ベロシティ | 10-12 SP | 12 SP |
| テスト数 | 100+ | **158** ユニット / 統合 / Arch + **14 E2E** |
| テスト pass 率 | 100% | 100% |
| ステートメントカバレッジ | 80% 以上 | 78.67%（IT1 75.71% → +2.96pt） |
| ブランチカバレッジ | - | 72.11% |
| ScalafmtCheck / ScalafixAll | pass | pass |
| ArchUnit | pass | 5 ルール全 pass |
| SonarQube QG | PASS | ⚠️ 条件付き（new_violations=0 / new_duplications=0 / new_coverage=68.9% < 80%） |
| 実コミット数 | - | 35（IT2 期間） |

## KPT 分析

### Keep（継続すること）

- **ADR 駆動の意思決定**: ADR 0005 を Spike と並行で書き起こすことで、IT3 US08 への申し送り 5 件が具体化された
- **インクリメンタル DDD 再構成**: ヘキサゴナル DDD への 29 ファイル移動を最初のイテレーションで実施したことで、後続の US24/25 Routing コンテキストが正しい場所に最初から配置できた
- **ArchUnit ファースト**: ルール 4 命名規約を追加した直後に CarrierMovementInput が違反して即時改名（→ CarrierMovementCommand）、構造ルールが TDD のように機能した
- **マルチパースペクティブレビュー**: 5 XP エージェント並列レビューで「ダッシュボード disabled 残り」「楽観ロック WHERE 欠落」「ArchUnit ルール 3 のコンテキスト未追加」を IT2 完了承認前に検出
- **Spike + ADR セット**: スパイクで純関数プロトタイプを作り即 ADR に落とすパターンが、IT3 着手前のリスク低減と意思決定の両方を満たした
- **DbCleanupSupport**: テスト間独立性を担保したことで、Cargo の refrigeration round-trip テストや Voyage の upsert テストを衝突なく書けた

### Problem（課題）

- **scoverage カバレッジが 78.67% に低下**: Controller / Twirl / Dashboard の新規コード（US24/25 含む）がテスト未カバー、E2E に頼って統合テストが薄い
- **SonarQube new_coverage 68.9%**: QG ⚠️ 条件付きでリリースゲート通過、IT3 で必ず復元する必要あり
- **VoyageCommandService.register / update + VoyageController.create / update の 4 箇所重複**: programmer エージェント指摘、将来 schedule バリデーション追加時の二重修正リスク
- **航海登録 UI の複数区間対応漏れ**: 当初 IT2 末に「単区間注記」のまま放置していた → user-rep レビューでブロッカー認定後に pure JS 追加で対応
- **ScalikeJdbcVoyageRepository.save の楽観ロック WHERE 欠落**: version カラム追加と UPDATE 文の version+1 だけで満足していた → architect レビューで指摘されて修正
- **scoverage + Twirl + coverage モード偶発エラー**: `views/html/<ctx>/list$` の NoClassDefFoundError が clean coverage 実行時に発生（IT3 で調査）
- **iteration_plan-2.md 成功基準 / DoD / 進捗率の更新漏れ**: 個別タスク `[x]` に対しサマリ層 `[ ]` のまま、tech-writer レビューで自己矛盾と指摘

### Try（次イテレーションで試すこと）

- **Controller / Twirl 統合テストパターンの確立**: Play `FakeRequest` + Testcontainers でロール認可・CSRF・Flash・PRG リダイレクトの境界を検証する `*EndpointSpec` を Voyage / Booking / Home の Controller に追加 → 担当者: IT3 着手者、期限: IT3 Day 3、期待効果: new_coverage を 80% 復元、E2E への過度依存を回避
- **`upsert(vn, build)` 共通骨格抽出**: VoyageCommandService.register/update と VoyageController.create/update の共通部を private 抽出 → IT3 Day 1（リファクタ第 1 弾）、効果: 重複削減 + 将来のバリデーション追加時の修正箇所一元化
- **集約への version フィールド活性化**: Cargo / Estimate / Shipper にも `version: Int` フィールドを追加し、`Either[DomainError.ConcurrentModification, A]` で完全な競合検出を活性化 → IT3 タスク 0.x、効果: 楽観ロックを「準備」から「実用」へ
- **ScalaCheck プロパティテスト導入**: ShipperId / Money / VoyageNumber などの値オブジェクトの不変条件をプロパティで検証 → IT3 Day 5、効果: magic value 依存の silent break を防止
- **scoverage + Twirl 問題の調査**: clean coverage 時の `NoClassDefFoundError` 再現条件を特定し、build.sbt の coverageExcludedPackages 設定を見直し → IT3 Day 2、効果: CI で coverageReport が安定する
- **dashboard 集計の pure function 切り出し**: HomeController から RouteProposed 件数集計などを pure function に分離 → IT3 Day 1（リファクタ第 2 弾）、効果: Controller 密結合の解消とテスト容易化
- **マルチパースペクティブレビューの定常化**: 各イテレーション末（コミット集計後）に 5 XP エージェント並列レビューを必ず実施し、必須対応をリリース承認前に解消する運用を IT3 から定常化

## 申し送り事項（IT3 へ）

### 技術的負債

| 項目 | 重要度 | 引き継ぎ理由 |
|------|--------|--------------|
| Controller / Dashboard / Twirl の統合テスト追加（new_coverage 80% 復元） | 高 | IT2 で SonarQube QG ⚠️ 条件付き通過、IT3 リリース判定でも継続できない |
| 集約 Cargo / Estimate / Shipper / Voyage に `version: Int` フィールドを追加し OptimisticLockException を完全活性化 | 高 | IT2 タスク 0.11 で準備のみ、IT3 で並行更新シナリオが増える前に活性化 |
| VoyageCommandService / VoyageController の重複抽出 | 中 | 将来 Schedule バリデーション追加時に二重修正リスク |
| BookingCommandService の `_ => "荷主が見つかりません"` を sealed エラー網羅 match へ | 中 | Cargo.book が別失敗理由を追加した時メッセージ握りつぶし |
| 危険物・冷凍フィールドの htmx 動的表示 | 中 | IT2 では常時表示、user-rep レビューで「日常業務 8 割の一般貨物で誤入力誘発」と指摘 |
| BookingCommandService.assignToRouting / book の境界値・エラー経路網羅 | 中 | 状態遷移はバグの温床、現状 happy path 偏重 |
| 予約詳細に温度管理条件の表示 | 中 | 経路設計者が冷凍要件を確認できない |
| Dashboard 集計を pure function 切り出し | 中 | Controller 密結合でテスト不能 |
| README に「動かし方」追加 | 中 | tech-writer 指摘、CHANGELOG だけでは到達できない |
| Spike `RouteCandidateSearchSpike` を domain 格上げ + 隣接リスト化 | 中 | ADR 0005 の IT3 申し送り、O(\|legs\|) の線形フィルタは小規模でも `maxLegs=5` で爆発 |
| README / CHANGELOG の比較リンクリポジトリ名修正 | 低 | `case-study-cargo-tracker` → `case-study-cargo-tracker-scala-take-1` |
| ADR 0005 関連リンクを相対パス化 | 低 | mkdocs ビルド時のリンク解決 |

### IT3 ストーリーへの影響

- **US07（航海スケジュール検索）**: IT2 で Voyage 集約と ScalikeJdbcVoyageRepository.findAll が実装済み。`findByCriteria` を追加して検索 UI（IT2 の航路一覧から発展）を作る
- **US08（経路候補算出）**: IT2 Spike `RouteCandidateSearchSpike` を `routing.application.RouteCandidateSearch` に格上げし、domain へ `RoutingLeg` / `RouteCandidate` を移行。料金スコアリング（PricingService 連携）+ 対応貨物種別フィルタ + 上位 N 件選定 + パフォーマンス目標 P95 < 3 秒（非機能要件）
- **US09 / US11 / US12 / US13**: 経路選択 → 予約紐付け → 通知 → 予約確定の流れ。Cargo.confirm() を BookingStatus.RouteProposed → Confirmed として追加し、`AssignToRoutingCommand` 完了後のフローを引き継ぐ
- **データモデル追補 ADR**: 船名 / 運送会社 / 対応貨物種別カラムは US07 検索要件と合わせて IT3 で定義する

### IT2 で導入された規律（IT3 も継続）

- Ralph Loop イテレーション運用（同じプロンプトで Stop hook が次イテレーション投入、Done を出さない方針）
- 5 XP エージェント並列レビュー + 必須対応即時実施
- pre-commit は `scalafmtCheckAll` のみ（scalafix は CI 専用）
- DbCleanupSupport によるテスト独立性
- ArchUnit 5 ルールで構造を TDD 的に強制
