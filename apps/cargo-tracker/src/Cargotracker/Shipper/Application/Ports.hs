{- | Shipper Application 層のポート (IT1 US02/US03)

`ShipperRepository` を Application が依存する。Infrastructure 層で
PostgreSQL 実装を注入する (IT1 後半 or IT2 で本実装)。
-}
module Cargotracker.Shipper.Application.Ports
  ( ShipperRepository (..),
    resolveDiscountPercentageByShipperId,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Domain.Model.Shipper (Shipper, discountPercentage)
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (ContactEmail)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (ShipperId, mkShipperId)

data ShipperRepository m = ShipperRepository
  { findByContactEmail :: ContactEmail -> m (Maybe Shipper)
  , findById :: ShipperId -> m (Maybe Shipper)
  , save :: Shipper -> m ()
  , searchByQuery :: ContactEmail -> m [Shipper]
  , findAllShippers :: m [Shipper]
  -- ^ IT2 一覧画面用 (暫定ページング無し、最大 100 件) — IT4 で findShippersPaged へ移行 (ADR-0006)
  }

{-# DEPRECATED
  findAllShippers
  "ADR-0006 PG-03: IT4 で findShippersPaged :: PageReq -> m (Page Shipper) へ移行する。\
  \新規 callsite の追加は避け、既存 callsite は段階的に移行すること。"
  #-}

{- | Cross-BC helper: raw shipperId (Text) から契約割引率 (0-100 Integer) を解決する (US22, IT7)。

Pricing BC 等の他 BC が Shipper.Shipper 型に依存せず Text-DTO 経由で割引率のみを
取得できる (ADR-0012 / ADR-0004 Rule 4 準拠)。

失敗パス:

* raw shipperId の形式違反 → InvalidShipperId (mkShipperId 由来)
* shipperId が存在しない → ShipperNotFound raw
-}
resolveDiscountPercentageByShipperId ::
  Monad m =>
  ShipperRepository m ->
  Text ->
  m (Either DomainError Integer)
resolveDiscountPercentageByShipperId repo raw =
  case mkShipperId raw of
    Left err -> pure (Left err)
    Right sid -> do
      mShipper <- findById repo sid
      pure
        ( case mShipper of
            Nothing -> Left (ShipperNotFound raw)
            Just s -> Right (discountPercentage s)
        )
