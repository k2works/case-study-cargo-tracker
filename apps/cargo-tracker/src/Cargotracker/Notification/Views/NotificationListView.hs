{-# LANGUAGE OverloadedStrings #-}

{- | 通知一覧画面 (US26, IT6, 管理者ビュー)

`/notifications?bookingId=...` に配置される。予約 ID の通知履歴を
テーブル形式で表示する。
-}
module Cargotracker.Notification.Views.NotificationListView
  ( notificationListPage,
    NotificationRow (..),
  ) where

import qualified Data.Foldable as F
import Data.Maybe (fromMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)
import Lucid
import Lucid.Base (makeAttribute)

{- | 表示用の通知行 (Cross-BC 境界を Text 化し、Domain 型を持ち込まない)。

- nrCreatedAt: 作成時刻
- nrChannel: "Log" / "EmailMock" / "PrintableHtml" 等
- nrStatus: "Pending" / "Sent" / "Failed"
- nrSentAt: Just t (成功時) / Nothing
- nrFailureReason: Just msg (失敗時) / Nothing
- nrSubject: 通知件名
-}
data NotificationRow = NotificationRow
  { nrCreatedAt :: !UTCTime
  , nrChannel :: !Text
  , nrStatus :: !Text
  , nrSentAt :: !(Maybe UTCTime)
  , nrFailureReason :: !(Maybe Text)
  , nrSubject :: !Text
  , nrBody :: !Text
  -- ^ 通知本文 (US26/US12: 荷受人・荷主が内容を確認できるよう一覧に表示)
  }
  deriving stock (Eq, Show)

notificationListPage :: Text -> [NotificationRow] -> Html ()
notificationListPage bookingId rows = doctypehtml_ $ do
  head_ $ do
    meta_ [charset_ "utf-8"]
    title_ "通知一覧 - Cargo Tracker"
    link_
      [ rel_ "stylesheet"
      , href_ "https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css"
      ]
  body_ $ div_ [class_ "container my-5"] $ do
    h1_ [class_ "mb-4"] "通知一覧"

    div_ [class_ "mb-3 text-muted"] $ do
      "予約 ID: "
      code_ [makeAttribute "data-testid" "booking-id"] (toHtml bookingId)

    if null rows
      then emptyState
      else notificationTable rows

emptyState :: Html ()
emptyState =
  div_
    [ class_ "alert alert-info"
    , makeAttribute "data-testid" "empty-state"
    ]
    "この予約の通知履歴はまだありません。"

notificationTable :: [NotificationRow] -> Html ()
notificationTable rs =
  table_
    [ class_ "table table-striped table-hover"
    , makeAttribute "data-testid" "notif-table"
    ]
    ( do
        thead_ $ tr_ $ do
          th_ "作成時刻"
          th_ "件名"
          th_ "本文"
          th_ "配信手段"
          th_ "状態"
          th_ "配信時刻"
          th_ "失敗理由"
        tbody_ (F.for_ rs notificationRow)
    )

notificationRow :: NotificationRow -> Html ()
notificationRow r =
  tr_ [makeAttribute "data-testid" "notif-row"] $ do
    td_ (toHtml (T.pack (show (nrCreatedAt r))))
    td_ (toHtml (nrSubject r))
    td_ [class_ "small"] (toHtml (nrBody r))
    td_ (toHtml (nrChannel r))
    td_
      ( span_
          [class_ (statusBadgeClass (nrStatus r))]
          (toHtml (nrStatus r))
      )
    td_ (toHtml (maybe "-" (T.pack . show) (nrSentAt r)))
    td_ (toHtml (fromMaybe "-" (nrFailureReason r)))

statusBadgeClass :: Text -> Text
statusBadgeClass "Sent" = "badge bg-success"
statusBadgeClass "Failed" = "badge bg-danger"
statusBadgeClass _ = "badge bg-secondary"
