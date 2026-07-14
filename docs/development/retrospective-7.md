# イテレーション 7 ふりかえり（KPT）

- 実施日: 2026-07-14
- 対象: IT7（2026-09-28 〜 10-09）／US21・US22・US23（13 SP）+ IT6 レビュー高優先（H1/H2/M1）の先行消化 + 正式 developing-review の是正

## サマリー

| 指標 | 計画 | 実績 |
|------|------|------|
| ストーリーポイント | 13 SP | 13 SP（達成率 **100%**） |
| ベロシティ | 13 SP + IT6 フィードバック | **13 SP/IT**（IT1=13・IT2=10・IT3=14・IT4=12・IT5=17・IT6=6・IT7=13、平均 12.1 SP/IT） |
| テスト | ドメイン 85% 目標 | **302 件全パス**（Domain 140 / App 3 / Arch 9 / Web 66 / E2E 4 / Infra 80） |
| 品質 | 警告 0・format 準拠 | 達成（全コミット pre-commit 通過・ビルド警告 0・ドメイン被覆 Invoice 95.2%/Money 86.7%） |
| マイグレーション | - | exception_notification 冪等キー（0015）・invoice/invoice_line_item/payment（0016）を追加 |
| ADR | - | ADR-0009 を InvoiceRequested/精算状態同期に適用（新規起票なし） |

局面（開発戦略）: **終盤 = アウトサイドインの最終イテレーション**（IT6-7）。中盤（IT3-5）で作り込んだ Booking/Shipper のドメイン中核を再利用し、料金算出→割引→精算書発行→入金確認→予約 Settled 同期を業務シナリオ起点で結合。IT6 レビュー高優先を先行消化し、正式レビューの高・中優先も IT7 内で是正。**全 85 SP を消化し Release 1.1（Phase 2）の機能実装が完了**した。

## Keep（継続すること）

### 技術的成功

- **Billing BC を既存パターンの忠実な踏襲で立ち上げた**。ACL（`BillingSnapshotProvider` の SQL 直接参照＋プリミティブ DTO）・post-commit イベント（`PaymentConfirmedEvent`→予約 Settled 同期）・集約永続化（delete→再挿入）・ポート/アダプター（`IPaymentGatewayPort`/スタブ）をすべて IT4-6 の確立パターンで実装。5 エージェントレビューで「変更を楽に安全にできる良質な追加・リリース可能な品質」と評価された。
- **金額ドメインを値オブジェクトに凝集した**。`Money`（最小通貨単位・通貨不一致禁止・銀行家丸め）・`DiscountRate`（0〜30%）・`Invoice` 集約（基本料金・割引率・最終金額の一貫性）をドメイン層に閉じ込め、境界値（0%/30%/上限超過・2.5→2/3.5→4）を単体テストで固定。ドメイン被覆 86-100%。
- **IT6 レビュー高優先 H1/H2/M1 を Week 1 前半で先行消化した**。特に H2（変換ヘルパ Shared 集約）は 3 IT 越しの負債を `DatabaseTimestamp`/`EnumDbCodec` として返済。Billing の新規リポジトリを最初から共通版で書き、負債の再拡大を防いだ。
- **正式 developing-review の高・中優先指摘を IT7 内で即是正した**。高 H1（延滞が発火しない＝壊れた機能）を照会時 MarkOverdue 起動で修正、中 M2（異常系テスト）・M4（invoice 楽観ロック）・M3（用語統一 #17）・M1（payment 未使用注記）を消化。レビューを次 IT へ持ち越さず締めた。

### プロセス的成功

- **計画着手前の 2 段階検証（validating-iteration-plan → validating-design）が終盤でも有効**。コマンド名・BookingStatus.Settled・呼称差を開発着手前に確定し docs/design を正に修正してから実装した。
- **Ralph Loop による層単位の反復消化**（ドメイン→永続化→アプリ→イベント→UI→受け入れ）で、各コミット全テスト緑・警告 0 を担保しながら 13 SP＋レビュー是正を着実に消化した。
- **設計案と実装の乖離を隠さず文書化した**。精算開始を `InvoiceRequested`（自動起票）ではなく `GenerateInvoiceCommand`（手動発行＋Delivered 制限）で実装した理由を domain-model に補足。architect レビューで「BC 結合を小さくする妥当な判断」と評価された。

## Problem（問題点）

