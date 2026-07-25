---
title: イテレーション 3 完了報告書
description: IT3（航海スケジュール US24/US25/US07・見積 US01・引き渡し US06・2 新規 BC 立ち上げ）の完了報告。Phase 2 に着手。
tags: development, iteration-report, iteration-3, go
---

# イテレーション 3 完了報告書

## エグゼクティブサマリー

国際貨物輸送管理システム（Go 版）の **IT3 を完了**した。中盤局面（インサイドアウト）の初回として Phase 2（経路設計・貨物追跡）に着手し、**Routing Context**（航海スケジュール）と **Estimation Context**（輸送見積）の 2 新規 BC を DDD + ヘキサゴナル + CQRS で立ち上げた。**航海スケジュールの登録・更新・検索（US24/US25/US07）**、**輸送見積の作成（US01）**、**予約の経路設計者への引き渡し（US06）** を実装。基盤として **CargoType の共有カーネル昇格（ADR-0006）** と **sqlc の BC 別分割（ADR-0005 決定3 完遂）** を先行整備した。実績 **17 SP**、品質ゲート（make check / SonarQube Quality Gate PASS / CI success）を全通過。

## 達成状況

| ストーリー | 内容 | SP | BC | 状態 |
|---|---|---|---|---|
| US24 | 航海スケジュールを新規登録する | 3 | routing | ✅ 完了 |
| US25 | 既存航海スケジュールを更新する | 2 | routing | ✅ 完了 |
| US07 | 航海スケジュールを検索する | 5 | routing | ✅ 完了（直接フィルタ。寄港地グラフ探索は US08） |
| US01 | 輸送見積を作成する | 5 | estimation | ✅ 完了（ルート候補はスタブ。精緻化は US08） |
| US06 | 予約情報を経路設計者に引き渡す | 2 | booking | ✅ 完了 |
| **合計** | | **17** | | **達成率 100%** |

### 成功基準

- [x] US24/US25/US07/US01/US06 の受け入れ基準を満たす（Phase 2 後続依存分は「注」で明示）
- [x] Routing・Estimation のドメイン層カバレッジ 90% 以上（routing 100%・estimation 97.4%）
- [x] Try T3（sqlc BC 別分割）実施・go-arch-lint で BC 越境検出（ADR-0005 決定3 完遂）
- [x] カバレッジ計測を integration タグ込みに標準化
- [x] `make check` green・SonarQube Quality Gate PASS・CI success
- [x] 設計ドキュメント（domain-model / data-model / ui_design）と実装が一致

## 技術的成果

### 実装

- **Routing Context（新設）**: `Voyage` 集約（`VoyageNumber` 固有型・`Schedule` の空間/時刻連結制約・`CarrierMovement`・vessel_name/carrier/supported_cargo_types）。登録・更新（差分確認）・検索（貨物種別/出発期間フィルタ）。
- **Estimation Context（新設）**: `Estimate` 集約（`EstimateId`・`RouteCandidate`・`EstimateStatus`）。見積作成・簡易ルート候補付与（希望期限フィルタ）・一覧・詳細。
- **US06 引き渡し**: `Cargo.AssignToRouting`（PRELIMINARY→ROUTE_PROPOSED、BC 正典の 8 状態を維持）。
- **基盤整備**: CargoType を `shared/domain` へ昇格（型エイリアスで booking 互換）、sqlc を booking/shipper/routing/estimation の 4 BC + auth に分割。navbar に見積管理導線を追加、CanAccess（admin 全アクセス）と route-designer デモユーザーを整備。

### コード規模

- 実装差分 約 3,400 行（IT3 全体）。Go 33 ファイル + テンプレート + マイグレーション 000006/000007/000008。
- コミット 17 件（feat 5・fix 4・refactor 2・docs 2・style 1・クローズ作業）。

## 品質指標

| 指標 | 結果 |
|---|---|
| 単体テスト | 全 green |
| 統合テスト（testcontainers） | 全 green（Voyage/Estimate の round-trip・Update・検索） |
| E2E（Playwright） | 全 29 本 green（IT3 分: 航海 5・見積 1・引き渡し 1・ナビ導線を追加） |
| ドメイン層カバレッジ | routing 100% / estimation 97.4% |
| SonarQube Quality Gate | **PASS**（Bug 0・Vulnerability 0・Code Smell 0・重複 0%・新規カバレッジ 80.6%） |
| make check | green（build + test + lint + govulncheck + arch） |
| CI（Backend CI） | success |

## レビュー結果

マルチパースペクティブレビュー（XP 5 視点）を実施（[レビューレポート](../review/it3_go_review_20260725.md)）。高優先度は**クローズ前に対応**:

- Programmer H-1: Schedule に時刻連続性の不変条件を追加
- Technical Writer 高 4: 設計ドキュメント本体に voyage 拡張・CargoType 昇格・航路権限を反映（DoD 充足）
- User-rep H1: 航海登録フォームの必須検証・複数区間対応
- Tester: E2E ハードコード日付を相対化（時限爆弾の除去）
- Architect: 構造健全（sqlc models 全型複製は ADR に注記）

中・低優先度（動的区間・edit 複数区間・候補精緻化・境界テスト）は IT4 の Try に計上。

## 課題と残作業

- **UI 深掘り**: 運送区間の動的行追加、edit の複数区間対応（現状 edit は 1 区間で上書きするデータ損失リスク）、見積候補の経由港表示。
- **Phase 2 後続依存**: US07 の寄港地接続グラフ探索・US01 のルート候補精緻化は US08（IT4）。通知（US06/US12）は通知基盤。
- **集約の不変条件**: Estimate の arrivalDeadline 検証を集約側へ寄せる。

## 次イテレーション（IT4）への引き継ぎ

- **IT4 スコープ**: US08（経路候補を算出する・8SP）・US09（経路を選択・確定する・3SP）。中盤の複雑ドメインの核心。
- **優先 Try**: 動的区間・edit 複数区間・候補精緻化（Clock 注入含む）を US08 と同時に。設計是正の同時反映・アクセシビリティ DoD は継続。
- **ベロシティ**: IT1 15・IT2 8・IT3 17 SP。3 IT 平均 ≒ 13 SP/IT。

## 関連ドキュメント

- [IT3 計画](iteration_plan-3.md)
- [IT3 ふりかえり](retrospective-3.md)
- [IT3 レビュー](../review/it3_go_review_20260725.md)
- [リリース計画](release_plan.md)
- [ADR-0005](../adr/0005-bc-reference-and-shared-sqlcgen.md) / [ADR-0006](../adr/0006-shared-cargo-type-and-voyage-model.md)
