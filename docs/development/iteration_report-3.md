---
title: イテレーション 3 完了報告書
description: IT3（航海スケジュール US24/US25/US07/US08・Routing Context・Location 共有カーネル・外部経路 ACL）の完了報告
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 3 完了報告書

## エグゼクティブサマリー

Routing Context の Voyage 集約と航海スケジュールを確立し、航海スケジュールの新規登録（US24）・更新（US25）・検索（US07）・経路候補算出（US08）を中盤インサイドアウトの TDD で完成させた。あわせて Location 共有カーネル（packs/shared）を導入し、外部経路システムの ACL（Faraday HTTP + フォールバック）を WebMock 契約テストで確立した。計画 14 SP を 100% 消化し、RSpec 205 examples 0 failures・カバレッジ Line 94.27% を達成。US08 の BC 帰属を ADR-0004 で調停し、マルチパースペクティブレビューの高優先 6 件を修正のうえクローズした。

## 達成状況

| US | 概要 | SP | 状態 |
|:---|:-----|:--|:-----|
| US24 | 航海スケジュールを新規登録する | 3 | ✅ 完了 |
| US25 | 既存航海スケジュールを更新する | 3 | ✅ 完了（差分確認 UI は IT4） |
| US07 | 航海スケジュールを検索する | 3 | ✅ 完了 |
| US08 | 経路候補を算出する | 5 | ✅ 完了（寄港地接続評価の多区間は IT4） |
| **計** | | **14** | **100%** |

デモ項目 4 点すべてを system spec で green 確認:

1. 航海スケジュール（航海番号・運送会社・寄港地・出発/到着日）を登録できる
2. 出発地・目的地・貨物種別で航海を検索し、対応可能な航海が一覧表示される
3. 出発地・目的地・期限から経路候補が所要日数・経由港・費用・推奨順（直行便優先）で算出される
4. 外部経路システムがタイムアウト/障害でもフォールバック候補が返る

## 技術的成果

- **Location 共有カーネル（packs/shared）**: `Location` 値オブジェクト（UN/LOCODE 形式検証・same_as?）・locations テーブル・`Shared::Public::LocationDirectory` 公開 API。全 BC が UN/LOCODE でマスタ参照する方式（VO 埋め込み回避・privacy 両立）。
- **Routing Context（インサイドアウト）**: `Voyage` 集約・`VoyageNumber`・`Schedule`（時系列連結・時系列順検証）・`CarrierMovement`（日付整合）・`ActiveRecordVoyageRepository`（PORO↔AR・トランザクション差替）・`RegisterVoyage`/`UpdateSchedule`/`SearchVoyages`/`CalculateRouteCandidates`。
- **外部経路 ACL（ADR-0004）**: `ExternalCargoRoutingService` ポート・`ExternalCargoRoutingClient`（Faraday）・`LocalRoutingFallback`（過去実績データ）。WebMock 契約テスト（正常・タイムアウト・5xx・不正 JSON→フォールバック）。
- **RouteCandidate（一時計算値・非永続・ADR-0004）**: 推奨順（直行便優先→所要日数昇順）・期限内判定（DATE 単位比較で当日着を刈らない）。
- **負債返済**: T2（ドメイン AR 禁止 RuboCop cop）・T11（playwright :js ドライバ + US05 動的表示検証）・T13（安全変換ヘルパ）・T14（荷主名選択 UX）。

変更規模: 60 files changed, +2429 / -95（13 コミット）。

## 品質指標

| 指標 | 結果 |
|:-----|:-----|
| RSpec | 205 examples, 0 failures（:js 3 例含む） |
| カバレッジ | Line 94.27%（DoD: ドメイン 85%・全体 80% を超過） |
| RuboCop | no offenses（カスタム cop `NoActiveRecordInDomain` 込み） |
| Brakeman | Security Warnings 0 |
| bundler-audit | 0 vulnerabilities |
| Packwerk | validate/check green・privacy 実効化（shared/routing 追加） |
| CI（Backend CI） | success |
| SonarQube | Quality Gate PASS（Bug 0・Vulnerability 0・重複 0.0%・カバレッジ 82.7%・Code Smell 9 は方針明記で許容。クローズ後に T8 返済） |

## レビュー結果

5 視点のマルチパースペクティブレビューを実施（[レポート](../review/IT3実装_review_20260728.md)）。

- **クローズ前対応（高 6 件）**: Schedule の時系列連続性検証、RouteCandidate の transit_days 型正規化、外部 ACL の 5xx/不正 JSON フォールバック、経路割り当て画面のオーファン導線、Location 未配線の正直な記述、architecture_backend の ADR-0004 整合。
- **次 IT 対応（Try 反映）**: 楽観ロック・Location 実在検証配線・経路候補 UX（到着日/費用/運送会社）・入力補助・US25 差分確認・US08 寄港地接続評価・RouteCandidate 射影・命名統一・SonarQube。

## 課題と残作業

- **SonarQube 品質ゲート**: 本 IT クローズ後に T8 を返済し導入完了（Quality Gate PASS）。sonarqube.config.json・sonar-project.properties・SimpleCov JSON 連携を整備。残 Code Smell 9 件は方針明記で許容（ふりかえり参照）。
- **US25 差分確認・US08 寄港地接続評価**: スコープ調整として IT4。
- **Location 実在検証・楽観ロック**: IT4 序盤。
- **業務 UX ギャップ**（経路候補の到着日/費用/運送会社・航海登録の入力補助・港名検索）: IT4 の優先事項。「ローカル緑・CI 赤」が 2 回発生し、CI 相当のローカル検証標準化（T16）を Try に追加。

## 次イテレーション（IT4）引き継ぎ

- 持ち越しストーリーなし（IT3 スコープ完了・US25/US08 の一部はスコープ調整）。
- IT4 は Phase 2 後半（US09/US10/US11/US12/US13）。IT3 の Voyage・経路候補・Location・BookingStatus 状態機械を結合し、経路選択→予約確定まで通す。
- ふりかえり Try（T8/T16-T23）を IT4 計画に組み込む。

## 関連ドキュメント

- [イテレーション 3 計画](iteration_plan-3.md)
- [イテレーション 3 ふりかえり](retrospective-3.md)
- [IT3 実装レビュー](../review/IT3実装_review_20260728.md)
- [ADR-0004 US08 経路候補の BC 帰属](../adr/0004-us08-route-candidate-bc-placement.md)
- [リリース計画](release_plan.md)
