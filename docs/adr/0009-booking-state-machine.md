# 0009 Booking 状態機械の SSoT 化 (BookingStatus.canTransitionTo)

予約 (Cargo) の状態遷移ルールを `BookingStatus.canTransitionTo` 純粋関数に集約し、Domain / Application / UI / DB CHECK 制約の全レイヤがこれを真実とする規約

日付: 2026-06-30

## ステータス

採用 (2026-06-30、IT4 マルチパースペクティブレビュー H-01 を受けて起票・即時リファクタ・採用)

採用判断の根拠: IT4 で BookingStatus に RouteAssigned/Cancelled を追加した際、状態遷移ルールが `Cargo.hs` の各遷移関数 (`case` パターン) と `BookingStatus.canTransitionTo` (テストのみ使用) の **2 箇所に分散** していた。新状態追加時に確実に乖離するリスクが IT4 レビュー H-01 で指摘されたため、本 ADR で SSoT を確定する。

## コンテキスト

予約 (Cargo) は 7 状態の状態機械として表現される。

```
Draft → Submitted → RouteProposed → RouteAssigned → Confirmed → Closed
                ↘          ↘            ↘             ↘
                  Cancelled (4 状態からの遷移を許可)
                                        ↘
                                          Draft (RouteAssigned からの unlink)
```

| 状態 | 説明 |
| :--- | :--- |
| Draft           | 予約登録直後 (US04) |
| Submitted       | 営業担当者が確定送信 (US06 → IT3) |
| RouteProposed   | 経路設計者に引き渡し済 (US06) |
| RouteAssigned   | 経路紐付け済 (US11, IT4) |
| Confirmed       | 予約確定 (US13, IT4) |
| Cancelled       | キャンセル済 (US13, IT4、4 状態から遷移可) |
| Closed          | 完了 (将来 IT7+) |

IT4 までに `canTransitionTo` 関数 (49 ペア中 10 許可) は定義していたが、`Cargo.hs` 側の遷移関数 (`submitBooking` / `linkRoute` / `cancelBooking` 等) は個別に `case` 式やリテラルリスト (`s \`elem\` [Submitted, RouteProposed, RouteAssigned, Confirmed]`) で状態判定しており、`canTransitionTo` を呼んでいなかった (`grep` で確認: テストでしか使われず)。

## 検討した選択肢

* (A) **状態遷移を Cargo の関数群に閉じ込める** (IT4 までの実装): 関数ごとに完結するが、新状態追加時に同じルールを 2 箇所 (Cargo + canTransitionTo) で更新する必要があり乖離リスク
* (B) **canTransitionTo を SSoT とし、Cargo の関数は呼び出すだけ** (採用): 真実が 1 つ。新状態追加時の修正箇所が 1 ファイル
* (C) **State Machine ライブラリ (`stm-state-machine` 等) を導入**: 過剰設計。ADT + 純粋関数で十分

## 決定

**(B) `BookingStatus.canTransitionTo` を SSoT とする** を採用する。

### 規約 SM-01: canTransitionTo の責務

```haskell
canTransitionTo :: BookingStatus -> BookingStatus -> Bool
canTransitionTo Draft Submitted = True
canTransitionTo Submitted RouteProposed = True
canTransitionTo Submitted Cancelled = True
canTransitionTo RouteProposed RouteAssigned = True
canTransitionTo RouteProposed Cancelled = True
canTransitionTo RouteAssigned Confirmed = True
canTransitionTo RouteAssigned Draft = True       -- US11 unlink
canTransitionTo RouteAssigned Cancelled = True
canTransitionTo Confirmed Cancelled = True
canTransitionTo Confirmed Closed = True
canTransitionTo _ _ = False
```

* 全 7 × 7 = 49 ペア中 10 ペアが許可、39 ペアが拒否
* 新状態追加時はこの関数のみ更新

### 規約 SM-02: Cargo の遷移関数は transitionTo ヘルパを呼ぶ

```haskell
transitionTo :: BookingStatus -> Cargo -> Either DomainError Cargo
transitionTo to cargo
  | canTransitionTo (cargoStatus cargo) to = Right cargo { cargoStatus = to, cargoVersion = +1 }
  | otherwise = Left (InvalidStateTransition (show (cargoStatus cargo)) (show to))

submitBooking  = transitionTo Submitted
requestRouting = transitionTo RouteProposed
linkRoute      = transitionTo RouteAssigned
unlinkRoute    = transitionTo Draft
confirmBooking = transitionTo Confirmed
cancelBooking  = transitionTo Cancelled
```

