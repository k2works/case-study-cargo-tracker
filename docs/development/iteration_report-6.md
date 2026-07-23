---
title: イテレーション 6 完了報告書 - 見積・公開照会・遅延例外
description: IT6（US01/US18/US19）輸送見積・追跡情報公開照会・遅延例外処理の完了報告
published: true
date: 2026-07-23T00:00:00.000Z
---

# イテレーション 6 完了報告書

## エグゼクティブサマリー

| 項目 | 内容 |
|------|------|
| **イテレーション** | 6（見積・公開照会・遅延例外） |
| **期間** | 2026-07-23（実績・集中実装セッション） |
| **局面** | 終盤（アウトサイドイン） |
| **計画 SP / 実績 SP** | 13 / 13 |
| **達成率** | 100%（機能スコープ） |
| **対象ストーリー** | US01・US18・US19 |
| **主要成果** | Estimation Context をスケルトンから本格実装し US01 見積フローが成立。公開追跡ページ（認証不要）で US18 照会、Tracking 例外イベントで US19 遅延例外→対応報告が成立。**Release 1.1 に着手（Phase 3 開始）**。IT5 Try#1/#2/#4/#5 返済＋#3 宛先解決。ADR-0007/0008 起票 |

IT6 は終盤（アウトサイドイン）の初回イテレーションとして、既存コンテキスト（Routing・Tracking）を業務シナリオ起点で束ね、輸送見積（US01）・追跡情報の公開照会（US18）・遅延例外処理（US19）を成立させた。新コンテキスト Estimation を BC 独立で新設し、Tracking に例外イベントを導入した。5 視点マルチパースペクティブレビューで重複指摘された高優先度（通知宛先ハードコード＝機能欠陥・料金テスト欠如・危険物出し分けテスト欠如・対応表不整合）をクローズ前に全対応し、ADR を 2 件起票した。累計 79/97 SP（81%）で計画ラインと一致、ベロシティは 6 IT 連続で安定推移している。

## 1. イテレーション概要

### 1.1 目的と背景

営業担当者が輸送要件から見積を作成し（US01）、荷主・荷受人がログイン不要で追跡状況を照会でき（US18）、追跡管理者が遅延例外を記録して荷主へ通知・対応報告できる（US19）ようにする。これにより Release 1.1（例外対応・請求）の起点となる見積・例外の基盤を確立する。

### 1.2 スコープ

| ID | ストーリー | SP | 結果 |
|----|-----------|----|----|
| US01 | 輸送見積を作成する | 5 | 完了（要件入力→ルート候補算出→見積保存・番号発行・期限内ルート無し通知・危険物出し分け） |
| US18 | 追跡情報を照会する | 3 | 完了（認証不要公開ページ・現在状態/位置/履歴/推定到着日・不存在エラー・共有 URL 導線） |
| US19 | 遅延例外を処理する | 5 | 完了（遅延記録→Exception 状態→荷主通知・対応報告→解決・履歴記録） |
| **合計** | | **13** | **全完了** |

スコープ外（後続 IT）: 破損・紛失例外（IT7）、料金算出・法人割引（IT7）、通知の実配信（メール送信）、確定経路からの推定到着日厳密化、dashboard 拡充。

## 2. 達成状況

### 2.1 ストーリー別受入条件

- **US01**: 輸送要件（出発地・目的地・期限・貨物種別・重量）入力→既存 Routing からルート候補（経由港・所要日数・概算料金・航海番号）算出→`Estimate` 集約に保存し `EstimateId`（EST-UUID）発行。期限内ルート無しは候補 0 件で通知。危険物選択時に申告フォーム出し分け。HTTP フロー 2 件・E2E 3 件・単体（料金 3 含む）で検証。
- **US18**: 追跡番号で現在状態・位置・履歴・推定到着日を認証不要ページ `/public/tracking/{trackingNumber}` で照会。不存在時「追跡番号が見つかりません」。共有 URL 導線を明示。HTTP フロー 2 件・E2E 1 件で検証。
- **US19**: 追跡管理者が遅延例外（種別・場所・日時・理由）を記録→`TrackingExceptionEvent` 追加→`current_status()` が Exception。荷主へ遅延通知（notification に EXCEPTION_RAISED・宛先＝荷受人連絡先）。対応報告（新到着予定日・対応方針）で解決し EXCEPTION_RESOLVED 記録・直前状態へ復帰。HTTP フロー 1 件（宛先アサート含む）・E2E 1 件・単体で検証。

