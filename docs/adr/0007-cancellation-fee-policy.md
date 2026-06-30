# 0007 キャンセル料 3 段階ルールのドメインポリシー化

予約キャンセル時の料金算定を「出航日基準の 3 段階ティア」として Booking BC の純粋ドメインサービスに集約する規約

日付: 2026-06-30

## ステータス

採用 (2026-06-30、IT4 US13 で実装 + IT4 マルチパースペクティブレビュー H-03 を受けて起票・採用昇格)

採用判断の根拠: IT4 で `CancellationPolicy.calculate` を純粋関数として実装し、`CancelBookingCommand` から組み立てる構造で hspec 8 例 + hedgehog 6 プロパティ (600 ケース) を全パス。境界値ルールが ADR 化されていない状態が IT4 レビューで H-03 (リンク切れ) として指摘されたため、本 ADR で正式採用する。

## コンテキスト

US13「予約を確定する」の受入条件で、確定後のキャンセル料が以下の 3 段階で課金される業務ルールが定義されている。

| ティア | 条件 | 料率 |
| :--- | :--- | ---: |
| Free    | 確定後 〜 出航 7 日前         | 0%   |
| Partial | 出航 7 日前 〜 出航 1 日前    | 30%  |
| Full    | 出航 1 日前 〜 出航後 (含む) | 100% |

実装の選択肢として以下を検討した。

* (A) **Application 層 (CancelBookingCommand) に直接埋め込む**: シンプルだが、料率変更や 4 段階化の際に Application + テストの両方を修正する必要がある。料率の単体テストが Command 経由になり境界値検証のテストが重くなる。
* (B) **Domain Service として純粋関数化** (採用): `CancellationPolicy.calculate :: UTCTime -> UTCTime -> CancellationFee` を Booking BC の Domain 層に配置。境界値テスト・プロパティテストが純粋関数として完結する。Application 層は組み立てるだけ。
* (C) **Strategy パターン (型クラス)**: 料率算定アルゴリズムを差し替え可能にする。本要件では将来差し替えの想定がないため過剰設計。

## 決定

**(B) Domain Service として純粋関数化** を採用する。

### 規約 CF-01: CancellationPolicy.calculate は純粋関数 (T-03 規約準拠)

* シグネチャ: `calculate :: UTCTime -> UTCTime -> CancellationFee`
* 引数: 現在時刻 `now` + 出航日時 `departure`
* `IO` を持たない。`now` は呼び出し側 (Application 層) が `getCurrentTime` で取得して渡す
* 配置: `Cargotracker.Booking.Domain.Service.CancellationPolicy`

### 規約 CF-02: ティア境界値の定義

* `diff = departure - now`
* `diff >= 7 日 (= 604,800 秒)` → `Free`  / rate = 0 / 100
* `1 日 (= 86,400 秒) <= diff < 7 日` → `Partial` / rate = 30 / 100
* `diff < 1 日 (出航時刻と同時・過去含む)` → `Full` / rate = 100 / 100

境界値の数値は `Cargotracker.Booking.Domain.Service.CancellationPolicy` に `sevenDays` / `oneDay` 定数として置く。

### 規約 CF-03: CancellationFee VO の構成

```haskell
data CancellationTier = Free | Partial | Full
  deriving stock (Eq, Show, Enum, Bounded)

data CancellationFee = CancellationFee
  { cfTier         :: !CancellationTier
  , cfRate         :: !Rational    -- 例: 30 % 100
  , cfCalculatedAt :: !UTCTime     -- 監査用
  }
```

* 料率は `Rational` で持つ (浮動小数誤差を排除)
* `cfCalculatedAt` は `now` をそのまま保持し、後段の監査・領収書発行に利用

### 規約 CF-04: Application 層の責務

* `CancelBookingCommand` が `getCurrentTime` で `now` を取得
* `findItineraryByBookingId` で `departure` を取得 (IT4 段階では Itinerary 永続化未実装のため Input の `Maybe UTCTime` で受け取る暫定運用、IT5 で port 経由に移行)
* `CancellationPolicy.calculate now departure` を呼んで `CancellationFee` を得る
* `Cargo.cancelBooking` で状態を Cancelled に遷移 + `CancelBookingResult { cargo, fee }` で返す

### 規約 CF-05: Confirmed 以外のキャンセルは料金 Free

確定前 (Submitted / RouteProposed / RouteAssigned) からのキャンセルは料金算定対象外。`computeFee` で `CancellationFee { tier = Free, rate = 0, calculatedAt = now }` を即時生成する。

## 影響

### 影響を受けるモジュール

| 層 | モジュール | 変更 |
| :--- | :--- | :--- |
| Domain | `Booking.Domain.Model.Value.CancellationFee` (新規) | VO + Tier enum + tierRate |
| Domain | `Booking.Domain.Service.CancellationPolicy` (新規) | `calculate` 純粋関数 |
| Application | `Booking.Application.CancelBookingCommand` | Policy 呼び出し + 結果アセンブル |
| Views | `Booking.Views.CancellationFeeView` | 3 ティア色分け表示 (Free=success / Partial=warning / Full=danger) |

### テスト戦略

* `CancellationPolicySpec.hs`: 例ベース 8 件 (168h / 24h / 0h / 過去) で境界値網羅
* `CancellationPolicyPropertiesSpec.hs`: hedgehog 6 プロパティ × 100 ケース = 600 ケース
  * P-1〜P-3: 各ティアの不変条件
  * P-4: cfCalculatedAt == now (恒等性)
  * P-5: rate == tierRate tier (整合性)
  * P-6: 出航後は必ず Full

## 段階移行計画

| 段階 | タイミング | 内容 |
| :--- | :--- | :--- |
| Phase 0 (IT4) | 2026-06-30 | 本 ADR 採用 + Domain Service + CancelBookingCommand 実装完了 |
| Phase 1 (IT5) | 未着手 | `CancelBookingInput.inputDepartureTime :: Maybe UTCTime` を削除し、`ItineraryRepository` から departure を取得する port 経由設計に移行 (H-05 解消) |
| Phase 2 (将来) | 未定 | 料率変更要件が発生したら本 ADR を改訂 |

## 関連

* [iteration_plan-4.md](../development/iteration_plan-4.md) US13 / Task 4.1 / Task 4.3
* [it4_code_review_20260630.md](../review/it4_code_review_20260630.md) H-03 / H-05
* [requirements/user_story.md](../requirements/user_story.md) US13 受入条件
