{-# LANGUAGE OverloadedStrings #-}

{- | 手動状態更新モーダル + 監査履歴タブ ビュー (US17 5.4, IT7)

Tracker/Admin が Tracking 状態を手動修正するためのフォームフラグメントと、
過去の TrackingStateAudit を一覧表示する監査履歴タブを提供する。
Servant 側からは htmx フラグメントとして返却する想定 (hx-target="#modal-slot")。
-}
module Cargotracker.Tracking.Views.ManualUpdateView
  ( manualUpdateFormFragment,
    auditHistoryFragment,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Shared.Domain.TransportStatus
  ( TransportStatus (..),
    transportStatusToText,
  )
import Cargotracker.Tracking.Domain.Model.TrackingStateAudit
  ( TrackingStateAudit (..),
  )

selectableStatuses :: [TransportStatus]
selectableStatuses =
  [ TsNotReceived
  , TsReceived
  , TsLoaded
  , TsOnboardCarrier
  , TsUnloaded
  , TsAwaitingClaim
  , TsClaimed
  ]

statusLabel :: TransportStatus -> Text
statusLabel TsNotReceived = "未受領"
statusLabel TsReceived = "受領済"
statusLabel TsLoaded = "積載済"
statusLabel TsOnboardCarrier = "輸送中"
statusLabel TsUnloaded = "荷卸済"
statusLabel TsAwaitingClaim = "受取待ち"
statusLabel TsClaimed = "引取済"
statusLabel TsInException = "例外対応中"
statusLabel _ = "その他"

{- | 手動状態更新フォーム (htmx フラグメント)。
POST 先: /tracking/:tn/manual-update (PRG 303、Tracker/Admin 限定)。
-}
manualUpdateFormFragment :: Text -> TransportStatus -> Html ()
manualUpdateFormFragment tn currentStatus =
  div_ [class_ "modal-content p-3", makeAttribute "data-story" "US17"] $ do
    h5_ [class_ "mb-3"] "状態を手動更新"
    p_ [class_ "text-muted small"] $ do
      "追跡番号: "
      strong_ (toHtml tn)
      " / 現在状態: "
      strong_ (toHtml (statusLabel currentStatus))
    form_
      [ action_ ("/tracking/" <> tn <> "/manual-update")
      , method_ "post"
      , class_ "mb-0"
      ]
      $ do
        div_ [class_ "mb-3"] $ do
          label_ [for_ "newStatus", class_ "form-label"] "新しい状態"
          select_ [name_ "newStatus", id_ "newStatus", class_ "form-select", required_ ""] $
            mapM_ renderOption selectableStatuses
        div_ [class_ "mb-3"] $ do
          label_ [for_ "reason", class_ "form-label"] "変更理由 (必須)"
          textarea_
            [ name_ "reason"
            , id_ "reason"
            , class_ "form-control"
            , rows_ "3"
            , required_ ""
            , makeAttribute "minlength" "5"
            ]
            ""
        div_ [class_ "d-flex justify-content-end gap-2"] $ do
          button_
            [ type_ "button"
            , class_ "btn btn-secondary"
            , makeAttribute "data-bs-dismiss" "modal"
            ]
            "キャンセル"
          button_ [type_ "submit", class_ "btn btn-primary"] "更新"
  where
    renderOption :: TransportStatus -> Html ()
    renderOption s =
      option_ [value_ (transportStatusToText s)] (toHtml (statusLabel s))

{- | 監査履歴タブ (htmx フラグメント)。
TrackingStateAudit を新しい順に列挙する。空リストの場合は「履歴なし」を表示。
-}
auditHistoryFragment :: [TrackingStateAudit] -> Html ()
auditHistoryFragment audits =
  div_ [class_ "audit-history", makeAttribute "data-story" "US17"] $ do
    h5_ [class_ "mb-3"] "手動更新履歴"
    if null audits
      then p_ [class_ "text-muted"] "この貨物には手動更新履歴がありません。"
      else table_ [class_ "table table-striped table-sm"] $ do
        thead_ $ tr_ $ do
          th_ "変更日時"
          th_ "変更前"
          th_ "変更後"
          th_ "理由"
          th_ "変更者"
        tbody_ $ mapM_ renderRow audits
  where
    renderRow :: TrackingStateAudit -> Html ()
    renderRow a = tr_ $ do
      td_ (toHtml (formatTime (tsaChangedAt a)))
      td_ (toHtml (statusLabel (tsaPreviousStatus a)))
      td_ (toHtml (statusLabel (tsaNewStatus a)))
      td_ (toHtml (tsaReason a))
      td_ (toHtml (tsaChangedBy a))

    formatTime :: UTCTime -> Text
    formatTime = T.pack . show
