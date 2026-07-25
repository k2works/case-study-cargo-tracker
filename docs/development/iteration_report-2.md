---
title: イテレーション 2 完了報告書
description: IT2（US05 危険物・冷凍貨物予約・US13 予約確定/キャンセル/差し戻し・Try 返済）の完了報告。Phase 1 完了・Release 0.1 MVP 到達。
tags: development, iteration-report, iteration-2, go
---

# イテレーション 2 完了報告書

## エグゼクティブサマリー

国際貨物輸送管理システム（Go 版）の **IT2 を完了**した。序盤局面（アウトサイドイン）の締めくくりとして、**危険物・冷凍貨物の予約登録（US05）** と **予約確定・キャンセル・経路再設計への差し戻し（US13）** を DDD + ヘキサゴナル + CQRS で実装し、**Phase 1（予約・荷主管理基盤 MVP）を完了**、**Release 0.1 MVP** に到達した。

あわせて IT1 ふりかえりの Try を返済し、共有カーネルの `ShipperCode` 改称（意味二重化の解消）・`ShipperId`(UUID) の Shipper BC 移設（ADR-0005 決定2 完遂）・上流設計ドキュメントの実装是正・業務コード生成の共有化を実施した。貨物予約一覧を実装しナビ導線を接続、画面スタイルも整備した。実績 **8 SP**、品質ゲート（make check / SonarQube Quality Gate PASS / CI success）を全通過。

## 達成状況

| ストーリー | 内容 | SP | 状態 |
|---|---|---|---|
| US05 | 危険物・冷凍貨物の予約を登録する | 3 | ✅ 完了（候補フィルタは Phase 2/US08） |
| US13 | 予約を確定する | 3 | ✅ 完了（通知・選択ルート表示は Phase 2） |
| **合計** | | **6（実績 8）** | **達成率 100%** |

### 成功基準

- [x] US05・US13 の受け入れ基準を満たす（Phase 2 依存分は「注」で明示）
- [x] 危険物・冷凍貨物の異常系（クラス未入力・温度範囲逆転）を E2E/ユニットで固定
- [x] 設計ドキュメント（data-model / domain-model / ui_design）を実装に是正（T1）
- [x] 共有カーネルの `ShipperCode` 改称・`ShipperId` の Shipper BC 移設（T2 / ADR-0005 決定2）
- [x] `make check` green・SonarQube Quality Gate PASS・CI success
- [x] ドメイン層カバレッジ 90% 以上（booking 98.9%・shipper 100%）

## 技術的成果

### 実装

- **US05 特殊貨物**: `HazardousDeclaration`（危険物クラス・UN 番号・正式輸送品名）・`TemperatureRequirement`（最低/最高温度・温度単位）値オブジェクトと、貨物種別 × 特殊情報の整合検証を Cargo 集約に追加。マイグレーション 000005 で cargo に特殊貨物列を追加（nullable、必須性はドメイン不変条件）。フォームは貨物種別で入力欄を切替。
- **US13 予約ライフサイクル**: `Cargo.Confirm/Cancel/SendBackToRouting` 状態遷移と不変条件。`ManageBookingService` + `BookingLifecycleRepository` ポート。予約詳細画面 `/bookings/{bookingId}` と確定/キャンセル/差し戻しアクション（PRG）。操作可否をドメインの `CanConfirm/CanCancel/CanSendBackToRouting` で制御。
- **貨物予約一覧**: `CargoQueryService` + DTO + `CargoQuery` アダプタ（CQRS）。ナビ「貨物予約」→ 一覧 → 新規登録/詳細の導線を接続。
- **Try 返済**: T1（設計是正: data-model/domain-model/ui_design を shipper_code・ShipperCode・特殊貨物・US13 アクションに是正）、T2（共有カーネル `ShipperCode` 新設・`ShipperId` を Shipper BC へ移設・ADR-0005 決定2 完遂）、T4（`GenerateBusinessCode` で SHP-/BKG- コード生成を共有化）、T5（冷凍貨物 round-trip・状態更新の integration テスト）。

### コード規模

- 実装差分 約 1,794 行追加 / 123 行削除（internal 配下）。Go 実装 33 ファイル。
- コミット 17 件（feat 3・refactor 3・docs 4・style 2・test 1・fix 1・その他クローズ作業）。

## 品質指標

| 指標 | 結果 |
|---|---|
| 単体テスト | 全 green |
| 統合テスト（testcontainers） | 全 green（Repository round-trip・状態更新・一覧） |
| E2E（Playwright） | 全 22 本 green（US05 特殊貨物・US13 遷移・キャンセル後のボタン抑止・ナビ導線） |
| ドメイン層カバレッジ | booking 98.9% / shipper 100% |
| SonarQube Quality Gate | **PASS**（Bug 0・Vulnerability 0・Code Smell 0・重複 0%・新規カバレッジ 84.2%） |
| make check | green（build + test + lint + govulncheck + arch） |
| CI（Backend CI） | success |

## レビュー結果

マルチパースペクティブレビュー（XP 5 視点）を実施（[レビューレポート](../review/it2_go_review_20260725.md)）。高優先度は**クローズ前に全対応**:

- Programmer H1/H2: 予約操作の可否判定をドメインに集約し UI/ドメイン不整合を修正、真理値表テスト追加
- Architect H1: `shared.ShipperId`(UUID) を Shipper BC へ移設し ADR-0005 決定2 完遂
- Tester: 華氏正常系・温度境界・application 許容外遷移・E2E ボタン抑止など
- Programmer M3: 遷移エラーの日本語メッセージ化

中・低優先度（荷主選択導線・必須検証の UI 明示・T3 sqlc 分割・一覧の可読性など）は IT3 の Try に計上。

## 課題と残作業

- **T3（sqlc BC 別分割）**: ADR-0005 決定3。共有 sqlcgen のまま据え置き（規律で BC 越境を禁止）。IT3 で構造的強制へ。
- **Phase 2 依存**: US05 の経路候補フィルタ（US08）、US13 の追跡番号発行通知・選択ルート表示（US09/通知基盤）は該当 Phase 2 US で充足。
- **UX 改善**: 特殊貨物の必須検証の UI 明示・荷主選択導線・キャンセル/差し戻しの確認と理由入力。

## 次イテレーション（IT3）への引き継ぎ

- **局面転換**: 中盤局面（インサイドアウト）へ。Phase 2 の複雑ドメイン（航海スケジュール検索 US07・経路候補算出 US08）を domain/data 層から作り込む。
- **優先 Try**: T3（sqlc BC 別分割）・カバレッジ計測の integration タグ込み標準化を着手時に実施。
- **ベロシティ**: IT1 15 SP・IT2 8 SP。3 IT 完了時（IT3）に再評価。

## 関連ドキュメント

- [IT2 計画](iteration_plan-2.md)
- [IT2 ふりかえり](retrospective-2.md)
- [IT2 レビュー](../review/it2_go_review_20260725.md)
- [リリース計画](release_plan.md)
- [ADR-0005](../adr/0005-bc-reference-and-shared-sqlcgen.md)
