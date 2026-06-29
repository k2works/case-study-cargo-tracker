{-# LANGUAGE OverloadedStrings #-}

{- | 航海登録フォームのビュー (IT1 US24)

固定 3 区間の入力行を用意し、空行はハンドラ側でフィルタする。
IT2 で htmx による動的行追加に置き換える予定。
-}
module Cargotracker.Routing.Views.VoyageFormView
  ( voyageFormPage,
    voyageEditPage,
    voyageResultPage,
    movementRow,
    movementRowWith,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, defaultTimeLocale, formatTime)
import Lucid
import Lucid.Base (makeAttribute)

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Web.Layout
  ( FlashLevel (..),
    flashAlert,
    flashAlertWithAction,
    pageLayout,
  )

ports :: [(Text, Text)]
ports =
  [ ("", "(未選択)")
  , ("JPTYO", "JPTYO - Tokyo")
  , ("JPOSA", "JPOSA - Osaka")
  , ("JPYOK", "JPYOK - Yokohama")
  , ("USNYC", "USNYC - New York")
  , ("USLAX", "USLAX - Los Angeles")
  , ("USSEA", "USSEA - Seattle")
  , ("CNSHA", "CNSHA - Shanghai")
  , ("HKHKG", "HKHKG - Hong Kong")
  , ("SGSIN", "SGSIN - Singapore")
  , ("GBLON", "GBLON - London")
  ]

portSelect :: Text -> Text -> Bool -> Maybe Text -> Html ()
portSelect nameAttr label required mSelected = do
  label_ [class_ "form-label small"] (toHtml label)
  select_ ([name_ nameAttr, class_ "form-select form-select-sm"] <> [required_ "required" | required]) $
    mapM_ renderOption ports
  where
    renderOption :: (Text, Text) -> Html ()
    renderOption (code, lbl) =
      let baseAttrs = [value_ code]
          attrs = case mSelected of
            Just sel | sel == code -> selected_ "selected" : baseAttrs
            _ -> baseAttrs
       in option_ attrs (toHtml lbl)

-- | datetime-local input value 形式 ("YYYY-MM-DDTHH:MM") に整形する (U-03)
formatDateTimeLocal :: UTCTime -> Text
formatDateTimeLocal = T.pack . formatTime defaultTimeLocale "%Y-%m-%dT%H:%M"

-- | プリフィル無しの空行を返す互換シム (US24 登録フォーム / 追加行用)
movementRow :: Int -> Bool -> Html ()
movementRow i isFirst = movementRowWith i isFirst Nothing

{- | U-03: 既存の CarrierMovement をプリフィル可能な区間行。

`Just cm` を渡すと select は selected, input value は datetime-local 形式で
既存値を表示する。`Nothing` の場合は空フォームに退化する (登録時と同一)。
-}
movementRowWith :: Int -> Bool -> Maybe CarrierMovement -> Html ()
movementRowWith i isFirst mCm = do
  let n = T.pack (show i)
      mDep = fmap (unUnLocode . departureLocation) mCm
      mArr = fmap (unUnLocode . arrivalLocation) mCm
      mDepT = fmap (formatDateTimeLocal . departureTime) mCm
      mArrT = fmap (formatDateTimeLocal . arrivalTime) mCm
  h6_ [class_ "mt-3"] (toHtml ("区間 " <> n <> if isFirst then " (必須)" else " (任意)"))
  div_ [class_ "row g-2"] $ do
    div_ [class_ "col-md-3"] $
      portSelect ("movement" <> n <> "Departure") "出発港" isFirst mDep
    div_ [class_ "col-md-3"] $
      portSelect ("movement" <> n <> "Arrival") "到着港" isFirst mArr
    div_ [class_ "col-md-3"] $ do
      label_ [class_ "form-label small"] "出発時刻"
      input_ $
        [ type_ "datetime-local"
        , name_ ("movement" <> n <> "DepartureTime")
        , class_ "form-control form-control-sm"
        ]
          <> [required_ "required" | isFirst]
          <> maybe [] (\v -> [value_ v]) mDepT
    div_ [class_ "col-md-3"] $ do
      label_ [class_ "form-label small"] "到着時刻"
      input_ $
        [ type_ "datetime-local"
        , name_ ("movement" <> n <> "ArrivalTime")
        , class_ "form-control form-control-sm"
        ]
          <> [required_ "required" | isFirst]
          <> maybe [] (\v -> [value_ v]) mArrT

voyageFormPage :: Maybe Text -> Html ()
voyageFormPage mError = pageLayout "航海登録 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-10"] $ do
      h1_ [class_ "h3 mb-4"] "航海スケジュール登録 (US24)"
      case mError of
        Just msg -> flashAlert FlashDanger msg
        Nothing -> mempty
      form_ [action_ "/voyages/new", method_ "post"] $ do
        div_ [class_ "mb-3"] $ do
          label_ [for_ "voyageNumber", class_ "form-label"] "航海番号 (1-20 文字)"
          input_
            [ type_ "text"
            , id_ "voyageNumber"
            , name_ "voyageNumber"
            , class_ "form-control"
            , required_ "required"
            , maxlength_ "20"
            , placeholder_ "V0001"
            ]
        p_
          [class_ "text-muted small"]
          "区間 1 は必須、2-3 は任意。連続性 (前区間の到着港 = 次区間の出発港) は登録時に検証されます。"
        div_ [id_ "movements-container"] $ do
          movementRow 1 True
          movementRow 2 False
          movementRow 3 False
        div_ [class_ "mt-2"] $
          button_
            [ type_ "button"
            , class_ "btn btn-sm btn-outline-secondary"
            , makeAttribute "hx-get" "/voyages/new/movement-row"
            , makeAttribute "hx-target" "#movements-container"
            , makeAttribute "hx-swap" "beforeend"
            ]
            "+ 区間を追加"
        button_ [type_ "submit", class_ "btn btn-primary mt-4"] "登録"

