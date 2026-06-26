{-# LANGUAGE DataKinds #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

-- | 荷主検索 (htmx 部分 HTML) - GET /shippers/search?q=
module Cargotracker.Shipper.Interfaces.ShipperSearchApi
  ( ShipperSearchApi,
    shipperSearchApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Text (Text)
import Lucid
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)

import Cargotracker.Shipper.Application.Ports (ShipperRepository (..))
import Cargotracker.Shipper.Domain.Model.Shipper (Shipper (..))
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail
  ( ContactEmail (..),
  )
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))

type ShipperSearchApi =
  "shippers"
    :> "search"
    :> QueryParam "q" Text
    :> QueryParam "shipperId" Text
    :> Get '[HTML] (Html ())

shipperSearchApp :: ShipperRepository IO -> Application
shipperSearchApp repo = serve (Proxy :: Proxy ShipperSearchApi) (handler repo)

handler ::
  ShipperRepository IO -> Maybe Text -> Maybe Text -> Handler (Html ())
handler repo mq mSid =
  case firstNonEmpty [mq, mSid] of
    Nothing -> pure (renderResults [])
    Just q -> do
      rs <- liftIO (searchByQuery repo (ContactEmail q))
      pure (renderResults rs)
  where
    firstNonEmpty :: [Maybe Text] -> Maybe Text
    firstNonEmpty xs = case [t | Just t <- xs, t /= ""] of
      (t : _) -> Just t
      [] -> Nothing

renderResults :: [Shipper] -> Html ()
renderResults [] =
  div_ [class_ "list-group-item text-muted small"] "該当なし"
renderResults rs = mapM_ row rs
  where
    row :: Shipper -> Html ()
    row s = do
      let ShipperId sid = shipperId s
          ContactEmail em = shipperEmail s
      button_
        [ type_ "button"
        , class_ "list-group-item list-group-item-action"
        , onclick_ ("document.getElementById('shipperId').value='" <> sid <> "'")
        ]
        (toHtml (sid <> " — " <> em))
