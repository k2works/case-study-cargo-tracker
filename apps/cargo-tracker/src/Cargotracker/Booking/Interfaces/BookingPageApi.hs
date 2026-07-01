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
import Data.Maybe (fromMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, defaultTimeLocale, getCurrentTime, parseTimeM)
import GHC.Generics (Generic)
import Lucid (Html)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Booking.Application.AttachCustomsDeclarationCommand
  ( AttachCustomsInput (..),
  )
import qualified Cargotracker.Booking.Application.AttachCustomsDeclarationCommand as AttachCustoms
import Cargotracker.Booking.Application.CancelBookingCommand
  ( CancelBookingInput (..),
  )
import qualified Cargotracker.Booking.Application.CancelBookingCommand as CancelBk
import Cargotracker.Booking.Application.ConfirmBookingCommand
  ( ConfirmBookingInput (..),
  )
import qualified Cargotracker.Booking.Application.ConfirmBookingCommand as ConfirmBk
import Cargotracker.Booking.Application.CustomsPorts
  ( CustomsDeclarationRepository (..),
  )
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
import Cargotracker.Booking.Application.SubmitBookingCommand
  ( SubmitBookingInput (..),
  )
import qualified Cargotracker.Booking.Application.SubmitBookingCommand as Submit
import Cargotracker.Booking.Domain.Model.Cargo (cargoRouteSpec)
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import qualified Cargotracker.Booking.Domain.Model.Value.RouteSpecification as RouteSpec
import Cargotracker.Booking.Views.BookingFormView
  ( bookingFormPage,
    cargoTypeRowFragment,
  )
import Cargotracker.Booking.Views.BookingListView (bookingListPage)
import Cargotracker.Booking.Views.BookingShowView
  ( bookingNotFoundPage,
    bookingShowPage,
  )
import Cargotracker.Booking.Views.CustomsSectionView (customsEditPage)
import Cargotracker.Routing.Application.ComputeRouteCandidatesQuery
  ( ComputeRouteCandidatesInput (..),
  )
