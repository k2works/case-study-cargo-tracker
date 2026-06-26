{-# LANGUAGE OverloadedStrings #-}

{- | PostgreSQL 実装の ShipperRepository (IT1 US02/US03 2.4)

postgresql-simple を直接利用する。IT1 段階では単一テーブル操作のため
暗黙のトランザクション (execute 1 回) で十分。

スキーマ (db/migrations/20260706120200_create_shipper.sql):
- shipper (id BIGSERIAL PK, shipper_id UK, email UK,
           shipper_kind, corporate_number?, contract_rank?, version, ...)

IT1 注記:
- スキーマには `name` 列があるが Domain の Shipper には name フィールドが
  ないため、プレースホルダとして email を name 列にも書き込む。
  IT2 で Domain と UI に name を導入したら正しい値に置き換える。
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

newPostgresShipperRepository :: Connection -> ShipperRepository IO
newPostgresShipperRepository conn =
  ShipperRepository
    { findByContactEmail = \(ContactEmail e) -> findByEmail conn e
    , save = saveShipper conn
    }

findByEmail :: Connection -> Text -> IO (Maybe Shipper)
findByEmail conn email = do
  rows <-
    query
      conn
      "SELECT shipper_id, email, address, shipper_kind, corporate_number, contract_rank \
      \ FROM shipper WHERE email = ? LIMIT 1"
      (Only email) ::
      IO [(Text, Text, Text, Text, Maybe Text, Maybe Text)]
  case rows of
    [(sid, em, addr, kindText, mCn, mRank)] ->
      pure (toShipper sid em addr kindText mCn mRank)
    _ -> pure Nothing

toShipper :: Text -> Text -> Text -> Text -> Maybe Text -> Maybe Text -> Maybe Shipper
toShipper sid em addr kindText mCn mRank = do
  kind <- parseKind kindText mCn mRank
  Just
    Shipper
      { shipperId = ShipperId sid
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
      Address addrValue = shipperAddress s
  _ <-
    execute
      conn
      "INSERT INTO shipper (shipper_id, name, email, address, shipper_kind, corporate_number, contract_rank, version) \
      \ VALUES (?, ?, ?, ?, ?, ?, ?, 1)"
      ( sidValue
      , emailValue -- IT1 プレースホルダ
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
