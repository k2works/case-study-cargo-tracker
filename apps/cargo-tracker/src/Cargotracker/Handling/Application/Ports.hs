{- | Handling Context 出力ポート (US15, IT5)

T-02 準拠: Repository は IO のみ、Tx 境界は Application 側で管理する。
-}
module Cargotracker.Handling.Application.Ports
  ( HandlingActivityRepository (..),
  ) where

import Data.Text (Text)

import Cargotracker.Handling.Domain.Model.HandlingActivity (HandlingActivity)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data HandlingActivityRepository m = HandlingActivityRepository
  { saveHandlingActivity :: HandlingActivity -> m (Either DomainError ())
  , findByBookingId :: Text -> m [HandlingActivity]
  -- ^ 荷役履歴を時系列 (event_completion_time ASC) で返す
  }
