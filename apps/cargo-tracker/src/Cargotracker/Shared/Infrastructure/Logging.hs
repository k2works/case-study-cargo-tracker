{-# LANGUAGE OverloadedStrings #-}

{- | 構造化ログ基盤 (T4-15 / task 3.3, IT5)

katip 導入は依存追加が必要なため、IT5 段階では既存 aeson で JSON 出力する
軽量実装で置き換える。CloudWatch や jq で解析可能な JSON Lines 形式で
stderr に出力する。

出力例:
  {"level":"INFO","message":"HTTP request","path":"/health","status":200,"latency_ms":3}
  {"level":"ERROR","message":"Servant exception","path":"/bookings/BK-X/confirm","error":"ConcurrentModification","correlation_id":"abc123"}

IT6 で katip / co-log に置換予定 (ADR で判断)。
-}
module Cargotracker.Shared.Infrastructure.Logging
  ( LogLevel (..),
    logInfo,
    logError,
    withCorrelationId,
  ) where

import Data.Aeson (KeyValue ((.=)), Value, encode, object)
import qualified Data.Aeson.Key as Key
import qualified Data.ByteString.Lazy.Char8 as LBC
import Data.Text (Text)
import System.IO (hPutStrLn, stderr)

data LogLevel = Info | Warn | Error
  deriving stock (Eq, Show)

levelText :: LogLevel -> Text
levelText Info = "INFO"
levelText Warn = "WARN"
levelText Error = "ERROR"

{- | 情報ログを JSON Lines 形式で stderr に出力する。
第 2 引数の (Text, Value) リストに付加コンテキスト (path, booking_id 等) を
含めれば CloudWatch / jq 側で構造化検索できる。
-}
logInfo :: Text -> [(Text, Value)] -> IO ()
logInfo = writeLog Info

-- | エラーレベルログ。Servant グローバル例外ハンドラから呼び出す想定。
logError :: Text -> [(Text, Value)] -> IO ()
logError = writeLog Error

writeLog :: LogLevel -> Text -> [(Text, Value)] -> IO ()
writeLog lvl msg extras = do
  let base =
        [ "level" .= levelText lvl
        , "message" .= msg
        ]
      full = base <> map (\(k, v) -> Key.fromText k .= v) extras
  hPutStrLn stderr (LBC.unpack (encode (object full)))

{- | correlation_id を付けて処理を実行し、成功/失敗を問わず start/end の
JSON ログを出力する。Servant Handler の外側で使う想定。
IT5 段階では単純な wrapper のみ (IT6 で UUID 生成と組み合わせて拡張)。
-}
withCorrelationId :: Text -> IO a -> IO a
withCorrelationId corrId action = do
  logInfo "action:start" [("correlation_id", encodeText corrId)]
  a <- action
  logInfo "action:end" [("correlation_id", encodeText corrId)]
  pure a
  where
    encodeText :: Text -> Value
    encodeText t = object ["value" .= t]

-- WAI ミドルウェアは app/Main.hs 側で wai-extra の logStdoutDev を組み立てる
-- (library に wai-extra 依存を追加しないため、Middleware は Interfaces 層で構成する)
