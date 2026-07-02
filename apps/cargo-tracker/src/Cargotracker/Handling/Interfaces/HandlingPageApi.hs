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
import Cargotracker.Handling.Domain.Model.HandlingType (textToHandlingType)
import Cargotracker.Handling.Views.ClaimFormView (claimFormPage)
import Cargotracker.Handling.Views.HandlingFormView (handlingFormPage)
import Cargotracker.Shared.Application.TxRunner (TxRunner (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Security.BcryptHash (verifySecret)
import Cargotracker.Tracking.Application.ConfirmationCodePorts
  ( ConfirmationCodeRepository,
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
  Application
handlingPageApp tx repo codeRepo =
  serve
    (Proxy :: Proxy HandlingPageApi)
    ( handlerGet
        :<|> handlerPost repo
        :<|> handlerClaimGet
        :<|> handlerClaimPost tx codeRepo repo
    )

handlerGet :: Maybe Text -> Handler (Html ())
handlerGet mFlash = pure (handlingFormPage mFlash)

handlerPost ::
  HandlingActivityRepository IO ->
  HandlingFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerPost repo form = do
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
          Right () ->
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

-- | POST /handling/claim : 確認コード検証 → Claim イベント登録 (T5-03: 単一 Tx)
handlerClaimPost ::
  TxRunner ->
  ConfirmationCodeRepository IO ->
  HandlingActivityRepository IO ->
  ClaimFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handlerClaimPost tx codeRepo handlingRepo form = do
  now <- liftIO getCurrentTime
  -- T5-03 ADR-0012: verifyAndConsume + saveHandlingActivity を単一 Tx で包む。
  -- saveHandlingActivity が例外を投げた場合、attempt_count / used_at の更新も
  -- ロールバックされ整合性を保つ。ビジネスエラー (Left) は Tx コミット対象
  -- (再試行回数の永続化が必要なため)。
  result <-
    liftIO
      ( runInTx tx $
          VerifyClaim.execute
            verifySecret
            codeRepo
            handlingRepo
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
    Right () ->
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