### 2.2 局面移行の一貫性

終盤（アウトサイドイン）初回として、US01 を受入（HTTP/E2E）起点で設計し `domain-estimation` へ落とした。IT3-5 の対称 ACL パターンを踏襲し、Routing 参照を `RouteCandidateProvider` ACL に隔離。Tracking 例外は ADR-0006 の純粋関数導出方式を拡張した。

## 3. 技術的成果

### 3.1 実装（レイヤー別）

| レイヤー | 成果物 |
|---------|--------|
| domain-estimation（新設） | `Estimate` 集約・`EstimateId`(UUID)・`RouteCandidate`・`EstimateStatus`・`Weight`・`CargoType`・`EstimateLocation`・`replace_candidates()`/`has_feasible_route()`・`EstimateRepository` ポート（11 テスト） |
| app-estimation（昇格） | `CreateEstimateService`・`RouteCandidateProvider` ACL ポート・`RouteCandidateQuery`（mockall 4 テスト） |
| domain-tracking | `TrackingExceptionEvent`・`ExceptionType`(Delay)・`add_exception()`/`has_active_exception()`/`resolve_exception()`・`current_status()` 例外拡張・`find_by_booking_id`（12 テスト） |
| app-tracking | `TrackingExceptionService`（遅延記録・対応報告）・`notify_exception_raised/resolved`・冪等 `issue_tracking`（7 テスト） |
| infra-persistence | マイグレーション `20260916000001_it6_estimation_exception.sql`（estimate/route_candidate/tracking_exception_event・occurred_at は TIMESTAMPTZ）・`SqlxEstimateRepository`・例外永続化 |
| interface-web | `estimation_acl.rs`（`RoutingRouteCandidateProvider`・料金純粋関数）・`tracking_acl.rs`（例外通知・宛先解決）・見積/公開追跡/例外の各ハンドラ・テンプレート 6 種 |

### 3.2 アーキテクチャ上の意思決定（ADR）

- **ADR-0007 起票**: Estimation Context を独立クレートで新設し、Routing 参照を `RouteCandidateProvider` ACL に隔離。概算料金スタブは暫定的に ACL 層へ置き料金ポリシー確定時にドメインへ引き上げる。
- **ADR-0008 起票**: 公開照会ルートを per-handler で認証ガードを外して実現し、認可漏れ検出（ルーター分割）を将来対策として記録。
- **ADR-0006 踏襲**: 例外イベントを `current_status()` 末尾判定へ織り込む純粋関数導出の拡張。

## 4. 品質指標

| 指標 | 実績 |
|------|------|
| 全テスト | ワークスペース 230 テスト全 green（`cargo test` exit 0・domain-estimation 11 / app-estimation 4 / domain-tracking 12 / app-tracking 7 / interface-web 単体 7（料金 3）/ HTTP フロー 5（宛先アサート含む）/ E2E 25 件 IT1-6） |
| ビルド・Lint | ワークスペース clippy `-D warnings` クリーン・fmt 準拠 |
| ベロシティ | 13 SP（IT1=16→IT2=11→IT3=11→IT4=14→IT5=14→IT6=13・安定） |
| 累計進捗 | 79/97 SP（81%）・Phase 3 開始・Release 1.1 着手 |

### コミット内訳（IT6 分・計画作成以降）

