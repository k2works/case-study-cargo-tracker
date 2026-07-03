{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveAnyClass #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 荷役登録 SSR 画面 (US15, IT5)

- GET  /handling/new           : 登録フォーム
- POST /handling/new           : フォーム → RegisterHandlingEventCommand → PRG
-}
module Cargotracker.Handling.Interfaces.HandlingPageApi
  ( HandlingPageApi,
    handlingPageApp,
  ) where

import Control.Monad.IO.Class (liftIO)
import qualified Data.ByteString.Char8 as BC
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time
  ( UTCTime,
    defaultTimeLocale,
    getCurrentTime,
    parseTimeM,
  )
import GHC.Generics (Generic)
import Lucid (Html)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (FromForm (..), parseUnique)

import Cargotracker.Handling.Application.Ports (HandlingActivityRepository)
import Cargotracker.Handling.Application.RegisterHandlingEventCommand
  ( RegisterHandlingEventInput (..),
  )
import qualified Cargotracker.Handling.Application.RegisterHandlingEventCommand as RegisterHandling
import Cargotracker.Handling.Application.VerifyClaimAndRegisterCommand
  ( VerifyClaimInput (..),
  )
import qualified Cargotracker.Handling.Application.VerifyClaimAndRegisterCommand as VerifyClaim
import Cargotracker.Handling.Domain.Model.HandlingType (HandlingType (..), textToHandlingType)
import Cargotracker.Handling.Views.ClaimFormView (claimFormPage)
import Cargotracker.Handling.Views.HandlingFormView (handlingFormPage)
import Cargotracker.Shared.Application.TxRunner (TxRunner (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Security.BcryptHash (verifySecret)
import Cargotracker.Tracking.Application.ConfirmationCodePorts
  ( ConfirmationCodeRepository,
  )
import qualified Cargotracker.Tracking.Application.IssueConfirmationCodeCommand as IssueCode
import Cargotracker.Tracking.Application.Ports (TrackingRepository)

import Cargotracker.Notification.Application.Ports
  ( NotificationDeliveryPort,
    NotificationRepository,
  )
import Cargotracker.Notification.Application.SendClaimNotificationCommand
  ( sendClaimLogNotificationTextWithId,
  )

data HandlingFormRequest = HandlingFormRequest
  { bookingId :: !Text
  , eventType :: !Text
  , completionTime :: !Text
  -- ^ "YYYY-MM-DDTHH:MM" (datetime-local)
  , locationUnlocode :: !Text
  , voyageNumber :: !(Maybe Text)
  , operatorName :: !Text
  }
  deriving stock (Generic, Show, Eq)
  deriving anyclass (FromForm)

type HandlingPageApi =
  "handling"
    :> ( "new"
           :> QueryParam "flash" Text
           :> Get '[HTML] (Html ())
           :<|> "new"
             :> ReqBody '[FormUrlEncoded] HandlingFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> "claim"
             :> QueryParam "flash" Text
             :> Get '[HTML] (Html ())
           :<|> "claim"
             :> ReqBody '[FormUrlEncoded] ClaimFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
       )

data ClaimFormRequest = ClaimFormRequest
  { claimBookingId :: !Text
  , claimConfirmationCode :: !Text
  , claimLocationUnlocode :: !Text
  , claimOperatorName :: !Text
  }
  deriving stock (Generic, Show, Eq)

instance FromForm ClaimFormRequest where
  fromForm f =
    ClaimFormRequest
      <$> parseUnique "bookingId" f
      <*> parseUnique "confirmationCode" f
      <*> parseUnique "locationUnlocode" f
      <*> parseUnique "operatorName" f

handlingPageApp ::
  TxRunner ->
  HandlingActivityRepository IO ->
  ConfirmationCodeRepository IO ->
  TrackingRepository IO ->
  NotificationRepository IO ->
  NotificationDeliveryPort IO ->
  -- | Notification UUID v4 生成器 (ADR-0013 Phase 3)
  IO Text ->
  -- | 6 桁確認コード生成器 (T7-01 UNLOAD 時)
  IO Text ->
  Application
handlingPageApp tx repo codeRepo trackingRepo notifRepo notifDelivery genNid genCode =
  serve
    (Proxy :: Proxy HandlingPageApi)
    ( handlerGet
        :<|> handlerPost repo codeRepo genCode
        :<|> handlerClaimGet
        :<|> handlerClaimPost tx codeRepo repo trackingRepo notifRepo notifDelivery genNid
    )

handlerGet :: Maybe Text -> Handler (Html ())
handlerGet mFlash = pure (handlingFormPage mFlash)

handlerPost ::
  HandlingActivityRepository IO ->
  ConfirmationCodeRepository IO ->
  IO Text ->
  HandlingFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerPost repo codeRepo genCode form = do
  now <- liftIO getCurrentTime
  case parseCompletionTime (completionTime form) of
    Nothing -> redirectErr "/handling/new?flash=invalid-state"
    Just ct -> case textToHandlingType (eventType form) of
      Left _ -> redirectErr "/handling/new?flash=invalid-state"
      Right ht -> do
        result <-
          liftIO
            ( RegisterHandling.execute
                repo
                RegisterHandlingEventInput
                  { inputBookingId = bookingId form
                  , inputEventType = ht
                  , inputCompletionTime = ct
                  , inputLocationUnlocode = locationUnlocode form
                  , inputVoyageNumber = voyageNumber form
                  , inputOperatorName = operatorName form
                  , inputNow = now
                  }
            )
        case result of
          Right () -> do
            -- T7-01 (IT7): UNLOAD 完了時に確認コードを発行 (Handling → Tracking Cross-BC)
            -- 冪等: 既発行なら既存を返す (IssueConfirmationCodeCommand 側で保証)。
            -- 通知配信 (メール等) は US26 の後続ステップで追加予定。
            case ht of
              Unload -> do
                codeText <- liftIO genCode
                _ <-
                  liftIO
                    ( IssueCode.execute
                        codeRepo
                        IssueCode.IssueConfirmationCodeInput
                          { IssueCode.inputBookingId = bookingId form
                          , IssueCode.inputCodeText = codeText
                          , IssueCode.inputNow = now
                          }
                    )
                pure (addHeader "/handling/new?flash=success" NoContent)
              _ ->
                pure (addHeader "/handling/new?flash=success" NoContent)
          Left _ ->
            redirectErr "/handling/new?flash=invalid-state"
  where
    redirectErr :: Text -> Handler a
    redirectErr loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }

-- | "YYYY-MM-DDTHH:MM" (datetime-local) を UTCTime に変換。
parseCompletionTime :: Text -> Maybe UTCTime
parseCompletionTime t =
  parseTimeM True defaultTimeLocale "%Y-%m-%dT%H:%M" (T.unpack t)

-- | GET /handling/claim : 引取確認フォーム
handlerClaimGet :: Maybe Text -> Handler (Html ())
handlerClaimGet mFlash = pure (claimFormPage mFlash)

-- | POST /handling/claim : 確認コード検証 → Claim イベント登録 → Tracking 状態反映 (T5-03/T5-04: 単一 Tx)
handlerClaimPost ::
  TxRunner ->
  ConfirmationCodeRepository IO ->
  HandlingActivityRepository IO ->
  TrackingRepository IO ->
  NotificationRepository IO ->
  NotificationDeliveryPort IO ->
  IO Text ->
  ClaimFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerClaimPost tx codeRepo handlingRepo trackingRepo notifRepo notifDelivery genNid form = do
  now <- liftIO getCurrentTime
  -- T5-03 ADR-0012: verifyAndConsume + saveHandlingActivity + markClaimedByBookingId を
  -- 単一 Tx で包む。いずれかが例外を投げた場合、全体がロールバックされ整合性を保つ。
  result <-
    liftIO
      ( runInTx tx $
          VerifyClaim.execute
            verifySecret
            codeRepo
            handlingRepo
            trackingRepo
            VerifyClaimInput
              { inputBookingId = claimBookingId form
              , inputConfirmationCode = claimConfirmationCode form
              , inputLocationUnlocode = claimLocationUnlocode form
              , inputOperatorName = claimOperatorName form
              , inputCompletionTime = now
              , inputNow = now
              }
      )
  case result of
    Right () -> do
      -- US26 + ADR-0012 決定 3: Tx 完了後 (副作用は Tx 外) に引取完了通知を発火。
      -- ADR-0013 Phase 3: UUID v4 を Handling BC 側で採番し、Notification 集約に
      -- サロゲート識別子として渡す (業務キー変更耐性のため)。
      -- 失敗しても業務フロー (redirect) は継続する (通知は補助情報)。
      nid <- liftIO genNid
      _ <-
        liftIO
          ( sendClaimLogNotificationTextWithId
              notifRepo
              notifDelivery
              (claimBookingId form)
              "引取完了のお知らせ"
              ( "予約 "
                  <> claimBookingId form
                  <> " の引取が完了しました。ご利用ありがとうございました。"
              )
              now
              (Just nid)
          )
      pure (addHeader "/handling/claim?flash=success" NoContent)
    Left ConfirmationCodeMismatch ->
      redirectClaim "/handling/claim?flash=code-mismatch"
    Left ConfirmationCodeAlreadyUsed ->
      redirectClaim "/handling/claim?flash=code-used"
    Left (ConfirmationCodeMaxAttemptsExceeded _) ->
      redirectClaim "/handling/claim?flash=code-lock"
    Left (HandlingBookingNotFound _) ->
      redirectClaim "/handling/claim?flash=not-found"
    Left _ ->
      redirectClaim "/handling/claim?flash=invalid"
  where
    redirectClaim :: Text -> Handler a
    redirectClaim loc =
      throwError $
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          , errBody = ""
          }
