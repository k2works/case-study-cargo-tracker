{- | 破損例外 VO (US20, IT7)

Exception BC の破損例外詳細。損害額と説明文を保持する。
証拠写真 URL リストは Interfaces/View 層で追加予定 (現時点では description に
記述する運用)。
-}
module Cargotracker.Exception.Domain.Model.DamageException
  ( DamageException (..),
    mkDamageException,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Exception.Domain.Model.Amount (Amount)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data DamageException = DamageException
  { daAmount :: !Amount
  -- ^ 損害額
  , daDescription :: !Text
  -- ^ 説明 (空文字不可、trim 後 500 文字以内)
  }
  deriving stock (Eq, Show)

{- | DamageException のスマートコンストラクタ。

* description が trim 後空文字 → InvalidExceptionReason "empty"
* description が trim 後 500 文字超 → InvalidExceptionReason "too long"
-}
mkDamageException :: Amount -> Text -> Either DomainError DamageException
mkDamageException amount description
  | T.null trimmed = Left (InvalidExceptionReason "empty")
  | T.length trimmed > 500 = Left (InvalidExceptionReason "too long")
  | otherwise = Right (DamageException amount trimmed)
  where
    trimmed = T.strip description
