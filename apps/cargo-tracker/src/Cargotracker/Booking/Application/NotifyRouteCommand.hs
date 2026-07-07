{-# LANGUAGE OverloadedStrings #-}

{- | 確定経路の荷主通知コマンド (US12, IT8 ストレッチ)

業務フロー (受入基準):

1. 予約番号で Cargo と紐付け済み Itinerary を取得 (紐付け前は
   InvalidItinerary を返す — 受入基準 1「紐付けられた経路情報を確認できる」)
2. 通知内容 (経由港・所要日数・到着予定日・料金概算) を組み立てる
   (受入基準 2)
3. Notification BC の Cross-BC helper で通知を送信し、送信記録を登録する
   (受入基準 3・4。saveNotification が送信記録、deliver が配信)

料金概算は Estimation/Pricing の算出結果を呼出側 (Interfaces) が Text で
渡す。未算定の場合は「未算定」と表示する。
-}
module Cargotracker.Booking.Application.NotifyRouteCommand
  ( NotifyRouteInput (..),
    execute,
  ) where

import Data.Maybe (fromMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, diffUTCTime)

import Cargotracker.Booking.Application.ItineraryPorts (ItineraryRepository (..))
import Cargotracker.Booking.Application.Ports (BookingRepository (..))
import Cargotracker.Booking.Domain.Model.Itinerary
  ( Itinerary,
    itArrivalTime,
    itDepartureTime,
    itPorts,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (mkBookingId)
import Cargotracker.Notification.Application.Ports
  ( DeliveryResult,
    NotificationDeliveryPort,
    NotificationRepository,
  )
import Cargotracker.Notification.Application.SendClaimNotificationCommand
  ( sendClaimLogNotificationTextWithId,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data NotifyRouteInput = NotifyRouteInput
  { inputBookingId :: !Text
  , inputCostEstimateText :: !(Maybe Text)
  -- ^ 料金概算 (Interfaces 層が US01/US21 の算出結果を渡す。Nothing = 未算定)
  , inputNow :: !UTCTime
  , inputNotificationId :: !(Maybe Text)
  -- ^ ADR-0013: 呼出側採番の UUID v4
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  ItineraryRepository m ->
  NotificationRepository m ->
  NotificationDeliveryPort m ->
  NotifyRouteInput ->
  m (Either DomainError DeliveryResult)
execute bookingRepo itineraryRepo notifRepo delivery input =
  case mkBookingId (inputBookingId input) of
    Left err -> pure (Left err)
    Right bid -> do
      mCargo <- findCargoById bookingRepo bid
      case mCargo of
        Nothing -> pure (Left (BookingNotFound (inputBookingId input)))
        Just _ -> do
          mItinerary <- findItineraryByBookingId itineraryRepo bid
          case mItinerary of
            Nothing ->
              pure (Left (InvalidItinerary "no itinerary linked to booking"))
            Just itinerary ->
              sendClaimLogNotificationTextWithId
                notifRepo
                delivery
                (inputBookingId input)
                ("確定経路のお知らせ (" <> inputBookingId input <> ")")
                (routeBody itinerary (inputCostEstimateText input))
                (inputNow input)
                (inputNotificationId input)

-- | 通知本文 (受入基準 2: 経由港・所要日数・到着予定日・料金概算)
routeBody :: Itinerary -> Maybe Text -> Text
routeBody itinerary mCost =
  "経路が確定しました。経由港: "
    <> T.intercalate " → " (itPorts itinerary)
    <> " / 所要日数: "
    <> T.pack (show (transitDaysOf itinerary))
    <> " 日 / 到着予定: "
    <> T.pack (show (itArrivalTime itinerary))
    <> " / 料金概算: "
    <> fromMaybe "未算定" mCost
    <> "。内容をご確認のうえ、承認または変更依頼をお願いします。"

transitDaysOf :: Itinerary -> Int
transitDaysOf itinerary =
  let secs =
        realToFrac
          (diffUTCTime (itArrivalTime itinerary) (itDepartureTime itinerary)) ::
          Double
   in max 1 (ceiling (secs / 86400))
