---
title: イテレーション 4 ふりかえり
description: IT4（経路確定・荷主通知・予約確定・追跡番号発行）の KPT ふりかえり
date: 2026-07-29
---

# イテレーション 4 ふりかえり（KPT）

対象: IT4（US09 経路選択・確定、US10 条件調整、US11 経路紐付け、US12 荷主通知、US13 予約確定、US14 追跡番号発行）。目標 16SP / 実績 16SP（達成率 100%）。

## サマリー

| 指標 | 値 |
| :--- | :--- |
| 目標 SP / 実績 SP | 16 / 16 |
| テスト | 297 件 green（45 ファイル） |
| CI | Lint/Typecheck/Arch/Test・E2E（Playwright）とも success（run 30439517437） |
| SonarQube | ローカル未設定のため未実行。CI の lint/typecheck/arch/test/E2E で代替 |
| レビュー | XP 5 視点レビュー実施。クローズ内対応 8 件 / 次 IT 引き継ぎ 8 件 |
| Try 返済 | IT3 Try T1〜T3・T5・T6 を返済（T4 は IT5 以降へ明示後置、T7 は CI 改善として別扱い） |

## Keep（継続すること）

- **中盤インサイドアウトが機能した**。`CargoItinerary` / `Leg` の連結制約と `BookingStatus` 遷移を domain test から固めたことで、Application・Presentation を積み上げても中心ルールが崩れなかった。
- **BC 独立性を維持できた**。Booking は Routing のドメイン型（`RouteCandidate` / `Voyage`）を import せず、Booking 固有の読み取り ACL（`RouteCandidateAcl` / `KyselyRouteCandidateReader`）と `LegDraft` DTO を境界にした。dependency-cruiser は no violation。
- **Try を計画のタスク・成功基準に具体化して返済できた**。T1（ロール完結 E2E）・T2（`FindRouteCandidatesService` 抽出）・T3（timeout）・T5（ADR-008）・T6（確認前検証）を IT4 内でクローズし、繰越の固定化を避けた。
- **レビューの高優先度をクローズ内で反映した**。ドメイン不変条件（端点一致・期限内）を集約に追加し、Presentation のクライアント値信頼と二層で防御。越権遮断・境界値テストも補強した。

## Problem（問題点）

- **US12 の通知先が荷受人（consignee）になっていた**。要件は荷主（shipper）への通知であり、承認判断者を取り違えていた。荷主メール取得の ACL が未整備で、IT4 は「通知記録の登録」で受入基準を満たす形にとどまった。
- **US12 の通知内容確認画面が未実装**。経由港・所要日数・到着予定日・料金概算を荷主が確認できる画面がなく、通知は即送信の記録のみ。
- **通知・イベントの副作用が非原子的**。`notify` / `emit` がコミット後の別処理で、失敗時にコマンド全体を失敗表示にする。Tracking 購読（IT5）着手前に方針確立が必要。
- **共有 DB 直読が dependency-cruiser の統制盲点**。`KyselyRouteCandidateReader` の Routing テーブル直読はコード import 検証をすり抜ける。ADR-008 に盲点として明記済みだが契約テストは未整備。
- **SonarQube 品質ゲートをローカルで実行できなかった**。ローカル SonarQube 未起動・トークン未設定のため、静的解析の品質ゲートは CI の自動チェックで代替した（正直に記録）。

## Try（次に試すこと）

| # | アクション | 期待効果 | 反映先 |
| :--- | :--- | :--- | :--- |
| T1 | US12 の通知先を荷主（shipper）へ是正し、荷主メール取得 ACL（`shipper` 参照）を追加。通知内容（経由港・所要日数・到着予定日・料金概算）の確認画面を実装 | 承認フローが業務的に成立する | IT5 |
| T2 | コミット後副作用（通知・イベント）を「コマンド失敗として扱わない」共通方針（冪等リスナー・アウトボックス）を ADR-005 と整合させて確立 | Tracking 購読時の結果整合を担保 | IT5（Tracking 着手前） |
| T3 | `leg.load_time` / `unload_time` を NOT NULL 化する migration を追加し、reconstruct のフォールバックを削除 | データ破損のサイレント許容を防ぐ | IT5 |
| T4 | 共有 DB 直読（Booking→Routing テーブル）にスキーマ契約テストを追加、または Routing 側読み取り ACL API 化を検討 | BC 境界の静的検証盲点を埋める | IT5 以降 |
| T5 | ローカル SonarQube のセットアップ（`operating-qt`）を整え、クローズの品質ゲートを CI 頼みにしない | 静的解析の品質ゲートをローカルで確定できる | IT5 opening |
| T6 | 追跡番号の採番主体を Tracking Context 実装時に再配置（ADR-008）。US14 の貨物状態「受領待ち（NOT_RECEIVED）」を Tracking 側で表現 | domain-model のイベントフローと整合 | IT5-6 |

## 次イテレーション（IT5）への引き継ぎ

- **スコープ**: US15（荷役作業記録・妥当性検証・MISROUTED 判定）、US16（引取作業記録・通関 CLEARED 前提）、US17（貨物状態の手動更新）。局面は中盤（インサイドアウト）継続。
- **持ち越し**: US12 荷主宛通知＋通知確認画面、通知/イベントの原子性方針、`leg` 時刻 NOT NULL 化、SonarQube ローカル整備、Tracking Context 着手（追跡番号採番主体の再配置・受領待ち状態）。
- **重点**: IT4 で作った `CargoItinerary` / `Leg` を荷役妥当性検証（`isValidFor` デシジョンテーブル）へ接続する。荷役登録が追跡・予約状態へ波及するイベント連携（`HandlingActivityRegisteredEvent`）のコミット後発行・冪等性を統合テストで固める。

詳細は [IT4 実装レビュー](../review/IT4実装_review_20260729.md) を参照。
