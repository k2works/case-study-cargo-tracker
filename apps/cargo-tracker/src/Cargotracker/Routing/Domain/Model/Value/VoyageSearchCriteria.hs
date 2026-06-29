{- | 航海検索条件 VO (US07, IT3)

経路設計者が `/voyages/search` で入力する 4 条件を保持する値オブジェクト。
スマートコンストラクタで以下を検証:

* 出発期間 (from..to) は from <= to
* 出発地と目的地は別港
-}
module Cargotracker.Routing.Domain.Model.Value.VoyageSearchCriteria
  ( VoyageSearchCriteria (..),
    mkVoyageSearchCriteria,
  ) where

import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data VoyageSearchCriteria = VoyageSearchCriteria
  { vscOrigin :: !UnLocode
  , vscDestination :: !UnLocode
  , vscFromDate :: !UTCTime
  , vscToDate :: !UTCTime
  }
  deriving stock (Eq, Show)

mkVoyageSearchCriteria ::
  UnLocode -> UnLocode -> UTCTime -> UTCTime -> Either DomainError VoyageSearchCriteria
mkVoyageSearchCriteria o d from to
  | o == d = Left (SameOriginDestination (unUnLocode o))
  | from > to = Left (InvalidSearchPeriod from to)
  | otherwise =
      Right
        VoyageSearchCriteria
          { vscOrigin = o
          , vscDestination = d
          , vscFromDate = from
          , vscToDate = to
          }
