{- | Estimation Application 層のポート (US01, IT2)

EstimateRepository: 見積集約の永続化と ID 検索。
ACL 規約に従い、Estimate 自身は Shipper / Routing への参照を文字列化済の
状態で保持する (T-06 Rule 4 準拠)。
-}
module Cargotracker.Estimation.Application.Ports
  ( EstimateRepository (..),
  ) where

import Cargotracker.Estimation.Domain.Model.Estimate (Estimate)
import Cargotracker.Estimation.Domain.Model.Value.EstimateId (EstimateId)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data EstimateRepository m = EstimateRepository
  { saveEstimate :: Estimate -> m (Either DomainError ())
  , findEstimateById :: EstimateId -> m (Maybe Estimate)
  }
