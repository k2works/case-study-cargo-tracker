---
title: イテレーション 1 完了報告書
description: IT1（US26 認証・US02/US03 荷主登録・US04 貨物予約登録・ウォーキングスケルトン）の完了報告。
tags: development, iteration-report, iteration-1, go
---

# イテレーション 1 完了報告書

## エグゼクティブサマリー

国際貨物輸送管理システム（Go 版）の **IT1 を完了**した。序盤局面（アウトサイドイン）として、全ルートのナビゲーションと Playwright E2E 基盤を確立する**ウォーキングスケルトン**を通し、その上で**ログイン認証（US26）・荷主登録（US02/US03）・貨物予約登録（US04）**を DDD + ヘキサゴナル + CQRS で実装した。実績 **15 SP**、品質ゲート（make check / SonarQube Quality Gate / CI）を全通過。

当初 IT1 は認証をスタブで代替しスコープ外としていたが、ロール制御・認可の前提となる認証の欠落を重大なギャップと判断し、**US26 を新設して組み込んだ**（目標 SP 10→15）。

## 達成状況

| ストーリー | 内容 | SP | 状態 |
|---|---|---|---|
| US26 | システムにログインする | 5 | ✅ 完了 |
| US02 | 荷主を登録する | 3 | ✅ 完了 |
| US03 | 法人荷主を登録する | 2 | ✅ 完了 |
| US04 | 貨物予約を登録する | 5 | ✅ 完了（仮受付まで。通知・見積整合は Phase 2） |
| **合計** | | **15** | **達成率 100%** |

### 成功基準

- [x] 全ルートのナビゲーション E2E（Playwright）が green
- [x] US26・US02・US03・US04 の受入基準を満たす（一部は Phase 2 で充足と明記）
- [x] `make check`（build + test + lint + arch）green
- [x] ドメイン層カバレッジ 90% 以上（shipper 100%・booking 100%・shared 90%・auth 100%）
- [x] ヘキサゴナル + BC 境界（`make arch`）green
- [x] SonarQube Quality Gate PASS・CI success

## 技術的成果

### 実装

- **8 境界付けられたコンテキスト**のうち booking / shipper / shared（auth 含む）を実装。domain / application / infrastructure / interfaces の 4 層を各 BC に確立。
- **認証・認可**: scs セッション + bcrypt + 自作 RBAC（RequireAuth / RequireRole、ROLE_ADMIN は全機能）。users/user_roles テーブルとシード。
- **ウォーキングスケルトン**: 共通レイアウト・ロール制御 navbar・全ルートプレースホルダ・手動 DI（cmd/server）。
- **永続化**: sqlc + pgx v5、golang-migrate（4 マイグレーション + デモ seed）。
- **BC 独立性**: Booking→Shipper は業務識別子 shipper_code + ACL 経由。CargoBooked イベントの段階導入。

### コード規模

- プロダクションコード 約 2,100 行 / テストコード 約 1,100 行（Go）。E2E 15 本（TypeScript）。
- コミット 27 件（feat 11・docs 9・test 3・chore 2・fix/refactor 2）。

## 品質指標

| 指標 | 結果 | 目標 |
|---|---|---|
| SonarQube Quality Gate | **PASS** | PASS |
| Bug | 0 | 0 |
| Vulnerability | 0 | 0 |
| Code Smell | 0 | 可能な限り 0 |
| 重複率 | 0.0% | 3% 未満 |
| カバレッジ（全体） | 67.3% | 80%（ドメイン層 90%） |
| ドメイン層カバレッジ | 90-100% | 90% |
| E2E | 15/15 passed | 全 green |
| CI（lint/test/build） | success | success |

> 全体カバレッジ 67.3% は cmd/DI・埋め込み資産を含む値。ドメイン層は 90-100% を維持。

## レビュー結果

マルチパースペクティブレビュー（4 視点）を実施（[docs/review/it1_go_review_20260724.md](../review/it1_go_review_20260724.md)）。

- **高優先度 5 件**: エラー表示・予約確認画面の欠落 2 件を**クローズ前に修正**。ShipperId/shipper_code 意味論・共有 sqlcgen 結合の 2 件を **ADR-0005 で方針明記**（IT2 の Try）。認証 enabled 順序 1 件は情報漏洩がなく許容。
- **中 7 件・低 5 件**: ふりかえり Try（T1-T7）として IT2 へ引き継ぎ。

## 課題と残作業

- **上流設計の是正**（Try T1）: domain-model / data-model / ui_design を実装（shipper_code 参照・address 列・荷主画面）に合わせる。
- **共有カーネル型の改称**（Try T2）・**sqlc の BC 別分割**（Try T3）: ADR-0005 に基づき IT2 で構造的負債を返済。
- **US04 の受入拡充**（Try T5/T6）: 割引率異常系 E2E・品名等の入力項目。

## 次イテレーション引き継ぎ

- **IT2 スコープ**: US05（危険物・冷凍貨物予約）・US13（予約確定）で Phase 1 を完了し Release 0.1 MVP へ。
- **前提**: IT2 着手時に Try T1（設計是正）・T2（ShipperCode 改称）を優先実施。US05 は Cargo 集約の危険物申告/温度条件拡張が必要。
- **ベロシティ**: IT1 実績 15 SP（序盤オーバーヘッド込み）。3 IT 完了時に全体再見積もり。

## 更新履歴

| 日付 | 内容 |
|------|------|
| 2026-07-24 | 初版作成（IT1 クローズ） |
