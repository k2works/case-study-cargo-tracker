{-# LANGUAGE OverloadedStrings #-}

{- | 経路確定 / 紐付け / 解除の Lucid view (US09 + US11, IT4)

予約詳細画面 (`/bookings/:bookingId`) に埋め込む経路操作セクション。

* `routeSelectionForm`: 経路候補一覧から radio で 1 件選択して確定 (US09)
* `routeLinkSection`: 確定済 Itinerary を予約に紐付け / 解除 (US11)
* `routeAssignedBadge`: 状態バッジ (BookingStatus に応じた色分け)
-}
module Cargotracker.Booking.Views.RouteConfirmView
  ( RouteOption (..),
    routeSelectionForm,
    routeLinkSection,
    routeAssignedBadge,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Lucid

import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
    bookingStatusToText,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))

-- | 候補表示用の経路情報 (BC 非依存表現、HTTP ハンドラで構築)
data RouteOption = RouteOption
  { roId :: !Text -- 経路識別子 (Itinerary UUID 等)
  , roRank :: !Int
  , roPortsLabel :: !Text -- "JPTYO → USNYC" 形式
  , roVoyagesLabel :: !Text -- "V001, V002" 形式
  }

{- | 経路候補一覧 + 確定ボタン (US09)

US09 受入条件 1 + 3: radio で 1 件選択、確定後は disabled。
form action は POST /bookings/:id/routes/confirm。
-}
routeSelectionForm :: BookingId -> [RouteOption] -> BookingStatus -> Html ()
routeSelectionForm (BookingId bid) options currentStatus =
  let disabled = currentStatus `elem` [RouteAssigned, Confirmed, Cancelled, Closed]
   in form_
        [ method_ "post"
        , action_ ("/bookings/" <> bid <> "/routes/confirm")
        , class_ "mt-3"
        , id_ "route-confirm-form"
        ]
        $ do
          h3_ [class_ "h5 mb-3"] "経路選択"
          if null options
            then div_ [class_ "alert alert-info"] "選択可能な経路候補がありません。先に経路評価を実行してください。"
            else do
              table_ [class_ "table table-sm"] $ do
                thead_ $ tr_ $ do
                  th_ "選択"
                  th_ "rank"
                  th_ "寄港港"
                  th_ "航海"
                tbody_ $ mapM_ (optionRow disabled) options
              if disabled
                then
                  button_
                    [type_ "button", class_ "btn btn-secondary btn-sm", disabled_ ""]
                    "経路を確定 (確定済)"
                else
                  button_
                    [type_ "submit", class_ "btn btn-primary btn-sm"]
                    "経路を確定"

optionRow :: Bool -> RouteOption -> Html ()
optionRow disabled RouteOption {..} = tr_ $ do
  td_ $
    input_
      ( [ type_ "radio"
        , name_ "selected_route"
        , value_ roId
        , class_ "form-check-input"
        ]
          <> [disabled_ "" | disabled]
      )
  td_ (toHtml (T.pack (show roRank)))
  td_ (toHtml roPortsLabel)
  td_ (toHtml roVoyagesLabel)

{- | 経路紐付け / 解除セクション (US11)

* Draft / Submitted / RouteProposed: 「経路を紐付け」ボタン (POST)
* RouteAssigned: 「経路紐付けを解除」ボタン (DELETE form override)
* Confirmed / Cancelled / Closed: 操作不可 (情報表示のみ)
-}
routeLinkSection :: BookingId -> BookingStatus -> Html ()
routeLinkSection (BookingId bid) status = div_ [class_ "mt-3"] $ do
  h3_ [class_ "h6 mb-2"] "経路紐付け状態"
  div_ [class_ "mb-2"] (routeAssignedBadge status)
  case status of
    RouteAssigned ->
      form_
        [ method_ "post"
        , action_ ("/bookings/" <> bid <> "/route")
        , class_ "d-inline"
        ]
        $ do
          input_ [type_ "hidden", name_ "_method", value_ "DELETE"]
          button_
            [ type_ "submit"
            , class_ "btn btn-outline-warning btn-sm"
            ]
            "経路紐付けを解除"
    RouteProposed ->
      form_
        [ method_ "post"
        , action_ ("/bookings/" <> bid <> "/route")
        , class_ "d-inline"
        ]
        $ button_
          [type_ "submit", class_ "btn btn-outline-primary btn-sm"]
          "経路を紐付け"
    _ -> small_ [class_ "text-muted"] "本状態では経路操作は不可"

-- | BookingStatus に対応した Bootstrap バッジ
routeAssignedBadge :: BookingStatus -> Html ()
routeAssignedBadge status =
  let label = bookingStatusToText status
      cls = "badge " <> badgeClass status
   in span_ [class_ cls] (toHtml label)

badgeClass :: BookingStatus -> Text
badgeClass Draft = "bg-secondary"
badgeClass Submitted = "bg-info"
badgeClass RouteProposed = "bg-primary"
badgeClass RouteAssigned = "bg-success"
badgeClass Confirmed = "bg-success"
badgeClass Settled = "bg-dark" -- US23 精算済 (IT8)
badgeClass Cancelled = "bg-danger"
badgeClass Closed = "bg-dark"
