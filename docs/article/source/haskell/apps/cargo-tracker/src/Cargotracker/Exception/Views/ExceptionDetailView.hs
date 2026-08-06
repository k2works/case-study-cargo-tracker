{-# LANGUAGE OverloadedStrings #-}

{- | 例外詳細ページ (US19/US20, IT7)

`/exceptions/:exceptionId` の GET で返される Lucid View。
`ExceptionRecord` 集約を Text-DTO の `DetailRow` に変換してから表示する
(Rule 4 準拠、Domain 型は View 層に露出させない)。
-}
module Cargotracker.Exception.Views.ExceptionDetailView
  ( exceptionDetailPage,
    DetailRow (..),
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)
import Lucid
import Lucid.Base (makeAttribute)

data DetailRow = DetailRow
  { drId :: !Text
  , drTrackingNumber :: !Text
  , drType :: !Text
  -- ^ "DELAY" / "DAMAGE" / "LOSS"
  , drSeverity :: !Text
  -- ^ "LOW" / "MEDIUM" / "HIGH" / "CRITICAL"
  , drReporter :: !Text
  , drReportedAt :: !UTCTime
  , drResolvedAt :: !(Maybe UTCTime)
  , drTypeDetail :: ![(Text, Text)]
  {- ^ 種別固有の詳細 (キー名 → 値) を dl_ で列挙。
  例: Delay → [(\"遅延時間\", \"48 時間\"), (\"理由\", \"港湾ストライキ\")]
  -}
  }
  deriving stock (Eq, Show)

exceptionDetailPage :: DetailRow -> Html ()
exceptionDetailPage r = doctypehtml_ $ do
  head_ $ do
    meta_ [charset_ "utf-8"]
    title_ (toHtml ("例外詳細 " <> drId r <> " - Cargo Tracker"))
    link_ [rel_ "stylesheet", href_ "/static/css/bootstrap.min.css"]
  body_ $ div_ [class_ "container my-4"] $ do
    div_ [class_ "d-flex justify-content-between align-items-center mb-3"] $ do
      h1_ (toHtml ("例外詳細 " <> drId r))
      statusBadge (drResolvedAt r)

    div_
      [ class_ "card"
      , makeAttribute "data-testid" "exception-detail"
      ]
      $ div_ [class_ "card-body"]
      $ do
        summaryTable r
        div_ [class_ "mt-4"] $ do
          h5_ "種別詳細"
          dl_ [class_ "row"] $ mapM_ pairRow (drTypeDetail r)

    div_ [class_ "mt-3 d-flex gap-2"] $ do
      a_ [href_ "/exceptions", class_ "btn btn-outline-secondary"] "← 一覧に戻る"
      case drResolvedAt r of
        Just _ -> pure ()
        Nothing ->
          form_
            [ action_ ("/exceptions/" <> drId r <> "/resolve")
            , method_ "post"
            , class_ "d-inline"
            ]
            $ button_
              [ type_ "submit"
              , class_ "btn btn-outline-success"
              , makeAttribute "data-testid" "resolve-button"
              ]
              "解決する"

statusBadge :: Maybe UTCTime -> Html ()
statusBadge Nothing =
  span_ [class_ "badge bg-danger"] "未解決"
statusBadge (Just t) = do
  span_ [class_ "badge bg-success"] "解決済"
  small_ [class_ "text-muted ms-2"] (toHtml (T.pack (show t)))

summaryTable :: DetailRow -> Html ()
summaryTable r = table_ [class_ "table table-borderless mb-0"] $ tbody_ $ do
  row "例外 ID" (drId r)
  row "追跡番号" (drTrackingNumber r)
  row "種別" (drType r)
  row "重要度" (drSeverity r)
  row "報告者" (drReporter r)
  row "報告時刻" (T.pack (show (drReportedAt r)))
  case drResolvedAt r of
    Just t -> row "解決時刻" (T.pack (show t))
    Nothing -> pure ()
  where
    row :: Text -> Text -> Html ()
    row k v = tr_ $ do
      th_ [class_ "text-muted", style_ "width: 8em;"] (toHtml k)
      td_ (toHtml v)

pairRow :: (Text, Text) -> Html ()
pairRow (k, v) = do
  dt_ [class_ "col-sm-3"] (toHtml k)
  dd_ [class_ "col-sm-9"] (toHtml v)
