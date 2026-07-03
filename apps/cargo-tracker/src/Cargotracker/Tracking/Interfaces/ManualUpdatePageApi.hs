{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 手動状態更新エンドポイント (US17 5.4, IT7)

Servant handler:

- GET  /tracking/:tn/manual-update : 手動更新フォーム (Lucid)
- POST /tracking/:tn/manual-update : ManualStateUpdateCommand 実行 + 303 リダイレクト

Role-based 認可 (Tracker / MasterAdmin) は T6-09 で AuthProtect 経由に統合する。
本反復では handler の骨組みと POST->Command 配線を確立するに留める。
-}
module Cargotracker.Tracking.Interfaces.ManualUpdatePageApi
  ( ManualUpdateApi,
    manualUpdateApp,
    AuditHistoryApi,
    auditHistoryApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import qualified Data.ByteString.Char8 as BC
import qualified Data.ByteString.Lazy as BSL
import Data.Text (Text)
import Data.Time (getCurrentTime)
import GHC.Generics (Generic)
import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm (..), parseUnique)

import Cargotracker.Shared.Domain.TransportStatus
  ( TransportStatus (..),
    textToTransportStatus,
  )
import qualified Cargotracker.Tracking.Application.ManualStateUpdateCommand as Cmd
import Cargotracker.Tracking.Application.Ports (TrackingRepository (..))
import Cargotracker.Tracking.Application.TrackingStateAuditPorts
  ( TrackingStateAuditRepository (..),
  )
import Cargotracker.Tracking.Domain.Model.TrackingActivity (TrackingActivity (..))
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber (mkTrackingNumber)
import Cargotracker.Tracking.Views.ManualUpdateView
  ( auditHistoryFragment,
    manualUpdateFormFragment,
  )

data ManualUpdateForm = ManualUpdateForm
  { formNewStatus :: !Text
  , formReason :: !Text
  }
  deriving stock (Eq, Show, Generic)

instance FromForm ManualUpdateForm where
  fromForm f =
    ManualUpdateForm
      <$> parseUnique "newStatus" f
      <*> parseUnique "reason" f

type ManualUpdateApi =
  "tracking"
    :> Capture "trackingNumber" Text
    :> "manual-update"
    :> ( Get '[HTML] (Html ())
           :<|> ReqBody '[FormUrlEncoded] ManualUpdateForm
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
       )

manualUpdateApp ::
  TrackingRepository IO ->
  TrackingStateAuditRepository IO ->
  Text ->
  Application
manualUpdateApp trackingRepo auditRepo changedBy =
  serve (Proxy :: Proxy ManualUpdateApi) $ \tn ->
    handlerForm trackingRepo tn
      :<|> handlerSubmit trackingRepo auditRepo changedBy tn

handlerForm ::
  TrackingRepository IO ->
  Text ->
  Handler (Html ())
handlerForm trackingRepo tn = do
  case mkTrackingNumber tn of
    Left _ -> pure (manualUpdateFormFragment tn TsUnknown)
    Right tnObj -> do
      mActivity <- liftIO (findByTrackingNumber trackingRepo tnObj)
      let current = maybe TsUnknown taTransportStatus mActivity
      pure (manualUpdateFormFragment tn current)

handlerSubmit ::
  TrackingRepository IO ->
  TrackingStateAuditRepository IO ->
  Text ->
  Text ->
  ManualUpdateForm ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerSubmit trackingRepo auditRepo changedBy tn form = do
  now <- liftIO getCurrentTime
  let input =
        Cmd.ManualStateUpdateInput
          { Cmd.inputTrackingNumberText = tn
          , Cmd.inputNewStatus = textToTransportStatus (formNewStatus form)
          , Cmd.inputReason = formReason form
          , Cmd.inputChangedBy = changedBy
          , Cmd.inputNow = now
          }
  result <- liftIO (Cmd.execute trackingRepo auditRepo input)
  case result of
    Left err ->
      throwError
        err422
          { errBody =
              BSL.fromStrict ("{\"error\":\"" <> BC.pack (show err) <> "\"}")
          }
    Right () ->
      pure
        ( addHeader
            ("/public/tracking/" <> tn :: Text)
            NoContent
        )

{- | 監査履歴タブ (htmx フラグメント)。
GET /tracking/:tn/audit-history で auditHistoryFragment を返す。
-}
type AuditHistoryApi =
  "tracking"
    :> Capture "trackingNumber" Text
    :> "audit-history"
    :> Get '[HTML] (Html ())

auditHistoryApp ::
  TrackingStateAuditRepository IO ->
  Application
auditHistoryApp auditRepo =
  serve (Proxy :: Proxy AuditHistoryApi) $ \tn ->
    case mkTrackingNumber tn of
      Left _ -> pure (auditHistoryFragment [])
      Right tnObj -> do
        audits <- liftIO (findAuditsByTrackingNumber auditRepo tnObj)
        pure (auditHistoryFragment audits)
