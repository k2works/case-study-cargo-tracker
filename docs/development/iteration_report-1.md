---
title: イテレーション 1 完了報告書
description: IT1（基盤 + 認証 US26/US27 + 荷主登録 US02/US03 + ウォーキングスケルトン）の完了報告
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 1 完了報告書

## エグゼクティブサマリー

Rails 8 + packs（DDD/ヘキサゴナル/CQRS）基盤の上に、ユーザー認証（US26/US27）とロール別ダッシュボード、荷主・法人荷主登録（US02/US03）を序盤アウトサイドインの TDD で完成させた。あわせて UI 設計の画面遷移図に沿った全ルートのプレースホルダ画面とロール制御ナビゲーションをウォーキングスケルトンとして先行配線した。計画 11 SP を 100% 消化し、RSpec 94 examples 0 failures・カバレッジ Line 97.49% / Branch 92.42% を達成。マルチパースペクティブレビューの高優先指摘 2 件を修正のうえクローズした。

## 達成状況

| US | 概要 | SP | 状態 |
|:---|:-----|:--|:-----|
| US26 | システムにログインする | 3 | ✅ 完了 |
| US27 | システムからログアウトする | 2 | ✅ 完了 |
| US02 | 荷主を登録する | 3 | ✅ 完了 |
| US03 | 法人荷主を登録する | 3 | ✅ 完了 |
| **計** | | **11** | **100%** |

デモ項目（イテレーションレビュー）4 点すべてを system spec で green 確認:

1. 利用者がログインし、ロールに応じたダッシュボードが表示される
2. 認証失敗を 5 回繰り返すとアカウントがロックされる
3. 営業担当者が個人荷主・法人荷主（割引率付き）を登録できる
4. ログアウトするとセッションが破棄され、業務画面へ戻れない

## 技術的成果

- **認証・認可基盤（共通）**: `User`/`UserRole`（5 ロール RBAC）・`AuthenticationService`（アカウントロック・監査ログ・無効化判定）・セッション認証コンサーン・Pundit ベースのロール認可。
- **Shipper Context（ヘキサゴナル PORO）**: 値オブジェクト（`ShipperCode`/`ShipperType`/`Address`/`DiscountRate`）・集約（`Shipper`/`CorporateShipper`）・出力アダプタ（`ActiveRecordShipperRepository`・PORO↔AR 変換）・ユースケース（`RegisterShipper`・メール重複検出）。
- **ウォーキングスケルトン**: 予約/経路/見積/追跡/荷役/航路/例外/請求/割引ポリシー/公開追跡の全 GET ルートをプレースホルダ化し、ロール別 `require_role`・ロール制御 navbar・ロール別到達性を担保。
- **UI 基盤**: CDN 非依存の素の CSS、開発環境のシード利用者（5 ロール）とログイン画面のロール別アカウント案内。

変更規模: 60 files changed, +2204 / -34（14 コミット）。

## 品質指標

| 指標 | 結果 |
|:-----|:-----|
| RSpec | 94 examples, 0 failures |
| カバレッジ | Line 97.49% / Branch 92.42%（DoD: ドメイン 85%・全体 80% を超過） |
| RuboCop | no offenses |
| Brakeman | Security Warnings 0 |
| bundler-audit | 0 vulnerabilities（loofah/rails-html-sanitizer をパッチ更新） |
| Packwerk | validate/check ともに違反 0（BC 独立性・依存宣言 OK） |
| CI（Backend CI） | success（workflow_dispatch / ruby/take-1） |
| SonarQube | 未実施（下記「課題と残作業」参照） |

## レビュー結果

5 視点のマルチパースペクティブレビューを実施（[レポート](../review/IT1実装_review_20260728.md)）。

- **クローズ前対応（高 2 件）**: email 一意制約欠如による重複登録の穴（TOCTOU）を DB 制約 + `RecordNotUnique` 合流で解消。法人割引率未入力時の内部例外露出を日本語メッセージ化。あわせて受入基準の未検証テスト 4 件を追加。
- **次 IT 対応（Try 反映）**: リポジトリポート抽象化（DIP）、ADR-0001 の RuboCop カスタム cop、Packwerk privacy、識別子の正本確定、README 整備、ユビキタス言語統一、荷主登録フォームの動的表示 UX。

## 課題と残作業

- **SonarQube 品質ゲート未実施**: ruby/take-1 用の `sonar-project.properties`・`SONAR_TOKEN` が未整備。静的解析は rubocop/brakeman/bundler-audit/packwerk（全 0 指摘）と SimpleCov で代替した。IT2 の運用タスクとして導入する（Try T8）。
- **設計硬化の残**: リポジトリポート・Packwerk privacy・越境識別子は Booking→Shipper の ACL に直結するため IT2 序盤で先着手する（Try T1/T3/T4）。

## 次イテレーション（IT2）引き継ぎ

- 持ち越しストーリーなし（IT1 スコープ全完了）。
- IT2 は貨物予約登録（US04/US05/US06・Cargo 集約・BookingStatus 状態機械）。Shipper で確立した PORO↔AR 変換パターンを踏襲。
- ふりかえり Try のうち T1/T3/T4/T8 を IT2 計画に「独立コミット枠」で組み込み、負債の固定化を防ぐ。

## 関連ドキュメント

- [イテレーション 1 計画](iteration_plan-1.md)
- [イテレーション 1 ふりかえり](retrospective-1.md)
- [IT1 実装レビュー](../review/IT1実装_review_20260728.md)
- [リリース計画](release_plan.md)
