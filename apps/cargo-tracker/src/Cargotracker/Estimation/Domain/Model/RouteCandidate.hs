{- | 経路候補 エンティティ (US01, IT2)

Estimate 集約配下のエンティティ。1 件の見積に対して 0 件以上の RouteCandidate
を `rank` 昇順 (0 = 最優先) で保持する。

- rank: 0..N の整数 (集約内でユニーク、0 を直行便とする慣例)
- transitDays: 所要日数 (1 以上)
- estimatedCost: 概算料金 (0 以上、IT2 では円単位 Int で簡略化)
- voyageNumbers: 経由航海番号テキスト (1 件以上)。
  cross-BC ACL を尊重し Routing.VoyageNumber 値オブジェクトを直接
  保持しない (T-06 Rule 4)。Application 層で文字列変換する。
-}
module Cargotracker.Estimation.Domain.Model.RouteCandidate
  ( RouteCandidate (..),
    mkRouteCandidate,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data RouteCandidate = RouteCandidate
  { rank :: !Int
  , transitDays :: !Int
  , estimatedCost :: !Int
  , voyageNumbers :: ![Text]
  }
  deriving stock (Eq, Show)

mkRouteCandidate ::
  Int -> Int -> Int -> [Text] -> Either DomainError RouteCandidate
mkRouteCandidate r td cost vns
  | r < 0 = Left (InvalidBookingId "rank must be >= 0")
  | td < 1 = Left (InvalidBookingId "transitDays must be >= 1")
  | cost < 0 = Left (InvalidBookingId "estimatedCost must be >= 0")
  | null vns = Left (InvalidBookingId "voyageNumbers must not be empty")
  | any (T.null . T.strip) vns =
      Left (InvalidBookingId "voyageNumbers must not contain empty strings")
  | otherwise =
      Right
        RouteCandidate
          { rank = r
          , transitDays = td
          , estimatedCost = cost
          , voyageNumbers = map T.strip vns
          }
