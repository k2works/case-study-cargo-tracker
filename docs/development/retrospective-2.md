# イテレーション 2 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 2 |
| **期間** | 2026-06-04 〜 2026-06-17（計画 2 週間） |
| **実施日** | 2026-05-25 |
| **参加者** | k2works |

---

## 実績サマリー

| 項目 | 計画 | 実績 | 達成率 |
|------|------|------|--------|
| SP | 10 | 10 | 100% |
| テストカバレッジ（全体） | 80% | 78.4% | 98% |
| テストカバレッジ（新規コード） | 80% | 82.4% | 103% |
| 重複率 | 3% 未満 | 0.5% | ✅ |
| Code Smell | 0 | 0 | ✅ |
| Bug | 0 | 0 | ✅ |
| コミット数 | - | 31 | - |
| テスト件数（合計） | - | 122 件（前回比 +68） | ✅ |

### 完了ストーリー

| ID | ユーザーストーリー | 計画 SP | 実績 SP |
|----|-------------------|---------|---------|
| - | bookingms 基盤構築（Gradle / Spring Boot 4 / Axon / Flyway） | 0 | 0 |
| US02 | 荷主を登録する（個人） | 2 | 2 |
| US03 | 法人荷主を登録する（契約番号・割引率） | 2 | 2 |
| US04 | 貨物予約を登録する（一般貨物） | 3 | 3 |
| US05 | 危険物・冷凍貨物の予約を登録する | 3 | 3 |
| **合計** | | **10** | **10** |

### 追加成果（SP 外、計画外）

| 成果 | 分類 | 内容 |
|------|------|------|
| Navigation メニュー追加 | フロントエンド | 荷主管理 / 予約管理リンク（ROLE_ADMIN / ROLE_SALES） |
| ページネーション | バックエンド + フロント | 共通 `Pagination` + `PageResponse<T>` + `/shippers/search` 分離 |
| E2E booking.spec.ts | テスト | ナビゲーション 4 + 荷主予約 11 + ページネーション 3 = 18 件 |
| SonarQube カバレッジ連携 | 品質 | JaCoCo XML + Vitest LCOV を SonarQube に取り込み |
| ADR-0008 | ドキュメント | ページネーション戦略を Offset/Limit + PageResponse で記録 |
| マルチパースペクティブレビュー | レビュー | 5 エージェントでレビュー、H1-H4 を即対応 |

---

## KPT

### Keep（よかったこと・継続すること）

#### 技術的成功事項

1. **Codex 分業による高速実装**: ストーリー単位ではなく 1 ファイル 1 指示の粒度で Codex を活用し、US02-US05 を効率的に実装できた
2. **TDD インサイドアウト**: Aggregate → Service → Controller → Frontend の順で Red-Green-Refactor を回し、設計と実装の整合性を維持した
3. **Cargo Aggregate の CargoType 別検証**: `validateCargoTypeSpecificInfo` で GENERAL / HAZARDOUS / REFRIGERATED の不変条件を 1 メソッドに集約し、US05 拡張時もテスト 7 件で網羅できた
4. **共通 `Pagination` コンポーネント**: presentational + props ベースで実装し、Vitest 8 件 + E2E 3 件で再利用性を担保
5. **ページネーション ADR-0008**: 代替案 5 件と却下理由、コンプライアンス基準を記録し、設計判断の根拠を残せた
6. **マルチパースペクティブレビュー**: programmer / architect / writer の指摘で `PageRequest` 値オブジェクト・`/shippers/search` 分離・設計ドリフト明記まで一気に解消

#### プロセス的成功事項

1. **イテレーション計画タスク 6 (E2E 整備) の追加**: DoD「E2E テストがパス」を満たすため、当初計画にないタスクをイテレーション中に追加し DoD を達成
2. **Quality Gate PASS の継続維持**: SonarQube ローカルスキャンを IT2 末に実施し、Code Smell 2 件と new_coverage 不足を即修正
3. **コミット規律**: Conventional Commits 形式（feat / refactor / docs / test / chore）を 31 コミット全てで遵守
4. **ADR + レビュー結果の文書化**: ADR-0008 と `docs/review/pagination_review_20260525.md` を残し、IT3 でフォローアップ可能な状態に

