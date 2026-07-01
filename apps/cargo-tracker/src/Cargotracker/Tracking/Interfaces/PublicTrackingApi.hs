{-# LANGUAGE DataKinds #-}
{-# LANGUAGE TypeOperators #-}

{- | 公開追跡ページ (US18, IT5)

認証不要のパブリックエンドポイント:
- GET /public/tracking                          : 追跡番号入力フォーム
- GET /public/tracking?trackingNumber=TR12345A  : クエリで直接ジャンプ (form GET)
- GET /public/tracking/:trackingNumber           : 追跡詳細ページ (パス指定)

Servant の型として QueryParam と Capture の両方を定義し、フォーム送信 (GET
+ QueryParam) と直接リンク (Capture) の両方で同じビューへ到達できるようにする。
-}
module Cargotracker.Tracking.Interfaces.PublicTrackingApi
  ( PublicTrackingApi,
    publicTrackingApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Text (Text)
import Lucid (Html)
import Servant
import Servant.HTML.Lucid (HTML)

import Cargotracker.Handling.Application.Ports (HandlingActivityRepository)
import qualified Cargotracker.Handling.Application.QueryHandlingHistoryQuery as QueryHandling
import Cargotracker.Tracking.Application.Ports (TrackingRepository)
import qualified Cargotracker.Tracking.Application.QueryTrackingByNumberQuery as QueryTracking
import Cargotracker.Tracking.Views.PublicTrackingView
  ( publicTrackingDetailPage,
    publicTrackingNotFoundPage,
    publicTrackingSearchPage,
  )

type PublicTrackingApi =
  "public"
    :> "tracking"
    :> ( QueryParam "trackingNumber" Text
           :> Get '[HTML] (Html ())
           :<|> Capture "trackingNumber" Text
             :> Get '[HTML] (Html ())
       )

publicTrackingApp ::
  TrackingRepository IO ->
  HandlingActivityRepository IO ->
  Application
publicTrackingApp trackingRepo handlingRepo =
  serve
    (Proxy :: Proxy PublicTrackingApi)
    ( handlerSearch trackingRepo handlingRepo
        :<|> handlerDetail trackingRepo handlingRepo
    )

{- | GET /public/tracking (?trackingNumber=...)
- QueryParam なし: 検索フォームページ
- QueryParam あり: 詳細ページ (Capture ハンドラに委譲)
-}
handlerSearch ::
  TrackingRepository IO ->
  HandlingActivityRepository IO ->
  Maybe Text ->
  Handler (Html ())
handlerSearch trackingRepo handlingRepo mTn = case mTn of
  Nothing -> pure publicTrackingSearchPage
  Just tn -> handlerDetail trackingRepo handlingRepo tn

-- | GET /public/tracking/:trackingNumber
handlerDetail ::
  TrackingRepository IO ->
  HandlingActivityRepository IO ->
  Text ->
  Handler (Html ())
handlerDetail trackingRepo handlingRepo raw = do
  result <- liftIO (QueryTracking.execute trackingRepo raw)
  case result of
    Left _ -> pure (publicTrackingNotFoundPage raw)
    Right Nothing -> pure (publicTrackingNotFoundPage raw)
    Right (Just view) -> do
      -- US18 拡張: 荷役履歴タイムラインを Cross-BC で取得
      events <-
        liftIO
          ( QueryHandling.queryHandlingHistoryText
              handlingRepo
              (QueryTracking.tvBookingId view)
          )
      pure (publicTrackingDetailPage view events)
