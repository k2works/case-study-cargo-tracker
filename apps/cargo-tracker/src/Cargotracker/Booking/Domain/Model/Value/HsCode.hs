{- | HS コード VO (US27, IT3)

Harmonized System code は国際共通の関税分類コードで、本実装では 6-10 桁の
数字のみを受け付ける (要件定義 / user_story.md US27)。

スマートコンストラクタ `mkHsCode` で形式を検証し、永続化からの復元は
`unsafeHsCode` を使う (DB の CHECK 制約と二重防御)。
-}
module Cargotracker.Booking.Domain.Model.Value.HsCode
  ( HsCode,
    unHsCode,
    mkHsCode,
    unsafeHsCode,
  ) where

import Data.Char (isDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype HsCode = HsCode {unHsCode :: Text}
  deriving stock (Eq, Show)

mkHsCode :: Text -> Either DomainError HsCode
mkHsCode t
  | T.length t >= 6 && T.length t <= 10 && T.all isDigit t = Right (HsCode t)
  | otherwise = Left (InvalidHsCode t)

-- | 永続化からの復元専用。アプリケーションコードでは使用しない。
unsafeHsCode :: Text -> HsCode
unsafeHsCode = HsCode
