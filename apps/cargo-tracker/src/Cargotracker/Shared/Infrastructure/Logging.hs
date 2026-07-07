{-# LANGUAGE OverloadedStrings #-}

{- | 構造化ログ基盤 (T4-15 IT5 → T7-H katip 正式化, IT8)

IT5-IT7 の自作 aeson JSON Lines 実装を katip に置換した (T6-07 = T7-H)。
既存呼出側 (Main / LogDeliveryPort) の API (`logInfo` / `logError` /
`withCorrelationId` / `newCorrelationId`) は互換のまま維持する。

- Scribe: stderr + `jsonFormat` (JSON Lines、CloudWatch / jq で解析可能)
- Namespace: "cargo-tracker"
- 付加コンテキスト ((Text, Value) リスト) は katip の LogContexts として
  `data` フィールドに構造化される
- correlation_id は `withCorrelationId` がコンテキストに積み、
  action:start / action:end の両ログへ伝搬する

出力例 (katip jsonFormat):
  {"at":"...","env":"app","ns":["cargo-tracker"],"data":{"path":"/health"},"sev":"Info","msg":"HTTP request",...}

グローバル LogEnv は Composition Root を汚さないための意図的な
プロセス単位シングルトン (NOINLINE + unsafePerformIO)。
-}
module Cargotracker.Shared.Infrastructure.Logging
  ( LogLevel (..),
    logInfo,
    logError,
    withCorrelationId,
    newCorrelationId,
  ) where

import Data.Aeson (ToJSON (toJSON), Value)
import Data.Text (Text)
import qualified Data.UUID as UUID
import qualified Data.UUID.V4 as UUIDv4
import Katip
  ( ColorStrategy (ColorIfTerminal),
    LogContexts,
    LogEnv,
    Severity (ErrorS, InfoS, WarningS),
    Verbosity (V2),
    defaultScribeSettings,
    initLogEnv,
    jsonFormat,
    liftPayload,
    logF,
    logStr,
    mkHandleScribeWithFormatter,
    permitItem,
    registerScribe,
    runKatipT,
    sl,
  )
import System.IO (stderr)
import System.IO.Unsafe (unsafePerformIO)

data LogLevel = Info | Warn | Error
  deriving stock (Eq, Show)

toSeverity :: LogLevel -> Severity
toSeverity Info = InfoS
toSeverity Warn = WarningS
toSeverity Error = ErrorS

{- | プロセス単位の LogEnv シングルトン (T7-H)。
`closeScribes` は呼ばない (プロセス終了まで stderr scribe を維持)。
-}
globalLogEnv :: LogEnv
globalLogEnv = unsafePerformIO $ do
  scribe <-
    mkHandleScribeWithFormatter
      jsonFormat
      ColorIfTerminal
      stderr
      (permitItem InfoS)
      V2
  env <- initLogEnv "cargo-tracker" "app"
  registerScribe "stderr" scribe defaultScribeSettings env
{-# NOINLINE globalLogEnv #-}

{- | 情報ログを katip 経由で JSON Lines 出力する。
(Text, Value) リストは katip コンテキストの `data` に構造化される。
-}
logInfo :: Text -> [(Text, Value)] -> IO ()
logInfo = writeLog Info

-- | エラーレベルログ。Servant グローバル例外ハンドラから呼び出す想定。
logError :: Text -> [(Text, Value)] -> IO ()
logError = writeLog Error

writeLog :: LogLevel -> Text -> [(Text, Value)] -> IO ()
writeLog lvl msg extras =
  runKatipT globalLogEnv $
    logF (extrasContext extras) mempty (toSeverity lvl) (logStr msg)

extrasContext :: [(Text, Value)] -> LogContexts
extrasContext = liftPayload . foldMap (uncurry sl)

{- | T6-07 (IT7): UUID v4 の Text 表現を新規 correlation_id として生成する。
Servant Handler の入口で呼び出し、リクエスト全体をまたぐ相関 ID とする。
出力例: "0f8fad5b-d9cb-469f-a165-70867728950e"
-}
newCorrelationId :: IO Text
newCorrelationId = UUID.toText <$> UUIDv4.nextRandom

{- | correlation_id を付けて処理を実行し、成功/失敗を問わず start/end の
ログを出力する。T7-H: katip コンテキストとして correlation_id が
`data` フィールドに構造化される。
-}
withCorrelationId :: Text -> IO a -> IO a
withCorrelationId corrId action = do
  logWith "action:start"
  a <- action
  logWith "action:end"
  pure a
  where
    logWith msg = logInfo msg [("correlation_id", toJSON corrId)]
