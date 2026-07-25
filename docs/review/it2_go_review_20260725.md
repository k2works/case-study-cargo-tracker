---
title: IT2 マルチパースペクティブレビュー
description: IT2（US05 特殊貨物・US13 予約ライフサイクル・Try 返済）の XP 5 視点レビュー統合レポート。
tags: review, iteration-2, developing-review, go
---

# IT2 マルチパースペクティブレビュー（2026-07-25）

対象: IT2 実装差分 `5c857f8c..HEAD`（US05 危険物・冷凍貨物予約、US13 予約確定・キャンセル・差し戻し、Try 返済 T1/T2/T4、貨物予約一覧、CSS）。
手法: XP 5 視点（programmer / tester / architect / technical-writer / user-representative）を並列起動し統合。

## エグゼクティブサマリー

土台は堅く、ヘキサゴナル/DDD の分離・BC 独立性（ShipperCode）・CQRS 分離・TDD が一貫。高優先度指摘は **クローズ前にすべて対応済み**。中・低優先度は次イテレーションの Try として計上した。

## 視点別サマリーと対応

| 視点 | 判定 | 主な高優先度指摘 | 対応 |
|---|---|---|---|
| Programmer | 要対応 2 | H1: `CanCancel` がドメイン許容状態と不一致 / H2: 可否判定のテスト欠如 | ✅ 可否判定を `Cargo.CanConfirm/CanCancel/CanSendBackToRouting` にドメイン集約し不整合修正。真理値表テスト追加 |
| Tester | 要追加 8（高 4） | 華氏正常系・温度境界(min==max)・application の許容外遷移・E2E ボタン抑止 | ✅ 高 4 件を実装（domain/application/E2E） |
| Architect | 要対応 1（高） | H1: `shared.ShipperId`(UUID) が共有カーネルに残存（ADR-0005 決定2 未完） | ✅ Shipper BC へ移設し共有カーネルから除去。ADR-0005 更新 |
| Technical Writer | 要対応 3（中1・低2） | iteration_plan DoD 未チェック / ui_design の軽微な追従漏れ | ✅ ui_design 補記・ADR 更新。DoD はステップ3で更新 |
| User Representative | 要対応 3（高2・中1） | H1: 特殊貨物必須検証 / H2: 荷主選択導線 / M3: ルート欄 | 一部対応・一部次 IT（下記） |

## クローズ前に対応した高優先度

- **可否判定のドメイン集約**（Prog H1/H2）: `CanCancel` が TRACKING_ISSUED 以降でもキャンセルボタンを表示していた不整合を、ドメインの許容状態に一致させて修正。UI とドメイン規則を単一情報源化。
- **遷移エラーの日本語化**（Prog M3）: `applyTransition` が英語センチネルを露出していたのを日本語メッセージにマッピング。
- **ShipperId の Shipper BC 移設**（Arch H1）: ADR-0005 決定2 を完遂。共有カーネルは Location・ShipperCode・BusinessCode のみに。
- **テスト補強**（Tester 高 4）: 華氏正常系・温度境界(min==max)・application の Cancel/SendBack 許容外遷移＋副作用なし・E2E キャンセル後のボタン抑止。domain カバレッジ 86.4%→98.9%。

補足: User-rep H1（特殊貨物の必須検証）は、サーバ側で `buildSpecialCargo` → 値オブジェクト生成時に空欄を弾いており **法令上の申告漏れは発生しない**（温度逆転 E2E で実証済み）。ただしクライアント側 `required` 属性・フィールド単位のエラー表示は未実装のため、UX 改善として次 IT の Try に計上する。

## 次イテレーション（IT3）への Try（保留・持ち越し）

| 由来 | 内容 | 優先 |
|---|---|---|
| User-rep H1 | 特殊貨物フィールドのクライアント `required` 属性＋フィールド単位のエラー表示 | 高 |
| User-rep H2 | 荷主コード欄からの検索・選択導線（手打ちのみを解消） | 高 |
| User-rep M1 | キャンセル・差し戻しに理由入力＋確認ステップ（誤操作防止） | 中 |
| User-rep M2/M3 | 一覧の貨物種別を日本語ラベル/バッジ化、詳細のルート欄プレースホルダ | 中 |
| Prog M1/M2 | フォーム経路の「GENERAL+特殊情報→エラー」統合テスト、numericFromFloat3 の丸め境界テスト | 中 |
| Prog L3 | 特殊貨物列の「3列セット/3列 NULL」CHECK 制約（DB 側の不変条件担保） | 低 |
| Prog L1/L2 | Money の検証追加、pow10 の math.Pow 置換 | 低 |
| Arch 中 | ShipperCode 型の二重定義（shared / shipper 双方）の意図明文化 | 中 |
| Arch/計画 | 共有 sqlcgen の BC 別分割（T3・go-arch-lint 強制へ） | 中 |
| Tester 中 | handler の 422/404 異常系テスト、E2E の状態アサートを data 属性ベースへ | 中 |

## 品質確認

- 全単体テスト green・integration（testcontainers）green・E2E 22 本 green
- `make check`（build + test + lint + govulncheck + arch）green
- ドメイン層カバレッジ 98.9%（booking）・100%（shipper）

## 関連

- [IT2 計画](../development/iteration_plan-2.md)
- [ADR-0005](../adr/0005-bc-reference-and-shared-sqlcgen.md)
