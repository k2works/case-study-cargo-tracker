{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 輸送見積の SSR 画面 (U-01 / US01, IT3)

GET  /estimates/new          : 見積作成フォーム
POST /estimates              : フォーム → CreateEstimateCommand → 303 /estimates/:id
GET  /estimates/:estimateId  : 見積詳細 + 経路候補 + 「この見積で予約する」リンク

候補生成は IT3 スコープではスタブ実装 (直行便 1 件 + 経由便 1 件) を返す。
将来 (IT4+) 実際の RouteFinder と統合する。
-}
module Cargotracker.Estimation.Interfaces.EstimatePageApi
  ( EstimatePageApi,
    estimatePageApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import qualified Data.ByteString.Char8 as BC
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, defaultTimeLocale, parseTimeM)
import qualified Data.UUID.V4 as UUID
import GHC.Generics (Generic)
import Lucid (Html)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Estimation.Application.CreateEstimateCommand
  ( CandidateInput (..),
    CreateEstimateInput (..),
    execute,
  )
import Cargotracker.Estimation.Application.Ports (EstimateRepository (..))
import Cargotracker.Estimation.Domain.Model.Value.EstimateId
  ( mkEstimateId,
  )
import Cargotracker.Estimation.Views.EstimateFormView
  ( estimateFormPage,
    estimateListPage,
    estimateNotFoundPage,
    estimateShowPage,
  )

data EstimateFormRequest = EstimateFormRequest
  { shipperId :: !Text
  , origin :: !Text
  , destination :: !Text
  , deadline :: !Text
  , cargoType :: !Text
  , weight :: !Text
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromForm)

type EstimatePageApi =
  "estimates"
    :> ( Get '[HTML] (Html ())
           :<|> "new"
             :> QueryParam "error" Text
             :> Get '[HTML] (Html ())
           :<|> ReqBody '[FormUrlEncoded] EstimateFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> Capture "estimateId" Text :> Get '[HTML] (Html ())
       )

estimatePageApp :: EstimateRepository IO -> Application
estimatePageApp repo =
  serve
    (Proxy :: Proxy EstimatePageApi)
    ( handlerList repo
        :<|> handlerNew
        :<|> handlerPost repo
        :<|> handlerShow repo
    )

handlerList :: EstimateRepository IO -> Handler (Html ())
handlerList repo = do
  ests <- liftIO (findAllEstimates repo)
  pure (estimateListPage ests)

handlerNew :: Maybe Text -> Handler (Html ())
handlerNew mError = pure (estimateFormPage (fmap errorMessage mError))

errorMessage :: Text -> Text
errorMessage "deadline-format" = "到着期限の日付形式が不正です"
errorMessage "weight-format" = "重量は正の数値を入力してください"
errorMessage e = "見積作成に失敗しました: " <> e

handlerPost ::
  EstimateRepository IO ->
  EstimateFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerPost repo req = case parseDeadline (deadline req) of
  Nothing -> redirect "/estimates/new?error=deadline-format"
  Just dt -> case readWeight (weight req) of
    Nothing -> redirect "/estimates/new?error=weight-format"
    Just w -> do
      uuid <- liftIO UUID.nextRandom
      let eidText = T.pack (show uuid)
      let input =
            CreateEstimateInput
              { inputEstimateId = eidText
              , inputShipperId = shipperId req
              , inputOrigin = origin req
              , inputDestination = destination req
              , inputDeadline = dt
              , inputCargoType = cargoType req
              , inputWeightKg = w
              , inputCandidates = stubCandidates
              }
      result <- liftIO (execute repo input)
      case result of
        Right _ ->
          pure (addHeader ("/estimates/" <> eidText) NoContent)
        Left e ->
          redirect ("/estimates/new?error=" <> T.pack (show e))
  where
    redirect :: Text -> Handler a
    redirect loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }

-- U-01 スタブ: IT3 では実 RouteFinder ではなく固定の 2 候補を返す。
-- 将来 (IT4 US08a 後) に Routing.Domain.Service.RouteFinder と統合する。
stubCandidates :: [CandidateInput]
stubCandidates =
  [ CandidateInput
      { inputRank = 0
      , inputTransitDays = 14
      , inputEstimatedCost = 250000
      , inputVoyageNumbers = ["V0001"]
      }
  , CandidateInput
      { inputRank = 1
      , inputTransitDays = 21
      , inputEstimatedCost = 180000
      , inputVoyageNumbers = ["V0002", "V0003"]
      }
  ]

parseDeadline :: Text -> Maybe UTCTime
parseDeadline t = parseTimeM True defaultTimeLocale "%Y-%m-%dT%H:%M" (T.unpack t)

readWeight :: Text -> Maybe Double
readWeight t = case reads (T.unpack t) of
  [(w, "")] | w > 0 -> Just w
  _ -> Nothing

handlerShow ::
  EstimateRepository IO -> Text -> Handler (Html ())
handlerShow repo eidText =
  case mkEstimateId eidText of
    Left _ -> pure estimateNotFoundPage
    Right eid -> do
      m <- liftIO (findEstimateById repo eid)
      pure (maybe estimateNotFoundPage estimateShowPage m)
