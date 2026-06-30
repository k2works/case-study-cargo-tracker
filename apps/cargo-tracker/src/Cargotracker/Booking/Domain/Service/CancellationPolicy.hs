{- | キャンセル料算定ドメインサービス (US13, IT4)

純粋関数。`now` と `出航日時` の差に基づき 3 段階ティアを決定する。
T-03 規約: IO に依存しない。`now` は呼び出し側 (Application 層) から渡される。

境界値 (出航日時 - 現在時刻):
- diff >= 7 日   → Free    (0%)
- 1 日 <= diff < 7 日 → Partial (30%)
- diff < 1 日 (= 24 時間未満、過去含む) → Full (100%)
-}
module Cargotracker.Booking.Domain.Service.CancellationPolicy
  ( calculate,
  ) where

import Data.Time (NominalDiffTime, UTCTime, diffUTCTime)

import Cargotracker.Booking.Domain.Model.Value.CancellationFee
  ( CancellationFee (..),
    CancellationTier (..),
    tierRate,
  )

sevenDays, oneDay :: NominalDiffTime
sevenDays = 7 * 24 * 3600
oneDay = 1 * 24 * 3600

-- | 現在時刻と出航日時からキャンセル料を算定する。
calculate :: UTCTime -> UTCTime -> CancellationFee
calculate now departure =
  let diff = diffUTCTime departure now
      tier
        | diff >= sevenDays = Free
        | diff >= oneDay = Partial
        | otherwise = Full
   in CancellationFee
        { cfTier = tier
        , cfRate = tierRate tier
        , cfCalculatedAt = now
        }
