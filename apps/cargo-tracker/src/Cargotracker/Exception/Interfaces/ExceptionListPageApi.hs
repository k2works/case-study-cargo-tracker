{-# LANGUAGE DataKinds #-}
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
import Data.Time (getCurrentTime)
import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import qualified Cargotracker.Exception.Application.ResolveExceptionCommand as Resolve
import Cargotracker.Exception.Domain.Model.ExceptionRecord (ExceptionRecord (..))
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( ExceptionSeverity (..),
    levelToText,
  )
import Cargotracker.Exception.Domain.Model.ExceptionType (exceptionTypeToText)
import Cargotracker.Exception.Domain.Model.Reporter (Reporter (..))
import Cargotracker.Exception.Views.ExceptionListView
  ( ExceptionRow (..),
    exceptionListPage,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

type ExceptionListApi =
  "exceptions"
    :> ( QueryParam "trackingNumber" Text :> Get '[HTML] (Html ())
           :<|> Capture "exceptionId" Text
             :> "resolve"
             :> Verb 'POST 303 '[HTML] (Headers '[Header "Location" Text] NoContent)
       )

exceptionListApp :: ExceptionRepository IO -> Application
exceptionListApp repo =
  serve
    (Proxy :: Proxy ExceptionListApi)
    (handler repo :<|> handleResolve repo)

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
