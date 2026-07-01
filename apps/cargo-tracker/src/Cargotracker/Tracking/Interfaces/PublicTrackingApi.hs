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

publicTrackingApp :: TrackingRepository IO -> Application
publicTrackingApp repo =
  serve
    (Proxy :: Proxy PublicTrackingApi)
    ( handlerSearch repo
        :<|> handlerDetail repo
    )

{- | GET /public/tracking (?trackingNumber=...)
- QueryParam なし: 検索フォームページ
- QueryParam あり: 詳細ページ (Capture ハンドラに委譲)
-}
handlerSearch ::
  TrackingRepository IO ->
  Maybe Text ->
  Handler (Html ())
handlerSearch repo mTn = case mTn of
  Nothing -> pure publicTrackingSearchPage
  Just tn -> handlerDetail repo tn

-- | GET /public/tracking/:trackingNumber
handlerDetail ::
  TrackingRepository IO ->
  Text ->
  Handler (Html ())
handlerDetail repo raw = do
  result <- liftIO (QueryTracking.execute repo raw)
  case result of
    Left _ -> pure (publicTrackingNotFoundPage raw)
    Right Nothing -> pure (publicTrackingNotFoundPage raw)
    Right (Just view) -> pure (publicTrackingDetailPage view)
