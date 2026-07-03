{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 例外一覧画面 API (US19/US20, IT7)

`GET /exceptions?trackingNumber=TR-XXXXXXXX` で追跡番号別の例外一覧を表示する。
`trackingNumber` クエリパラメータが空 or 欠落の場合は空リスト (empty-state) を返す。

現時点では findExceptionsByTrackingNumber を Postgres Repository が暫定的に
空リストで実装しているため、実データ表示には後続の JSONB パーサ実装が必要。
本 handler は View + Route の骨組みを整えることが目的。
-}
module Cargotracker.Exception.Interfaces.ExceptionListPageApi
  ( exceptionListApp,
    exceptionRecordToRow,
  ) where

import Control.Monad.IO.Class (liftIO)
import qualified Data.ByteString.Char8 as BC
import Data.Maybe (fromMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, getCurrentTime)
import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import qualified Cargotracker.Exception.Application.RecordDamageExceptionCommand as RecordDamage
import qualified Cargotracker.Exception.Application.RecordDelayExceptionCommand as RecordDelay
import qualified Cargotracker.Exception.Application.RecordLossExceptionCommand as RecordLoss
import qualified Cargotracker.Exception.Application.ResolveExceptionCommand as Resolve
import Cargotracker.Exception.Domain.Model.ExceptionRecord (ExceptionRecord (..))
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( ExceptionSeverity (..),
    Level (..),
    levelToText,
    textToLevel,
  )
import Cargotracker.Exception.Domain.Model.ExceptionType (exceptionTypeToText)
import Cargotracker.Exception.Domain.Model.Reporter (Reporter (..))
import Cargotracker.Exception.Views.ExceptionFormViews
  ( damageFormPage,
    delayFormPage,
    exceptionNotFoundPage,
    lossFormPage,
  )
