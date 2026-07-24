---
title: イテレーション 1 ふりかえり
description: IT1（US26 認証・US02/US03 荷主登録・US04 貨物予約登録・ウォーキングスケルトン）の KPT ふりかえり。
tags: development, retrospective, iteration-1, kpt, go
---

# イテレーション 1 ふりかえり（KPT）

対象: IT1（2026-07-24 開発完了）。US26 ログイン認証・US02/US03 荷主登録・US04 貨物予約登録・ウォーキングスケルトン。実績 15 SP。

## Keep（うまくいったこと）

### 技術的成功

- **インサイドアウト TDD の徹底**: 各 BC を domain → application → infrastructure → interfaces の順に Red-Green-Refactor で実装し、ドメイン層カバレッジ 90-100% を達成。
- **ヘキサゴナル + BC 独立性の実装**: ポートを application 層に定義、BC 間は ACL（ShipperExistenceChecker）+ イベント（CargoBooked）経由。go-arch-lint を BC×レイヤー構成にしレイヤー依存と BC 独立を同時強制。
- **ウォーキングスケルトンを Playwright E2E で担保**: 全ルートのナビゲーション + ロール制御を E2E で固定。以降の画面差し替えを安全に。
- **デモ項目を E2E 受け入れ基準に**: US02/03/04/26 の受入を E2E（15 本）で自動化。
- **品質ゲートの多層化**: make check（build/test/lint/govulncheck/arch）+ SonarQube Quality Gate（Bug 0・脆弱性 0・Code Smell 0・重複 0%）+ CI（unit+integration+build）を全通過。

### プロセス的成功

- **着手前検証（opening-iteration）で設計ギャップを早期検出**: UI 設計の荷主画面欠落・US 番号乖離を計画着手時に「注」として記録。
- **マルチパースペクティブレビュー（4 視点）で構造的負債を検出**: go-arch-lint 緑でも見えない「共有 sqlcgen の隠れた BC 結合」「ShipperId 意味二重化」をアーキテクト視点が発見。高優先度 2 件は即修正、2 件は ADR-0005 で方針明記。
- **ツーリング不整合の是正**: golangci-lint v2（go1.26 ビルド）への更新、go-arch-lint 構成刷新をイテレーション内で解決。

## Problem（課題・見積もりのズレ）

- **認証（US26）の当初スコープ漏れ**: IT1 で認証をスタブ（ROLE_SALES 固定）で代替し「スコープ外」とした判断が、ウォーキングスケルトンのロール制御の前提を欠く重大なギャップだった。ユーザー指摘で US26 を追加（目標 SP 10→15）。**要件段階でのロール/認可の位置づけが曖昧だった**。
- **設計と実装の乖離**: shipper_code 参照（BC 独立）と data-model の BIGINT FK・ShipperId(UUID) 定義の不一致、shipper に address 列がない等、上流設計（domain-model/data-model/ui_design）本体が未修正のまま計画「注」に留まっている。
- **共有カーネル型の意味論が未整理**: `ShipperId` が Shipper では UUID、Booking では shipper_code を運ぶ二重化。ADR-0005 で方針は決めたが型の改称は未実施。
- **US04 の受入充足が不完全だった**: 予約成功後の確認画面・エラー表示がレビューまで欠落していた（クローズ前に修正）。
- **見積もり**: US26 追加で 10→15 SP に増加。序盤オーバーヘッド（スケルトン・E2E 基盤・ツーリング是正）を含め実績 15 SP。ベロシティは 3 IT 完了時に再評価。

## Try（次イテレーションへの改善アクション）

| # | アクション | 担当 | 期限 | 期待効果 |
|---|---|---|---|---|
| T1 | **上流設計を実装に是正**: domain-model.md の ShipperId 定義・data-model.md の cargo.shipper_id(FK)/address 列・ui_design.md の荷主画面/US 番号を実装（shipper_code 参照）に合わせる | 開発チーム | IT2 着手時 | 設計を Single Source of Truth に保ち混乱コストを削減 |
| T2 | **共有カーネル型を改称**: BC 間参照キーを `ShipperCode` 型に正し、UUID の内部 ID を Shipper BC 内へ閉じる（ADR-0005） | 開発チーム | IT2 | ユビキタス言語の腐敗を除去 |
| T3 | **sqlc を BC 別パッケージへ分割**: `booking/infrastructure/sqlcgen` 等に分け go-arch-lint で BC 越境を構造的に検出（ADR-0005） | 開発チーム | IT2-3 | 隠れた BC 結合を構造で防止 |
| T4 | **重複の共有化**: `numericFromFloat`・コード生成（SHP-/BKG- プレフィックス）を shared へ抽出 | 開発チーム | IT2 | Rule of Three に基づく重複排除 |
| T5 | **受入 E2E の穴を埋める**: 割引率異常系（31%/負値）、cargo Save の round-trip 検証、CargoBooked ペイロード契約 | 開発チーム | IT2 | カバレッジ数値で見えない受入穴を塞ぐ |
| T6 | **予約入力項目の拡充**: 品名（優先）・寸法・個数・希望引渡日を US04 受入に沿って追加 | 開発チーム | IT2 | 現場の貨物特定要件を満たす |
| T7 | **要件段階でロール/認可を明示**: 各ユーザーストーリーに必要ロールを併記し、認証をスコープ外にしない | 開発チーム | 継続 | US26 のような横断関心事の漏れを防ぐ |

## 次イテレーション（IT2）への引き継ぎ

- **IT2 スコープ**: US05（危険物・冷凍貨物予約）・US13（予約確定）で Phase 1（予約・荷主基盤 MVP）を完了し Release 0.1 MVP へ。
- **持ち越し（Try 反映必須）**: T1（設計是正）・T2（ShipperCode 改称）は IT2 着手時に優先実施。US05 は CargoType の危険物申告/温度条件を扱うため、Cargo 集約の拡張（IT1 で PRELIMINARY まで）が前提。
- **ベロシティ**: IT1 実績 15 SP（序盤オーバーヘッド込み）。IT2 は 6 SP 計画だが、Try の設計是正分を加味して着手時に再見積もり。

## 更新履歴

| 日付 | 内容 |
|------|------|
| 2026-07-24 | 初版作成（IT1 クローズ時） |
