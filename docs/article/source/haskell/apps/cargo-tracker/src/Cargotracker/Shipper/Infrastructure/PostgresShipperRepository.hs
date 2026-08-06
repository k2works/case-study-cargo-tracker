{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の ShipperRepository (IT1 US02/US03 2.4)

postgresql-simple を直接利用する。IT1 段階では単一テーブル操作のため
暗黙のトランザクション (execute 1 回) で十分。

スキーマ (db/migrations/20260706120200_create_shipper.sql):
- shipper (id BIGSERIAL PK, shipper_id UK, email UK,
           shipper_kind, corporate_number?, contract_rank?, version, ...)

IT2 (T-09) 反映:
- IT1 では `name` 列に email を placeholder として書き込んでいた負債を解消し、
  Domain `Shipper.shipperName` の値を `name` 列に書き出すように変更。
- SELECT 文も `name` 列を取得し `ShipperName` で復元する。
- 永続化からの復元は値オブジェクトのコンストラクタを直接使う (スマート
  コンストラクタを通さない)。DB CHECK 制約と挿入時のバリデーションで
  不変条件が保たれている前提。
-}
module Cargotracker.Shipper.Infrastructure.PostgresShipperRepository
  ( newPostgresShipperRepository,
  ) where

import Data.Text (Text)
import Database.PostgreSQL.Simple
  ( Connection,
    Only (..),
    execute,
    query,
    query_,
  )

import Cargotracker.Shipper.Application.Ports (ShipperRepository (..))
import Cargotracker.Shipper.Domain.Model.Shipper
  ( ContractRank (..),
    CorporateNumber (..),
    Shipper (..),
    ShipperKind (..),
  )
import Cargotracker.Shipper.Domain.Model.Value.Address (Address (..))
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail
  ( ContactEmail (..),
  )
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperName (ShipperName (..))

newPostgresShipperRepository :: Connection -> ShipperRepository IO
newPostgresShipperRepository conn =
  ShipperRepository
    { findByContactEmail = \(ContactEmail e) -> findByEmail conn e
    , findById = \(ShipperId sid) -> findByShipperId conn sid
    , save = saveShipper conn
    , searchByQuery = \(ContactEmail q) -> searchShippers conn q
    , findAllShippers = listShippers conn
    }

listShippers :: Connection -> IO [Shipper]
listShippers conn = do
  rows <-
    query_
      conn
      "SELECT shipper_id, name, email, address, shipper_kind, corporate_number, contract_rank \
      \ FROM shipper ORDER BY shipper_id LIMIT 100" ::
      IO [(Text, Text, Text, Text, Text, Maybe Text, Maybe Text)]
  pure
    [ s
    | (sidV, nameV, em, addr, kindText, mCn, mRank) <- rows
    , Just s <- [toShipper sidV nameV em addr kindText mCn mRank]
    ]

findByShipperId :: Connection -> Text -> IO (Maybe Shipper)
findByShipperId conn sid = do
  rows <-
    query
      conn
      "SELECT shipper_id, name, email, address, shipper_kind, corporate_number, contract_rank \
      \ FROM shipper WHERE shipper_id = ? LIMIT 1"
      (Only sid) ::
      IO [(Text, Text, Text, Text, Text, Maybe Text, Maybe Text)]
  case rows of
    [(sidV, nameV, em, addr, kindText, mCn, mRank)] ->
      pure (toShipper sidV nameV em addr kindText mCn mRank)
    _ -> pure Nothing

searchShippers :: Connection -> Text -> IO [Shipper]
searchShippers conn q = do
  let pat = "%" <> q <> "%"
  rows <-
    query
      conn
      "SELECT shipper_id, name, email, address, shipper_kind, corporate_number, contract_rank \
      \ FROM shipper WHERE shipper_id ILIKE ? OR email ILIKE ? \
      \ ORDER BY shipper_id LIMIT 10"
      (pat, pat) ::
      IO [(Text, Text, Text, Text, Text, Maybe Text, Maybe Text)]
  pure
    [ s
    | (sidV, nameV, em, addr, kindText, mCn, mRank) <- rows
    , Just s <- [toShipper sidV nameV em addr kindText mCn mRank]
    ]

findByEmail :: Connection -> Text -> IO (Maybe Shipper)
findByEmail conn email = do
  rows <-
    query
      conn
      "SELECT shipper_id, name, email, address, shipper_kind, corporate_number, contract_rank \
      \ FROM shipper WHERE email = ? LIMIT 1"
      (Only email) ::
      IO [(Text, Text, Text, Text, Text, Maybe Text, Maybe Text)]
  case rows of
    [(sid, nameV, em, addr, kindText, mCn, mRank)] ->
      pure (toShipper sid nameV em addr kindText mCn mRank)
    _ -> pure Nothing

toShipper ::
  Text ->
  Text ->
  Text ->
  Text ->
  Text ->
  Maybe Text ->
  Maybe Text ->
  Maybe Shipper
toShipper sid nameV em addr kindText mCn mRank = do
  kind <- parseKind kindText mCn mRank
  Just
    Shipper
      { shipperId = ShipperId sid
      , shipperName = ShipperName nameV
      , shipperEmail = ContactEmail em
      , shipperAddress = Address addr
      , shipperKind = kind
      }
  where
    parseKind "Individual" _ _ = Just Individual
    parseKind "Corporate" (Just cn) (Just rankT) = do
      rank <- parseRank rankT
      Just (Corporate (CorporateNumber cn) rank)
    parseKind _ _ _ = Nothing

    parseRank "Bronze" = Just Bronze
    parseRank "Silver" = Just Silver
    parseRank "Gold" = Just Gold
    parseRank _ = Nothing

saveShipper :: Connection -> Shipper -> IO ()
saveShipper conn s = do
  let (kindText, mCn, mRank) = case shipperKind s of
        Individual -> ("Individual" :: Text, Nothing :: Maybe Text, Nothing :: Maybe Text)
        Corporate (CorporateNumber cn) rank ->
          ("Corporate", Just cn, Just (rankToText rank))
      ContactEmail emailValue = shipperEmail s
      ShipperId sidValue = shipperId s
      ShipperName nameValue = shipperName s
      Address addrValue = shipperAddress s
  _ <-
    execute
      conn
      "INSERT INTO shipper (shipper_id, name, email, address, shipper_kind, corporate_number, contract_rank, version) \
      \ VALUES (?, ?, ?, ?, ?, ?, ?, 1)"
      ( sidValue
      , nameValue -- T-09 (IT2): Domain の正しい name を書き出す
      , emailValue
      , addrValue
      , kindText
      , mCn
      , mRank
      )
  pure ()

rankToText :: ContractRank -> Text
rankToText Bronze = "Bronze"
rankToText Silver = "Silver"
rankToText Gold = "Gold"
