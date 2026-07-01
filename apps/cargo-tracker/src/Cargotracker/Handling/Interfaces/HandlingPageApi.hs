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
import Web.FormUrlEncoded (FromForm)

import Cargotracker.Handling.Application.Ports (HandlingActivityRepository)
import Cargotracker.Handling.Application.RegisterHandlingEventCommand
  ( RegisterHandlingEventInput (..),
  )
import qualified Cargotracker.Handling.Application.RegisterHandlingEventCommand as RegisterHandling
import Cargotracker.Handling.Domain.Model.HandlingType (textToHandlingType)
import Cargotracker.Handling.Views.HandlingFormView (handlingFormPage)

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
       )

handlingPageApp :: HandlingActivityRepository IO -> Application
handlingPageApp repo =
  serve
    (Proxy :: Proxy HandlingPageApi)
    ( handlerGet
        :<|> handlerPost repo
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
