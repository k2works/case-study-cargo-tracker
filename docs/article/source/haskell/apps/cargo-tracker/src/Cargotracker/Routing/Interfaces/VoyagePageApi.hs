{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 航海登録の SSR 画面 (IT1 US24)

GET  /voyages/new : 登録フォーム (区間 1 必須 + 2/3 任意)
POST /voyages/new : フォーム → RegisterVoyageCommand → 結果

空区間 (港未選択 or 時刻未入力) はハンドラ側で除外する。
-}
module Cargotracker.Routing.Interfaces.VoyagePageApi
  ( VoyagePageApi,
    voyagePageApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import qualified Data.ByteString.Char8 as BC
import Data.Maybe (catMaybes, fromMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, defaultTimeLocale, parseTimeM)
import GHC.Generics (Generic)
import Lucid (Html)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Application.RegisterVoyageCommand
  ( CarrierMovementInput (..),
    RegisterVoyageInput (..),
    execute,
  )
import Cargotracker.Routing.Application.SearchVoyagesQuery
  ( SearchVoyagesInput (..),
  )
import qualified Cargotracker.Routing.Application.SearchVoyagesQuery as Search
import qualified Cargotracker.Routing.Application.UpdateVoyageCommand as Update
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (VoyageNumber (..))
import Cargotracker.Routing.Domain.Model.Voyage (carrierMovements)
import Cargotracker.Routing.Views.VoyageFormView (voyageEditPage, voyageFormPage)
import Cargotracker.Routing.Views.VoyageListView (voyageListPage)
import Cargotracker.Routing.Views.VoyageSearchView
  ( VoyageSearchFormData (..),
    voyageSearchPage,
  )
import Cargotracker.Routing.Views.VoyageShowView
  ( voyageNotFoundPage,
    voyageShowPage,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..), mkUnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data VoyageFormRequest = VoyageFormRequest
  { voyageNumber :: !Text
  , movement1Departure :: !Text
  , movement1Arrival :: !Text
  , movement1DepartureTime :: !Text
  , movement1ArrivalTime :: !Text
  , movement2Departure :: !(Maybe Text)
  , movement2Arrival :: !(Maybe Text)
  , movement2DepartureTime :: !(Maybe Text)
  , movement2ArrivalTime :: !(Maybe Text)
  , movement3Departure :: !(Maybe Text)
  , movement3Arrival :: !(Maybe Text)
  , movement3DepartureTime :: !(Maybe Text)
  , movement3ArrivalTime :: !(Maybe Text)
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromForm)

type VoyagePageApi =
  "voyages"
    :> ( Get '[HTML] (Html ())
           :<|> "new"
             :> QueryParam "error" Text
             :> Get '[HTML] (Html ())
           :<|> "new"
             :> ReqBody '[FormUrlEncoded] VoyageFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> "search"
             :> QueryParam "from" Text
             :> QueryParam "to" Text
             :> QueryParam "from_date" Text
             :> QueryParam "to_date" Text
             :> Get '[HTML] (Html ())
           :<|> Capture "voyageNumber" Text :> Get '[HTML] (Html ())
           :<|> Capture "voyageNumber" Text
             :> "edit"
             :> QueryParam "error" Text
             :> Get '[HTML] (Html ())
           :<|> Capture "voyageNumber" Text
             :> "update"
             :> ReqBody '[FormUrlEncoded] VoyageFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
       )

voyagePageApp :: VoyageRepository IO -> Application
voyagePageApp repo =
  serve
    (Proxy :: Proxy VoyagePageApi)
    ( handlerList repo
        :<|> handlerGet
        :<|> handlerPost repo
        :<|> handlerSearch repo
        :<|> handlerShow repo
        :<|> handlerEdit repo
        :<|> handlerUpdate repo
    )

{- | US07 (IT3): /voyages/search の GET ハンドラ。

クエリパラメータが全て未指定ならフォームのみ表示 (初期画面)。
1 つでも指定があれば検索を実行し、結果テーブル or 0 件メッセージを表示。
バリデーションエラー (UnLocode 不正・日付不正・期間逆順) は alert-danger
で表示してフォームを再描画する (入力値は保持)。
-}
handlerSearch ::
  VoyageRepository IO ->
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Handler (Html ())
handlerSearch repo mFrom mTo mFromDate mToDate = do
  let form =
        VoyageSearchFormData
          { fdOrigin = fromMaybeT mFrom
          , fdDestination = fromMaybeT mTo
          , fdFromDate = fromMaybeT mFromDate
          , fdToDate = fromMaybeT mToDate
          }
  if allEmpty [mFrom, mTo, mFromDate, mToDate]
    then pure (voyageSearchPage form Nothing Nothing)
    else case parseSearchInput mFrom mTo mFromDate mToDate of
      Left msg -> pure (voyageSearchPage form (Just msg) Nothing)
      Right input -> do
        res <- liftIO (Search.execute repo input)
        case res of
          Left e -> pure (voyageSearchPage form (Just (searchErrorMessage e)) Nothing)
          Right voys -> pure (voyageSearchPage form Nothing (Just voys))
  where
    fromMaybeT = fromMaybe ""
    allEmpty = all (maybe True T.null)

parseSearchInput ::
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Either Text SearchVoyagesInput
parseSearchInput mFrom mTo mFromDate mToDate = do
  fromTxt <- requireField "from" mFrom
  toTxt <- requireField "to" mTo
  fromDateTxt <- requireField "from_date" mFromDate
  toDateTxt <- requireField "to_date" mToDate
  origin <- mapLeft (T.pack . show) (mkUnLocode fromTxt)
  destination <- mapLeft (T.pack . show) (mkUnLocode toTxt)
  fromDate <- parseDay fromDateTxt
  toDate <- parseDay toDateTxt
  Right
    SearchVoyagesInput
      { inputOrigin = origin
      , inputDestination = destination
      , inputFromDate = fromDate
      , inputToDate = toDate
      }
  where
    requireField name mt = case mt of
      Just t | not (T.null t) -> Right t
      _ -> Left (name <> " を入力してください")
    parseDay t = case parseTimeM True defaultTimeLocale "%Y-%m-%d" (T.unpack t) of
      Just d -> Right d
      Nothing -> Left ("日付の形式が不正です: " <> t)
    mapLeft f (Left e) = Left (f e)
    mapLeft _ (Right x) = Right x

searchErrorMessage :: DomainError -> Text
searchErrorMessage (InvalidSearchPeriod _ _) = "出発期間の開始日は終了日より前である必要があります"
searchErrorMessage (SameOriginDestination _) = "出発地と目的地は異なる港を指定してください"
searchErrorMessage (InvalidUnLocode t) = "UnLocode が不正です: " <> t
searchErrorMessage e = "検索に失敗しました: " <> T.pack (show e)

handlerList :: VoyageRepository IO -> Handler (Html ())
handlerList repo = do
  xs <- liftIO (findAllVoyages repo)
  pure (voyageListPage xs)

-- T-08 (IT2): ?error= クエリを Bootstrap alert に変換する。
handlerGet :: Maybe Text -> Handler (Html ())
handlerGet mError = pure (voyageFormPage (fmap voyageErrorMessage mError))

voyageErrorMessage :: Text -> Text
voyageErrorMessage "voyage-not-found" = "指定された航海が見つかりません"
voyageErrorMessage e = "航海登録に失敗しました: " <> e

handlerShow :: VoyageRepository IO -> Text -> Handler (Html ())
handlerShow repo vn = do
  m <- liftIO (findByVoyageNumber repo (VoyageNumber vn))
  pure (maybe voyageNotFoundPage voyageShowPage m)

handlerPost ::
  VoyageRepository IO ->
  VoyageFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerPost repo req = case toMovements req of
  Left err -> redirectErr ("/voyages/new?error=" <> err)
  Right ms -> do
    let input =
          RegisterVoyageInput
            { inputVoyageNumber = voyageNumber req
            , inputMovements = ms
            }
    result <- liftIO (execute repo input)
    case result of
      Right _ -> pure (addHeader ("/voyages/" <> voyageNumber req) NoContent)
      Left e -> redirectErr ("/voyages/new?error=" <> T.pack (show e))
  where
    redirectErr :: Text -> Handler a
    redirectErr loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }

