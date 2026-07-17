# イテレーション 7 ふりかえり

## 概要

| 項目 | 内容 |
|------|------|
| **イテレーション** | 7 |
| **期間** | 2026-10-06 〜 2026-10-17（計画） |
| **ゴール** | 割引ポリシー管理・輸送料金算出・法人割引適用・精算処理を実装し、配送完了から料金算出→精算書発行→入金確認→予約 Settled 同期までを一気通貫させる。Billing コンテキストを立ち上げ、Release 1.1 を出荷する |
| **局面** | 終盤（アウトサイドイン）・最終 |
| **計画 SP** | 16（US-ADM-01/US21/US22/US23） |
| **実績 SP** | 16（US-ADM-01/US21/US22/US23 完了） |
| **達成率** | 100% |

### 成果サマリー

- 全 375 テスト緑（Unit 211 / Integration 140 / Arch 24）・警告 0・カバレッジゲート合格（全体 90.6% / ドメイン 89.7%）
- **Billing コンテキストをドメイン→アプリ→永続化→Web→受け入れ→BC 連携で新規立ち上げ**
- `Money`（int64 + 通貨コード・銀行家丸め）・`DiscountRate`（0〜30%）・`PaymentState` DU で金額計算・支払い状態の不正状態を型排除
- 割引ポリシーマスタ（US-ADM-01）の CRUD・有効期限・無効化を全層実装
- 料金算出（距離係数×重量×貨物種別係数）・法人割引適用・精算書発行・入金確認・期限超過通知を実装
- `BookingState` に `Delivered`/`Settled` を段階追加し、精算完了の Settled 同期を結線
- **Release 1.1 の全体フロー（予約確定→追跡→荷役→照会→例外→解決→料金算出→精算→入金確認→Settled）が E2E で一気通貫**
- ADR-0013（料金算出と Billing↔Booking 連携）起票・通知ヘルパ集約（retro-6 Try#1 消化）

## Keep（うまくいったこと）

### 技術的成功

1. **金額の型安全**: `Money`（最小通貨単位 int64 + 通貨コード）＋銀行家丸め（`MidpointRounding.ToEven`）で丸め誤差を排除。`DiscountRate`（0〜30% スマートコンストラクタ）で不正割引を型で表現不能にした。FsCheck で丸めの往復性・境界値を性質テスト化。
2. **PaymentState DU の遷移ガード**: `Pending`/`Confirmed`/`Overdue`/`Refunded` に時刻を埋め込み「Confirmed なのに paidAt が null」を排除。`Invoice.execute` のパターンマッチで不正遷移を `InvalidStateTransition` として拒否し、往復不能な状態を型と関数で守った。
3. **BC 分離を保った横断解決**: 料金算出の貨物データ（重量・種別）と荷主法人判定を合成層 ACL（`CargoQueries.findChargeBasis`・`ShipperQueries.isCorporateByUuid`）で解決。Billing ドメインは Booking/Shipper を参照せず、ArchUnit の BC 分離を維持（IT4/IT5/IT6 の ACL パターンを Billing へ再利用＝retro-6 Try#4 消化）。
4. **BookingState の段階拡張**: iteration_plan-4 で予告した Delivered/Settled を [[adr-migration-via-maybe]] 方式で段階追加。予約ライフサイクルを型で表現しつつ、精算完了同期は状態射影で軽量に実装（ADR-0013 で明文化）。
5. **通知ヘルパ集約**: `writeNotificationLog` 共通ヘルパで追跡・例外・エスカレーション・精算通知の重複を排除（retro-6 Try#1・IT6 レビュー中#3 消化）。

### プロセス的成功

1. **終盤パターンの再利用で高速化**: 集約拡張＋DU 写像永続化＋合成層 ACL＋受け入れ縦貫通という中盤〜IT6 で確立したパターンを Billing にそのまま適用でき、新規ドメインながら 16 SP を計画どおり消化。
2. **Ralph Loop の小さな緑の連鎖**: ドメイン→割引管理→料金算出→精算 Web→BC 連携→通知集約/E2E を 7 ターンに分割し、各ターンでビルド緑・テスト緑・コミット。Giraffe routef の int64 問題・payment_status 大文字/name 不一致などの躓きを都度検出・即修正した。

