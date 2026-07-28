---
title: イテレーション 4 ふりかえり（KPT）
description: IT4（経路選択・確定 US09/条件調整再算出 US10/経路紐付け US11/確定経路通知 US12/予約確定・差戻し・キャンセル US13・通知基盤 ADR-0002・Phase 2 完了）の KPT と次イテレーション引き継ぎ
date: 2026-07-28T00:00:00.000Z
---

# イテレーション 4 ふりかえり（KPT）

- **対象**: IT4（経路選択・確定 US09・条件調整再算出 US10・経路紐付け US11・確定経路の荷主通知 US12・予約確定/差戻し/キャンセル US13・通知基盤 ADR-0002・Phase 2 完了）
- **期間**: Week 7-8（〜2026-07-28 クローズ）
- **実績**: 15 SP 完了（達成率 100%・Phase 2 完了）／ RSpec 239 examples 0 failures ／ 全体カバレッジ 94%超 ／ Release 0.2.0（`ruby/take-1/v0.2.0`）発行

## Keep（うまくいったこと）

- **期限当日着を日付単位で比較**。`RouteSpecification#satisfied_by?` で DATE 期限と TIMESTAMP 到着の素朴な After 比較による「期限当日着を誤って刈る」既知バグを、設計時点で日付単位比較として回避した。テストにも当日時刻付き着ケースを含めた。
- **ドメインイベント購読のテスト分離**。`rails_helper` の `before` で `reset!` → `install!` を行い、購読ハンドラのグローバル状態（`ActiveSupport::Notifications` 購読）が他 spec を汚染する「reset! ポリューション」を封じた。
- **通知基盤をアプリサービス発行に集約（ADR-0002）**。`DomainEvents`（`ActiveSupport::Notifications` ラップ）・`notifications` テーブル・`NotificationRecorder`・`NotificationWiring`・購読ハンドラという構成で、ドメイン層は純 PORO（DIP 優先）のまま発行をアプリ層に寄せた。将来の Outbox パターン移行を容易にする設計。
- **中盤インサイドアウトでタスク貫通**。データ層（`legs` 永続化・`cargos` 拡張＝consignee/routing_status）→ ドメイン（`CargoItinerary`/`Leg` 値オブジェクト・連結制約・到着時刻導出・最終脚 unload_time 必須・`Cargo#assign_itinerary`/`confirm`/`back_to_routing`/`cancel`・`BookingStatus` 差戻し遷移）→ アプリ（ドメインイベント `cargo_routed`/`cargo_confirmed`/`cargo_cancelled`/`cargo_consultation_requested`）→ UI の順で複雑な状態機械を貧血に陥らせず貫通できた。
- **クローズ前レビューで受入基準ギャップを是正**。マルチパースペクティブレビュー（5 視点）で検出した高 5 件をすべて対応済み（H1 `satisfied_by?` の nil 500 回避・H2 US10 協議依頼実装・H3 US12 明示送信化・H4 ADR-0002 正典改訂・H5 consignee 注記）。詳細は [IT4 実装レビュー](../review/IT4実装_review_20260728.md)。

## Problem（課題・負債の発生源）

- **負債返済枠の消化が不均衡**。T17/T18/T19（IT3 からの序盤先着手分）は完了、T23（イベント命名統一）は設計反映で実施した。しかし **T16（CI 相当ローカル検証の標準化）・T21（voyages 楽観ロック / reconstitute 分離）・T22（US25 差分確認画面・US08 多区間フォールバック）は未着手**。特に T22 は「余力次第にしない」と独立枠化したにもかかわらず未消化で、[[feedback_debt-allowance-defer-antipattern]]（余力次第の返済枠は固定化する）の再発リスクを露呈した。これはふりかえりの反省点である。
- **受入基準の読み込みが実装時に甘かった**。レビューで受入基準ギャップが複数露見した（US10 協議依頼の欠落・US12 が自動送信になっていた）。いずれもクローズ前レビューで初めて是正されており、計画段階での受入基準のテストケース化が不足していた。
- **architect 指摘の中優先事項を次 IT へ持ち越し**。`Cargo` の reconstitute 未分離（生成と復元の責務が混在）・`replace_legs` の無条件全置換・`install!` の冪等ガード欠如は、中優先として次 IT に持ち越した。

