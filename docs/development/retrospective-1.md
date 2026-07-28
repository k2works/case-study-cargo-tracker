---
title: イテレーション 1 ふりかえり（KPT）
description: IT1（基盤 + 認証 US26/US27 + 荷主登録 US02/US03）の KPT と次イテレーション引き継ぎ
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 1 ふりかえり（KPT）

- **対象**: IT1（Rails 8 基盤・認証 US26/US27・荷主登録 US02/US03・全ルートプレースホルダ）
- **期間**: Week 1-2（〜2026-07-28 クローズ）
- **実績**: 11 SP 完了（達成率 100%）／ RSpec 94 examples 0 failures ／ Line 97.49% / Branch 92.42%

## Keep（うまくいったこと）

- **ヘキサゴナル/DDD の層分離が明快に立ち上がった**。値オブジェクト（`Data.define` + 生成時不変条件）・集約 PORO・AR リポジトリ（`to_columns`/`to_domain`）の変換パターンを Shipper で確立でき、以降の BC に横展開できる。
- **アウトサイドインの TDD が機能**。system spec を入口に据え、受入基準（US26 ロック・US02 重複・US03 割引率境界）をテストで先行定義してから内側を実装できた。
- **ウォーキングスケルトンで全画面のプレースホルダ + ロール制御ナビを先行配線**。ナビゲーション整合とロール別到達性・403 を骨格段階で担保し、後続 IT は中身を埋めるだけになった。
- **開始準備（opening-iteration）の横断検証が効いた**。着手前に data-model の正典ドリフト（割引率 15%→30%・ロール語彙・住所カラム）を検出・修正でき、実装の手戻りを防げた。
- **認証を業務パック外（app/）に分離**する判断がレビューでも妥当と評価された。

## Problem（課題・負債の発生源）

- **約束したガードレールが未実装**。ADR-0001 の「ドメイン層 AR 依存禁止」を担保する RuboCop カスタム cop が無く、Packwerk privacy も未強制。ドメイン純度は「今は正しいが仕組みで守られていない」状態。
- **アプリ層 → 具象リポジトリの依存（DIP 逆流）**。`RegisterShipper` がデフォルト引数で `ActiveRecordShipperRepository` を名指し。リポジトリポート（抽象）が未定義。
- **正しさに関わる不具合が 2 件混入**（クローズ前に修正）。email 一意制約欠如による重複登録の穴、法人割引率未入力時の内部例外露出。DB 制約と入力ガードの検討が甘かった。
- **ユビキタス言語のドリフト**。認証フィールド名（利用者 ID / ユーザー名 / メール）、ui_design のロール（存在しない shipper/consignee）、識別子（設計 ShipperId vs 実装 ShipperCode）が設計と実装で不一致。
- **README が Rails 雛形のまま**で、新規開発者が IT1 の到達点に自力で到達できない。
- **SonarQube 品質ゲートが未整備**（sonar-project.properties/SONAR_TOKEN なし）で、静的解析ゲートの一部を代替ツールで賄った。
- **CI が take ブランチで自動起動しない**構成だった（workflow_dispatch を追加して対処）。

## Try（次イテレーションの改善アクション）

| # | アクション | 期待効果 | 担当/時期 |
|:--|:--|:--|:--|
| T1 | Booking→Shipper の ACL 導入前に、リポジトリポート（`Shipper::Domain::ShipperRepository`）を定義し DIP を正す | ヘキサゴナルの依存方向を回復、テスト容易性向上 | IT2 序盤 |
| T2 | ADR-0001 の RuboCop カスタム cop（ドメイン層 AR 禁止）を実装 or ADR を実態に改訂 | ドメイン純度を仕組みで担保 | IT2 |
| T3 | Packwerk `enforce_privacy: true` + `public/` で公開面を定義 | BC 越境前に境界を固定 | IT2（BC 連携着手前） |
| T4 | 越境識別子（ShipperId/ShipperCode）の正本を確定し ADR 追記、ユビキタス言語を統一 | 変換層の二重化を防止 | IT2 |
| T5 | README を IT1 到達点に整備（起動・seed・シードアカウント・テスト・packs 構成） | 引き継ぎの入口を確保 | IT2 序盤の独立コミット枠 |
| T6 | ui_design のロール表記を 5 ロール RBAC + 外部未認証に統一、認証フィールド名を「利用者 ID」に統一 | 設計・実装の語彙一致 | IT2 |
| T7 | 荷主登録フォームの動的表示（種別選択で法人契約情報を表示）を Stimulus で実装、US02 の既存荷主選択フローを充実 | 業務利用者の認知負荷軽減 | IT2 |
| T8 | SonarQube を ruby/take-1 に導入（sonar-project.properties・SimpleCov 連携・Quality Gate） | 静的解析ゲートの正式化 | IT2 の運用タスク |
| T9 | アカウントロック仕様（時限/恒久）を確定し文言と実装を整合、`register_failure!` のアトミック化 | セキュリティ挙動の一貫性 | IT2 |

## 次イテレーション（IT2）への引き継ぎ

- **持ち越しなし**（IT1 スコープの US26/US27/US02/US03 は全完了）。上記 Try は IT1 の負債返済であり IT2 計画に組み込む。
- IT2 は貨物予約登録（US04/US05/US06・Cargo 集約・BookingStatus 状態機械）。**T1/T3/T4（ポート・privacy・識別子）は Booking→Shipper の ACL に直結するため IT2 序盤で先着手する**（「余力次第」にせず独立コミット枠を確保）。
- Shipper で確立した PORO↔AR 変換パターンを Cargo 集約に踏襲する。
