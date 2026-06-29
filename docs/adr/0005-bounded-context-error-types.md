# 0005 Bounded Context 固有エラーを `<BC>.Domain.Error` に分離する

DDD の境界付けられたコンテキスト (BC) 内で発生する固有のドメインエラーを、Shared カーネルから BC 固有モジュールへ分離する規約

日付: 2026-06-29

## ステータス

2026-06-29 提案 (IT3 で実装着手、段階移行)

## コンテキスト

IT2 マルチパースペクティブレビュー (`docs/review/it2_code_review_20260627.md`) で、レビュー指摘 H-07 が次のように指摘した。

> `BookingNotFound` / `InvalidStateTransition` を `Cargotracker.Booking.Domain.Error` に分離 (Shared から移動)。ADR を起票して BC 境界規約を確定。
>
> 理由: Booking 固有エラーが Shared に常駐すると BC 境界が崩れる。新規 BC 追加時に判断が割れる。

現状の `Cargotracker.Shared.Domain.DomainError` には Booking BC 固有の構造的エラー (予約状態遷移違反・予約未検出) と Shared / Routing / Shipper / Auth の各 BC 固有エラーが混在しており、以下のリスクが顕在化している。

1. **依存方向の腐敗**: Routing BC の `UpdateVoyageCommand` のコメントで「`BookingNotFound` 相当の」と表現せざるを得ない (Voyage には booking の概念がないのに、エラー名は Booking 由来)
2. **新規 BC 追加時の判断分裂**: IT3 で Estimation BC の `DeadlineUnreachable` や IT4 で Tracking BC の `EventTimestampInPast` を導入する際、Shared に置くか BC 固有モジュールに置くかが規約化されていない
3. **コンパイル時の依存露出**: ある BC が他 BC の固有エラーをパターンマッチできてしまう (例: Routing から `BookingNotFound` を参照可能)
4. **ユビキタス言語の混濁**: `domain-model.md` ではエラーは BC のドメイン語彙の一部だが、Shared に置くと「共有語彙」として再解釈されてしまう

## 検討した選択肢

| 案 | 内容 | 評価 |
| :--- | :--- | :--- |
| A. 現状維持 (全エラーを Shared に集約) | `DomainError` を全 BC の和集合とする | 短期的には変更コスト 0 だが、新規 BC 追加で破綻 (リスク 1-4 全て該当) |
| B. BC 固有モジュールへ分離 (本 ADR の採用案) | `<BC>.Domain.Error` に固有エラーを定義、Shared には真の共有エラーのみ残す | 依存方向が明確化、ユビキタス言語と一致、HTTP 境界で統一変換 |
| C. 全 BC のエラーを Free Monad / Open Sum で表現 | `data BCError = Bk BookingError \| Sh ShipperError \| ...` | 既存コードの大規模書換、Haskell の表現として過剰 |

案 B を採用する。

## 決定

### 規約 BCE-01: BC 固有エラーは `Cargotracker.<BC>.Domain.Error` に置く

各 BC は自身の固有エラー sum type を `Cargotracker.<BC>.Domain.Error` モジュールに定義し、その BC 内のドメイン関数・アプリケーションサービスは `Either <BC>Error a` を返す。

```haskell
-- src/Cargotracker/Booking/Domain/Error.hs
module Cargotracker.Booking.Domain.Error
  ( BookingError (..)
  , toDomainError
  ) where

data BookingError
  = BookingNotFound !Text
  | InvalidStateTransition !Text !Text
  | IncompleteBooking !Text ![Text]
  | InvalidHazardousDeclaration !Text
  | InvalidTemperatureRequirement !Text
  deriving stock (Eq, Show)
```

### 規約 BCE-02: Shared.Domain.DomainError には真の共有エラーのみ残す

`Cargotracker.Shared.Domain.DomainError` には全 BC で意味が共通するエラーのみ残す。

| 残すエラー | 理由 |
| :--- | :--- |
| `InvalidUnLocode` | Shared.Domain.Common.UnLocode のスマートコンストラクタが返す |
| `InvalidEmail` | 共有 VO `Email` (Shipper/Auth で使用) |
| `ConcurrentModification` | 楽観ロック規約は全 BC 共通 (`docs/design/domain-model.md` §並行性制御) |
| `InvalidCredentials` / `AccessDenied` | Auth は横断関心事 |
| `InvalidSearchPeriod` (IT3 追加) | Routing/Estimation で共通 |