## Try（次イテレーションの改善アクション）

| # | アクション | 期待効果 | 担当/時期 |
|:--|:--|:--|:--|
| T16 | CI 相当のローカル検証をクローズ前チェック手順として実際に定着（`db:prepare`（seed 込み）+ eager load + `--order random` をローカルで実行） | 「ローカル緑・CI 赤」の再発防止・手順の形骸化解消 | IT5 |
| T21 | voyages 楽観ロックと `reconstitute` 分離を IT5 の独立コミット枠で先着手（また繰越さない） | 更新競合防止・責務明確化・負債固定化の回避 | IT5 序盤 |
| T22 | US25 差分確認画面・US08 寄港地接続評価（多区間フォールバック）を IT5 の独立コミット枠で先着手 | 受入基準の完全充足・繰越の連鎖を断つ | IT5 序盤 |
| T24 | `Cargo.reconstitute` を新設し生成／復元の責務を分離 | 集約の生成規律の明確化（architect 中優先） | IT5 |
| T25 | `replace_legs` を旅程変更時のみ実行するよう条件化 | 無条件全置換による意図しない上書きの防止 | IT5 |
| T26 | `install!` に冪等ガードを追加 | 多重購読・多重発行の防止 | IT5 |
| T27 | 受入基準を計画段階でテストケースに 1:1 マッピングし、UI 導線（協議依頼・明示通知）を実装 DoD に含める | 受入基準ギャップのクローズ前一括是正を避け、実装時に充足 | IT5 計画時 |

## SonarQube 品質ゲート

| 指標 | 結果 | 目標 | 判定 |
|:--|:--|:--|:--|
| Quality Gate | PASS | PASS | ✅ |
| 新規コードカバレッジ | 87.9% | 80% 以上 | ✅ |
| 重複率 | 0.0% | 3% 未満 | ✅ |
| 違反（Violation） | 0 | 0 | ✅ |

補助的な静的解析も全てクリーン（rubocop / packwerk(privacy) / brakeman 0 / bundler-audit 0）、CI success。ドメイン層のカバレッジは厚く、全体カバレッジは 94% 超を維持。

## 次イテレーション（IT5）への引き継ぎ

- **持ち越し（未着手・要着手）**: T16（CI 相当ローカル検証の定着）・T21（楽観ロック / reconstitute 分離）・T22（US25 差分確認・US08 多区間フォールバック）。いずれも IT5 の独立コミット枠で先着手し、繰越の連鎖を断つ。
- **architect 中優先（持ち越し）**: T24（`Cargo.reconstitute` 分離）・T25（`replace_legs` 条件化）・T26（`install!` 冪等ガード）。
- **低優先（一部反映済）**: L1-L7（多区間旅程 spec・悲観ロック競合 spec・通知 `event_type` 日本語ラベル・US06 イベント化・時系列連結検証・論理ポリモーフィック明記＝一部反映済）。
- **IT5 の位置づけ**: [release_plan.md](release_plan.md) の通り、IT5 は **Phase 3（追跡・荷役・例外処理）** の前半で、US14（追跡番号発行）・US15（荷役作業記録）を中核とする（Release 0.3 に向けた最初の IT）。[development_strategy.md](development_strategy.md) では IT3-IT6 を **中盤（インサイドアウト）** と位置づけており、IT5 も追跡・荷役という中核ドメインをデータ層・ドメイン層から作り込む局面が続く。IT4 で確立した通知基盤（ADR-0002・ドメインイベント）・`CargoItinerary`/`Leg`・`BookingStatus` 状態機械を、追跡番号発行・荷役記録の起点として結合する。

## 更新履歴

| 日付 | 版 | 変更内容 | 担当 |
|:--|:--|:--|:--|
| 2026-07-28 | 初版 | IT4 ふりかえり（KPT）を作成 | 開発チーム |