---

### Problem（問題・改善が必要なこと）

1. **gatewayms `local-h2` プロファイルに bookingms ルートが未登録だった**: E2E 実行時に `/api/v1/shippers` が 404 となり、原因特定までに時間を要した。IT3 で新サービスを追加する際は gatewayms ルート登録を「サービス追加チェックリスト」に明文化する
2. **`deploy:dev` の SERVICES / DEPLOY_ORDER に bookingms が未登録だった**: bookingms を追加したが Heroku デプロイスクリプトの更新を忘れており、IT2 末のレビューで指摘されて初めて発覚した
3. **設計ドキュメントと実装のドリフト**: `architecture_backend.md` の `findAll(offset, limit)` 単独例、`architecture_frontend.md` の React Query 採用宣言、`domain-model.md` の `ListCargoSummariesQuery(status?)` などが実装と乖離。ADR-0008 で追記したが、IT3 で設計書本体の更新が必要
4. **CQRS Query 側の Axon QueryHandler 未採用**: 設計書では `@QueryHandler handle(ListCargoSummariesQuery)` を想定していたが、Controller → QueryService 直行で実装。意思決定の根拠がコミット時点で不明確だった
5. **後方互換 `fetchBookings` / `fetchShippers` の `size=200` 暗黙打ち切り**: totalCount > 200 で silently truncate される実装。M1 として削除予定だが、利用箇所が残るリスク
6. **xp-tester / xp-user-representative エージェントが本文未出力**: マルチパースペクティブレビューで 5 視点のうち 2 視点が関連ファイルリストのみで終了。業務視点・テスト網羅性視点の評価が取れなかった
7. **`PAGE_SIZE = 20` ハードコードの散在**: BookingListPage / ShipperListPage に重複。共通定数化が IT2 では未対応

---

### Try（次イテレーションで試すこと）

| # | アクション | 期待効果 | 期限 |
|---|-----------|---------|------|
| T1 | IT3 着手前に「新サービス追加チェックリスト」を `docs/reference/` に作成（gatewayms routes・deploy:dev SERVICES・docker-compose・sonar-project.properties の更新項目を列挙） | サービス追加漏れによる E2E / デプロイ失敗の防止 | IT3 Day 1 |
| T2 | 設計ドキュメントを IT3 着手時に更新（`architecture_backend.md` / `architecture_frontend.md` / `domain-model.md` / `data-model.md`）。ADR-0008 「設計ドキュメントとの差分」を解消 | SSOT の維持、新規メンバーの認識ズレ防止 | IT3 Day 1-2 |
| T3 | `fetchBookings` / `fetchShippers` 後方互換ラッパを IT3 内で削除し、`fetchBookingsPage` / `fetchShippersPage` に統一 | silently truncate のリスク解消 | IT3 内 |
| T4 | `created_at DESC` インデックスを Flyway で追加し、`data-model.md` に反映 | LIMIT/OFFSET 性能劣化の予防 | IT3 内 |
| T5 | フロントエンドの `PageResponse<T>` 型を `src/shared/api/types.ts` に共通化、`PAGE_SIZE` 定数も同様 | IT3 で追加する Voyage / Tracking などの一覧で再利用 | IT3 内 |
| T6 | `xp-tester` / `xp-user-representative` のレビュー観点をレビューレポート発注時のプロンプトテンプレートとして整備 | 5 視点レビューの抜け漏れ防止 | IT3 開始時 |
| T7 | Axon Saga (`BookingSagaManager`) の導入を IT3 で開始（US06 経路設計連携と同時実装） | 「Kafka 連携してる？」の懸念を解消、設計通りの CQRS+Saga 構造に移行 | IT3 内 |

---

## ベロシティ分析

### IT2 実績

| 項目 | 値 |
|------|-----|
| **計画 SP** | 10 |
| **完了 SP** | 10 |
| **ベロシティ** | 10 SP |
| **コミット数** | 31 件 |
| **追加成果（SP 外）** | Navigation / Pagination / E2E 整備 / SonarQube 連携 / ADR-0008 / レビュー対応 |

