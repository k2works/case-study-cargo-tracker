---
title: IT7 開発成果物レビュー（US20/US21/US22）
description: 破損・紛失例外／輸送料金算出／法人割引の 5 視点マルチパースペクティブレビュー統合レポート
published: true
date: 2026-07-24T00:00:00.000Z
---

# IT7 開発成果物レビュー

対象差分: `fdd3058f..HEAD`（US20 破損・紛失例外／US21 輸送料金算出／US22 法人割引）。
5 つの XP 視点（programmer / tester / architect / technical-writer / user-representative）で並列レビューし統合した。

## 総合判定

**全視点でクローズ可（条件付き）**。計算・ルール層（domain-billing / app-billing / domain-tracking）は境界値・状態遷移・通知・BC 独立の観点で高品質。escalation の全層一貫検証は他 IT の手本。ギャップは (1) 再算出時の correctness バグ 1 件、(2) UI 可視化・導線、(3) HTTP テスト網羅、(4) 表示整形に集約される。

## 高優先度指摘とクローズ前対応

| # | 視点 | 指摘 | 重大度 | 対応 |
|---|------|------|--------|------|
| 1 | programmer | 再算出時に `calculate` が新 charge_id を採番するが upsert(ON CONFLICT booking_id) は charge_id を更新せず、戻り値の新 ID で redirect すると 404 | 高 | **クローズ前修正**: 既存があれば charge_id を再利用（冪等・IT6 ADR-0006 パターン踏襲）＋再算出テスト追加 |
| 2 | technical-writer | 割引率が「0.1500」と生 Decimal 表示。US22「0〜30%」表記意図とズレ | 高（UX） | **クローズ前修正**: % 換算表示（15.00%） |
| 3 | user-rep | 料金詳細に輸送実績（経路・距離・重量・貨物種別）が非表示。US21 受入基準2の核心・「根拠なしに確定」 | 高 | **クローズ前修正**: charge_show で実績を ACL 再取得して表示 |
| 4 | user-rep | 追跡詳細に例外・緊急フラグが非表示・対応報告(resolve)への導線がなく到達不能 | 高 | **クローズ前修正**: tracking_show に例外一覧・escalation バッジ・解決リンクを追加・登録リンク文言を「例外を登録する」に |
| 5 | tester | US20 破損/紛失の対応報告(resolve)テストが遅延種別のみ | 高 | **クローズ前修正**: 破損 resolve の HTTP テスト追加 |
| 6 | tester | US21 例外調整入力の HTTP 未実証（受入基準6） | 中 | **クローズ前修正**: 調整付き料金算出の HTTP テスト追加 |
| 7 | programmer | 金額の丸め未定義（`multiply_ratio`・NUMERIC(15,2)）。現状は割り切れる値のみで露見せず | 高（低リスク） | **クローズ前対応**: 円未満切り捨て（round_dp(0)）を Money 演算に明示＋端数テスト。JPY 前提を ADR-0010 に追記 |

## 中・低指摘（IT8 Try へ繰り越し・方針明記）

| 視点 | 指摘 | 方針 |
|------|------|------|
| user-rep / architect | 荷役作業実績が料金計算式に未反映（US21 受入基準2） | IT8 Try: 料金モデル拡張時に荷役実績を反映。現状は重量×距離×係数で近似（負債明記） |
| architect / user-rep | distance が名目スタブ（レグ×5000km）。実距離未保持 | IT8 Try: Routing 実績距離への差し替え（distance カラム追加）。ADR/コメントで可視化済み |
| architect | per-handler で service/ACL をハンドラ内組立。composition root へ引き上げ余地 | IT8 Try: DI を composition root へ整理 |
| architect / programmer | rank 一元化（ADR-0007・IT6 Try#5）未返済 | IT8 Try: 継続 |
| user-rep | 通知の実配信・履歴可視化 UI なし（送信＝記録） | IT8 Try#3（IT6 から繰り越し済み） |
| tester | 確定後の再操作拒否・法人×調整併用が HTTP 未実証 | IT8: US23 精算実装時に合わせて補完 |
| tech-writer | iteration_plan-7 ER 図が旧値（UUID/BIGINT）・exceptionId 表記ゆれ・料金画面見出しゆれ | 軽微・随時整える |

## 良い点（維持すべき規律）

- `RoleGuard<BillingRole>` 型レベル認可の踏襲（認可書き忘れをコンパイラが防ぐ）
- `rates` 名前付き定数＋`calculate_base_amount` 純粋関数＋リグレッションテスト固定（IT6 Try 教訓）
- `requires_escalation` を ExceptionType に閉じ込め種別非依存で維持（ADR-0006）
- domain-billing の BC 独立（Cargo.toml で shared-kernel のみ・ACL でプリミティブ受け渡し・ADR-0010）
- 確定済み集約への操作を `ensure_draft` で一元拒否

## 関連ドキュメント

- [IT7 計画](../development/iteration_plan-7.md)
- [ADR-0009 輸送料金と精算書の段階分割](../adr/0009-freight-charge-and-invoice-separation.md)
- [ADR-0010 Money の BC ローカル定義](../adr/0010-billing-money-value-object.md)