import qualified Cargotracker.Routing.Application.ComputeRouteCandidatesQuery as ComputeRoutes
import Cargotracker.Routing.Application.Ports (VoyageRepository)
import Cargotracker.Routing.Views.RouteCandidatesView
  ( routeCandidatesNotFoundPage,
    routeCandidatesPage,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Infrastructure.IdGenerator
  ( generateBookingIdText,
  )

data CustomsFormRequest = CustomsFormRequest
  { hs_code :: !Text
  , broker_name :: !Text
  , status :: !Text
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromForm)

data BookingFormRequest = BookingFormRequest
  { bookingId :: !(Maybe Text)
  {- ^ M-03 (IT3): クライアントからの bookingId は無視するが、後方互換のため
  Maybe Text で残す (Servant FromForm がフィールド欠落を許容する形)。
  -}
  , shipperId :: !Text
  , origin :: !Text
  , destination :: !Text
  , deadline :: !Text -- "YYYY-MM-DDTHH:MM" (datetime-local)
  , -- U-02 (IT3): 動的フィールド対応のため新規追加。値は htmx fragment が
    -- 提供する name=cargoType 等の Form フィールドからバインドされる。
    -- General の場合は Hazardous/Refrigerated 関連フィールドは Nothing。
    cargoType :: !(Maybe Text)
  , hazardousClass :: !(Maybe Text)
  , unNumber :: !(Maybe Text)
  , properShippingName :: !(Maybe Text)
  , minTemperature :: !(Maybe Text)
  , maxTemperature :: !(Maybe Text)
  , temperatureUnit :: !(Maybe Text)
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
           :<|> "new"
             :> "cargo-type-row"
             :> QueryParam "cargoType" Text
             :> Get '[HTML] (Html ())
           :<|> Capture "bookingId" Text :> Get '[HTML] (Html ())
           :<|> Capture "bookingId" Text
             :> "handover"
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> Capture "bookingId" Text
             :> "submit"
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> Capture "bookingId" Text
             :> "customs"
             :> "edit"
             :> QueryParam "error" Text
             :> Get '[HTML] (Html ())
           :<|> Capture "bookingId" Text
             :> "customs"
             :> ReqBody '[FormUrlEncoded] CustomsFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> Capture "bookingId" Text
             :> "routes"
             :> Get '[HTML] (Html ())
           :<|> Capture "bookingId" Text
             :> "confirm"
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> Capture "bookingId" Text
             :> "cancel"
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
       )

bookingPageApp ::
  BookingRepository IO ->
  ShipperExistenceChecker IO ->
  CustomsDeclarationRepository IO ->
  VoyageRepository IO ->
  Application
bookingPageApp repo checker customsRepo voyageRepo =
  serve
    (Proxy :: Proxy BookingPageApi)
    ( handlerList repo
        :<|> handlerGet
        :<|> handlerPost repo checker
        :<|> handlerCargoTypeRow
        :<|> handlerShow repo customsRepo
        :<|> handlerHandover repo
        :<|> handlerSubmit repo
        :<|> handlerCustomsEdit repo customsRepo
        :<|> handlerCustomsAttach repo customsRepo
        :<|> handlerRoutes repo voyageRepo
        :<|> handlerConfirm repo
        :<|> handlerCancel repo
    )

{- | US08a (IT3): GET /bookings/:bookingId/routes
予約から RouteSpecification を取り出して経路候補を算出する。
予約が存在しない場合は not-found ページを 200 で返す。
-}
handlerRoutes ::
  BookingRepository IO ->
  VoyageRepository IO ->
  Text ->
  Handler (Html ())
handlerRoutes repo voyageRepo bid = do
  m <- liftIO (findCargoById repo (BookingId bid))
  case m of
    Nothing -> pure routeCandidatesNotFoundPage
    Just cargo -> do
      let spec = cargoRouteSpec cargo
          input =
            ComputeRouteCandidatesInput
              { inputOrigin = RouteSpec.origin spec
              , inputDestination = RouteSpec.destination spec
              , inputDeadline = RouteSpec.arrivalDeadline spec
              }
      res <- liftIO (ComputeRoutes.execute voyageRepo input)
      case res of
        Left _ -> pure (routeCandidatesPage bid [])
        Right candidates -> pure (routeCandidatesPage bid candidates)

-- U-02 (IT3): htmx 部分 HTML を返す。cargoType=Hazardous なら危険物
-- フィールド、Refrigerated なら冷凍フィールドを返す。General/未指定は空。
handlerCargoTypeRow :: Maybe Text -> Handler (Html ())
handlerCargoTypeRow mType = pure (cargoTypeRowFragment (fromMaybe "" mType))

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
bookingErrorMessage "hazardous-fields-missing" = "危険物クラス / UN 番号 / 正式輸送品名を全て入力してください"
bookingErrorMessage "refrigerated-fields-missing" = "最低温度 / 最高温度 / 単位を全て入力してください"
bookingErrorMessage "temperature-format" = "温度は数値で入力してください"
bookingErrorMessage e = "予約登録に失敗しました: " <> e

handlerShow ::
  BookingRepository IO ->
  CustomsDeclarationRepository IO ->
  Text ->
  Handler (Html ())
handlerShow repo customsRepo bid = do
  m <- liftIO (findCargoById repo (BookingId bid))
  case m of
    Nothing -> pure bookingNotFoundPage
    Just cargo -> do
      mCustoms <- liftIO (findByBookingId customsRepo (BookingId bid))
      pure (bookingShowPage cargo mCustoms)

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
    case parseCargoType req of
      Left errCode -> redirectErr ("/bookings/new?error=" <> errCode)
      Right ct -> do
        let input =
              RegisterBookingInput
                { inputBookingId = generatedBid
                , inputShipperId = shipperId req
                , inputOrigin = origin req
                , inputDestination = destination req
                , inputDeadline = dt
                , inputCargoType = ct
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

{- | U-02 (IT3): フォーム入力から CargoTypeInput を組み立てる。

cargoType フィールドが Hazardous/Refrigerated の場合は対応する追加フィールド
(hazardousClass / unNumber / properShippingName / minTemperature / maxTemperature
 / temperatureUnit) が揃っているかを検証する。不足は Left エラーコードを返す。
-}
parseCargoType :: BookingFormRequest -> Either Text CargoTypeInput
parseCargoType req = case mTyped of
  "Hazardous" -> case (hazardousClass req, unNumber req, properShippingName req) of
    (Just cls, Just un, Just nm)
      | not (T.null cls) && not (T.null un) && not (T.null nm) ->
          Right (InputHazardous cls un nm)
    _ -> Left "hazardous-fields-missing"
  "Refrigerated" -> case (minTemperature req, maxTemperature req, temperatureUnit req) of
    (Just minT, Just maxT, Just unitT) ->
      case (readDouble minT, readDouble maxT) of
        (Just lo, Just hi) -> Right (InputRefrigerated lo hi unitT)
        _ -> Left "temperature-format"
    _ -> Left "refrigerated-fields-missing"
  _ -> Right InputGeneral
  where
    mTyped = fromMaybe "General" (cargoType req)
    readDouble t = case reads (T.unpack t) of
      [(x, "")] -> Just x
      _ -> Nothing

-- US06 (IT2): POST /bookings/:bookingId/handover で経路設計者へ引き渡す。
-- Submitted → RouteProposed の遷移が成功すれば詳細画面に 303、
-- 失敗時はエラー種別ごとのフラッシュ用クエリを付与して詳細画面に戻す。

{- | US27 (IT3): GET /bookings/:bookingId/customs/edit
通関情報編集フォーム (新規/更新兼用)。既存登録があればプリフィル。
-}
handlerCustomsEdit ::
  BookingRepository IO ->
  CustomsDeclarationRepository IO ->
  Text ->
  Maybe Text ->
  Handler (Html ())
handlerCustomsEdit repo customsRepo bid mError = do
  m <- liftIO (findCargoById repo (BookingId bid))
  case m of
    Nothing -> pure bookingNotFoundPage
    Just _ -> do
      mCustoms <- liftIO (findByBookingId customsRepo (BookingId bid))
      pure (customsEditPage (BookingId bid) mCustoms (fmap customsErrorMessage mError))

{- | US27 (IT3): POST /bookings/:bookingId/customs
通関情報を予約に紐付ける (PRG → /bookings/:bid?flash=customs-ok)。
-}
handlerCustomsAttach ::
  BookingRepository IO ->
  CustomsDeclarationRepository IO ->
  Text ->
  CustomsFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerCustomsAttach repo customsRepo bid req = do
  let input =
        AttachCustomsInput
          { inputBookingId = BookingId bid
          , inputHsCode = hs_code req
          , inputBrokerName = broker_name req
          , inputStatusText = status req
          }
  result <- liftIO (AttachCustoms.execute repo customsRepo input)
  let detail = "/bookings/" <> bid
      editBack = detail <> "/customs/edit"
  case result of
    Right _ ->
      pure (addHeader (detail <> "?flash=customs-ok") NoContent)
    Left (BookingNotFound _) ->
      throwError $
        err303
          { errHeaders =
              [("Location", BC.pack (T.unpack "/bookings/new?error=booking-not-found"))]
          , errBody = ""
          }
    Left (InvalidHsCode _) ->
      throwError $
        err303
          { errHeaders =
              [("Location", BC.pack (T.unpack (editBack <> "?error=invalid-hs-code")))]
          , errBody = ""
          }
    Left (InvalidDeclarationStatus _) ->
      throwError $
        err303
          { errHeaders =
              [("Location", BC.pack (T.unpack (editBack <> "?error=invalid-status")))]
          , errBody = ""
          }
    Left (InvalidBrokerName _) ->
      throwError $
        err303
          { errHeaders =
              [("Location", BC.pack (T.unpack (editBack <> "?error=invalid-broker")))]
          , errBody = ""
          }
    Left _ ->
      throwError $
        err303
          { errHeaders =
              [("Location", BC.pack (T.unpack (editBack <> "?error=unknown")))]
          , errBody = ""
          }

customsErrorMessage :: Text -> Text
customsErrorMessage "invalid-hs-code" = "HS コードは 6-10 桁の数字で入力してください"
customsErrorMessage "invalid-status" = "申告ステータスが不正です"
customsErrorMessage "invalid-broker" = "通関業者名は 1-100 文字で入力してください"
customsErrorMessage "unknown" = "通関情報の保存に失敗しました"
customsErrorMessage e = "通関情報の保存に失敗しました: " <> e

-- US06 (IT3, H-03): POST /bookings/:bookingId/submit で Draft → Submitted。
-- 経路設計者へ引き渡せる前提状態を作る。
handlerSubmit ::
  BookingRepository IO ->
  Text ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerSubmit repo bid = do
  result <-
    liftIO
      ( Submit.execute
          repo
          (SubmitBookingInput {inputBookingId = BookingId bid})
      )
  let detail = "/bookings/" <> bid
  case result of
    Right _ ->
      pure (addHeader (detail <> "?flash=submit-ok") NoContent)
    Left (BookingNotFound _) ->
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack "/bookings/new?error=booking-not-found"))]
          , errBody = ""
          }
    Left (InvalidStateTransition fromS _) ->
      throwError $
        err303
          { errHeaders =
              [
                ( "Location"
                , BC.pack (T.unpack (detail <> "?error=invalid-state&from=" <> fromS))
                )
              ]
          , errBody = ""
          }
    Left (ConcurrentModification _) ->
      throwError $
        err303
          { errHeaders =
              [("Location", BC.pack (T.unpack (detail <> "?error=concurrent-modification")))]
          , errBody = ""
          }
    Left e ->
      throwError $
        err303
          { errHeaders =
              [("Location", BC.pack (T.unpack (detail <> "?error=" <> T.pack (show e))))]
          , errBody = ""
          }

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

