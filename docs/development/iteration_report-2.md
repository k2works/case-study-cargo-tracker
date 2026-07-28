---
title: イテレーション 2 完了報告書
description: IT2（貨物予約 US04/US05/US06・Booking Context・ACL・IT1 負債返済）の完了報告
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 2 完了報告書

## エグゼクティブサマリー

Booking Context の Cargo 集約と BookingStatus 状態機械を確立し、貨物予約登録（US04）・危険物/冷凍貨物予約（US05）・経路設計者への引き渡し（US06）を序盤アウトサイドインの TDD で完成させた。あわせて Booking→Shipper の ACL 境界（`ShipperExistenceChecker`）を確立し、IT1 ふりかえりの技術的負債（DIP・Packwerk privacy・越境識別子）を返済した。計画 13 SP を 100% 消化し、RSpec 151 examples 0 failures・カバレッジ Line 96.07% / Branch 82.99% を達成。マルチパースペクティブレビューの高優先指摘 5 件を修正のうえクローズした。

## 達成状況

| US | 概要 | SP | 状態 |
|:---|:-----|:--|:-----|
| US04 | 貨物予約を登録する | 5 | ✅ 完了 |
| US05 | 危険物・冷凍貨物の予約を登録する | 5 | ✅ 完了 |
| US06 | 予約情報を経路設計者に引き渡す | 3 | ✅ 完了 |
| **計** | | **13** | **100%** |

デモ項目 4 点すべてを system spec で green 確認:

1. 営業担当者が荷主 ID・貨物仕様・輸送条件を入力して予約を登録すると、予約番号が発行され「仮受付」になる
2. 貨物種別で危険物/冷凍を選ぶと、危険物申告/温度条件が必須入力になる
3. 予約詳細から経路設計者へ引き渡すと「経路設計中」に更新され通知が送られる
4. Booking から Shipper への荷主存在確認が ACL 経由で行われる（直接参照なし）

## 技術的成果

- **Booking Context（ヘキサゴナル PORO）**: 値オブジェクト（BookingId・CargoType・RouteSpecification・Dimensions・HazardousDeclaration・TemperatureRequirement）・Cargo 集約・BookingStatus 状態機械（9 値・不正遷移で例外）・ActiveRecordCargoRepository（PORO↔AR・悲観ロック更新）・BookCargo ユースケース・CargoBookingService 公開ファサード。
- **ACL 境界**: `ShipperExistenceChecker` ポート → `ShipperDirectoryExistenceChecker` インプロセスアダプタ → `Shipper::Public::ShipperDirectory`。荷主存在確認を疎結合化。
- **負債返済**: T1（Shipper リポジトリポート/DIP）・T3（Packwerk privacy + 公開 API）・T4（越境識別子 ADR-0003）・T5（README）・T6（ui_design 語彙統一）・T9（ロックのアトミック化）。
- **UI**: 貨物予約一覧/登録/詳細（PRG・種別動的表示 Stimulus）・ロール別到達性。

変更規模: 54 files changed, +1777 / -63（8 コミット）。

## 品質指標

| 指標 | 結果 |
|:-----|:-----|
| RSpec | 151 examples, 0 failures |
| カバレッジ | Line 96.07% / Branch 82.99%（DoD 超過） |
| RuboCop | no offenses |
| Brakeman | Security Warnings 0 |
| bundler-audit | 0 vulnerabilities |
| Packwerk | validate/check green・**privacy 実効化**（packwerk-extensions） |
| CI（Backend CI） | success |
| SonarQube | 未実施（T8 として次 IT 繰越） |

## レビュー結果

5 視点のマルチパースペクティブレビューを実施（[レポート](../review/IT2実装_review_20260728.md)）。

- **クローズ前対応（高 5 件）**: US06 引き渡しの非アトミック更新を悲観ロックで解消、重量空文字の内部例外露出を日本語化、受入基準の未検証テスト（US06 通知・ファサード分岐・ACL アダプタ）を追加、ADR-0003 の設計正典同期（ShipperId VO 削除）、ui_design のロール/フィールド語彙統一（T6）。
- **次 IT 対応（Try 反映）**: JS 受入基準テスト（T11）・荷主名選択 UX（T14）・ドメイン層 AR 禁止 cop（T2）・SonarQube（T8）・Cargo.reconstitute 分離（T15）。

## 課題と残作業

- **SonarQube 品質ゲート未実施**: ruby/take-1 用の設定未整備。静的解析は rubocop/brakeman/bundler-audit/packwerk と SimpleCov で代替。IT3 で導入（T8）。
- **Release 0.1（v0.1.0）**: Phase 1 完了により本 IT クローズ後に `developing-release` でリリースする。
- **後続 IT スコープ**: US04 見積整合性（US01 依存）・寸法/個数/希望引渡日・US06 修正フロー・US05 の JS 動的表示テストは後続 IT で対応。

## 次イテレーション（IT3）引き継ぎ

- 持ち越しストーリーなし（IT2 スコープ全完了）。
- IT3 は中盤（インサイドアウト）・Routing Context（航海スケジュール US24/US25・経路候補 US07/US08）。**Location 共有カーネル（UN/LOCODE の VO 化・locations テーブル）を IT3 で導入**し RouteSpecification と整合させる。
- ふりかえり Try（T2/T8/T10-T15）を IT3 計画に組み込む。

## 関連ドキュメント

- [イテレーション 2 計画](iteration_plan-2.md)
- [イテレーション 2 ふりかえり](retrospective-2.md)
- [IT2 実装レビュー](../review/IT2実装_review_20260728.md)
- [ADR-0003 BC 越境識別子と ACL](../adr/0003-cross-context-identifier-and-acl.md)
- [リリース計画](release_plan.md)
