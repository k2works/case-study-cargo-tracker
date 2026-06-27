{- | 見積状態 値オブジェクト (US01, IT2)

- Created: 見積発行直後 (有効)
- Expired: 期限切れ (再見積が必要)

IT2 では作成時 Created のみ。Expired への遷移ロジックは
IT3 で発行時刻 + 有効期間ポリシー導入時に追加する。
-}
module Cargotracker.Estimation.Domain.Model.Value.EstimateStatus
  ( EstimateStatus (..),
    estimateStatusToText,
    parseEstimateStatus,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data EstimateStatus
  = Created
  | Expired
  deriving stock (Eq, Show, Enum, Bounded)

estimateStatusToText :: EstimateStatus -> Text
estimateStatusToText Created = "Created"
estimateStatusToText Expired = "Expired"

parseEstimateStatus :: Text -> Either DomainError EstimateStatus
parseEstimateStatus "Created" = Right Created
parseEstimateStatus "Expired" = Right Expired
parseEstimateStatus other =
  Left (InvalidBookingId ("unknown EstimateStatus: " <> other))
