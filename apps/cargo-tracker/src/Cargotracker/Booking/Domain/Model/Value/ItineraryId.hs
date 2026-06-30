{- | Itinerary 業務識別子 (US09, IT4)

UUID 文字列を保持する `newtype`。スマートコンストラクタで形式を検証する。
-}
module Cargotracker.Booking.Domain.Model.Value.ItineraryId
  ( ItineraryId (..),
    mkItineraryId,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype ItineraryId = ItineraryId {unItineraryId :: Text}
  deriving stock (Eq, Show, Ord)

-- | UUID v4 形式の文字列 (8-4-4-4-12 = 36 文字) のみ受理。
mkItineraryId :: Text -> Either DomainError ItineraryId
mkItineraryId t
  | T.length t == 36 && hasUuidShape t = Right (ItineraryId t)
  | otherwise = Left (InvalidItineraryId t)
  where
    hasUuidShape s =
      let parts = T.splitOn "-" s
       in length parts == 5
            && map T.length parts == [8, 4, 4, 4, 12]