## Problem（課題）

1. **決済 ACL がスタブに留まる**: `PaymentGatewayPort` は合成層スタブ（即時成功）で、WireMock.Net による外部決済契約固定は未達（外部連携実装 IT へ送り）。
2. **Settled 同期が状態射影**: 精算完了の Booking Settled 同期を Cargo 集約の execute でなく `booking_status` の射影更新で実装。ドメインの状態遷移ガードをバイパスするため、将来はイベント（`BookingSettled`）駆動の集約更新へ移行余地（ADR-0013 案 C）。
3. **消費税・付加料金が未実装**: data-model/ui_design は消費税・燃油サーチャージを含むが、domain-model 準拠で基本料金＋割引のみ実装。明細（`invoice_line_item`）＋ `tax_amount` の実装は保留。
4. **ドキュメント反映の残**: data-model への `discount_policy` テーブル追記・domain-model の消費税表現確定・ADR-0014（決済 ACL）が未反映（IT7 完了時反映事項）。
5. **通知が notification_log 止まり（継続）**: 実メール送信・荷主連絡先の実解決は依然未達（retro-5/6 Try#2・通知強化 IT へ継続）。

## Try（次イテレーションでの改善アクション）

| # | 改善アクション | 責任者 | 期限 | 期待効果 |
|---|--------------|--------|------|---------|
| 1 | data-model に `discount_policy` を追記、domain-model の Billing・BookingState 拡張と消費税表現を確定、ADR-0014（決済 ACL）を起票（IT7 完了時反映事項の消化） | 開発担当 | Release 1.1 出荷前 | 設計ドキュメントと実装の整合 |
| 2 | 決済 ACL を WireMock.Net で契約固定し、外部決済連携の受け入れテストを整備（本 IT スタブの実化） | 開発担当 | 外部連携 IT | 外部連携の契約保証 |
| 3 | 精算完了の Settled 同期をイベント駆動（`BookingSettled` 消費）の集約更新へ移行（ADR-0013 案 C・射影からの脱却） | 開発担当 | 改善 IT | ドメイン整合性の強化 |
| 4 | 通知を実メール送信化し荷主連絡先を実解決（retro-5/6 Try#2 の継続・通知強化 IT） | 開発担当 | 通知強化 IT | 通知の実効化 |
| 5 | 消費税・付加料金を `invoice_line_item`＋`tax_amount` で実装（ui_design の金額内訳に整合） | 開発担当 | 精算強化 IT | 請求金額の正確化 |

## ベロシティ実績と再較正（IT7 終了時・全 IT 完了）

| イテレーション | 局面 | 計画 SP | 実績 SP | 達成率 |
|---------------|------|---------|---------|--------|
| IT1 | 序盤 | 10 | 10 | 100% |
| IT2 | 序盤 | 10 | 10 | 100% |
| IT3 | 中盤 | 14 | 14 | 100% |
| IT4 | 中盤 | 12 | 12 | 100% |
| IT5 | 中盤 | 17 | 17 | 100% |
| IT6 | 終盤 | 6 | 6 | 100% |
| IT7 | 終盤 | 16 | 16 | 100% |
| **累計** | | **85** | **85** | **100%** |

- **7 イテレーション連続で計画 SP を 100% 達成（累計 85/85 SP）**。全 27 US（20 US＋認証基盤＋7 US）を計画どおり完遂した。
- 終盤（IT6-7・アウトサイドイン）は既存集約の結合と Billing 新規立ち上げで、中盤で確立したパターン（BC-local 型・ACL・post-commit・段階導入・カバレッジゲート・ArchUnit）の再利用が奏功。IT7 は新規ドメインを含む 16 SP を過積載なく消化した。
- **Release 1.1 の全機能が IT7 完了で一気通貫**。出荷判定・リリース完了報告はクロージングで実施する。
