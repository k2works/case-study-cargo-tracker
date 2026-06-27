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
import qualified Data.ByteString.Char8 as BC
import Data.Text (Text)
import qualified Data.Text as T
import GHC.Generics (Generic)
import Lucid (Html)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Application.Ports
  ( ShipperRepository (..),
  )
import Cargotracker.Shipper.Application.RegisterShipperCommand
  ( RegisterShipperInput (..),
    ShipperKindInput (..),
    execute,
  )
import Cargotracker.Shipper.Domain.Model.Shipper (ContractRank (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))
import Cargotracker.Shipper.Views.ShipperFormView (shipperFormPage)
import Cargotracker.Shipper.Views.ShipperShowView
  ( shipperNotFoundPage,
    shipperShowPage,
  )

data ShipperFormRequest = ShipperFormRequest
  { shipperId :: !Text
  , name :: !Text
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
    :> ( "new"
           :> QueryParam "error" Text
           :> Get '[HTML] (Html ())
           :<|> "new"
             :> ReqBody '[FormUrlEncoded] ShipperFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> Capture "shipperId" Text :> Get '[HTML] (Html ())
       )

shipperPageApp :: ShipperRepository IO -> Application
shipperPageApp repo =
  serve
    (Proxy :: Proxy ShipperPageApi)
    (handlerGet :<|> handlerPost repo :<|> handlerShow repo)

-- T-08 (IT2): ?error= クエリを受け取り、ShipperFormView の上部に
-- Bootstrap alert でフラッシュ表示する。エラーコードは
-- redirectErr 側で確定値 (例: duplicate-email) を渡している。
handlerGet :: Maybe Text -> Handler (Html ())
handlerGet mError = pure (shipperFormPage (fmap shipperErrorMessage mError))

shipperErrorMessage :: Text -> Text
shipperErrorMessage "duplicate-email" = "同じメールアドレスが既に登録されています"
shipperErrorMessage e = "登録に失敗しました: " <> e

handlerShow :: ShipperRepository IO -> Text -> Handler (Html ())
handlerShow repo sid = do
  m <- liftIO (findById repo (ShipperId sid))
  case m of
    Just s -> pure (shipperShowPage s)
    Nothing -> pure shipperNotFoundPage

handlerPost ::
  ShipperRepository IO ->
  ShipperFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerPost repo req = case toInput req of
  Left e -> redirectErr ("/shippers/new?error=" <> T.pack (show e))
  Right input -> do
    result <- liftIO (execute repo input)
    case result of
      Right _ -> pure (addHeader ("/shippers/" <> shipperId req) NoContent)
      Left (ConcurrentModification _) ->
        redirectErr "/shippers/new?error=duplicate-email"
      Left e -> redirectErr ("/shippers/new?error=" <> T.pack (show e))
  where
    redirectErr :: Text -> Handler a
    redirectErr loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }

toInput :: ShipperFormRequest -> Either DomainError RegisterShipperInput
toInput r = case kind r of
  "individual" ->
    Right
      RegisterShipperInput
        { inputId = shipperId r
        , inputName = name r
        , inputEmail = email r
        , inputAddress = address r
        , inputKind = InputIndividual
        }
  "corporate" -> case (corporateNumber r, contractRank r >>= parseRank) of
    (Just cn, Just rank) ->
      Right
        RegisterShipperInput
          { inputId = shipperId r
          , inputName = name r
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
