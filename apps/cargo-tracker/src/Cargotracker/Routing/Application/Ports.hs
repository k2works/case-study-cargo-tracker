{- | Routing Application 層のポート (IT1 US24)

VoyageRepository: 航海集約の永続化と航海番号での検索 (重複検出)。
-}
module Cargotracker.Routing.Application.Ports
  ( VoyageRepository (..),
  ) where

import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (VoyageNumber)
import Cargotracker.Routing.Domain.Model.Voyage (Voyage)

data VoyageRepository m = VoyageRepository
  { findByVoyageNumber :: VoyageNumber -> m (Maybe Voyage)
  , saveVoyage :: Voyage -> m ()
  }
