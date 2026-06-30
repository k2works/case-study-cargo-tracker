{- | キャンセル料 VO (US13, IT4)

確定後のキャンセル料を 3 段階で表現する純粋値オブジェクト。

ティア境界 (出航日基準):
- Free    : 出航 7 日以上前
- Partial : 出航 7 日未満 〜 1 日以上前 (料率 30%)
- Full    : 出航 1 日未満 〜 出航後   (料率 100%)
-}
module Cargotracker.Booking.Domain.Model.Value.CancellationFee
  ( CancellationTier (..),
    CancellationFee (..),
    tierRate,
  ) where

import Data.Ratio (Rational, (%))
import Data.Time (UTCTime)

data CancellationTier
  = Free
  | Partial
  | Full
  deriving stock (Eq, Show, Enum, Bounded)

data CancellationFee = CancellationFee
  { cfTier :: !CancellationTier
  , cfRate :: !Rational
  , cfCalculatedAt :: !UTCTime
  }
  deriving stock (Eq, Show)

-- | 各ティアの料率 (純粋関数、ドメインロジック)
tierRate :: CancellationTier -> Rational
tierRate Free = 0 % 100
tierRate Partial = 30 % 100
tierRate Full = 100 % 100
