{- | Shipper Cross-BC 参照値オブジェクト (ADR-0004 / U-05, IT3)

Booking BC など他 BC が Shipper を識別する目的で保持する Cross-BC 参照型。
業務識別子 (`SHP-XXXXXX` 6 文字) を `newtype` で包み、`Shipper.Domain.Model.Value.ShipperId`
への直接依存を排除する。

利用ルール:

* Booking BC 内の集約・Application 層・Repository・View はすべて `ShipperRef`
  を保持する。`Shipper.Domain.Model.Value.ShipperId` を直接 import しない
  (arch-check Phase 2 Rule 6 で gate)。
* ACL 層 (`Booking.Infrastructure.PostgresShipperExistenceChecker` 等) では
  必要に応じて Shipper BC のサロゲートキーを SQL で解決する。
* 同じ業務識別子文字列を異なる新 BC で参照する場合は、その BC の `<BC>.Domain.Reference.ShipperRef`
  を別途新設する (ADR-0004 BCE 規約)。

詳細は ADR-0004 を参照。
-}
module Cargotracker.Shared.Domain.Reference.ShipperRef
  ( ShipperRef (..),
    mkShipperRef,
  ) where

import Data.Char (isAsciiUpper, isDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype ShipperRef = ShipperRef {unShipperRef :: Text}
  deriving stock (Eq, Show)

{- | 業務識別子文字列から ShipperRef を構築する。

検証は ShipperId のスマートコンストラクタと同等 (SHP- 接頭辞 + 英数大文字 6 桁)。
値の生成箇所が Shipper BC とは独立に管理されることを保証する。
-}
mkShipperRef :: Text -> Either DomainError ShipperRef
mkShipperRef t = case T.stripPrefix "SHP-" t of
  Just rest
    | T.length rest == 6 && T.all isAlphaNum rest ->
        Right (ShipperRef t)
  _ -> Left (InvalidShipperId "expected SHP-XXXXXX")
  where
    isAlphaNum c = isAsciiUpper c || isDigit c
