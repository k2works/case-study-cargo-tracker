{- | 紛失例外 VO (US20, IT7)

Exception BC の紛失例外詳細。損失額と最終目視地点 (Maybe UN/LOCODE 業務キー) を
保持する。lastSeenAt は Text (Cross-BC 参照は Text-DTO、Rule 4) で保持する。
-}
module Cargotracker.Exception.Domain.Model.LossException
  ( LossException (..),
    mkLossException,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Exception.Domain.Model.Amount (Amount)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data LossException = LossException
  { loAmount :: !Amount
  -- ^ 損失額
  , loLastSeenAt :: !(Maybe Text)
  -- ^ 最終目視地点 (UN/LOCODE 業務キー Text、Nothing = 不明)
  }
  deriving stock (Eq, Show)

{- | LossException のスマートコンストラクタ。

* lastSeenAt が Just で trim 後空文字 → InvalidExceptionReason "empty last seen"
* lastSeenAt が Just で trim 後 5 文字でない → InvalidExceptionReason "invalid unlocode"
  (UN/LOCODE 形式検証は Shared.Domain.Common.UnLocode に委譲するのが本来だが、
   本 VO は Text-DTO 受入のため簡易チェックのみ)
-}
mkLossException :: Amount -> Maybe Text -> Either DomainError LossException
mkLossException amount mLastSeen = case mLastSeen of
  Nothing -> Right (LossException amount Nothing)
  Just raw ->
    let trimmed = T.strip raw
     in if T.null trimmed
          then Left (InvalidExceptionReason "empty last seen")
          else
            if T.length trimmed /= 5
              then Left (InvalidExceptionReason "invalid unlocode")
              else Right (LossException amount (Just trimmed))
