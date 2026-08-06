{- | UN/LOCODE 値オブジェクト (共有カーネル)

国際連合 LOCATION CODE。5 文字構成:
- 1-2 文字目: 国コード (ISO 3166-1 alpha-2、大文字英字)
- 3-5 文字目: 場所コード (英数字)

例: JPTYO (東京), USNYC (ニューヨーク), GBLON (ロンドン)。
-}
module Cargotracker.Shared.Domain.Common.UnLocode
  ( UnLocode (..),
    mkUnLocode,
  ) where

import Data.Char (isAsciiUpper, isDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype UnLocode = UnLocode {unUnLocode :: Text}
  deriving stock (Eq, Show, Ord)

mkUnLocode :: Text -> Either DomainError UnLocode
mkUnLocode t
  | T.length t /= 5 = Left (InvalidUnLocode "expected 5 chars")
  | not (T.all isAsciiUpper country) =
      Left (InvalidUnLocode "country code must be 2 uppercase letters")
  | not (T.all isLocChar loc) =
      Left (InvalidUnLocode "location code must be alphanumeric")
  | otherwise = Right (UnLocode t)
  where
    (country, loc) = T.splitAt 2 t
    isLocChar c = isAsciiUpper c || isDigit c
