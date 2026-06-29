{- | 通関申告ステータス sum type (US27, IT3)

通関業務のライフサイクル状態。状態遷移は通関業者・税関の判断に依存し、
本実装では遷移ルールは強制しない (各状態への直接遷移を許す)。
DB マッピングは SCREAMING_SNAKE_CASE 文字列で行う (data-model.md 規約)。
-}
module Cargotracker.Booking.Domain.Model.State.DeclarationStatus
  ( DeclarationStatus (..),
    declarationStatusToText,
    declarationStatusFromText,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data DeclarationStatus
  = Pending
  | Cleared
  | Held
  | Rejected
  deriving stock (Eq, Show, Read, Bounded, Enum)

declarationStatusToText :: DeclarationStatus -> Text
declarationStatusToText Pending = "PENDING"
declarationStatusToText Cleared = "CLEARED"
declarationStatusToText Held = "HELD"
declarationStatusToText Rejected = "REJECTED"

declarationStatusFromText :: Text -> Either DomainError DeclarationStatus
declarationStatusFromText "PENDING" = Right Pending
declarationStatusFromText "CLEARED" = Right Cleared
declarationStatusFromText "HELD" = Right Held
declarationStatusFromText "REJECTED" = Right Rejected
declarationStatusFromText t = Left (InvalidDeclarationStatus t)
