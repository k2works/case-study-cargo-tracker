# イテレーション 3 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 3 |
| **期間** | 2026-06-18 〜 2026-07-01（計画 2 週間） |
| **実施日** | 2026-05-26 |
| **参加者** | k2works |

---

## 実績サマリー

| 項目 | 計画 | 実績 | 達成率 |
|------|------|------|--------|
| SP | 10 | 10 | 100% |
| テストカバレッジ（Backend 全体） | 80% | 80.0% | ✅ |
| テストカバレッジ（Frontend 全体） | 80% | 81.0% | ✅ |
| テストカバレッジ（新規コード） | 80% | PASS | ✅ |
| 重複率 | 3% 未満 | Backend 1.9% / Frontend 0.0% | ✅ |
| Code Smell | 0 | 0（25 → 0 に解消） | ✅ |
| Bug / Vulnerability | 0 | 0 / 0 | ✅ |
| Quality Gate | PASS | 両プロジェクト PASS | ✅ |
| コミット数 | - | 41 | - |
| テスト件数（合計） | - | 約 277 件（Backend 124 / Frontend 119 / E2E 34） | ✅ |

### 完了ストーリー

| ID | ユーザーストーリー | 計画 SP | 実績 SP |
|----|-------------------|---------|---------|
| US07 | 航海スケジュールを検索する | 3 | 3 |
| US01 | 輸送見積を作成する | 3 | 3 |
| US06 | 予約情報を経路設計者に引き渡す | 2 | 2 |
| US13 | 予約を確定する | 2 | 2 |
| **合計** | | **10** | **10** |

### 追加成果（SP 外、計画外）

| 成果 | 分類 | 内容 |
|------|------|------|
| ADR-0009 | ドキュメント | cross-service イベント連携と Axon Saga 採用を記録（承認済み） |
| cross-service 基盤（T7） | バックエンド | `BookingSagaManager` + shared モジュールへのイベント移動 + `RouteDesignRequestedEvent` 拡張 + routingms 受信ハンドラ + Kafka tracking モード移行 |
| cross-service 疎通の実証 | テスト | Testcontainers Kafka 統合テスト + 環境変数ゲート付き E2E（`npm run e2e:cross-service`）で bookingms → Kafka → routingms 伝搬を実証 |
| 認証ヘッダ不整合の修正 | フロントエンド | `sessionStorage('token')` → `localStorage('auth_token')` に統一し `shared/api/auth` に集約 |
| Code Smell 25 → 0 | 品質 | FormEvent 非推奨置換・useMemo 安定化・認知的複雑度低減ほか |
| カバレッジ 80% 達成 | 品質 | Frontend API クライアントのテスト追加（+32 件）、Backend JaCoCo 再生成（測定アーティファクト修正） |
| 経路設計依頼 参照 API | バックエンド | routingms に `route_design_request` read model の参照 API を追加（US08 ワークベンチの先取り） |

---

## KPT

### Keep（よかったこと・継続すること）

#### 技術的成功事項

1. **フロント主導の cross-service 合成 + イベント駆動の使い分け**: 見積フロー（US07→US01）は「フロントが routingms の航海検索結果を候補として bookingms に渡す」REST 合成で実現し、経路設計依頼（US06）は bookingms → Kafka → routingms のイベント駆動で実現。同期合成と非同期伝搬を業務特性で適切に使い分けた
2. **cross-service イベントを shared モジュールに配置**: `RouteDesignRequestedEvent` を共有モジュールに置き同一 FQCN で送受信、自己完結（経路設計情報込み）にすることで routingms 側の処理に必要な情報を担保（ADR-0009）
3. **ProcessingGroup による processor 分離**: `route-design-requests` を default プロセッサ（event store source, subscribing）から分離し、Kafka source の tracking プロセッサに割り当て。サービス内 Projection の IT2 退行を回避
4. **Testcontainers Kafka で物理経路を検証**: cross-service の Kafka 疎通を実バックエンド非依存の統合テストで再現可能にした
5. **冪等な受信ハンドラ**: tracking 再処理に備え `findByBookingId` 存在チェックで冪等性をアプリレベルで担保
6. **Saga による予約ライフサイクル管理**: `BookingSagaManager` で CargoBooked → RouteDesignRequested → BookingCancelled の状態遷移を表現し、設計通りの CQRS + Saga 構造に移行