- **`MarkOverdue`（延滞）の呼び出し元が実装されておらず、US23 AC5 が発火しない「壊れた機能」を作り込んでいた**。延滞遷移メソッドと赤バッジは実装済みだったが、期限超過を検知して呼ぶ経路がなく、画面のバッジが永遠に点かない状態だった。正式レビュー（user-representative 高）で検出し IT7 内で是正したが、受け入れテストが「延滞の発火」まで検証していなかったことが根因。
- **payment テーブルを作成しながら一切書き込まない実装ドリフト**。0016 で payment（支払方法・取引参照・入金額）を作成したが、入金確認は invoice.paid_at のみ更新し payment へ INSERT しない。決済証跡が縮退し「動くきれいなゴミ」寄りのスキーマになった（data-model に注記し可視化）。
- **invoice の version カラムが楽観ロックに未使用だった**。CargoRepository は `WHERE version=@ExpectedVersion` で並行制御するのに、InvoiceRepository は version を書くだけで WHERE は id のみ。同一コードベース内で version の意味が分裂していた（M4 で是正）。
- **用語統一 #17 が DoD にありながら未徹底だった**。domain-model/data-model は精算書へ寄せたが、ui_design（22 箇所）・計画書（13 箇所）・ビューのラベル（請求金額）に「請求書」が残存。同一ドキュメント内で表記が悪化していた（M3 で一括統一）。
- **品質ゲートが 5 イテレーション連続で繰り越し**。Playwright E2E・カバレッジ CI ハードゲート・SonarQube SQ-3/SQ-2 が IT3-IT7 まで持ち越し。いずれも環境操作（ブラウザ・CI・SonarQube サーバ）が前提で Ralph Loop 内では着手できなかった（ドメイン被覆 86-100% は実測確認済み）。

## Try（次に試すこと）

| # | 改善アクション | 責任者 | 期限 | 期待効果 |
|---|--------------|--------|------|---------|
| T1 | **「壊れた機能」を作り込まないため、状態遷移メソッド追加時は必ず起動経路（呼び出し元）と発火の受け入れテストをセットで実装**する。ドメインメソッド単体テストだけでなく「システムとして状態が立つ」ことを検証する | 開発チーム | 常時 | 到達不能な機能の未然防止 |
| T2 | **決済（payment）の永続化と外部呼び出しの冪等性を実決済機関連携時に実装**する。payment への記録、gateway 成功後の DB コミット失敗窓、invoiceNumber 単位の冪等キーを設計。金銭ドメインは結果整合の乖離コストが高い（レビュー M1/M5） | 開発チーム | 実決済連携 IT | 決済証跡の監査可能性・二重決済防止 |
| T3 | **品質ゲート（Playwright・カバレッジ CI・SonarQube）を環境ごと決着**する。5 IT 連続繰り越し。`operating-qt`・`operating-cicd` で環境を起動し積み残しを止める。Release 1.1 のリリース前チェックとして必須化 | 開発チーム | Release 1.1 リリース前 | 品質の定量ゲート化（積み残し解消） |
| T4 | **AC2 荷主通知の記録枠を Tracking の ExceptionNotification パターンで横展開**する。精算書通知（いつ・どの精算書を・誰に）の記録を追加し督促漏れを防ぐ。実メール送信は後続（レビュー L1） | 開発チーム | 次 IT | 精算書通知の証跡確保 |
| T5 | **ArchUnit の BC 依存ルールをホワイトリスト方式から汎用ルールへ集約**する。BC 追加ごとにルールを 1 本足す運用（ルール 5/6/7）を「Shared 以外の他 BC .Domain.Model への一律禁止」1 本へ。次に BC を増やす前にリファクタ（レビュー L4） | 開発チーム | 次 BC 追加前 | ルール追加漏れの構造的防止 |
| T6 | **用語集を 1 箇所で定義**する。「精算書＝Invoice の日本語表記」を用語集に明記し、表記揺れを設計ドキュメント横断で防ぐ。#17 の再発防止 | 開発チーム | 次 IT | ユビキタス言語の一貫性 |

## ベロシティと計画への反映

- IT7 実績 **13 SP**（計画どおり）。US21-23（13 SP）に加え IT6 レビュー高優先 H1/H2/M1 と正式レビュー是正を消化。IT1=13・IT2=10・IT3=14・IT4=12・IT5=17・IT6=6・IT7=13 で平均 **12.1 SP/IT**。7 イテレーション連続で計画=実績が一致。
- 残 SP: **0 SP**（全 85 SP を消化）。バーンダウンは計画線に完全追従。
- **Release 1.1（Phase 2・IT6-7・19 SP）の機能実装が完了**し出荷条件を充足。予約〜追跡〜例外〜請求精算の業務ライフサイクルが全層で完結。中盤（IT3-5）で作り込んだドメイン中核（Booking/Routing/Tracking/Handling/Shipper/Billing）を終盤（IT6-7）で再利用し、例外対応・請求精算を業務シナリオ起点で結合できた。

## 次のステップ

1. `creating-iteration-report`（`--report`）で IT7 完了報告書を作成（本ふりかえりとセット）
2. `creating-release-report` で Release 1.1（Phase 2）完了報告書を作成
3. `syncing-github-project --sync` で US21/US22/US23 の Issue を Done 同期
4. `operating-qt`・`operating-cicd` で品質ゲート（SonarQube・カバレッジ・Playwright）を環境ごと決着（T3）
5. 実決済機関連携 IT で payment 永続化・決済冪等を実装（T2）

## 関連ドキュメント

- [イテレーション 7 計画](./iteration_plan-7.md)
- [イテレーション 6 ふりかえり](./retrospective-6.md)
- [開発戦略](./development_strategy.md)
- [リリース計画](./release_plan.md)
- [開発成果物レビュー（IT7）](../review/開発成果物_IT7_review_20260714.md)
- [ADR-0009 post-commit イベント連鎖の結果整合性方針](../adr/0009-post-commitイベント連鎖の結果整合性方針.md)
