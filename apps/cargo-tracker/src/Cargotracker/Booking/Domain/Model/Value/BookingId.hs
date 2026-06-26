{- | 予約 ID (IT1 US04)

採番ルール: `BK-XXXXXX` (英数字 6 文字)。
ID 採番は IT2 で UUID/Snowflake に置き換える可能性あり。
-}
module Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId (..),
    mkBookingId,
  ) where

import Data.Char (isAsciiUpper, isDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype BookingId = BookingId {unBookingId :: Text}
  deriving stock (Eq, Show, Ord)

mkBookingId :: Text -> Either DomainError BookingId
mkBookingId t = case T.stripPrefix "BK-" t of
  Just rest
    | T.length rest == 6 && T.all isAlphaNum rest ->
        Right (BookingId t)
  _ -> Left (InvalidBookingId "expected BK-XXXXXX")
  where
    isAlphaNum c = isAsciiUpper c || isDigit c