toMovements :: VoyageFormRequest -> Either Text [CarrierMovementInput]
toMovements req = do
  m1 <-
    parseMovement
      "区間 1"
      (movement1Departure req)
      (movement1Arrival req)
      (movement1DepartureTime req)
      (movement1ArrivalTime req)
  let m2 =
        parseOptionalMovement
          (movement2Departure req)
          (movement2Arrival req)
          (movement2DepartureTime req)
          (movement2ArrivalTime req)
      m3 =
        parseOptionalMovement
          (movement3Departure req)
          (movement3Arrival req)
          (movement3DepartureTime req)
          (movement3ArrivalTime req)
  Right (m1 : catMaybes [m2, m3])

-- US25 (IT2): 航海更新フォームの表示。対象が見つからない場合は 404 ページ。
-- T-08 (IT2): ?error= クエリを Bootstrap alert として渡す。
handlerEdit ::
  VoyageRepository IO -> Text -> Maybe Text -> Handler (Html ())
handlerEdit repo vn mError = do
  m <- liftIO (findByVoyageNumber repo (VoyageNumber vn))
  pure $
    maybe
      voyageNotFoundPage
      (\voy -> voyageEditPage vn (carrierMovements voy) (fmap voyageEditErrorMessage mError))
      m

voyageEditErrorMessage :: Text -> Text
voyageEditErrorMessage "leg-continuity" =
  "区間の連続性が崩れています (前区間の到着港 = 次区間の出発港 となるよう修正してください)"
