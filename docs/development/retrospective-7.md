---
title: イテレーション 7 ふりかえり
description: IT7（破損紛失例外・料金算出・法人割引・US20/US21/US22）の Keep・Problem・Try
published: true
date: 2026-07-24T00:00:00.000Z
---

# イテレーション 7 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 7（破損紛失例外・輸送料金算出・法人割引） |
| **局面** | 終盤（アウトサイドイン） |
| **計画 SP / 実績 SP** | 13 / 13（達成率 100%） |
| **対象ストーリー** | US20（破損・紛失例外）・US21（輸送料金算出）・US22（法人割引適用） |
| **テスト** | domain-billing 13 + app-billing 8 + domain-tracking 16 + app-tracking 9 + interface-web 単体 7 + billing_flow 6 + estimation_exception_flow 8 + freight_charge_repository 2 + 既存統合（booking/route/shipper/tracking/voyage/auth）＝全 green。E2E it7-demo 3（seed 実値で割引期待値修正） |
| **実装コミット** | Billing Context 本格実装（domain-billing/app-billing 新設）・ExceptionType 拡張（Damage/Lost・escalation）・freight_charge マイグレーション＋リポジトリ・ADR-0009/0010 起票・レビュー高優先度 7 件返済 |
| **成果** | Billing Context をスケルトンから本格実装し US21 料金算出・US22 法人割引が実 PostgreSQL 上で成立。Tracking 例外を破損・紛失へ拡張し US20（紛失 escalation）が成立。**Phase 3 継続・累計 92/97 SP（95%）** |

## Keep（継続すること）

### 技術的成功事項

- **BC 独立の徹底（新コンテキストでも維持）**: 新設した `domain-billing`／`app-billing` を他 domain クレート非依存に保ち、Booking/Handling/Routing・Shipper 参照を `BookingActualsProvider`／`ShipperDiscountProvider` ACL（`billing_acl.rs`）に隔離。architect が「BC 独立性違反なし・Cargo.toml で強制・ACL 隔離は設計意図どおり」と評価。ADR-0010 で `Money`／`DiscountRate` の BC ローカル定義方針を明文化。
- **金額を純粋関数＋名前付き定数で固定（IT6 Try 教訓の継続）**: `rates` モジュールの定数と `calculate_base_amount` 純粋関数で基本料金を算定し、一般/危険物/冷凍の 3 係数を単体テストでリグレッション固定。tester が「金額計算の安全網として適切」と評価。
- **状態導出パターンの一貫適用（ADR-0006）**: `ExceptionType::requires_escalation()`（`matches!(self, Self::Lost)`）で紛失のみ escalation を種別非依存に導出。破損/遅延が false であることまでテストで固定し、種別追加でも導出ロジック一点で閉じている。
- **型レベル認可の踏襲**: `RoleGuard<BillingRole>`（`ROLE_BILLING`）で料金ハンドラの認可書き忘れをコンパイラが防止。opening-iteration の検証で「経理ロールは実装済み・marker のみ新設」と正典を早期特定できたのが効いた。

### プロセス的成功事項

- **opening-iteration の検証が手戻りを未然防止**: validating-iteration-plan／validating-design で経理ロールの正典（`ROLE_BILLING` 既存・seed 済み）を着手前に特定し、`AccountingUser`/`ROLE_ACCOUNTING` の誤りを計画段階で修正。実装時のロール不整合をゼロにした。
- **5 視点レビューの実効（クローズ前返済）**: programmer が再算出時の charge_id 不整合（404 バグ）を、user-rep が例外可視化・料金根拠非表示を、tester が受入基準×テストの穴を検出。高優先度 7 件をクローズ前に全返済し、負債を次 IT に持ち越さなかった。
- **E2E がテスト側の想定ミスを検出**: 法人割引の E2E で期待値 10% を置いたが、seed 実値 15% との差で失敗。実装は正しく、テスト期待値の誤りと判明。「動作の正しさをテストが裏付け、テストの前提誤りを露見させた」健全な例。

## Problem（問題点）

### UI 導線・可視化の穴（レビューで検出）

- **例外・料金の可視化が実装当初欠落していた**: US20 の追跡詳細に例外一覧・緊急フラグが表示されず、対応報告(resolve)への導線もなかった（IT6 US19 から継続の借金）。US21 の料金詳細に輸送実績（算定根拠）が非表示で、経理が「根拠なしに確定」する状態だった。user-rep が「動作するきれいなゴミに片足」と指摘。クローズ前に例外一覧・resolve リンク・実績表示を追加して返済した。**受入基準のチェックボックスは埋まっても業務価値が欠ける**典型を再び踏んだ。
- **受入基準×テストの 1:1 対応にドリフト**: US20 の破損/紛失「対応報告」テストが遅延種別のみ、US21「例外時料金調整」が HTTP 未実証だった。domain では守られていたが、顧客が受け入れ確認する経路（HTTP/E2E）で穴。IT4/IT5 でも同種の対応表ドリフトが再発している。