#### プロセス的成功事項

1. **IT2 の Try T1-T7 を全消化**: 新サービス追加チェックリスト（T1）、設計ドキュメントの ADR 整合（T2）、後方互換ラッパ削除（T3）、created_at インデックス（T4）、PageResponse/PAGE_SIZE 共通化（T5）、レビュープロンプトテンプレート（T6）、Axon Saga 導入（T7）をイテレーション内ですべて完了
2. **負債返済を機能開発と並行**: 機能（US01/06/07/13）と技術的負債（T1-T7）を同一イテレーションで両立し、IT2 から持ち越した 9 件の技術的負債の大半を解消
3. **Quality Gate を「実質的な」グリーンに**: 当初のゲートは新規コード 0 行で自明 PASS だったが、Code Smell 25 → 0・カバレッジ 80% 達成により実質的な品質も基準到達
4. **測定アーティファクトの発見と是正**: Backend カバレッジが 63.9% と低く見えた原因が「`--tests` 部分実行による JaCoCo exec データの陳腐化」と特定し、全テスト再実行で 80.0% に是正（実コードは元から十分）

---

### Problem（問題・改善が必要なこと）

1. **認証ヘッダが実質無効だった**: フロントの API クライアントが `sessionStorage('token')` を読む一方、`AuthContext` は `localStorage('auth_token')` に保存しており、`Authorization` ヘッダが常に空だった。gateway が local でこれらのエンドポイントの認証を強制していなかったため既存 E2E は通り、長く検知されなかった
2. **JaCoCo レポートの陳腐化リスク**: `gradlew :svc:test --tests X` の部分実行が exec データを上書きし、後続スキャンのカバレッジを誤って低く見せた。フィルタ実行とフルスキャンの混在に注意が必要
3. **cross-service 経路が E2E で未カバーだった**: 「Kafka を止めても動く」状態（=単一サービス完結フローのみ E2E 化）に気付くまで、T7 の成果が回帰テストで守られていなかった。後から環境変数ゲート付き E2E を追加して解消
4. **HMR による E2E 偽失敗**: フロントのソース編集直後に E2E を実行すると vite HMR の過渡状態でテストが散発失敗。再実行で安定するが、編集直後の判定に注意が必要
5. **スケジュールの名目乖離**: 計画上の IT3 期間（2026-06-18〜）に対し実作業は大幅に先行。日付ベースの進捗管理が形骸化しており、SP ベースの管理に一本化すべき
6. **Quality Gate の条件が新規コード偏重**: ベースライン解析では `new_lines=0` で自明 PASS となり、全体カバレッジ・全体 Code Smell がゲートに反映されない。プロジェクト品質目標（全体 80%）との二重管理が必要

---

### Try（次イテレーションで試すこと）

| # | アクション | 期待効果 | 期限 |
|---|-----------|---------|------|
| T1 | API クライアントの認証ヘッダ付与を共通ラッパ（`shared/api/auth`）経由に統一する規約をレビュー観点に追加 | 認証ヘッダ不整合の再発防止 | IT4 開始時 |
| T2 | cross-service を伴うストーリーは「イベント駆動 E2E」を DoD に含める（環境変数ゲートで CI 分離） | T7 のような連携を回帰テストで保護 | IT4 内 |
| T3 | JaCoCo は `gradle check`（フル）で生成し、`--tests` 部分実行の後は必ずフル再実行してからスキャンする運用を明文化 | カバレッジ誤計測の防止 | IT4 開始時 |
| T4 | US08（経路候補算出）の `OptimalRouteService` を routingms に実装。`route_design_request` を入力に経路探索ロジックを構築 | Phase 1 の中核機能、IT4 の主目標 | IT4 内 |
| T5 | フロント編集後の E2E は dev サーバー再起動 or HMR 沈静化を待ってから実行する手順をチェックリスト化 | HMR 偽失敗による誤診の防止 | IT4 開始時 |
| T6 | プロジェクト品質目標（全体カバレッジ 80%・全体 Code Smell 0）を SonarQube の追加 Quality Gate 条件として定義 | ゲートと品質目標の二重管理を解消 | IT4 内 |

---

## ベロシティ分析

### IT3 実績

