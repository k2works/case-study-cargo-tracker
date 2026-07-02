{-# LANGUAGE OverloadedStrings #-}

{- | Pricing BC の PostgreSQL 実装 CurrencyRateRepository (US21 Phase 8, IT6)

currency_rate テーブルから from → to の有効レートを取得。
`valid_from <= now AND now < valid_to` で境界判定 (Domain 側 isRateValidAt と同一)。

T-02 準拠。
-}
module Cargotracker.Pricing.Infrastructure.PostgresCurrencyRateRepository
  ( newPostgresCurrencyRateRepository,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)
import Database.PostgreSQL.Simple
  ( Connection,
    query,
  )

import Cargotracker.Pricing.Application.Ports
  ( CurrencyRateRepository (..),
  )
import Cargotracker.Pricing.Domain.Model.Value.Cost (Currency (..))
import Cargotracker.Pricing.Domain.Model.Value.CurrencyRate
  ( CurrencyRate (..),
  )

newPostgresCurrencyRateRepository :: Connection -> CurrencyRateRepository IO
newPostgresCurrencyRateRepository conn =
  CurrencyRateRepository
    { findValidRate = findValidRateImpl conn
    }

type CurrencyRateRow = (Text, Text, Integer, UTCTime, UTCTime)

findValidRateImpl ::
  Connection -> Currency -> Currency -> UTCTime -> IO (Maybe CurrencyRate)
findValidRateImpl conn (Currency from) (Currency to) now = do
  rows <-
    query
      conn
      "SELECT from_currency, to_currency, rate, valid_from, valid_to \
      \ FROM currency_rate \
      \ WHERE from_currency = ? AND to_currency = ? \
      \   AND valid_from <= ? AND ? < valid_to \
      \ ORDER BY valid_from DESC LIMIT 1"
      (from, to, now, now) ::
      IO [CurrencyRateRow]
  pure $ case rows of
    ((f, t, r, vf, vt) : _) ->
      Just
        CurrencyRate
          { crFromCurrency = Currency f
          , crToCurrency = Currency t
          , crRate = r
          , crValidFrom = vf
          , crValidTo = vt
          }
    [] -> Nothing
