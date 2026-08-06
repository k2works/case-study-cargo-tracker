{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 航海スケジュール API (IT1 US24 4.5)

POST /voyages
  Body: { voyageNumber, movements: [{ departure, arrival, departureTime, arrivalTime }] }
  Response:
    201 Created : 登録成功
    422         : ドメイン検証エラー (VoyageNumber 不正 / UnLocode 不正 / 連続性違反)
    409 Conflict: 航海番号重複
    400         : JSON パース失敗
-}
module Cargotracker.Routing.Interfaces.VoyageApi
  ( VoyageApi,
    RegisterVoyageRequest (..),
    MovementRequest (..),
    voyageApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Aeson (FromJSON, ToJSON)
import qualified Data.ByteString.Lazy.Char8 as LBC
import Data.Text (Text)
import Data.Time (UTCTime)
import GHC.Generics (Generic)
import Servant

import Cargotracker.Routing.Application.Ports (VoyageRepository)
import Cargotracker.Routing.Application.RegisterVoyageCommand
  ( CarrierMovementInput (..),
    RegisterVoyageInput (..),
    execute,
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (unVoyageNumber)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data MovementRequest = MovementRequest
  { departure :: !Text
  , arrival :: !Text
  , departureTime :: !UTCTime
  , arrivalTime :: !UTCTime
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromJSON, ToJSON)

data RegisterVoyageRequest = RegisterVoyageRequest
  { voyageNumber :: !Text
  , movements :: ![MovementRequest]
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromJSON, ToJSON)

newtype RegisterVoyageResponse = RegisterVoyageResponse {voyageNumberValue :: Text}
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromJSON, ToJSON)

type VoyageApi =
  "voyages"
    :> ReqBody '[JSON] RegisterVoyageRequest
    :> PostCreated '[JSON] RegisterVoyageResponse

voyageHandler ::
  VoyageRepository IO ->
  RegisterVoyageRequest ->
  Handler RegisterVoyageResponse
voyageHandler repo req = do
  let input =
        RegisterVoyageInput
          { inputVoyageNumber = voyageNumber req
          , inputMovements = map toMovementInput (movements req)
          }
  result <- liftIO (execute repo input)
  case result of
    Right vn -> pure (RegisterVoyageResponse (unVoyageNumber vn))
    Left (ConcurrentModification msg) ->
      throwError err409 {errBody = errorBody (ConcurrentModification msg)}
    Left e ->
      throwError err422 {errBody = errorBody e}

toMovementInput :: MovementRequest -> CarrierMovementInput
toMovementInput m =
  CarrierMovementInput
    { inputDeparture = departure m
    , inputArrival = arrival m
    , inputDepartureTime = departureTime m
    , inputArrivalTime = arrivalTime m
    }

errorBody :: DomainError -> LBC.ByteString
errorBody e = LBC.pack ("{\"error\":\"" <> show e <> "\"}")

voyageApp :: VoyageRepository IO -> Application
voyageApp repo = serve (Proxy :: Proxy VoyageApi) (voyageHandler repo)
