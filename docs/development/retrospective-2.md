---
title: イテレーション 2 ふりかえり（KPT）
description: IT2（貨物予約 US04/US05/US06・Booking Context・ACL）の KPT と次イテレーション引き継ぎ
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 2 ふりかえり（KPT）

- **対象**: IT2（貨物予約 US04/US05/US06・Booking Context・Booking→Shipper の ACL・IT1 負債返済）
- **期間**: Week 3-4（〜2026-07-28 クローズ）
- **実績**: 13 SP 完了（達成率 100%）／ RSpec 151 examples 0 failures ／ Line 96.07% / Branch 82.99%

## Keep（うまくいったこと）

- **負債返済を BC 越境の前に先着手できた**。T1（リポジトリポート/DIP）・T3（Packwerk privacy）・T4（越境識別子 ADR-0003）を US04 着手前の独立コミット枠で片付け、ACL 実装をクリーンな境界の上で行えた。「余力次第にしない」方針が機能した。
- **ACL 境界の実効化**。`ShipperExistenceChecker` ポート → インプロセスアダプタ → `Shipper::Public::ShipperDirectory` の三層で Booking→Shipper を疎結合化し、Packwerk privacy（packwerk-extensions）で直接参照を静的に禁止できた。
- **公開 API パターンの確立**。Shipper/Booking とも `app/public` に公開ファサードを置き、内部集約を隠蔽しつつコントローラを薄く保てた。IT1 の Shipper 変換パターンも Cargo に踏襲でき、値オブジェクト・集約・リポジトリの立ち上げが速かった。
- **BookingStatus 状態機械**を単一の遷移表（FORWARD/CANCELLABLE）で実装し、不正遷移を型で弾けた。
- **開始準備の横断検証が今回も効いた**。着手前に RouteSpecification の Location 逸脱・ShipperId 二重定義・ナビ整合を検出し計画に織り込めた。

## Problem（課題・負債の発生源）

- **並行更新の考慮が Booking 側で漏れた**。IT1 で User にアトミック化（T9）したのに、US06 引き渡しの read-modify-write が非アトミックでレビューまで残った（クローズ前に悲観ロックで修正）。「並行更新はロックで一貫」を横断ルール化できていない。
- **rack_test では JS 受入基準を検証できない**。US05 の「種別選択で入力欄が表示」は Stimulus 依存で、rack_test の system spec が緑でも AC を保証しない構造的な穴が残った。
- **ADR で決めた正典同期が実装と同時に完了しなかった**。ADR-0003 で「domain-model の ShipperId VO 削除」を決めたのに、実装（scalar）と設計（VO 図示）が乖離したままレビューまで残った（MEMORY「スコープ変更→正典 3 点同時更新」の再発）。
- **入力例外の日本語化漏れ**。重量空文字で BigDecimal の英語例外が露出（IT1 の割引率と同型）。値変換の安全化がパターン化できていない。
- **フォームの荷主 ID 数値直接入力**は業務的に非現実的（`shippers.id` を知っている前提）。

## Try（次イテレーションの改善アクション）

| # | アクション | 期待効果 | 担当/時期 |
|:--|:--|:--|:--|
| T10 | 「並行更新はロックで一貫」を横断ルール化（状態遷移リポジトリに悲観ロック更新の口を標準装備） | 並行バグの再発防止 | IT3 |
| T11 | capybara-playwright の `:js` driver を導入し、JS 依存の受入基準（動的表示等）を system spec で検証 | rack_test の穴を塞ぐ | IT3 |
| T12 | ADR で正典変更を決めたら「実装と同一コミットで domain-model/data-model/該当計画を更新」を DoD 化 | 正典ドリフト防止 | IT3 |
| T13 | フォーム値の安全変換ヘルパ（空/非数値→ドメインで日本語メッセージ）を共通化 | 内部例外露出の再発防止 | IT3 |
| T14 | 荷主 ID 入力を荷主名/コードの検索・選択 UX に改善（`Shipper::Public::ShipperDirectory` を活用） | 営業担当者の業務適合性 | IT3 序盤 |
| T2（継続） | ドメイン層 AR 禁止 RuboCop カスタム cop を実装し CI 組込 | ドメイン純度を仕組みで担保 | IT3 |
| T8（継続） | SonarQube を ruby/take-1 に導入（sonar-project.properties・SimpleCov 連携） | 静的解析ゲートの正式化 | IT3 |
| T15 | Cargo.reconstitute（復元専用）を分離し、生成ファクトリの復元流用をやめる | 将来のルール厳格化に耐える | IT3 |

## 次イテレーション（IT3）への引き継ぎ

- **持ち越しなし**（IT2 スコープの US04/US05/US06 は全完了）。上記 Try は IT2 の負債返済であり IT3 計画に組み込む。
- **Release 0.1（v0.1.0）** は Phase 1（US26/US27/US02/US03/US04/US05/US06）完了により本 IT クローズ後に `developing-release` でリリースする。
- IT3 は中盤（インサイドアウト）・Routing Context（航海スケジュール US24/US25・経路候補 US07/US08）。**Location 共有カーネル（IT2 で String 保持とした UN/LOCODE の VO 化・locations テーブル）を IT3 で導入**し、Booking の RouteSpecification と整合させる。
- Booking で確立した ACL・公開 API・状態機械パターンを Routing に踏襲する。
