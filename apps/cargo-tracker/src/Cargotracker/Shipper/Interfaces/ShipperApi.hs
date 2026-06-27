{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 荷主登録 API (IT1 US02/03 2.5)

POST /shippers
  Body: { id, email, address, kind: "individual" | "corporate",
          corporateNumber?, contractRank? ("Bronze"/"Silver"/"Gold") }
  Response:
    201 Created : 登録成功
    422         : ドメイン検証エラー
    409         : メール重複 (ConcurrentModification)
    400         : 入力 JSON 不正 (Servant のリクエストパース失敗)

エラー処理戦略は iteration_plan-1.md §設計 > エラー処理戦略に従う。
-}
module Cargotracker.Shipper.Interfaces.ShipperApi
  ( ShipperApi,
    RegisterShipperRequest (..),
    shipperApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Aeson (FromJSON (..), ToJSON, withObject, (.:), (.:?))
import qualified Data.ByteString.Lazy.Char8 as LBC
import Data.Text (Text)
import qualified Data.Text as T
import GHC.Generics (Generic)
import Network.Wai (Application)
import Servant

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Application.Ports (ShipperRepository)
import Cargotracker.Shipper.Application.RegisterShipperCommand
  ( RegisterShipperInput (..),
    ShipperKindInput (..),
    execute,
  )
import Cargotracker.Shipper.Domain.Model.Shipper (ContractRank (..))

data RegisterShipperRequest = RegisterShipperRequest
  { shipperId :: !Text
  , name :: !Text
  , email :: !Text
  , address :: !Text
  , kind :: !Text
  , corporateNumber :: !(Maybe Text)
  , contractRank :: !(Maybe Text)
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (ToJSON)

-- Maybe フィールドの欠落を許容するため手書き
instance FromJSON RegisterShipperRequest where
  parseJSON = withObject "RegisterShipperRequest" $ \o ->
    RegisterShipperRequest
      <$> o .: "shipperId"
      <*> o .: "name"
      <*> o .: "email"
      <*> o .: "address"
      <*> o .: "kind"
      <*> o .:? "corporateNumber"
      <*> o .:? "contractRank"

newtype RegisterShipperResponse = RegisterShipperResponse {shipperIdValue :: Text}
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromJSON, ToJSON)

type ShipperApi =
  "shippers"
    :> ReqBody '[JSON] RegisterShipperRequest
    :> PostCreated '[JSON] RegisterShipperResponse

shipperHandler ::
  ShipperRepository IO ->
  RegisterShipperRequest ->
  Handler RegisterShipperResponse
shipperHandler repo req = do
  input <- case toInput req of
    Right i -> pure i
    Left e -> throwError err422 {errBody = errorBody e}
  result <- liftIO (execute repo input)
  case result of
    Right _ -> pure (RegisterShipperResponse (shipperId req))
    Left (ConcurrentModification msg) ->
      throwError err409 {errBody = errorBody (ConcurrentModification msg)}
    Left e ->
      throwError err422 {errBody = errorBody e}

errorBody :: DomainError -> LBC.ByteString
errorBody e = LBC.pack ("{\"error\":\"" <> show e <> "\"}")

shipperApp :: ShipperRepository IO -> Application
shipperApp repo = serve (Proxy :: Proxy ShipperApi) (shipperHandler repo)

{- | リクエストをコマンド入力に変換する。kind が "corporate" の場合は
corporateNumber / contractRank が必要 (なければ 422)。
-}
toInput :: RegisterShipperRequest -> Either DomainError RegisterShipperInput
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
    _ -> Left (InvalidShipperId "corporate requires corporateNumber and contractRank")
  other -> Left (InvalidShipperId ("unknown kind: " <> T.pack (T.unpack other)))

parseRank :: Text -> Maybe ContractRank
parseRank t = case t of
  "Bronze" -> Just Bronze
  "Silver" -> Just Silver
  "Gold" -> Just Gold
  _ -> Nothing
