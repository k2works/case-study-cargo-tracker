---
title: イテレーション 5 ふりかえり（KPT）
description: IT5（追跡番号発行 US14・荷役記録 US15・引取 US16・貨物状態手動更新 US17・Tracking/Handling Context 新設・3 BC イベントコレオグラフィ・Phase 3 前半）の KPT と次イテレーション引き継ぎ
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 5 ふりかえり（KPT）

- **対象**: IT5（追跡番号発行 US14・荷役記録 US15・引取 US16・貨物状態手動更新 US17・Tracking Context / Handling Context 新設・3 BC イベントコレオグラフィ・Phase 3 前半）
- **期間**: Week 9-10（〜2026-07-28 クローズ）
- **実績**: 14 SP 完了（目標 14 SP・達成率 100%・Phase 3 前半）／ RSpec 294 examples 0 failures ／ 新規カバレッジ 92.7%・全体カバレッジ厚め維持 ／ Release 0.3 は Phase 3 完了（IT6）時のため IT5 では未発行

## Keep（うまくいったこと）

- **中盤インサイドアウトで新規 2 BC を貫通**。Tracking Context（`TrackingActivity` 集約・`TrackingNumber`・`TrackingStatus`・追跡イベント履歴）と Handling Context（`HandlingActivity`・`HandlingType`・`RecipientConfirmation`・`CargoSnapshot` ACL・`route_check` デシジョンテーブル・`HandlingActivityHistory` Read Model）を、集約 → リポジトリ → ユースケース → UI の順で貧血に陥らせず貫通できた。
- **3 BC のイベントコレオグラフィを疎結合に実現**。`handling_activity_registered`（1 イベント）→ Tracking 同期・Booking 状態同期・荷主通知（3 ハンドラ）というファンアウトを、`install_once` 冪等ガードとテスト分離（`rails_helper` の `reset!` → 3 wiring 再結線）で多重購読を防ぎつつ実装した。
- **負債返済枠を序盤の独立コミットで全消化**。T16（CI 相当ローカル検証定着）・T21+T24（voyages 楽観ロック・`reconstitute` 分離）・T25（`legs` 条件置換）・T26（`install_once` 冪等ガード）・T22（US08 多区間フォールバック・US25 差分確認）を IT5 序盤の独立コミットで先着手し、すべて消化した。特に IT4 で繰越した T22 を「繰越の連鎖を断つ」方針どおり序盤で消化できた（[[feedback_debt-allowance-defer-antipattern]] の再発回避）。
- **BC 独立性を ACL 射影とプリミティブイベントで担保**。`CargoSnapshot`（ACL 射影）とプリミティブ Hash イベントにより BC 越境を疎結合化し、Packwerk 違反ゼロ・一方向依存を維持した。
- **クローズ前レビューで受入基準ギャップを是正**。マルチパースペクティブレビュー（5 視点）で高 5 件をすべてクローズ前に対応済み（H1 MISROUTED 警告分離・H2 CLAIM 動的表示 Stimulus・H3 荷役前提状態ガードで Booking/Tracking 状態機械の乖離防止・H4 3 ハンドラ結合 spec・H5 ui_design 未実装ルート整合）。中 7 件中 5 件対応（M1 日時差分正規化・M2 発行冪等回復・M3 enum 日本語化・M4 ADR-0002 追記・M5 route_check 補完）。詳細は [IT5 実装レビュー](../review/IT5実装_review_20260728.md)。

## Problem（課題・負債の発生源）

