{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 貨物予約 API (IT1 US04 3.5)

POST /bookings
  Body: { bookingId, shipperId, origin, destination, deadline (ISO8601) }
  Response:
    201 Created : 登録成功
    422         : ドメイン検証エラー
    404         : 荷主未存在 (ShipperNotFound)
    400         : JSON パース失敗
-}
module Cargotracker.Booking.Interfaces.BookingApi
  ( BookingApi,
    RegisterBookingRequest (..),
    bookingApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Aeson (FromJSON, ToJSON)
import qualified Data.ByteString.Lazy.Char8 as LBC
import Data.Text (Text)
import Data.Time (UTCTime)
import GHC.Generics (Generic)
import Network.Wai (Application)
import Servant

import Cargotracker.Booking.Application.Ports
  ( BookingRepository,
    ShipperExistenceChecker,
  )
import Cargotracker.Booking.Application.RegisterBookingCommand
  ( RegisterBookingInput (..),
    execute,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (unBookingId)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data RegisterBookingRequest = RegisterBookingRequest
  { bookingId :: !Text
  , shipperId :: !Text
  , origin :: !Text
  , destination :: !Text
  , deadline :: !UTCTime
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromJSON, ToJSON)

newtype RegisterBookingResponse = RegisterBookingResponse {bookingIdValue :: Text}
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromJSON, ToJSON)

type BookingApi =
  "bookings"
    :> ReqBody '[JSON] RegisterBookingRequest
    :> PostCreated '[JSON] RegisterBookingResponse

bookingHandler ::
  BookingRepository IO ->
  ShipperExistenceChecker IO ->
  RegisterBookingRequest ->
  Handler RegisterBookingResponse
bookingHandler repo checker req = do
  let input =
        RegisterBookingInput
          { inputBookingId = bookingId req
          , inputShipperId = shipperId req
          , inputOrigin = origin req
          , inputDestination = destination req
          , inputDeadline = deadline req
          }
  result <- liftIO (execute repo checker input)
  case result of
    Right bid -> pure (RegisterBookingResponse (unBookingId bid))
    Left (ShipperNotFound msg) ->
      throwError err404 {errBody = errorBody (ShipperNotFound msg)}
    Left e ->
      throwError err422 {errBody = errorBody e}

errorBody :: DomainError -> LBC.ByteString
errorBody e = LBC.pack ("{\"error\":\"" <> show e <> "\"}")

bookingApp :: BookingRepository IO -> ShipperExistenceChecker IO -> Application
bookingApp repo checker =
  serve (Proxy :: Proxy BookingApi) (bookingHandler repo checker)
