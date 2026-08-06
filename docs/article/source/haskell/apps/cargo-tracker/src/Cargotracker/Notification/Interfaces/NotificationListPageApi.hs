{-# LANGUAGE DataKinds #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 通知一覧画面 API (US26, IT6)

`GET /notifications?bookingId=BK-XXXX` で予約 ID 別の通知履歴を表示する。
`bookingId` クエリパラメータが空 or 欠落の場合はプロンプト画面を返す。
-}
module Cargotracker.Notification.Interfaces.NotificationListPageApi
  ( notificationListApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Maybe (fromMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)

import Cargotracker.Notification.Application.Ports
  ( NotificationRepository (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationChannel (..),
    NotificationContent (..),
    NotificationStatus (..),
  )
import Cargotracker.Notification.Views.NotificationListView
  ( NotificationRow (..),
    notificationListPage,
  )

type NotificationListApi =
  "notifications"
    :> QueryParam "bookingId" Text
    :> Get '[HTML] (Html ())

notificationListApp :: NotificationRepository IO -> Application
notificationListApp repo =
  serve (Proxy :: Proxy NotificationListApi) (handler repo)

handler :: NotificationRepository IO -> Maybe Text -> Handler (Html ())
handler repo mBid = do
  let bid = fromMaybe "" mBid
  if T.null bid
    then pure (notificationListPage "(bookingId が未指定です)" [])
    else do
      notifs <- liftIO (findByBookingId repo bid)
      let rows = fmap notificationToRow notifs
      pure (notificationListPage bid rows)

-- | Domain 集約から View 用 Row への Text 変換 (同 BC 内なので Rule 4 対象外)。
notificationToRow :: Notification -> NotificationRow
notificationToRow n =
  NotificationRow
    { nrCreatedAt = nCreatedAt n
    , nrChannel = channelText (nChannel n)
    , nrStatus = statusText (nStatus n)
    , nrSentAt = nSentAt n
    , nrFailureReason = nFailureReason n
    , nrSubject = ncSubject (nContent n)
    , nrBody = ncBody (nContent n)
    }

channelText :: NotificationChannel -> Text
channelText LogChannel = "Log"
channelText EmailMockChannel = "EmailMock"
channelText PrintableHtmlChannel = "PrintableHtml"

statusText :: NotificationStatus -> Text
statusText Pending = "Pending"
statusText Sent = "Sent"
statusText Failed = "Failed"