- **状態機械の不変条件不足**。`TrackingStatus` / `HandlingActivity` に順序ガードがなく、RECEIVE 直後 CLAIM で Booking/Tracking が乖離しうる欠陥をレビューで初めて検出した（H3 で是正）。ドメイン設計時に状態遷移の前提条件（precondition）を洗い出せていなかった。
- **BC 越境の副作用テスト漏れ**。`handling_activity_registered` の 3 ハンドラのうち通知永続化を結合テストしておらず、レビュー（tester 高）で指摘された（H4 で是正）。
- **非原子な越境更新**。追跡番号発行が Booking コミット後に Tracking 保存という順序のため宙吊りリスクがあり、冪等回復で対応した（M2）。architect が指摘したファンアウトの非トランザクション性（partial-apply・イベント喪失窓）は将来 Outbox で受容する（L1）。
- **UI 実装漏れ**。CLAIM 動的表示 Stimulus 未実装・`trackings#new` プレースホルダ放置があった。US17 実装済にもかかわらず `trackings#new` がプレースホルダで UI 到達不能だった導線欠落を発見・修正した（追跡番号入力フォーム実装）。受入基準の UI 挙動（動的表示）を実装 DoD に落とし切れていなかった。

## Try（次イテレーションの改善アクション）

| # | アクション | 期待効果 | 担当/時期 |
|:--|:--|:--|:--|
| T28 | 荷役の二重登録防止（冪等キー: `booking_id` + `event_type` + `completion_time` + `voyage`） | 多重 POST での二重通知を防ぐ | IT6 序盤 |
| T29 | 楽観/悲観ロック競合（`StaleObjectError`・並行荷役）の回帰テストを追加 | ロックを入れた以上、競合時挙動を固定する | IT6 |
| T30 | ドメイン設計時に「状態遷移の前提条件（precondition）」を集約ごとに洗い出し、状態機械テーブルをユニット spec で固定する設計チェックを DoD 化 | 状態機械の乖離欠陥をレビュー前に検出 | IT6 計画時 |
| T31 | 受入基準の UI 挙動（動的表示・警告表示・導線到達）を実装 DoD のチェック項目に含め、プレースホルダ残存を機械的に検出 | 導線欠落・UI 実装漏れの再発防止 | IT6 |
| T32 | MISROUTED → `cargos.routing_status` 反映（導出と保存の整合設計） | 導出値と永続値の整合 | IT6（L2） |
| 将来 | `handling_activity_registered` のファンアウトを Solid Queue + Outbox へ（ADR-0002 既定） | イベント喪失窓の解消・越境更新の原子性確保 | Phase 後半以降（L1） |

## SonarQube 品質ゲート

| 指標 | 結果 | 目標 | 判定 |
|:--|:--|:--|:--|
| Quality Gate | PASS | PASS | ✅ |
| 新規コードカバレッジ | 92.7% | 80% 以上 | ✅ |
| 重複率 | 0.0% | 3% 未満 | ✅ |
| 違反（Violation） | 0 | 0 | ✅ |

補助的な静的解析も全てクリーン（rubocop / packwerk（privacy 新 pack）/ brakeman 0 / bundler-audit 0）、CI success。ドメイン層のカバレッジは厚く、新規カバレッジ 92.7% を確保した。

## 次イテレーション（IT6）への引き継ぎ

- **持ち越し**: T28（荷役二重登録防止）・T29（ロック競合回帰テスト）・T30（precondition 洗い出しの DoD 化）・T31（UI 挙動 DoD 化）・T32（MISROUTED 反映）・L1（Outbox 将来）・L3-L8（追跡番号 distinct テスト・`route_check` leg 単位照合・US14 通知本文の追跡 URL・handler 追跡導線・接続バッファ等）。
- **IT6 の位置づけ**: [release_plan.md](release_plan.md) の通り、IT6 は **Phase 3 後半** で、US18（追跡照会＝公開追跡ページ・Turbo 差分ポーリング）・US19（遅延例外）・US20（破損/紛失例外）を中核とする。IT5 で確立した Tracking Context（追跡イベント履歴）・通知基盤・`TrackingExceptionEvent`（IT6 で導入）を起点に結合する。[development_strategy.md](development_strategy.md) では IT3-IT6 を **中盤（インサイドアウト）** と位置づけており、IT6 も追跡・例外という中核ドメインをデータ層・ドメイン層から作り込む局面が続く。**IT6 完了で Release 0.3** を発行する。

## 更新履歴

| 日付 | 版 | 変更内容 | 担当 |
|:--|:--|:--|:--|
| 2026-07-28 | 初版 | IT5 ふりかえり（KPT）を作成 | 開発チーム |
