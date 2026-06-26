{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 貨物予約登録の SSR 画面 (IT1 US04)

- GET  /bookings/new : 登録フォーム
- POST /bookings/new : フォーム → RegisterBookingCommand → 結果ページ

deadline は datetime-local (例: "2027-12-31T00:00") として送られる。
末尾に :00 + UTC オフセット ":00Z" を付けて UTCTime にパースする。
-}
module Cargotracker.Booking.Interfaces.BookingPageApi
  ( BookingPageApi,
    bookingPageApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, defaultTimeLocale, parseTimeM)
import GHC.Generics (Generic)
import Lucid (Html)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Booking.Application.Ports
  ( BookingRepository,
    ShipperExistenceChecker,
  )
import Cargotracker.Booking.Application.RegisterBookingCommand
  ( RegisterBookingInput (..),
    execute,
  )
import Cargotracker.Booking.Views.BookingFormView
  ( bookingFormPage,
    bookingResultPage,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data BookingFormRequest = BookingFormRequest
  { bookingId :: !Text
  , shipperId :: !Text
  , origin :: !Text
  , destination :: !Text
  , deadline :: !Text -- "YYYY-MM-DDTHH:MM" (datetime-local)
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromForm)

type BookingPageApi =
  "bookings"
    :> "new"
    :> ( Get '[HTML] (Html ())
           :<|> ReqBody '[FormUrlEncoded] BookingFormRequest :> Post '[HTML] (Html ())
       )

bookingPageApp :: BookingRepository IO -> ShipperExistenceChecker IO -> Application
bookingPageApp repo checker =
  serve
    (Proxy :: Proxy BookingPageApi)
    (handlerGet :<|> handlerPost repo checker)

handlerGet :: Handler (Html ())
handlerGet = pure (bookingFormPage Nothing)

handlerPost ::
  BookingRepository IO ->
  ShipperExistenceChecker IO ->
  BookingFormRequest ->
  Handler (Html ())
handlerPost repo checker req = case parseDeadline (deadline req) of
  Nothing ->
    pure
      (bookingResultPage False "到着期限の形式が不正です")
  Just dt -> do
    let input =
          RegisterBookingInput
            { inputBookingId = bookingId req
            , inputShipperId = shipperId req
            , inputOrigin = origin req
            , inputDestination = destination req
            , inputDeadline = dt
            }
    result <- liftIO (execute repo checker input)
    pure $ case result of
      Right _ ->
        bookingResultPage
          True
          ("予約 " <> bookingId req <> " を登録しました")
      Left (ShipperNotFound sid) ->
        bookingResultPage
          False
          ("荷主 " <> sid <> " が見つかりません。先に登録してください")
      Left e ->
        bookingResultPage False ("登録失敗: " <> T.pack (show e))

-- datetime-local 形式 "YYYY-MM-DDTHH:MM" を UTCTime に変換
parseDeadline :: Text -> Maybe UTCTime
parseDeadline t = parseTimeM True defaultTimeLocale "%Y-%m-%dT%H:%M" (T.unpack t)
