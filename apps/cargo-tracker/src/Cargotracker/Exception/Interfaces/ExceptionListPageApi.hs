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
import qualified Cargotracker.Exception.Application.RecordDelayExceptionCommand as RecordDelay
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

type ExceptionListApi =
  "exceptions"
    :> ( QueryParam "trackingNumber" Text :> Get '[HTML] (Html ())
           :<|> Capture "exceptionId" Text
             :> "resolve"
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
           :<|> "delay"
             :> ReqBody '[FormUrlEncoded] RecordDelayFormRequest
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
       )

exceptionListApp :: ExceptionRepository IO -> Application
exceptionListApp repo =
  serve
    (Proxy :: Proxy ExceptionListApi)
    (handler repo :<|> handleResolve repo :<|> handleRecordDelay repo)

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
