{-# LANGUAGE OverloadedStrings #-}

{- | 航海スケジュール検索ページ (US07, IT3)

`/voyages/search` から GET で表示される。

* フォーム部分: 出発地・目的地 (UnLocode 5 文字) + 出発期間 from..to
* 結果部分: クエリ既入力時に該当航海の一覧を出発時刻昇順で表示
* 該当 0 件: 「該当する航海がありません」メッセージ
* バリデーションエラー: alert-danger で再入力を促す
-}
module Cargotracker.Routing.Views.VoyageSearchView
  ( voyageSearchPage,
    VoyageSearchFormData (..),
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, defaultTimeLocale, formatTime)
import Lucid

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( unVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..))
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Web.Layout (FlashLevel (..), flashAlert, pageLayout)

-- | クエリパラメータから収集したフォームの初期値 (入力保持に使用)
data VoyageSearchFormData = VoyageSearchFormData
  { fdOrigin :: !Text
  , fdDestination :: !Text
  , fdFromDate :: !Text
  , fdToDate :: !Text
  }
  deriving stock (Eq, Show)

formatLocal :: UTCTime -> Text
formatLocal = T.pack . formatTime defaultTimeLocale "%Y-%m-%dT%H:%M"

voyageSearchPage ::
  VoyageSearchFormData ->
  Maybe Text ->
  Maybe [Voyage] ->
  Html ()
voyageSearchPage form mError mResults = pageLayout "航海スケジュール検索 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-10"] $ do
      h1_ [class_ "h3 mb-4"] "航海スケジュール検索 (US07)"
      case mError of
        Just msg -> flashAlert FlashDanger msg
        Nothing -> mempty
      searchForm form
      case mResults of
        Nothing -> mempty -- フォームのみ
        Just [] -> div_ [class_ "alert alert-warning mt-4", role_ "alert"] "該当する航海がありません"
        Just voys -> resultsTable voys

searchForm :: VoyageSearchFormData -> Html ()
searchForm form =
  form_ [action_ "/voyages/search", method_ "get", class_ "row g-3"] $ do
    div_ [class_ "col-md-3"] $ do
      label_ [for_ "from", class_ "form-label small"] "出発地 (UnLocode)"
      input_
        [ type_ "text"
        , id_ "from"
        , name_ "from"
        , class_ "form-control"
        , value_ (fdOrigin form)
        , placeholder_ "JPTYO"
        , maxlength_ "5"
        ]
    div_ [class_ "col-md-3"] $ do
      label_ [for_ "to", class_ "form-label small"] "目的地 (UnLocode)"
      input_
        [ type_ "text"
        , id_ "to"
        , name_ "to"
        , class_ "form-control"
        , value_ (fdDestination form)
        , placeholder_ "USNYC"
        , maxlength_ "5"
        ]
    div_ [class_ "col-md-3"] $ do
      label_ [for_ "from_date", class_ "form-label small"] "出発日 (from)"
      input_
        [ type_ "date"
        , id_ "from_date"
        , name_ "from_date"
        , class_ "form-control"
        , value_ (fdFromDate form)
        ]
    div_ [class_ "col-md-3"] $ do
      label_ [for_ "to_date", class_ "form-label small"] "出発日 (to)"
      input_
        [ type_ "date"
        , id_ "to_date"
        , name_ "to_date"
        , class_ "form-control"
        , value_ (fdToDate form)
        ]
    div_ [class_ "col-12 mt-3"] $
      button_ [type_ "submit", class_ "btn btn-primary"] "検索"

resultsTable :: [Voyage] -> Html ()
resultsTable voys =
  div_ [class_ "mt-4"] $ do
    h2_ [class_ "h5 mb-3"] (toHtml ("検索結果 " <> T.pack (show (length voys)) <> " 件"))
    table_ [class_ "table table-sm table-striped"] $ do
      thead_ $
        tr_ $ do
          th_ "航海番号"
          th_ "出発港"
          th_ "出発時刻"
          th_ "到着港"
          th_ "到着時刻"
          th_ "寄港地数"
      tbody_ $ mapM_ voyageRow voys

voyageRow :: Voyage -> Html ()
voyageRow v =
  case carrierMovements v of
    [] -> mempty
    (first : _) ->
      let lastM = last (carrierMovements v)
          UnLocode orig = departureLocation first
          UnLocode dest = arrivalLocation lastM
          numStops = T.pack (show (length (carrierMovements v) - 1))
       in tr_ $ do
            td_ (toHtml (unVoyageNumber (voyageNumber v)))
            td_ (toHtml orig)
            td_ (toHtml (formatLocal (departureTime first)))
            td_ (toHtml dest)
            td_ (toHtml (formatLocal (arrivalTime lastM)))
            td_ (toHtml numStops)
