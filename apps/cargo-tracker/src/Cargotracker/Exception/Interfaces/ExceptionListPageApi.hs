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
import Data.Maybe (fromMaybe)
import Data.Text (Text)
import qualified Data.Text as T
import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
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

type ExceptionListApi =
  "exceptions"
    :> QueryParam "trackingNumber" Text
    :> Get '[HTML] (Html ())

exceptionListApp :: ExceptionRepository IO -> Application
exceptionListApp repo =
  serve (Proxy :: Proxy ExceptionListApi) (handler repo)

handler :: ExceptionRepository IO -> Maybe Text -> Handler (Html ())
handler repo mTn = do
  let tn = fromMaybe "" mTn
  if T.null tn
    then pure (exceptionListPage [])
    else do
      records <- liftIO (findExceptionsByTrackingNumber repo tn)
      pure (exceptionListPage (fmap exceptionRecordToRow records))

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
