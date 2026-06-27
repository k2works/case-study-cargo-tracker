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
import Data.Maybe (catMaybes)
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
import qualified Cargotracker.Routing.Application.UpdateVoyageCommand as Update
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (VoyageNumber (..))
import Cargotracker.Routing.Views.VoyageFormView (voyageEditPage, voyageFormPage)
import Cargotracker.Routing.Views.VoyageShowView
  ( voyageNotFoundPage,
    voyageShowPage,
  )
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
    :> ( "new" :> Get '[HTML] (Html ())
           :<|> "new"
             :> ReqBody '[FormUrlEncoded] VoyageFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> Capture "voyageNumber" Text :> Get '[HTML] (Html ())
           :<|> Capture "voyageNumber" Text
             :> "edit"
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
    ( handlerGet
        :<|> handlerPost repo
        :<|> handlerShow repo
        :<|> handlerEdit repo
        :<|> handlerUpdate repo
    )

handlerGet :: Handler (Html ())
handlerGet = pure (voyageFormPage Nothing)

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
handlerEdit :: VoyageRepository IO -> Text -> Handler (Html ())
handlerEdit repo vn = do
  m <- liftIO (findByVoyageNumber repo (VoyageNumber vn))
  pure $
    maybe
      voyageNotFoundPage
      (\_ -> voyageEditPage vn Nothing)
      m

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