import Cargotracker.Exception.Views.ExceptionListView
  ( ExceptionRow (..),
    exceptionListPage,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import GHC.Generics (Generic)
import Web.FormUrlEncoded (FromForm (..), parseUnique)

-- | US19 遅延例外登録フォームの受信体
data RecordDelayFormRequest = RecordDelayFormRequest
  { formExceptionId :: !Text
  , formTrackingNumber :: !Text
  , formDelayHours :: !Text
  , formReason :: !Text
  , formSeverity :: !Text
  , formReporterUserId :: !Text
  , formReporterRole :: !Text
  }
  deriving stock (Eq, Show, Generic)

instance FromForm RecordDelayFormRequest where
  fromForm f =
    RecordDelayFormRequest
      <$> parseUnique "exceptionId" f
      <*> parseUnique "trackingNumber" f
      <*> parseUnique "delayHours" f
      <*> parseUnique "reason" f
      <*> parseUnique "severity" f
      <*> parseUnique "reporterUserId" f
      <*> parseUnique "reporterRole" f

-- | US20 破損例外登録フォームの受信体
data RecordDamageFormRequest = RecordDamageFormRequest
  { dmgExceptionId :: !Text
  , dmgTrackingNumber :: !Text
  , dmgAmountValue :: !Text
  , dmgAmountCurrency :: !Text
  , dmgDescription :: !Text
  , dmgSeverity :: !Text
  , dmgReporterUserId :: !Text
  , dmgReporterRole :: !Text
  }
  deriving stock (Eq, Show, Generic)

instance FromForm RecordDamageFormRequest where
  fromForm f =
    RecordDamageFormRequest
      <$> parseUnique "exceptionId" f
      <*> parseUnique "trackingNumber" f
      <*> parseUnique "amountValue" f
      <*> parseUnique "amountCurrency" f
      <*> parseUnique "description" f
      <*> parseUnique "severity" f
      <*> parseUnique "reporterUserId" f
      <*> parseUnique "reporterRole" f

-- | US20 紛失例外登録フォームの受信体
data RecordLossFormRequest = RecordLossFormRequest
  { lossExceptionId :: !Text
  , lossTrackingNumber :: !Text
  , lossAmountValue :: !Text
  , lossAmountCurrency :: !Text
  , lossLastSeenAt :: !Text
  , lossSeverity :: !Text
  , lossReporterUserId :: !Text
  , lossReporterRole :: !Text
  }
  deriving stock (Eq, Show, Generic)

instance FromForm RecordLossFormRequest where
  fromForm f =
    RecordLossFormRequest
      <$> parseUnique "exceptionId" f
      <*> parseUnique "trackingNumber" f
      <*> parseUnique "amountValue" f
      <*> parseUnique "amountCurrency" f
      <*> parseUnique "lastSeenAt" f
      <*> parseUnique "severity" f
      <*> parseUnique "reporterUserId" f
      <*> parseUnique "reporterRole" f

type ExceptionListApi =
  "exceptions"
    :> ( QueryParam "trackingNumber" Text :> Get '[HTML] (Html ())
           :<|> Capture "exceptionId" Text
             :> "resolve"
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> "delay" :> Get '[HTML] (Html ())
           :<|> "damage" :> Get '[HTML] (Html ())
           :<|> "loss" :> Get '[HTML] (Html ())
           :<|> Capture "exceptionId" Text :> Get '[HTML] (Html ())
           :<|> "delay"
             :> ReqBody '[FormUrlEncoded] RecordDelayFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> "damage"
             :> ReqBody '[FormUrlEncoded] RecordDamageFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> "loss"
             :> ReqBody '[FormUrlEncoded] RecordLossFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
       )

exceptionListApp :: ExceptionRepository IO -> Application
exceptionListApp repo =
  serve
    (Proxy :: Proxy ExceptionListApi)
    ( handler repo
        :<|> handleResolve repo
        :<|> pure delayFormPage
        :<|> pure damageFormPage
        :<|> pure lossFormPage
        :<|> handleShowDetail repo
        :<|> handleRecordDelay repo
        :<|> handleRecordDamage repo
        :<|> handleRecordLoss repo
    )

{- | GET /exceptions/:exceptionId 詳細ページ

Postgres Repository の findExceptionById が現状 Nothing を返すため、
本ハンドラは exceptionNotFoundPage を返す骨組み実装。JSONB detail_json
パーサ実装後 (次反復以降) に詳細表示ページに置き換える予定。
-}
handleShowDetail :: ExceptionRepository IO -> Text -> Handler (Html ())
handleShowDetail repo eid = do
  mRecord <- liftIO (findExceptionById repo eid)
  case mRecord of
    Nothing -> pure (exceptionNotFoundPage eid)
    Just _ -> pure (exceptionNotFoundPage eid) -- 詳細ビューは次反復で追加

handler :: ExceptionRepository IO -> Maybe Text -> Handler (Html ())
handler repo mTn = do
  let tn = fromMaybe "" mTn
  if T.null tn
    then pure (exceptionListPage [])
    else do
      records <- liftIO (findExceptionsByTrackingNumber repo tn)
      pure (exceptionListPage (fmap exceptionRecordToRow records))

{- | POST /exceptions/:exceptionId/resolve

ResolveExceptionCommand を呼び、成功時は 303 で `/exceptions` に戻る。
失敗時 (NotFound / 二重解決) は 303 でクエリパラメータにエラーコードを付ける。
権限判定 (Tracker / Admin のみ) は将来 T6-09 で追加予定。
-}
handleResolve ::
  ExceptionRepository IO ->
  Text ->
  Handler (Headers '[Header "Location" Text] NoContent)
handleResolve repo eid = do
  now <- liftIO getCurrentTime
  result <-
    liftIO
      ( Resolve.execute
          repo
          (Resolve.ResolveExceptionInput eid now)
      )
  case result of
    Right _ ->
      pure (addHeader "/exceptions?flash=resolved" NoContent)
    Left ExceptionAlreadyResolved ->
      throwErr ("/exceptions?error=already-resolved&id=" <> eid)
    Left (InvalidExceptionReason "not found") ->
      throwErr ("/exceptions?error=not-found&id=" <> eid)
    Left e ->
      throwErr ("/exceptions?error=unknown&id=" <> eid <> "&detail=" <> T.pack (show e))
  where
    throwErr ::
      Text ->
      Handler (Headers '[Header "Location" Text] NoContent)
    throwErr loc =
      throwError
        err303
          { errHeaders = [("Location", BC.pack (T.unpack loc))]
          }

{- | ExceptionRecord (Domain) → ExceptionRow (View DTO) の変換。

同 BC 内の変換のため Rule 4 対象外。severity / type は Text 列挙表現、
reporter は "userId (Role)" 形式で表示する。
-}
exceptionRecordToRow :: ExceptionRecord -> ExceptionRow
exceptionRecordToRow er =
  ExceptionRow
    { erRowId = erExceptionId er
    , erRowTrackingNumber = erTrackingNumber er
    , erRowType = exceptionTypeToText (erType er)
    , erRowSeverity = levelToText (unSeverity (erSeverity er))
    , erRowReporter = reporterUserId (erReporter er) <> " (" <> reporterRole (erReporter er) <> ")"
    , erRowReportedAt = erReportedAt er
    , erRowResolvedAt = erResolvedAt er
    }

{- | POST /exceptions/delay: US19 遅延例外を登録する。

Cross-BC 統合 (ADR-0014 Phase 2) は Interfaces 層では no-op callback を渡す
(呼出時に markInException を渡すのが本来の実装、次イテレーションで対応)。

失敗時は 303 でクエリパラメータにエラーコードを埋める。
権限判定 (Handler / Tracker) は T6-09 で追加予定。
-}
handleRecordDelay ::
  ExceptionRepository IO ->
  RecordDelayFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handleRecordDelay repo form = do
  now <- liftIO getCurrentTime
  case parseDelayInput form now of
    Left err ->
      throwRedirect ("/exceptions?error=" <> err)
    Right input -> do
      -- ADR-0014 Phase 2: 現段階では markInException を no-op で渡す
      -- (View 用の骨組み目的。将来的に Tracking Cross-BC helper を注入する)
      result <- liftIO (RecordDelay.execute repo (\_ -> pure (Right ())) input)
      case result of
        Right _ ->
          pure (addHeader "/exceptions?flash=delay-recorded" NoContent)
        Left e ->
          throwRedirect ("/exceptions?error=domain&detail=" <> T.pack (show e))
  where
    throwRedirect ::
      Text ->
      Handler (Headers '[Header "Location" Text] NoContent)
    throwRedirect loc =
      throwError
        err303 {errHeaders = [("Location", BC.pack (T.unpack loc))]}

parseDelayInput ::
  RecordDelayFormRequest ->
  UTCTime ->
  Either Text RecordDelay.RecordDelayExceptionInput
parseDelayInput f now = do
  hours <- case reads (T.unpack (formDelayHours f)) :: [(Int, String)] of
    [(n, "")] -> Right n
    _ -> Left "invalid-delay-hours"
  severity <- case textToLevel (T.toUpper (formSeverity f)) of
    Just l -> Right l
    Nothing -> Left "invalid-severity"
  Right
    RecordDelay.RecordDelayExceptionInput
      { RecordDelay.inputExceptionId = formExceptionId f
      , RecordDelay.inputTrackingNumber = formTrackingNumber f
      , RecordDelay.inputDelayHours = hours
      , RecordDelay.inputReason = formReason f
      , RecordDelay.inputSeverityLevel = severity
      , RecordDelay.inputReporterUserId = formReporterUserId f
      , RecordDelay.inputReporterRole = formReporterRole f
      , RecordDelay.inputReportedAt = now
      }

-- | POST /exceptions/damage: US20 破損例外を登録する。
handleRecordDamage ::
  ExceptionRepository IO ->
  RecordDamageFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handleRecordDamage repo form = do
  now <- liftIO getCurrentTime
  case parseDamageInput form now of
    Left err ->
      throwRedirect ("/exceptions?error=" <> err)
    Right input -> do
      result <- liftIO (RecordDamage.execute repo (\_ -> pure (Right ())) input)
      case result of
        Right _ ->
          pure (addHeader "/exceptions?flash=damage-recorded" NoContent)
        Left e ->
          throwRedirect ("/exceptions?error=domain&detail=" <> T.pack (show e))
  where
    throwRedirect ::
      Text ->
      Handler (Headers '[Header "Location" Text] NoContent)
    throwRedirect loc =
      throwError err303 {errHeaders = [("Location", BC.pack (T.unpack loc))]}

parseDamageInput ::
  RecordDamageFormRequest ->
  UTCTime ->
  Either Text RecordDamage.RecordDamageExceptionInput
parseDamageInput f now = do
  value <- case reads (T.unpack (dmgAmountValue f)) :: [(Integer, String)] of
    [(n, "")] -> Right n
    _ -> Left "invalid-amount-value"
  severity <- case textToLevel (T.toUpper (dmgSeverity f)) of
    Just l -> Right l
    Nothing -> Left "invalid-severity"
  Right
    RecordDamage.RecordDamageExceptionInput
      { RecordDamage.inputExceptionId = dmgExceptionId f
      , RecordDamage.inputTrackingNumber = dmgTrackingNumber f
      , RecordDamage.inputAmountValue = value
      , RecordDamage.inputAmountCurrency = dmgAmountCurrency f
      , RecordDamage.inputDescription = dmgDescription f
      , RecordDamage.inputSeverityLevel = severity
      , RecordDamage.inputReporterUserId = dmgReporterUserId f
      , RecordDamage.inputReporterRole = dmgReporterRole f
      , RecordDamage.inputReportedAt = now
      }

-- | POST /exceptions/loss: US20 紛失例外を登録する。
handleRecordLoss ::
  ExceptionRepository IO ->
  RecordLossFormRequest ->
  Handler (Headers '[Header "Location" Text] NoContent)
handleRecordLoss repo form = do
  now <- liftIO getCurrentTime
  case parseLossInput form now of
    Left err ->
      throwRedirect ("/exceptions?error=" <> err)
    Right input -> do
      result <- liftIO (RecordLoss.execute repo (\_ -> pure (Right ())) input)
      case result of
        Right _ ->
          pure (addHeader "/exceptions?flash=loss-recorded" NoContent)
        Left e ->
          throwRedirect ("/exceptions?error=domain&detail=" <> T.pack (show e))
  where
    throwRedirect ::
      Text ->
      Handler (Headers '[Header "Location" Text] NoContent)
    throwRedirect loc =
      throwError err303 {errHeaders = [("Location", BC.pack (T.unpack loc))]}

parseLossInput ::
  RecordLossFormRequest ->
  UTCTime ->
  Either Text RecordLoss.RecordLossExceptionInput
parseLossInput f now = do
  value <- case reads (T.unpack (lossAmountValue f)) :: [(Integer, String)] of
    [(n, "")] -> Right n
    _ -> Left "invalid-amount-value"
  severity <- case textToLevel (T.toUpper (lossSeverity f)) of
    Just l -> Right l
    Nothing -> Left "invalid-severity"
  let mLastSeen =
        let stripped = T.strip (lossLastSeenAt f)
         in if T.null stripped then Nothing else Just stripped
  Right
    RecordLoss.RecordLossExceptionInput
      { RecordLoss.inputExceptionId = lossExceptionId f
      , RecordLoss.inputTrackingNumber = lossTrackingNumber f
      , RecordLoss.inputAmountValue = value
      , RecordLoss.inputAmountCurrency = lossAmountCurrency f
      , RecordLoss.inputLastSeenAt = mLastSeen
      , RecordLoss.inputSeverityLevel = severity
      , RecordLoss.inputReporterUserId = lossReporterUserId f
      , RecordLoss.inputReporterRole = lossReporterRole f
      , RecordLoss.inputReportedAt = now
      }
