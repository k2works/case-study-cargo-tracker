{-# LANGUAGE DataKinds #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

-- | htmx: 航海フォームに区間行を動的追加 (GET /voyages/new/movement-row?index=N)
module Cargotracker.Routing.Interfaces.VoyageMovementRowApi
  ( VoyageMovementRowApi,
    voyageMovementRowApp,
  ) where

import Lucid
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)

import Cargotracker.Routing.Views.VoyageFormView (movementRow)

type VoyageMovementRowApi =
  "voyages"
    :> "new"
    :> "movement-row"
    :> QueryParam "index" Int
    :> Get '[HTML] (Html ())

voyageMovementRowApp :: Application
voyageMovementRowApp = serve (Proxy :: Proxy VoyageMovementRowApi) handler

handler :: Maybe Int -> Handler (Html ())
handler mIdx = pure (movementRow (maybe 4 id mIdx) False)
