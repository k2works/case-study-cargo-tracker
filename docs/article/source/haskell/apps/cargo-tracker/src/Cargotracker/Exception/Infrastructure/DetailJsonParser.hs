{-# LANGUAGE OverloadedStrings #-}

{- | Exception BC の detail_json パーサ (US19/US20, IT7)

`PostgresExceptionRepository.saveImpl` で書き出した最小限 JSON を Domain の
`ExceptionType` に復元する pure 関数。ADR-0014 の JSONB detail_json 保存を
Application 層に依存させないためのユーティリティ。

書式 (ExceptionListPageApi の逆変換):

- Delay:  {"delayHours":N,"reason":"..."}
- Damage: {"amount":N,"currency":"XXX","description":"..."}
- Loss:   {"amount":N,"currency":"XXX","lastSeenAt":"..."|null}

aeson 依存を PostgresExceptionRepository に閉じ込め、Domain 層は依然として
純粋 Haskell (Data.Text のみ) で完結する。
-}
module Cargotracker.Exception.Infrastructure.DetailJsonParser
  ( parseDetailJson,
  ) where

import Data.Aeson (Object, Value (..), decode, withObject, (.:), (.:?))
import Data.Aeson.Types (Parser, parseEither)
import qualified Data.ByteString.Lazy as BSL
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE

import Cargotracker.Exception.Domain.Model.Amount (Amount, mkAmount)
import Cargotracker.Exception.Domain.Model.DamageException
  ( DamageException,
    mkDamageException,
  )
import Cargotracker.Exception.Domain.Model.DelayException
  ( DelayException,
    mkDelayException,
  )
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType (..))
import Cargotracker.Exception.Domain.Model.LossException
  ( LossException,
    mkLossException,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

{- | Text の exception_type ("DELAY" / "DAMAGE" / "LOSS") と JSONB detail_json 文字列を
Domain の `ExceptionType` に復元する。

- 型判別列の値が未定義 → Left (InvalidExceptionReason "unknown exception type")
- JSON パース失敗 → Left (InvalidExceptionReason "malformed detail json")
- Domain 検証 (mkDelayException 等) 失敗 → その DomainError を伝播
-}
parseDetailJson :: Text -> Text -> Either DomainError ExceptionType
parseDetailJson typ raw =
  case T.toUpper typ of
    "DELAY" -> Delay <$> parseWith delayParser raw
    "DAMAGE" -> Damage <$> parseWith damageParser raw
    "LOSS" -> Loss <$> parseWith lossParser raw
    _ -> Left (InvalidExceptionReason "unknown exception type")

parseWith :: (Value -> Parser (Either DomainError a)) -> Text -> Either DomainError a
parseWith p raw = case decode (BSL.fromStrict (TE.encodeUtf8 raw)) of
  Nothing -> Left (InvalidExceptionReason "malformed detail json")
  Just v -> case parseEither p v of
    Left _ -> Left (InvalidExceptionReason "malformed detail json")
    Right (Left err) -> Left err
    Right (Right x) -> Right x

delayParser :: Value -> Parser (Either DomainError DelayException)
delayParser = withObject "DelayException" $ \o -> do
  hours <- o .: "delayHours"
  reason <- o .: "reason"
  pure (mkDelayException hours reason)

damageParser :: Value -> Parser (Either DomainError DamageException)
damageParser = withObject "DamageException" $ \o -> do
  amount <- parseAmount o
  description <- o .: "description"
  pure (amount >>= \a -> mkDamageException a description)

lossParser :: Value -> Parser (Either DomainError LossException)
lossParser = withObject "LossException" $ \o -> do
  amount <- parseAmount o
  mLast <- o .:? "lastSeenAt"
  pure (amount >>= \a -> mkLossException a mLast)

parseAmount :: Object -> Parser (Either DomainError Amount)
parseAmount o = do
  value <- o .: "amount"
  currency <- o .: "currency"
  pure (mkAmount value currency)
