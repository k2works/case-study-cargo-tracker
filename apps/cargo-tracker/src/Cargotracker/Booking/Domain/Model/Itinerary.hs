{- | Itinerary エンティティ (US09, IT4)

確定された経路を表す。1 つ以上の `Leg` で構成され、隣接 Leg は接続性を満たす。

不変条件:
- 1 つ以上の Leg を持つ (空 Itinerary は禁止)
- seq_number は 1 から始まる連番
- 隣接 Leg の接続性: leg[i].unloadLocation == leg[i+1].loadLocation
- 隣接 Leg の時刻順序: leg[i].unloadTime <= leg[i+1].loadTime
-}
module Cargotracker.Booking.Domain.Model.Itinerary
  ( Itinerary (..),
    mkItinerary,
    itDepartureTime,
    itArrivalTime,
    itPorts,
  ) where

import Data.List.NonEmpty (NonEmpty)
import qualified Data.List.NonEmpty as NE
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)

import Cargotracker.Booking.Domain.Model.Leg (Leg (..))
import Cargotracker.Booking.Domain.Model.Value.ItineraryId (ItineraryId)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data Itinerary = Itinerary
  { itItineraryId :: !ItineraryId
  , itLegs :: !(NonEmpty Leg)
  }
  deriving stock (Eq, Show)

-- | スマートコンストラクタ。接続性と時刻順序を検証する。
mkItinerary :: ItineraryId -> NonEmpty Leg -> Either DomainError Itinerary
mkItinerary iid legs =
  case validateContinuity (NE.toList legs) of
    Left err -> Left err
    Right () -> Right (Itinerary iid legs)

-- | 隣接 Leg の港接続と時刻順序を検証 (1 区間時は常に OK)
validateContinuity :: [Leg] -> Either DomainError ()
validateContinuity [] = Left (InvalidItinerary "no legs")
validateContinuity [_] = Right ()
validateContinuity (a : b : rest)
  | legUnloadLocation a /= legLoadLocation b =
      Left (InvalidItinerary ("leg " <> tshow (legSeqNumber a) <> " unload location != leg " <> tshow (legSeqNumber b) <> " load location"))
  | legUnloadTime a > legLoadTime b =
      Left (InvalidItinerary ("leg " <> tshow (legSeqNumber a) <> " unload_time > leg " <> tshow (legSeqNumber b) <> " load_time"))
  | legSeqNumber b /= legSeqNumber a + 1 =
      Left (InvalidItinerary ("seq_number must be consecutive: " <> tshow (legSeqNumber a) <> " -> " <> tshow (legSeqNumber b)))
  | otherwise = validateContinuity (b : rest)

tshow :: Show a => a -> Text
tshow = T.pack . show

-- | 経路全体の出発時刻 (= 先頭 Leg の load_time)
itDepartureTime :: Itinerary -> UTCTime
itDepartureTime = legLoadTime . NE.head . itLegs

-- | 経路全体の到着時刻 (= 末尾 Leg の unload_time)
itArrivalTime :: Itinerary -> UTCTime
itArrivalTime = legUnloadTime . NE.last . itLegs

-- | 経路を構成する全寄港港 (先頭 Leg の出発港 + 各 Leg の到着港)
itPorts :: Itinerary -> [Text]
itPorts it =
  let legsList = NE.toList (itLegs it)
   in legLoadLocation (NE.head (itLegs it)) : map legUnloadLocation legsList
