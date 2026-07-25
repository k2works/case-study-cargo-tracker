---
title: イテレーション 5 ふりかえり
description: IT5（US10 経路条件調整・US11 経路情報可視化・US12 確定経路通知・経路設計ナビ導線）の KPT ふりかえり。
tags: development, retrospective, iteration-5, kpt, go
---

# イテレーション 5 ふりかえり（KPT）

対象: IT5（2026-07-25 開発完了）。中盤局面（インサイドアウト）。US10 経路条件調整（再算出・協議依頼・MISROUTED 再調整）・US11 経路情報の可視化補完・US12 確定経路通知（NotificationPort・通知記録）を実装し、経路設計フローを運用完成させた。実績 7 SP。

## Keep（うまくいったこと）

### 技術的成功

- **既存資産の再利用で軽量実装**: US10 の再算出は IT4 の `SearchRoutesService`/`RouteFinder` を `RouteAdjustment`（期限オーバーライド）で再駆動するだけで実現。US11 は IT4 の `AssignItinerary` を前提に CQRS クエリ側の可視化補完に絞り、二重実装を回避。7 SP で 3 ストーリー＋ナビ導線を収めた。
- **BC 独立性の要を opening で潰した効果**: NotificationPort の配置を opening の validating-design で「shared ではなく booking/application」に確定していたため、実装は既存 ACL 先例（EventPublisher/RouteSearcher）と同型でぶれなかった。Architect レビューも「構造的問題なし」。着手前検証の投資が実装のブレを防いだ典型。
- **NotificationPort のログ実装パターンの再利用**: 実メール送信を抽象化し、IT1 の loggingPublisher と同型の loggingNotifier ＋ notification 記録テーブルで US12 を実現。外部連携の作り込みを避けつつ受入基準（送信・記録）を満たした。
- **ドメイン層カバレッジ 97.5%（booking）**: 値オブジェクト（Notification・Leg・CargoItinerary）と集約メソッド（MarkMisrouted・BuildRouteNotificationContent）の不変条件を隔離検証。

### プロセス的成功

- **中間 self-review が dead code と境界バグを捕捉**: 開発中の 3 観点 self-review で、tester/programmer が同時に「MarkMisrouted がアプリ層で未配線（dead code）」を指摘。これを Readjust フロー（/route/readjust・再調整ボタン）に配線して MISROUTED に実ライフサイクルを与えた。期限ちょうど（残 0 日）のバッジ消失も self-review で是正。
- **T1（マイグレーション時の data-model 同時更新）が機能**: notification テーブル追加時に data-model の物理テーブル・DDL・論理モデルを同一コミットで更新。IT4 で漏れた同期漏れが再発しなかった。
- **クローズ 5 観点レビューで UX と設計整合を締めた**: technical-writer が ui_design のストーリー ID 衝突（IT5 の付随的再採番の残骸）を、user-representative が業務ループの穴（協議依頼が営業に届かない）を検出。軽量な整合・UX 改善はクローズ前に対応し、業務ワークリストは IT6 へ明示繰越。

## Problem（うまくいかなかったこと・課題）

- **経路設計者のナビ導線が実装時に欠落**（ユーザー報告で発覚）: US08〜US12 を実装したが、経路設計者はダッシュボード/ナビから経路設計対象の予約に到達できなかった（ナビは「航路管理」のみ・予約一覧は営業専用）。受入基準は画面単体で満たしていたが、ロール別の「作業入口」を検証していなかった。/route-design を追加して解消したが、開発中に気づくべきだった。
- **協議依頼が業務ループとして閉じない**（user-rep H-1）: US10 の「営業に条件協議を依頼」はイベント Publish（ログ）のみで、営業側に受信導線がない。受入基準「依頼できる」は送信で満たすが、業務としては宙に浮く。ワークリスト実装は IT6 へ繰越。
- **US10 の見出しが実機能より広かった**（user-rep M-1）: 「条件を調整して再算出」と謳いながら実装は期限延長のみ。見出しを「到着期限を調整」に限定し経由地/種別は後続明示、で対応したが、計画時点で MVP スコープを見出しに正確に落とすべきだった。
- **付随的なストーリー ID 再採番が中途半端**（TW-M）: IT4/IT5 で US10-12 の ui_design マッピングを是正した際、周辺の追跡/荷役/請求行のストーリー ID 衝突を残した。ID 変更は影響範囲全体を一度に是正すべき。

## Try（次イテレーションでの改善アクション）

| Try | 内容 | 担当 | 期限/期待効果 |
|-----|------|------|--------------|
| T1 | **ロール別の「作業入口」を DoD 化**: 画面を実装したら、そのロールがダッシュボード/ナビから到達できる導線を必ず確認（画面単体でなく導線まで）。ナビ整合チェックを DoD に | AI | IT6〜。導線欠落の事前検出 |
| T2 | **協議依頼ワークリスト（IT6）**: 条件協議依頼を予約状態/バッジで保持し、営業ダッシュボードに「協議依頼待ち」一覧を出す。業務ループを閉じる（user-rep H-1） | AI | IT6。US10 の運用完成 |
| T3 | **通知待ちワークリスト（IT6）**: 「経路確定・未通知」の予約一覧または経路状態フィルタ（user-rep M-2） | AI | IT6 |
| T4 | **見出しと機能スコープの一致**: MVP で機能を絞るなら画面見出し・受入基準にスコープを明記し期待を管理（user-rep M-1） | AI | IT6〜 |
| T5 | **ID 再採番は影響全体を一度に**: ストーリー ID・命名の変更はトレーサビリティ全行を同一 PR で是正（TW-M） | AI | IT6〜 |
| T6 | **実 notifier の outbox 化**（実メール導入時）: 送信・記録の冪等化（programmer Med） | AI | 外部連携 IT |
| T7 | **sqlcgen per-BC schema 分離**: 全スキーマ重複の返済（IT4 からの継続負債） | AI | IT6-7 |

## 次イテレーション（IT6）への引き継ぎ

- **IT6 スコープ**: US14 追跡番号発行・US15 荷役記録・US16 引取記録・US18 追跡照会（Release 0.2 完了 → Phase 2 完了）。
- **持ち越し UX**: 協議依頼/通知待ちワークリスト（T2/T3）は US10-12 の運用完成として IT6 で実装。ロール別作業入口の DoD 化（T1）。
- **技術的負債**: sqlcgen 全スキーマ重複（T7）・実 notifier の outbox（T6・外部連携時）。
- **良好な状態の維持**: BC 独立性（go-arch-lint 無改変）・ドメイン層 90%+ カバレッジ・NotificationPort の application 配置・中間 self-review + クローズ 5 観点の 2 段運用。

## 実績サマリー

| 項目 | 値 |
|------|-----|
| 計画 SP | 7（US10 3・US11 2・US12 2） |
| 実績 SP | 7（100%）+ ナビ導線（/route-design）追加 |
| ドメイン層カバレッジ | booking 97.5%・routing 96.6%・estimation 91.5%・shared 95.8% |
| SonarQube Quality Gate | PASS（new_coverage 81.2%・重複 0.4%・violations 0・Bug 0・Vuln 0） |
| 品質ゲート | make check green・CI success・govulncheck 脆弱性なし |
| レビュー | 中間 self-review（3 観点）+ クローズ（5 観点統合）・高優先度は対応/繰越を明記 |
| 設計反映 | ADR-0003（MISROUTED 2 系統）・ui_design/domain-model/data-model 同時反映（T1） |