### 設計・実装の課題

- **再算出時の charge_id 不整合（correctness バグ）**: `calculate` が毎回新 ID を採番する一方 upsert が charge_id を更新せず、再算出で redirect 先が 404 になる状態だった。冪等に見えて実は不整合という危険な中間状態。既存 ID 再利用＋確定済み拒否で修正。
- **金額の丸め規則が未定義だった**: `multiply_ratio` が円未満を丸めず、割り切れる値でしか露見していなかった。JPY 円未満四捨五入を `Money::rounded()` に明示し ADR-0010 に追記。
- **distance が名目スタブ・荷役実績が料金式に未反映**: 料金の根幹値が暫定（レグ数×5000km）で、荷役実績は式に入っていない。意図的負債として ADR/コメントに可視化済みだが、US21 受入基準「荷役作業実績をもとに」を厳密には満たさない。

## Try（次に試すこと）

| # | 改善アクション | 担当 | 期限 | 期待効果 |
|---|--------------|------|------|----------|
| 1 | 受入基準に「対応報告」「調整入力」等の状態変更系がある場合、**HTTP/E2E での 1:1 実証をタスク分解時に必須チェック**にする（domain 単体だけで満足しない） | 開発 | IT8 | 受入基準×テストのドリフト根本対策（IT4-7 で 4 IT 連続露見） |
| 2 | 新規画面/状態を追加したら、**その状態を「一覧・可視化する画面」も同一 IT でセット実装**する（登録だけで終わらせない） | 開発 | IT8 | 「登録できるが確認できない」型の業務価値欠落の再発防止（US20 例外可視化・US21 根拠表示） |
| 3 | 通知の実配信（メール送信アダプター）と通知履歴の可視化 UI を実装する（IT6 Try#3 の再繰り越し分） | 開発 | IT8 | 記録止まりの通知を実際に届け、担当者が送信を確認できる |
| 4 | 確定経路からの推定到着日導出（IT6 Try#4 の再繰り越し）＋ distance を Routing 実績距離へ差し替え | 開発 | IT8 | 料金・到着日の根拠を暫定スタブから実データへ |
| 5 | rank 採番の責務を集約に一元化（ADR-0007・IT6 Try#5 の再繰り越し） | 開発 | IT8 | 二重責務の解消 |
| 6 | per-handler の service/ACL 組立を composition root へ引き上げる（architect 指摘） | 開発 | IT8 | DI 方式の一貫性回復・ハンドラの責務縮小 |
| 7 | dashboard 拡充・荷役実績の料金反映（IT6 Try#6・US21 受入の厳密化） | 開発 | IT8 | 業務導線の充実と料金根拠の完全化 |

## 次イテレーション（IT8）への引き継ぎ

- **IT8 スコープ**: 精算処理（US23・Billing Context 完成）＋統合・E2E ハードニング＋非機能要件の受け入れ確認・リリース準備（Release 1.1 完成）。確定した `freight_charge` を入力に `invoice`（精算書）を生成（ADR-0009 の段階分割）。
- **IT8 冒頭で返済必須**（user-rep 条件）: 例外の可視化・対応報告導線は返済済みだが、通知実配信（Try#3）・料金根拠の実データ化（Try#4）は US23 精算業務レビュー前に対処する。
- **US22 の精算書記載**: 割引根拠の精算書（invoice）記載は US23 で完全達成。
- **料金モデルの拡張**: 荷役実績の反映・distance 実距離化を料金モデル拡張時に対応。

## 数値指標

| 指標 | 実績 |
|------|------|
| テスト | ワークスペース全 green（domain/app 単体 + infra/interface 統合 testcontainers + E2E）。IT7 新規: domain-billing 13・app-billing 8・billing_flow 6・freight_charge_repository 2 等 |
| ビルド・Lint | ワークスペース clippy `-D warnings` クリーン・fmt 準拠（`+stable` 1.97.1 で確認・CI 同一ツールチェーン） |
| ベロシティ | 13 SP（IT1=16 → IT2=11 → IT3=11 → IT4=14 → IT5=14 → IT6=13 → IT7=13、計画ラインと一致し安定） |
| 累計進捗 | 92/97 SP（95%）・Phase 3 継続・Release 1.1 前進 |
| ADR | 2 件起票（0009 料金/精算書段階分割・0010 Money の BC ローカル定義） |
| レビュー | 5 視点・高優先度 7 件クローズ前返済・中低は IT8 Try 繰り越し |

## 関連ドキュメント

- [イテレーション 7 計画](./iteration_plan-7.md)
- [IT7 開発成果物レビュー](../review/it7_development_review_20260724.md)
- [ADR-0009 輸送料金と精算書の段階分割](../adr/0009-freight-charge-and-invoice-separation.md)
- [ADR-0010 Money の BC ローカル定義](../adr/0010-billing-money-value-object.md)
- [イテレーション 6 ふりかえり](./retrospective-6.md)
