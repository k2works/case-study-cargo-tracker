{- | 荷主氏名・社名 値オブジェクト (IT2 T-09)

US02 では個人氏名、US03 では法人社名を保持する。IT1 では Domain に
フィールド未実装で email 文字列を placeholder としていた負債を、
本値オブジェクトの導入で解消する。

制約 (data-model.md `shipper.name` 列):
- 1〜255 文字
- 前後空白は許容しない (`T.strip` で正規化)
-}
module Cargotracker.Shipper.Domain.Model.Value.ShipperName
  ( ShipperName (..),
    mkShipperName,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype ShipperName = ShipperName {unShipperName :: Text}
  deriving stock (Eq, Show)

mkShipperName :: Text -> Either DomainError ShipperName
mkShipperName raw =
  let t = T.strip raw
   in if T.null t
        then Left (InvalidShipperId "shipper name must not be empty")
        else
          if T.length t > 255
            then Left (InvalidShipperId "shipper name too long (max 255)")
            else Right (ShipperName t)
