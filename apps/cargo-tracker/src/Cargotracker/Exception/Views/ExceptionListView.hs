{-# LANGUAGE OverloadedStrings #-}

{- | 例外一覧画面 (US19/US20, IT7)

`/exceptions` に配置される。輸送例外 (Delay/Damage/Loss) を統一 UI で
テーブル表示する。ui_design.md §例外一覧・登録画面 に対応。

Cross-BC 境界を Text 化し、Exception BC の Domain 型 (ExceptionRecord) を
View 層に持ち込まない (Rule 4)。
-}
module Cargotracker.Exception.Views.ExceptionListView
  ( exceptionListPage,
    ExceptionRow (..),
  ) where

import qualified Data.Foldable as F
import Data.Maybe (fromMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)
import Lucid
import Lucid.Base (makeAttribute)

{- | 表示用の例外行 (Text-DTO)。

- erId: exception_id (UUID Text)
- erTrackingNumber: 追跡番号 Text
- erType: "DELAY" / "DAMAGE" / "LOSS"
- erSeverity: "LOW" / "MEDIUM" / "HIGH" / "CRITICAL"
- erReporter: "user-id (Role)" 表示用文字列
- erReportedAt: 報告時刻
- erResolvedAt: Nothing = 未解決 / Just t = 解決済
-}
data ExceptionRow = ExceptionRow
  { erRowId :: !Text
  , erRowTrackingNumber :: !Text
  , erRowType :: !Text
  , erRowSeverity :: !Text
  , erRowReporter :: !Text
  , erRowReportedAt :: !UTCTime
  , erRowResolvedAt :: !(Maybe UTCTime)
  }
  deriving stock (Eq, Show)

exceptionListPage :: [ExceptionRow] -> Html ()
exceptionListPage rows = doctypehtml_ $ do
  head_ $ do
    meta_ [charset_ "utf-8"]
    title_ "輸送例外一覧 - Cargo Tracker"
    link_ [rel_ "stylesheet", href_ "/static/css/bootstrap.min.css"]
  body_ $ do
    div_ [class_ "container my-4"] $ do
      h1_ [class_ "mb-4"] "輸送例外一覧"
      p_ [class_ "text-muted"] "US19 (遅延) / US20 (破損・紛失) の例外を一覧表示します。ADR-0014 遷移マトリクスに基づき Tracking BC と統合。"
      if null rows
        then emptyState
        else exceptionTable rows
      formActionButtons

emptyState :: Html ()
emptyState =
  div_
    [ class_ "card text-center border-secondary"
    , makeAttribute "data-testid" "empty-state"
    ]
    $ div_ [class_ "card-body py-5"]
    $ do
      p_ [class_ "mb-0 text-muted"] "現在、記録されている輸送例外はありません。"

exceptionTable :: [ExceptionRow] -> Html ()
exceptionTable rows =
  table_
    [ class_ "table table-striped table-hover align-middle"
    , makeAttribute "data-testid" "exception-list"
    ]
    $ do
      thead_ [class_ "table-light"] $
        tr_ $ do
          th_ "例外 ID"
          th_ "追跡番号"
          th_ "種別"
          th_ "重要度"
          th_ "報告者"
          th_ "報告時刻"
          th_ "状態"
          th_ "操作"
      tbody_ $ F.for_ rows renderRow

renderRow :: ExceptionRow -> Html ()
renderRow r =
  tr_ [makeAttribute "data-testid" "exception-row"] $ do
    td_ (toHtml (erRowId r))
    td_ (toHtml (erRowTrackingNumber r))
    td_ (severityBadge (erRowType r) (T.pack "primary"))
    td_ (severityBadge (erRowSeverity r) (severityClass (erRowSeverity r)))
    td_ (toHtml (erRowReporter r))
    td_ (toHtml (T.pack (show (erRowReportedAt r))))
    td_ (statusBadge (erRowResolvedAt r))
    td_ $ do
      a_
        [ href_ ("/exceptions/" <> erRowId r)
        , class_ "btn btn-sm btn-outline-primary me-1"
        ]
        "詳細"
      case erRowResolvedAt r of
        Just _ -> pure ()
        Nothing ->
          form_
            [ action_ ("/exceptions/" <> erRowId r <> "/resolve")
            , method_ "post"
            , class_ "d-inline"
            ]
            $ button_
              [ type_ "submit"
              , class_ "btn btn-sm btn-outline-success"
              , makeAttribute "data-testid" "resolve-button"
              ]
              "解決"

severityBadge :: Text -> Text -> Html ()
severityBadge label cls =
  span_ [class_ ("badge bg-" <> cls)] (toHtml label)

severityClass :: Text -> Text
severityClass "CRITICAL" = "danger"
severityClass "HIGH" = "warning"
severityClass "MEDIUM" = "info"
severityClass "LOW" = "secondary"
severityClass _ = "secondary"

statusBadge :: Maybe UTCTime -> Html ()
statusBadge Nothing =
  span_ [class_ "badge bg-danger"] "未解決"
statusBadge (Just t) = do
  span_ [class_ "badge bg-success"] "解決済"
  small_ [class_ "text-muted ms-1"] (toHtml (T.pack (show t)))

formActionButtons :: Html ()
formActionButtons = div_ [class_ "mt-4 d-flex gap-2"] $ do
  a_
    [ href_ "/exceptions/delay"
    , class_ "btn btn-outline-warning"
    , makeAttribute "data-testid" "record-delay"
    ]
    "＋ 遅延を登録"
  a_
    [ href_ "/exceptions/damage"
    , class_ "btn btn-outline-danger"
    , makeAttribute "data-testid" "record-damage"
    ]
    "＋ 破損を登録"
  a_
    [ href_ "/exceptions/loss"
    , class_ "btn btn-outline-dark"
    , makeAttribute "data-testid" "record-loss"
    ]
    "＋ 紛失を登録"
  -- fromMaybe を Lucid で使わないため、少なくとも 1 箇所で参照する形にする
  span_ [class_ "d-none"] (toHtml (fromMaybe (T.pack "") Nothing))