* 各遷移関数は **2 行で表現** (旧実装は各 13 行)
* `Cargo.hs` 全体で 161 行 → 119 行 (-26%)

### 規約 SM-03: cancellableStatuses は派生情報

```haskell
cancellableStatuses :: [BookingStatus]
cancellableStatuses = [Submitted, RouteProposed, RouteAssigned, Confirmed]
```

* `canTransitionTo s Cancelled == True` となる s のリストを命名
* 将来は `cancellableStatuses = filter (\s -> canTransitionTo s Cancelled) [minBound .. maxBound]` で自動導出可能 (現状はリテラル定義、IT5 でリファクタ候補)

### 規約 SM-04: DB CHECK 制約は bookingStatusToText で生成

```haskell
bookingStatusToText :: BookingStatus -> Text
-- Draft -> "DRAFT" / RouteAssigned -> "ROUTE_ASSIGNED" 等
```

* `data-model.md` の `booking.status` CHECK 制約値と一致
* マイグレーション SQL を書く際は `[minBound..maxBound]` を `map bookingStatusToText` で生成し、列挙忘れを防ぐ

### 規約 SM-05: 状態遷移の網羅性テスト

```haskell
describe "全 7 状態 × 7 状態 = 49 ペアの網羅性" $
  it "許可遷移は 10 件、それ以外は 39 件全て False" $ do
    let accepted = [(a, b) | a <- [minBound..maxBound], b <- [minBound..maxBound], canTransitionTo a b]
    length accepted `shouldBe` 10
```

新状態追加時はこの件数を更新し、許可遷移を新ペアに対しても明示する。

## 影響

### 影響を受けるモジュール

| 層 | モジュール | 変更 |
| :--- | :--- | :--- |
| Domain | `Booking.Domain.Model.State.BookingStatus` | canTransitionTo + cancellableStatuses export |
| Domain | `Booking.Domain.Model.Cargo` | 遷移関数 6 個を transitionTo ヘルパ経由に統一 |
| Domain (テスト) | `Booking.Domain.Model.State.BookingStatusSpec` | 49 ペア網羅 + Enum/Bounded |
| Domain (テスト) | `Booking.Domain.Model.CargoSpec` | 4 遷移関数 (linkRoute/unlinkRoute/confirmBooking/cancelBooking) のシナリオ網羅 |
| Application | (該当なし) | `withCargo` (ADR 化なし) 経由で透過的に動作 |
| Infrastructure | `Booking.Infrastructure.PostgresBookingRepository` | `bookingStatusToText` で DB CHECK 値を生成 (IT5 リファクタ候補) |

### 既存 IT4 実装との整合

IT4 commit `08eecbba` (H-01 リファクタ) で本 ADR の規約 SM-01〜SM-04 を実装済。
本 ADR は **事後追認** であり、テスト 443 件全パスを確認済 (リグレッションゼロ)。

## 段階移行計画

| 段階 | タイミング | 内容 |
| :--- | :--- | :--- |
| Phase 0 (IT4) | 2026-06-30 | 本 ADR 採用 + Cargo リファクタ実装完了 (commit 08eecbba) |
| Phase 1 (IT5) | 未着手 | `cancellableStatuses` を canTransitionTo からの自動導出に置き換え |
| Phase 2 (IT5+) | 未着手 | `PostgresBookingRepository.bookingStatusToText` を共通モジュール (BookingStatus) のものに統一 (現状リポジトリ内に独自実装) |
| Phase 3 (将来) | 未定 | Closed 後の状態 (アーカイブ等) 追加時に本 ADR を改訂 |

## 関連

* [it4_code_review_20260630.md](../review/it4_code_review_20260630.md) H-01
* [ADR-0005 BC 固有エラー](0005-bounded-context-error-types.md) InvalidStateTransition
* [Cargo 集約実装](../../apps/cargo-tracker/src/Cargotracker/Booking/Domain/Model/Cargo.hs)
* [BookingStatus 状態機械](../../apps/cargo-tracker/src/Cargotracker/Booking/Domain/Model/State/BookingStatus.hs)