{- | US25 (IT2 → U-03 IT3): 既存航海の更新フォーム。

U-03 で既存 `CarrierMovement` をフォームにプリフィルできるよう [CarrierMovement]
を引数で受ける。先頭 3 区間を `movementRowWith Just` で埋め、4 区間目以降は htmx で
追加可能。
-}
voyageEditPage :: Text -> [CarrierMovement] -> Maybe Text -> Html ()
voyageEditPage vn movements mError = pageLayout "航海更新 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-10"] $ do
      h1_ [class_ "h3 mb-4"] (toHtml ("航海スケジュール更新 (US25): " <> vn))
      -- M-08 (IT3): 楽観ロック衝突メッセージは「最新を再読込」アクションを併置
      case mError of
        Just msg
          | "他の利用者により更新されました" `T.isInfixOf` msg ->
              flashAlertWithAction
                FlashDanger
                msg
                "最新を再読込"
                ("/voyages/" <> vn <> "/edit")
        Just msg -> flashAlert FlashDanger msg
        Nothing -> mempty
      flashAlert
        FlashWarning
        "既存の区間を全て上書きします。プリフィル済の値を編集して更新してください。"
      form_
        [action_ ("/voyages/" <> vn <> "/update"), method_ "post"]
        $ do
          input_ [type_ "hidden", name_ "voyageNumber", value_ vn]
          p_
            [class_ "text-muted small"]
            "区間 1 は必須、2-3 は任意。連続性 (前区間の到着港 = 次区間の出発港) は更新時に検証されます。"
          let pick n = case drop (n - 1) movements of
                (cm : _) -> Just cm
                _ -> Nothing
          div_ [id_ "movements-container"] $ do
            movementRowWith 1 True (pick 1)
            movementRowWith 2 False (pick 2)
            movementRowWith 3 False (pick 3)
          div_ [class_ "mt-2"] $
            button_
              [ type_ "button"
              , class_ "btn btn-sm btn-outline-secondary"
              , makeAttribute "hx-get" "/voyages/new/movement-row"
              , makeAttribute "hx-target" "#movements-container"
              , makeAttribute "hx-swap" "beforeend"
              ]
              "+ 区間を追加"
          div_ [class_ "mt-4"] $ do
            button_ [type_ "submit", class_ "btn btn-primary me-2"] "更新する"
            a_
              [href_ ("/voyages/" <> vn), class_ "btn btn-light"]
              "キャンセル"

voyageResultPage :: Bool -> Text -> Html ()
voyageResultPage success message = pageLayout "航海結果 - Cargo Tracker" $ do
  div_ [class_ "row justify-content-center"] $
    div_ [class_ "col-md-8"] $ do
      h1_ [class_ "h3 mb-4"] "航海登録結果"
      flashAlert (if success then FlashSuccess else FlashDanger) message
      a_ [href_ "/voyages/new", class_ "btn btn-secondary me-2"] "もう 1 件登録"
      a_ [href_ "/", class_ "btn btn-light"] "トップへ"
