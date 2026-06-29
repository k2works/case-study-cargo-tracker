{- | Booking Application 層のポート (IT1 US04)

- BookingRepository: 自 BC の集約永続化
- ShipperExistenceChecker: 他 BC (Shipper) への参照を ACL 抽象化
-}
module Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  ) where

import Cargotracker.Booking.Domain.Model.Cargo (Cargo)
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Shared.Domain.DomainError (DomainError)
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef)

-- T-01 (IT2): saveBooking は Infrastructure 側の検証失敗 (例: 荷主サロゲート
-- キー解決不可) を例外で潰さず DomainError として返す。Application 層が
-- Either を観測して呼び出し元に伝播できるようにする。
data BookingRepository m = BookingRepository
  { saveBooking :: Cargo -> m (Either DomainError ())
  , findCargoById :: BookingId -> m (Maybe Cargo)
  , updateBooking :: Cargo -> m (Either DomainError ())
  {- ^ US06 (IT2): 既存 Cargo の状態 / version を更新する。
  楽観ロック衝突や対象不在を DomainError で表現できるよう Either を返す。
  -}
  , findAllCargos :: m [Cargo]
  -- ^ IT2 一覧画面用 (暫定ページング無し、最大 100 件) — IT4 で findCargosPaged へ移行 (ADR-0006)
  }

{-# DEPRECATED
  findAllCargos
  "ADR-0006 PG-03: IT4 で findCargosPaged :: BookingSearchCriteria -> PageReq -> m (Page Cargo) へ\
  \移行する。新規 callsite の追加は避け、既存 callsite は段階的に移行すること。"
  #-}

newtype ShipperExistenceChecker m = ShipperExistenceChecker
  { exists :: ShipperRef -> m Bool
  }
