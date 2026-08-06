{- | 荷主連絡先メール (IT1 US02/03)

Shipper コンテキスト独自の値オブジェクト (Auth.User.Email とは別概念)。
最小限の構造検証 (@ の有無) のみ。
-}
module Cargotracker.Shipper.Domain.Model.Value.ContactEmail
  ( ContactEmail (..),
    mkContactEmail,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype ContactEmail = ContactEmail {unContactEmail :: Text}
  deriving stock (Eq, Show)

mkContactEmail :: Text -> Either DomainError ContactEmail
mkContactEmail t = case T.splitOn "@" t of
  [local, domain]
    | not (T.null local) && not (T.null domain) ->
        Right (ContactEmail t)
  _ -> Left (InvalidShipperId "invalid email")