### 累計ベロシティ

| イテレーション | 計画 SP | 完了 SP | ベロシティ |
|--------------|---------|---------|------------|
| IT1 | 10 | 10 | 10 |
| IT2 | 10 | 10 | 10 |
| **平均** | **10** | **10** | **10** |

### 次イテレーションへの反映

- IT3 目標 SP: **10**（IT1 / IT2 共に 10 SP で安定。継続）
- IT3 は計画上 10 SP だが、T1-T7 のフォローアップ (約 2 SP 相当) を吸収するため、新規ストーリーは 8 SP 程度に絞ることを検討

---

## 品質振り返り

### SonarQube メトリクス（IT2 完了時点）

| メトリクス | 値 | 評価 |
|-----------|-----|------|
| カバレッジ（全体） | 78.4% | △ 目標 80% 近接（前回比 +16.1pt） |
| カバレッジ（新規コード） | 82.4% | ✅ 目標達成 |
| 重複率 | 0.5% | ✅ 目標達成 |
| Bug | 0 | ✅ |
| Vulnerability | 0 | ✅ |
| Code Smell | 0 | ✅ |
| **Quality Gate** | **PASS** | ✅ |

### テスト件数推移

| イテレーション | Backend | Frontend | E2E | 合計 |
|--------------|---------|---------|-----|------|
| IT1 | 26 | 11 | 17 | 54 |
| IT2 | 46 | 58 | 18 | **122** |
| 増分 | +20 | +47 | +1 | **+68** |

### 技術的負債（IT3 に持ち越し）

| # | 内容 | 緊急度 |
|---|------|--------|
| 1 | 設計ドキュメントと実装のドリフト（ADR-0008 「設計ドキュメントとの差分」5 項目） | 中 |
| 2 | `fetchBookings` / `fetchShippers` 後方互換ラッパの削除 | 中 |
| 3 | `created_at DESC` インデックス未追加（LIMIT/OFFSET 性能劣化リスク） | 中 |
| 4 | `PageResponse<T>` 型のフロント共通化 | 中 |
| 5 | `ListCargoSummariesQuery.status?` フィルタ未実装 | 中 |
| 6 | ArchUnit テスト未整備（CQRS 構造の機械的ガード不在） | 低 |
| 7 | `SELECT *` の明示カラム列挙 | 低 |
| 8 | URL クエリ非同期（ブラウザバックで page=0 戻り） | 低 |
| 9 | Axon Kafka 連携が subscribing モード（cross-service イベント未対応） | 中（IT3 で対応予定） |

---

## IT3 への引き継ぎ事項

### 持ち越しタスク（レビュー M1-M7 + Try T1-T7 統合）

- [ ] T1: 新サービス追加チェックリスト作成
- [ ] T2: 設計ドキュメントを ADR-0008 と整合させる
- [ ] T3: `fetchBookings` / `fetchShippers` 削除
- [ ] T4: `created_at DESC` インデックス Flyway 追加
- [ ] T5: `PageResponse<T>` 型 + `PAGE_SIZE` 定数の共通化
- [ ] T6: レビュープロンプトテンプレート整備
- [ ] T7: Axon Saga (`BookingSagaManager`) + Kafka tracking モード移行

### IT3 スコープ候補（次計画時に決定）

| ID | ユーザーストーリー | SP | 備考 |
|----|-------------------|-----|------|
| US01 | 輸送見積を作成する | 3 | bookingms 拡張 |
| US06 | 予約情報を経路設計者に引き渡す | 2 | 初の cross-service イベント |
| US07 | 航海スケジュールを検索する | 2 | routingms |
| US13 | 予約を確定する | 2 | BookingSagaManager 起動 |
| US08 | 経路候補を算出する | 3 | OptimalRouteService |
| **合計候補** | | **12** | バッファ考慮で 10 SP 選択 |

---

## 更新履歴

| 日付 | 更新内容 | 更新者 |
|------|---------|--------|
| 2026-05-25 | 初版作成 | k2works |
