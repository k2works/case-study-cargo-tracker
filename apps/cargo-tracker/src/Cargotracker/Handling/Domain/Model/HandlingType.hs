{- | 荷役イベント種別 (US15, IT5)

domain-model.md §5 Handling Context の HandlingType に準拠。
5 種類の sum type + DB 変換関数 (SCREAMING_SNAKE_CASE 文字列)。
-}
module Cargotracker.Handling.Domain.Model.HandlingType
  ( HandlingType (..),
    handlingTypeToText,
    textToHandlingType,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data HandlingType
  = -- | 貨物受領 (出発地で荷主から)
    Receive
  | -- | 積込 (港湾で船へ)
    Load
  | -- | 荷降し (港湾で船から)
    Unload
  | -- | 通関手続完了
    Customs
  | -- | 引取 (最終目的地で荷受人へ)
    Claim
  deriving stock (Eq, Show, Enum, Bounded)

-- | DB CHECK 制約と一致する Text 表現 (RECEIVE/LOAD/UNLOAD/CUSTOMS/CLAIM)。
handlingTypeToText :: HandlingType -> Text
handlingTypeToText Receive = "RECEIVE"
handlingTypeToText Load = "LOAD"
handlingTypeToText Unload = "UNLOAD"
handlingTypeToText Customs = "CUSTOMS"
handlingTypeToText Claim = "CLAIM"

-- | 逆変換。想定外文字列は InvalidHandlingType 相当のエラー扱い。
textToHandlingType :: Text -> Either DomainError HandlingType
textToHandlingType "RECEIVE" = Right Receive
textToHandlingType "LOAD" = Right Load
textToHandlingType "UNLOAD" = Right Unload
textToHandlingType "CUSTOMS" = Right Customs
textToHandlingType "CLAIM" = Right Claim
textToHandlingType other = Left (InvalidHandlingType other)