### 規約 BCE-03: HTTP 境界で `DomainError` へ統一変換

Interfaces 層の Servant ハンドラは `<BC>Error -> DomainError` で BC 固有エラーを Shared.DomainError に lift し、その後 `domainErrorToServerError` で HTTP エラーに変換する。

```haskell
-- src/Cargotracker/Booking/Domain/Error.hs
toDomainError :: BookingError -> DomainError
toDomainError (BookingNotFound bid)                = Shared.BookingNotFound bid
toDomainError (InvalidStateTransition from to_)    = Shared.InvalidStateTransition from to_
-- ...
```

この時点で、Shared.DomainError は **HTTP 境界の通貨型** として位置付けられ、BC 固有エラーはアプリケーション層までで完結する。

### 規約 BCE-04: BC 間で他 BC のエラーをパターンマッチしない

例えば Routing BC のコードは `import Cargotracker.Booking.Domain.Error` してはならない。BC 間連携はイベント or ACL ポート (型クラス) 経由のみ。

このルールは arch-check Phase 2 Rule 6 (Interfaces → Domain) と Phase 3 で gate 化する候補。

## 段階移行計画

ADR の即時施行は既存コード全体の大規模書換になるため、3 段階で移行する。

| 段階 | 内容 | タイミング |
| :--- | :--- | :--- |
| **Phase 1** (本 IT3) | `Cargotracker.Booking.Domain.Error` 新規作成 + 既存 Booking 関数の戻り値型を順次 `Either BookingError a` に置換。Shared 側の `BookingNotFound` / `InvalidStateTransition` には `{-# DEPRECATED #-}` プラグマを付与 | IT3 内 |
| **Phase 2** (IT4) | Estimation BC (`DeadlineUnreachable`) / Tracking BC (`EventTimestampInPast`) 等を `<BC>.Domain.Error` に新規定義 | IT4 |
| **Phase 3** (IT5+) | Shared.DomainError から BC 固有エラーを完全削除 (deprecation 期間終了)。arch-check で BC 間 import を gate | IT5+ |

## 影響

### 影響を受けるモジュール (Phase 1)

| モジュール | 変更 |
| :--- | :--- |
| `Cargotracker.Booking.Domain.Error` | 新規作成 |
| `Cargotracker.Booking.Domain.Model.Cargo` | `submitBooking` / `requestRouting` / `assignRoute` の戻り値型を `Either BookingError Cargo` に変更 |
| `Cargotracker.Booking.Application.HandOverToRouterCommand` | 戻り値型変更 + `toDomainError` 経由で HTTP 境界に伝搬 |
| `Cargotracker.Booking.Interfaces.BookingPageApi` | パターンマッチ先を `BookingError` に変更 |
| `Cargotracker.Shared.Domain.DomainError` | `BookingNotFound` / `InvalidStateTransition` に DEPRECATED コメント付与 |
| `test/unit/Booking/**` | 期待型を `Either BookingError` に揃える |

### CI / arch-check への影響

- arch-check Phase 2 (Rule 6: Interfaces → Domain) は既存ルールで担保
- BCE-04 (BC 間で他 BC のエラーをパターンマッチしない) は arch-check Phase 3 (T-01〜T-03) 完了後の新ルール候補として IT4 で起票

### ロールバック

- Phase 1 で `Cargotracker.Booking.Domain.Error` を削除し、Shared.DomainError の DEPRECATED を外せばロールバック可能
- Phase 2 / Phase 3 のロールバックは既存コードへの後方互換 export で対応

## 関連 ADR

- [ADR 0002 arch-check 自作実装](0002-arch-check-implementation.md): Phase 3 で BCE-04 を gate 化する候補
- ADR-0004 (起票予定): Cross-BC 参照に ShipperRef VO を導入

## 参照

- [IT2 マルチパースペクティブレビュー](../review/it2_code_review_20260627.md) H-07
- [イテレーション 3 計画](../development/iteration_plan-3.md) §7 (タスク 7.4)
- [ドメインモデル設計](../design/domain-model.md) §ドメインエラー