| 項目 | 値 |
|------|-----|
| **計画 SP** | 10 |
| **完了 SP** | 10 |
| **ベロシティ** | 10 SP |
| **コミット数** | 41 件 |
| **追加成果（SP 外）** | ADR-0009 / cross-service 基盤（T7）/ Testcontainers Kafka + E2E / 認証修正 / Code Smell 25→0 / カバレッジ 80% / 負債返済 T1-T7 |

### 累計ベロシティ

| イテレーション | 計画 SP | 完了 SP | ベロシティ |
|--------------|---------|---------|------------|
| IT1 | 10 | 10 | 10 |
| IT2 | 10 | 10 | 10 |
| IT3 | 10 | 10 | 10 |
| **平均** | **10** | **10** | **10** |

### 次イテレーションへの反映

- IT4 目標 SP: **10**（IT1-IT3 すべて 10 SP で安定。ベロシティは確定値とみなせる）
- IT4 は経路設計（US08 経路候補算出 5 SP・US09 経路選択確定 2 SP・US10 経路条件調整 2 SP・US11 経路紐付け 2 SP・US12 荷主通知 2 SP = 13 SP）。バッファ考慮で US08+US09+US11（9 SP）を中心に選択し、Phase 1 完了（Release 1.0 MVP）を目指す

---

## 品質振り返り

### SonarQube メトリクス（IT3 完了時点）

| メトリクス | Backend | Frontend | 評価 |
|-----------|---------|----------|------|
| カバレッジ（全体） | 80.0% | 81.0% | ✅ 目標達成 |
| 行カバレッジ | 81.9% | 84.0% | ✅ |
| 重複率 | 1.9% | 0.0% | ✅ |
| Bug | 0 | 0 | ✅ |
| Vulnerability | 0 | 0 | ✅ |
| Code Smell | 0 | 0 | ✅ |
| **Quality Gate** | **PASS** | **PASS** | ✅ |

### テスト件数推移

| イテレーション | Backend | Frontend | E2E | 合計 |
|--------------|---------|---------|-----|------|
| IT1 | 26 | 11 | 17 | 54 |
| IT2 | 46 | 58 | 18 | 122 |
| IT3 | 124 | 119 | 34 | **約 277** |
| 増分 | +78 | +61 | +16 | **+155** |

### 技術的負債（IT4 に持ち越し）

| # | 内容 | 緊急度 |
|---|------|--------|
| 1 | cross-service E2E のデフォルトスイート未統合（環境変数ゲートで除外中、Kafka 起動が前提） | 低 |
| 2 | SonarQube Quality Gate が新規コード偏重（全体品質目標との二重管理） | 低 |
| 3 | `ListCargoSummariesQuery.status?` フィルタ未実装（IT2 から継続） | 低 |
| 4 | ArchUnit テスト未整備（CQRS 構造の機械的ガード不在、IT2 から継続） | 低 |
| 5 | URL クエリ非同期（ブラウザバックで page=0 戻り、IT2 から継続） | 低 |

---

## IT4 への引き継ぎ事項

### 持ち越しタスク（Try T1-T6）

- [ ] T1: 認証ヘッダ共通ラッパ規約をレビュー観点に追加
- [ ] T2: cross-service ストーリーのイベント駆動 E2E を DoD 化
- [ ] T3: JaCoCo フル再生成運用の明文化
- [ ] T4: US08 `OptimalRouteService` 実装（IT4 主目標）
- [ ] T5: フロント編集後 E2E のチェックリスト化
- [ ] T6: 全体品質目標を Quality Gate 条件として定義

### IT4 スコープ候補（次計画時に決定）

| ID | ユーザーストーリー | SP | 備考 |
|----|-------------------|-----|------|
| US08 | 経路候補を算出する | 5 | `OptimalRouteService`、`route_design_request` を入力 |
| US09 | 経路を選択・確定する | 2 | routingms |
| US11 | 経路情報を予約に紐付ける | 2 | cross-service（routingms → bookingms） |
| US10 | 経路条件を調整して再算出する | 2 | バッファ次第 |
| US12 | 確定経路を荷主に通知する | 2 | バッファ次第 |
| **合計候補** | | **13** | バッファ考慮で 9-10 SP 選択、Phase 1 完了・Release 1.0 MVP へ |

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-26 | 初版作成 | k2works |
