---
title: イテレーション 3 完了報告書
description: IT3（航海スケジュール・経路候補算出）の完了報告
date: 2026-07-29
---

# イテレーション 3 完了報告書

## エグゼクティブサマリー

IT3 では、Routing Context の中核として航海スケジュール登録・更新・検索（US24/US25/US07）と経路候補算出（US08）を実装した。`Voyage` 集約、`Schedule`、`CarrierMovement`、`RouteCandidateFinder`、外部経路サービス Port + fallback、`/voyages` / `/routing/candidates` の TSX SSR + htmx 導線までを縦に接続した。

目標 13SP を 100% 達成。レビューで検出された High 指摘（複数寄港地入力、出発期間検索、候補算出導線、必須項目エラー、ADR/文書同期）をクローズ内で対応した。

| 項目 | 内容 |
| :--- | :--- |
| 期間 | 2026-08-24 〜 2026-09-06（計画） / 2026-07-29（実績記録） |
| 目標 SP / 実績 SP | 13 / 13（達成率 100%） |
| 対象ストーリー | US24・US25・US07・US08 |
| コミット数 | 14（実装 12 + 計画/完了条件 2、クローズ追補を除く） |
| 累計 SP | 36 / 81 |
| Phase 2 進捗 | 13 / 29 SP |

## 達成状況

| ID | ストーリー | SP | 状態 | 備考 |
| :--- | :--- | :--: | :--- | :--- |
| US24 | 航海スケジュールを新規登録する | 3 | 完了 | 航海番号・船名・運送会社・貨物種別・複数区間・必須エラー |
| US25 | 既存航海スケジュールを更新する | 2 | 完了 | 更新フォーム・差分確認・更新/キャンセル |
| US07 | 航海スケジュールを検索する | 3 | 完了 | 予約条件引き継ぎ、出発地/目的地/出発期間/希望着日/貨物種別 |
| US08 | 経路候補を算出する | 5 | 完了 | 直行優先、1 寄港接続、期限日判定、費用・所要日数表示、fallback |

## 技術的成果

- **Domain**: `Voyage` / `Schedule` / `CarrierMovement` / `RouteCandidateFinder` / 日付単位比較。
- **Application**: 登録・更新 command service、航海検索 query service。
- **Infrastructure**: `voyage` / `carrier_movement` 永続化、Booking 条件 reader ACL、HTTP 外部経路 service、fallback service。
- **Presentation / UI**: `/voyages` 一覧・検索・登録・更新確認、`/routing/candidates` htmx fragment、予約条件からの候補算出導線。
- **設計同期**: data-model / domain-model / ui_design / ADR-007 / 開発 index を IT3 実績へ同期。

## 品質指標

| メトリクス | 実績 | 目標 | 判定 |
| :--- | :--- | :--- | :--- |
| `npm run verify` | 38 files / 251 tests green | 全 green | PASS |
| lint / typecheck / arch | no violation | 全 green | PASS |
| カバレッジ（全体 statements） | 94.38% | 80% | PASS |
| カバレッジ（全体 branches） | 82.84% | 75% | PASS |
| Routing domain coverage | 93.91% | 85% | PASS |
| SonarQube Quality Gate | PASS（new coverage 91.9%、new violations 0） | PASS | PASS |
| Docs build | PASS（既存 nav/link warning あり） | build 成功 | PASS |

## レビュー結果

XP 5 視点のマルチパースペクティブレビューを実施（[レビューレポート](../review/IT3実装_review_20260729.md)）。High 8 件は本クローズで対応または ADR/完了報告に方針明記した。

主な対応:

- 複数寄港地入力をフォームと Controller に追加し、2 区間 schedule として登録可能にした。
- 出発期間・希望着日検索、検索画面から候補算出する htmx 導線を追加した。
- 必須未入力時に項目名を示す RoutingValidationError を返すようにした。
- ADR-007 を段階移行判断へ更新し、Routing 候補は外部 ACL へ返済、Estimation 見積候補は IT4 まで暫定併存とした。

## 課題と残作業（IT4 引き継ぎ）

- 経路候補選択、予約への `CargoItinerary` 紐付け、`ROUTING_IN_PROGRESS → ROUTE_PROPOSED` 遷移。
- `FindRouteCandidatesService` への Application Service 化。
- 外部経路 service の timeout / abort と遅延 fallback test。
- Voyage 検索の join / aggregate query 化。
- Estimation 見積候補 Port と Routing 経路候補 Port の境界 ADR。

詳細は [イテレーション 3 ふりかえり](retrospective-3.md) を参照。
