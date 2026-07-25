---
title: IT5 マルチパースペクティブレビュー
description: IT5（US10 経路条件調整・US11 経路情報可視化・US12 確定経路通知・経路設計ナビ導線）の XP 5 視点レビュー統合レポート。
tags: review, iteration-5, developing-review, go
---

# IT5 マルチパースペクティブレビュー（2026-07-25）

対象: IT5 実装差分 `bafec47f..HEAD`（約 40 ファイル・約 1,600 行）。US10 経路条件調整（再算出・協議依頼・MISROUTED 再調整）・US11 経路情報の可視化補完・US12 確定経路通知（NotificationPort・通知記録）・経路設計者の作業導線（/route-design）。
手法: XP 5 視点（programmer / tester / architect / technical-writer / user-representative）。開発中の中間 self-review（programmer/tester/architect の 3 視点）とクローズ時の正式レビュー（technical-writer/user-representative を追加）を統合。

## エグゼクティブサマリー

経路設計フローの運用完成（再算出・協議依頼・通知）を 7 SP で実装。BC 独立性の要である NotificationPort の booking/application 配置（opening の validating-design 是正）が守られ（Architect: 構造健全）、ドメイン層カバレッジは全 BC 90% 以上（booking 97.5%）。開発中の self-review で確証バグ（DATE-TIMESTAMP 期限境界の再発防止・MISROUTED 未配線）を是正。クローズ時レビューの高優先度指摘（協議依頼の業務ループ・通貨表記・US10 見出し過大）に対し、軽量な UX/整合改善はクローズ前に対応し、業務ワークリスト系（協議依頼・通知待ち）は IT6 へ明示繰越した。

## 視点別サマリーと対応

| 視点 | 判定 | 主な高/中優先度指摘 | 対応 |
|---|---|---|---|
| Programmer | 重大なし（Med 1・Low 3） | 通知の非トランザクション at-least-once・期限ちょうどバッジ消失・MarkMisrouted 未使用・空 itinerary ガード | ✅ MISROUTED 配線・ShowDeadline 修正・IsEmpty ガード・at-least-once TODO を対応 |
| Tester | 重大ギャップ 3 | 期限境界未検証・複数通知ソート未検証・MISROUTED 未配線 | ✅ 期限境界/Readjust/複数通知降順/再通知/異常系テスト・E2E 再調整を追加 |
| Architect | 構造健全（高 0） | ADR-0003 に MISROUTED 2 系統の追記提案 | ✅ ADR-0003 に能動的再調整（US10）を明記 |
| Technical Writer | 重大なし（Med 2・Low 1） | ui_design 周辺のストーリー ID 衝突・予約詳細行の US10/12 未反映・通貨表記不統一 | ✅ ui_design のストーリー ID 是正・予約詳細行に US10/US12・通貨を「円」に統一 |
| User Representative | 要対応（High 1・Med 3・Low 4） | H-1 協議依頼が営業に届かない・US10 見出し過大・通知待ちリスト無・二重送信防止無 | 一部対応・一部 IT6 繰越（下記） |

## クローズ前に対応した指摘

- **MISROUTED の実フロー配線**（Prog/Tester）: `Cargo.MarkMisrouted` を `AssignRouteService.Readjust`・`/route/readjust`・予約詳細「経路を再調整する」で配線（dead code 解消）。
- **期限境界の是正とテスト**（Prog/Tester）: 期限ちょうど（残 0 日）のバッジ消失を `ShowDeadline` で修正。境界（残 0/超過/前日）・Readjust・複数通知降順・再通知・異常系のテストと E2E を追加。
- **設計本体の整合**（TW）: ui_design のストーリー ID 衝突（追跡/荷役/請求の周辺行）を canonical に是正、予約詳細行に US10/US12 を追記。
- **US10 見出しの適正化**（User-rep M-1）: 「条件を調整して再算出」→「到着期限を調整して再算出」に限定し、経由地追加・貨物種別変更は後続 IT のバックログである旨を画面に明示。
- **通貨表記の統一**（TW-L）: 通知プレビュー・送信サマリを「円（JPY）」に統一（経路割当画面の「円」と整合）。
- **二重送信の抑止（部分）**（User-rep M-3）: 送信済み時にボタンを「再通知する」に切替＋重複警告、送信前に確認ダイアログ。
- **協議依頼の理由編集**（User-rep L-1）: 固定文言 hidden を textarea 入力可に。
- **ADR-0003 追記**（Architect）: MISROUTED の 2 系統（荷役検知/能動的再調整）を明記。

## 次イテレーション（IT6）への Try（保留・繰越）

| 由来 | 内容 | 優先 |
|---|---|---|
| User-rep H-1 | 条件協議依頼を予約状態/バッジで保持し、営業ダッシュボードに「協議依頼待ち」ワークリストを出す（業務ループを閉じる） | 高 |
| User-rep M-2 | 営業向け「荷主通知待ち（経路確定・未通知）」ワークリスト／予約一覧の経路状態フィルタ | 中 |
| User-rep M-1(全) | US10 の経由地追加・貨物種別変更による再算出 | 中 |
| User-rep M-3(全) | 通知送信後の成功フラッシュ表示 | 中 |
| User-rep L-3 | 通知プレビューに荷主名併記（Shipper BC ACL 経由） | 低 |
| User-rep L-4 | US11 の状態語彙（紐付け=経路確定）を user_story と整合 | 低 |
| Programmer Med | 実 notifier 導入時の outbox パターン（送信・記録の冪等化） | 中 |
| Architect | sqlcgen 全スキーマ重複の返済（IT4 からの継続負債） | 中 |

## 品質ゲート

- 全テスト green（単体・統合 testcontainers・E2E Playwright 7 本）
- `make check`（build + test + lint + arch）・go-arch-lint OK・govulncheck 脆弱性なし
- ドメイン層カバレッジ 90% 以上（booking 97.5%・routing 96.6%・estimation 91.5%・shared 95.8%）
- CI（Backend CI・go/take-1）: success
