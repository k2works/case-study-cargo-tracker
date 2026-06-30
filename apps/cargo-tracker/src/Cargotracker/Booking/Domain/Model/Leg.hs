{- | Leg エンティティ (US09, IT4)

経路 (Itinerary) を構成する 1 区間。
ADR-0004 Cross-BC 規約に従い、`voyageNumber` / `loadLocation` / `unloadLocation` は
Text として保持し、Routing BC の `VoyageNumber` / Shared.UnLocode を直接 import しない。
-}
module Cargotracker.Booking.Domain.Model.Leg
  ( Leg (..),
    mkLeg,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data Leg = Leg
  { legSeqNumber :: !Int
  , legLoadLocation :: !Text
  -- ^ UnLocode 文字列 (5 文字、ADR-0004)
  , legUnloadLocation :: !Text
  -- ^ UnLocode 文字列 (5 文字、ADR-0004)
  , legLoadTime :: !UTCTime
  , legUnloadTime :: !UTCTime
  , legVoyageNumber :: !Text
  -- ^ VoyageNumber 文字列 (ADR-0004)
  }
  deriving stock (Eq, Show)

-- | スマートコンストラクタ。順序・空文字を検証する。
mkLeg ::
  -- | seq_number (1 以上)
  Int ->
  -- | load_location_unlocode
  Text ->
  -- | unload_location_unlocode
  Text ->
  -- | load_time
  UTCTime ->
  -- | unload_time
  UTCTime ->
  -- | voyage_number
  Text ->
  Either DomainError Leg
mkLeg seq_ loadLoc unloadLoc loadT unloadT voyNum
  | seq_ < 1 = Left (InvalidLeg "seq_number must be >= 1")
  | T.null loadLoc = Left (InvalidLeg "load_location is empty")
  | T.null unloadLoc = Left (InvalidLeg "unload_location is empty")
  | T.null voyNum = Left (InvalidLeg "voyage_number is empty")
  | loadT >= unloadT = Left (InvalidLeg "load_time must be < unload_time")
  | otherwise =
      Right
        Leg
          { legSeqNumber = seq_
          , legLoadLocation = loadLoc
          , legUnloadLocation = unloadLoc
          , legLoadTime = loadT
          , legUnloadTime = unloadT
          , legVoyageNumber = voyNum
          }
