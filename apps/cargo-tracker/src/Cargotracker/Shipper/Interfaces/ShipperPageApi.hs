{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 荷主登録の SSR 画面 (IT1 US02/03)

- GET  /shippers/new : 登録フォーム
- POST /shippers/new : フォームを受け取り、RegisterShipperCommand を実行し、
                       結果ページを返す
-}
module Cargotracker.Shipper.Interfaces.ShipperPageApi
  ( ShipperPageApi,
    shipperPageApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Text (Text)
import qualified Data.Text as T
import GHC.Generics (Generic)
import Lucid (Html)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Application.Ports (ShipperRepository)
import Cargotracker.Shipper.Application.RegisterShipperCommand
  ( RegisterShipperInput (..),
    ShipperKindInput (..),
    execute,
  )
import Cargotracker.Shipper.Domain.Model.Shipper (ContractRank (..))
import Cargotracker.Shipper.Views.ShipperFormView
  ( shipperFormPage,
    shipperResultPage,
  )

data ShipperFormRequest = ShipperFormRequest
  { shipperId :: !Text
  , email :: !Text
  , address :: !Text
  , kind :: !Text
  , corporateNumber :: !(Maybe Text)
  , contractRank :: !(Maybe Text)
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromForm)

type ShipperPageApi =
  "shippers"
    :> "new"
    :> ( Get '[HTML] (Html ())
           :<|> ReqBody '[FormUrlEncoded] ShipperFormRequest :> Post '[HTML] (Html ())
       )

shipperPageApp :: ShipperRepository IO -> Application
shipperPageApp repo =
  serve (Proxy :: Proxy ShipperPageApi) (handlerGet :<|> handlerPost repo)

handlerGet :: Handler (Html ())
handlerGet = pure (shipperFormPage Nothing)

handlerPost :: ShipperRepository IO -> ShipperFormRequest -> Handler (Html ())
handlerPost repo req = case toInput req of
  Left e ->
    pure
      (shipperResultPage False ("登録失敗: " <> T.pack (show e)))
  Right input -> do
    result <- liftIO (execute repo input)
    pure $ case result of
      Right _ ->
        shipperResultPage
          True
          ("荷主 " <> shipperId req <> " を登録しました")
      Left (ConcurrentModification _) ->
        shipperResultPage False "メールアドレスが既に使用されています"
      Left e ->
        shipperResultPage False ("登録失敗: " <> T.pack (show e))

toInput :: ShipperFormRequest -> Either DomainError RegisterShipperInput
toInput r = case kind r of
  "individual" ->
    Right
      RegisterShipperInput
        { inputId = shipperId r
        , inputEmail = email r
        , inputAddress = address r
        , inputKind = InputIndividual
        }
  "corporate" -> case (corporateNumber r, contractRank r >>= parseRank) of
    (Just cn, Just rank) ->
      Right
        RegisterShipperInput
          { inputId = shipperId r
          , inputEmail = email r
          , inputAddress = address r
          , inputKind = InputCorporate cn rank
          }
    _ -> Left (InvalidShipperId "法人時は法人番号と契約ランクが必要")
  _ -> Left (InvalidShipperId "種別が不正です")

parseRank :: Text -> Maybe ContractRank
parseRank "Bronze" = Just Bronze
parseRank "Silver" = Just Silver
parseRank "Gold" = Just Gold
parseRank _ = Nothing