| type | 件数 |
|------|------|
| feat | 6 |
| test | 2 |
| fix | 2 |
| refactor | 1 |
| docs | 4 |
| **変更規模** | 54 ファイル・+3,637 / -111 行 |

## 5. レビュー結果

5 視点マルチパースペクティブレビュー（[IT6 レビュー](../review/it6_development_review_20260723.md)）を実施。総評は「BC 独立・純粋関数導出・冪等性の中核設計は 5 視点いずれからも高評価」。高優先度 6 件・ADR 起票 2 件をクローズ前に対応した。

| # | 視点 | 指摘 | 対応 |
|---|------|------|------|
| H1 | programmer/tester/user/writer | 通知宛先が 4 箇所ハードコード＝荷主に届かない機能欠陥 | `resolve_recipient(booking_id)` で荷受人連絡先へ解決・HTTP フローで宛先アサート（Try#3） |
| H2 | programmer/architect | 概算料金のマジックナンバーがテスト非対象 | 名前付き定数＋純粋関数化・単体テスト 3 件追加 |
| H3 | tester/writer | 危険物フォーム出し分けが全レベル未検証 | E2E に HAZARDOUS 選択時の表示検証を追加 |
| H4 | writer | 受入基準対応表の通知種別が実装と不整合（DELAY_NOTIFIED） | EXCEPTION_RAISED に訂正 |
| H5 | writer/user | 公開追跡ページに共有 URL 導線が無い | 共有 URL 案内を追加・E2E で検証 |
| H6 | tester/programmer/user | 推定到着日が簡易実装で受入基準を厳密には未達 | 既知の負債として文書化・IT7 で確定経路連携（Try#4） |
| A1 | architect | Estimation Context 導入の ADR 未起票 | ADR-0007 起票 |
| A2 | architect | 公開ルート認可境界の ADR 未起票 | ADR-0008 起票 |

中・低優先度（rank 二重責務・transit_ports 切り詰め・複数例外テスト・JS 依存・UX 改善）は IT7 の Try に計上した。

## 6. 課題と残作業

- **通知テストの穴が 3 IT 連続で形を変えて露見**: IT4-6 で通知系の未検証（今回は宛先ハードコード）。IT7 で「宛先・本文までアサート」を DoD に追記（Try#1）。
- **推定到着日の厳密化**: 確定経路連携が前提のため IT7（Try#4）。
- **通知の実配信**: 記録止まり。メール送信・通知履歴導線は IT7（Try#3 繰り越し）。
- **UX 改善**: dashboard 拡充・見積有効期限・公開ページ再照会フォームは IT7（Try#6 繰り越し）。
- **料金ロジックの配置**: 料金ポリシー確定時に ACL からドメインへ引き上げ（ADR-0007）。

## 7. 次イテレーション（IT7）への引き継ぎ

- **IT7 スコープ**: 破損・紛失例外処理と輸送料金算出・法人割引適用。`ExceptionType` に Damage/Lost 追加、概算料金スタブをドメインへ引き上げ。
- **Try 6 件**: 通知宛先/本文アサート DoD 化・UI 表示制御の E2E 必須化・通知実配信・推定到着日厳密化・rank 採番一元化・UX 改善まとめ対応。

## 更新履歴

| 日付 | 内容 |
|------|------|
| 2026-07-23 | IT6 完了報告書作成 |

## 関連ドキュメント

- [イテレーション 6 計画](./iteration_plan-6.md)
- [イテレーション 6 ふりかえり](./retrospective-6.md)
- [IT6 開発成果物レビュー](../review/it6_development_review_20260723.md)
- [ADR-0007 Estimation Context 導入と Routing ACL 隔離](../adr/0007-estimation-context-and-routing-acl.md)
- [ADR-0008 公開照会ルートの認証境界分離](../adr/0008-public-route-authz-boundary.md)
- [リリース計画](./release_plan.md)