voyageEditErrorMessage "concurrent-modification" =
  -- M-08 (IT3): 業務語に強化 + 編集内容保持に関する明示
  "他の利用者により更新されました。今回の入力は破棄されます — 最新を再読込してから再度編集してください"
voyageEditErrorMessage e = "航海更新に失敗しました: " <> e

-- US25 (IT2): 航海更新の POST 実行 (PRG)。UpdateVoyageCommand 経由。
handlerUpdate ::
  VoyageRepository IO ->
  Text ->
  VoyageFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerUpdate repo vn req = case toMovements req of
  Left err -> redirectToEdit vn ("update-" <> err)
  Right ms -> do
    let input =
          Update.UpdateVoyageInput
            { Update.inputVoyageNumber = vn
            , Update.inputMovements = ms
            }
    result <- liftIO (Update.execute repo input)
    case result of
      Right _ -> redirectOk ("/voyages/" <> vn <> "?flash=updated")
      Left (InvalidVoyageNumber _) ->
        redirectErr "/voyages/new?error=voyage-not-found"
      Left (LegContinuityViolation _) ->
        redirectToEdit vn "leg-continuity"
      Left (ConcurrentModification _) ->
        redirectToEdit vn "concurrent-modification"
      Left e -> redirectToEdit vn (T.pack (show e))
  where
    redirectErr :: Text -> Handler a
    redirectErr loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }
    redirectToEdit :: Text -> Text -> Handler a
    redirectToEdit v e =
      redirectErr ("/voyages/" <> v <> "/edit?error=" <> e)
    redirectOk :: Text -> Handler (Headers '[Header "Location" Text] NoContent)
    redirectOk loc = pure (addHeader loc NoContent)

parseMovement ::
  Text -> Text -> Text -> Text -> Text -> Either Text CarrierMovementInput
parseMovement label dep arr depTime arrTime = case (parseTime depTime, parseTime arrTime) of
  (Just dt, Just at) ->
    Right
      CarrierMovementInput
        { inputDeparture = dep
        , inputArrival = arr
        , inputDepartureTime = dt
        , inputArrivalTime = at
        }
  _ -> Left (label <> ": 時刻の形式が不正です")

parseOptionalMovement ::
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe CarrierMovementInput
parseOptionalMovement mDep mArr mDepT mArrT = case (mDep, mArr, mDepT, mArrT) of
  (Just dep, Just arr, Just depT, Just arrT)
    | not (T.null dep) && not (T.null arr) && not (T.null depT) && not (T.null arrT) ->
        case (parseTime depT, parseTime arrT) of
          (Just dt, Just at) ->
            Just
              CarrierMovementInput
                { inputDeparture = dep
                , inputArrival = arr
                , inputDepartureTime = dt
                , inputArrivalTime = at
                }
          _ -> Nothing
  _ -> Nothing

parseTime :: Text -> Maybe UTCTime
parseTime t = parseTimeM True defaultTimeLocale "%Y-%m-%dT%H:%M" (T.unpack t)