{- | US13 (IT5 task 1.1 IT4 繰越): POST /bookings/:bookingId/confirm
RouteAssigned → Confirmed 状態遷移を試行し、PRG で予約詳細に戻す。
-}
handlerConfirm ::
  BookingRepository IO ->
  Text ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerConfirm repo bid = do
  result <-
    liftIO
      ( ConfirmBk.execute
          repo
          (ConfirmBookingInput {inputBookingId = BookingId bid})
      )
  let detail = "/bookings/" <> bid
  case result of
    Right _ ->
      pure (addHeader (detail <> "?flash=confirm-ok") NoContent)
    Left (BookingNotFound _) ->
      redirectConfirmErr "/bookings/new?error=booking-not-found"
    Left (InvalidStateTransition fromS _) ->
      redirectConfirmErr (detail <> "?error=invalid-state&from=" <> fromS)
    Left (ConcurrentModification _) ->
      redirectConfirmErr (detail <> "?error=concurrent-modification")
    Left e ->
      redirectConfirmErr (detail <> "?error=" <> T.pack (show e))
  where
    redirectConfirmErr :: Text -> Handler a
    redirectConfirmErr loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }

{- | US13 (IT5 task 1.1 IT4 繰越): POST /bookings/:bookingId/cancel
Confirmed → Cancelled 状態遷移。キャンセル料は Cargo 更新時に併せて記録。
-}
handlerCancel ::
  BookingRepository IO ->
  Text ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerCancel repo bid = do
  now <- liftIO getCurrentTime
  result <-
    liftIO
      ( CancelBk.execute
          repo
          ( CancelBookingInput
              { inputBookingId = BookingId bid
              , inputNow = now
              , inputDepartureTime = Nothing
              }
          )
      )
  -- \^ IT5 段階: departureTime 参照は task 2.1 完了後の Itinerary 統合で有効化

  let detail = "/bookings/" <> bid
  case result of
    Right _ ->
      pure (addHeader (detail <> "?flash=cancel-ok") NoContent)
    Left (BookingNotFound _) ->
      redirectCancelErr "/bookings/new?error=booking-not-found"
    Left (InvalidStateTransition fromS _) ->
      redirectCancelErr (detail <> "?error=invalid-state&from=" <> fromS)
    Left (ConcurrentModification _) ->
      redirectCancelErr (detail <> "?error=concurrent-modification")
    Left e ->
      redirectCancelErr (detail <> "?error=" <> T.pack (show e))
  where
    redirectCancelErr :: Text -> Handler a
    redirectCancelErr loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }
