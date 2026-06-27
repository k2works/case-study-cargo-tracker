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
import qualified Data.ByteString.Char8 as BC
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, defaultTimeLocale, parseTimeM)
import GHC.Generics (Generic)
import Lucid (Html)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Booking.Application.HandOverToRouterCommand
  ( HandOverToRouterInput (..),
  )
import qualified Cargotracker.Booking.Application.HandOverToRouterCommand as HandOver
import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker,
  )
import Cargotracker.Booking.Application.RegisterBookingCommand
  ( CargoTypeInput (..),
    RegisterBookingInput (..),
    execute,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Views.BookingFormView (bookingFormPage)
import Cargotracker.Booking.Views.BookingListView (bookingListPage)
import Cargotracker.Booking.Views.BookingShowView
  ( bookingNotFoundPage,
    bookingShowPage,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Infrastructure.IdGenerator
  ( generateBookingIdText,
  )

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
    :> ( Get '[HTML] (Html ())
           :<|> "new"
             :> QueryParam "error" Text
             :> Get '[HTML] (Html ())
           :<|> "new"
             :> ReqBody '[FormUrlEncoded] BookingFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> Capture "bookingId" Text :> Get '[HTML] (Html ())
           :<|> Capture "bookingId" Text
             :> "handover"
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
       )

bookingPageApp :: BookingRepository IO -> ShipperExistenceChecker IO -> Application
bookingPageApp repo checker =
  serve
    (Proxy :: Proxy BookingPageApi)
    ( handlerList repo
        :<|> handlerGet
        :<|> handlerPost repo checker
        :<|> handlerShow repo
        :<|> handlerHandover repo
    )

handlerList :: BookingRepository IO -> Handler (Html ())
handlerList repo = do
  xs <- liftIO (findAllCargos repo)
  pure (bookingListPage xs)

-- T-08 (IT2): ?error= クエリを Bootstrap alert に変換する。
handlerGet :: Maybe Text -> Handler (Html ())
handlerGet mError = pure (bookingFormPage (fmap bookingErrorMessage mError))

bookingErrorMessage :: Text -> Text
bookingErrorMessage "deadline-format" = "到着期限の日付形式が不正です"
bookingErrorMessage "shipper-not-found" = "指定された荷主が見つかりません"
bookingErrorMessage "booking-not-found" = "指定された予約が見つかりません"
bookingErrorMessage e = "予約登録に失敗しました: " <> e

handlerShow :: BookingRepository IO -> Text -> Handler (Html ())
handlerShow repo bid = do
  m <- liftIO (findCargoById repo (BookingId bid))
  pure (maybe bookingNotFoundPage bookingShowPage m)

handlerPost ::
  BookingRepository IO ->
  ShipperExistenceChecker IO ->
  BookingFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
-- T-07 (IT2): BookingId はサーバ側で自動採番する。クライアントから
-- 送られた bookingId は無視する。
handlerPost repo checker req = case parseDeadline (deadline req) of
  Nothing -> redirectErr "/bookings/new?error=deadline-format"
  Just dt -> do
    generatedBid <- liftIO generateBookingIdText
    let input =
          RegisterBookingInput
            { inputBookingId = generatedBid
            , inputShipperId = shipperId req
            , inputOrigin = origin req
            , inputDestination = destination req
            , inputDeadline = dt
            , -- US05 (IT2): フォーム UI の cargoType 選択は次反復で導入。
              --   現状 General 固定で IT1 後方互換を維持する。
              inputCargoType = InputGeneral
            }
    result <- liftIO (execute repo checker input)
    case result of
      Right _ -> pure (addHeader ("/bookings/" <> generatedBid) NoContent)
      Left (ShipperNotFound _) -> redirectErr "/bookings/new?error=shipper-not-found"
      Left e -> redirectErr ("/bookings/new?error=" <> T.pack (show e))
  where
    redirectErr :: Text -> Handler a
    redirectErr loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }

-- datetime-local 形式 "YYYY-MM-DDTHH:MM" を UTCTime に変換
parseDeadline :: Text -> Maybe UTCTime
parseDeadline t = parseTimeM True defaultTimeLocale "%Y-%m-%dT%H:%M" (T.unpack t)

-- US06 (IT2): POST /bookings/:bookingId/handover で経路設計者へ引き渡す。
-- Submitted → RouteProposed の遷移が成功すれば詳細画面に 303、
-- 失敗時はエラー種別ごとのフラッシュ用クエリを付与して詳細画面に戻す。
handlerHandover ::
  BookingRepository IO ->
  Text ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerHandover repo bid = do
  result <-
    liftIO
      ( HandOver.execute
          repo
          (HandOverToRouterInput {inputBookingId = BookingId bid})
      )
  let detail = "/bookings/" <> bid
  case result of
    Right _ ->
      pure (addHeader (detail <> "?flash=handover-ok") NoContent)
    Left (BookingNotFound _) ->
      redirectErr "/bookings/new?error=booking-not-found"
    Left (InvalidStateTransition fromS _) ->
      redirectErr (detail <> "?error=invalid-state&from=" <> fromS)
    Left (ConcurrentModification _) ->
      redirectErr (detail <> "?error=concurrent-modification")
    Left e ->
      redirectErr (detail <> "?error=" <> T.pack (show e))
  where
    redirectErr :: Text -> Handler a
    redirectErr loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }
