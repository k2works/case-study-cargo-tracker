{-# LANGUAGE PatternSynonyms #-}

{- | Booking Application 層のポート (IT1 US04)

- BookingRepository: 自 BC の集約永続化
- ShipperExistenceChecker: 他 BC (Shipper) への参照を ACL 抽象化
- withCargo: Command 共通の load → transition → save パターンヘルパ (M-01, IT4 レビュー)
-}
module Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
    withCargo,
  ) where

import Cargotracker.Booking.Domain.Error (pattern BookingNotFound)
import Cargotracker.Booking.Domain.Model.Cargo (Cargo)
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId, unBookingId)
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

{- | Command 共通の load → transition → save パターンヘルパ (M-01, IT4 レビュー)

5 つの Command (SubmitBooking / HandOverToRouter / LinkRoute / UnlinkRoute /
ConfirmBooking) の execute が同型コードを繰り返していたため共通化。

* `BookingId` で Cargo を取得 (なければ BookingNotFound)
* 純粋な遷移関数 `f :: Cargo -> Either DomainError Cargo` を適用
* 成功時のみ updateBooking で永続化し、更新済 Cargo を返す

各 Command は `execute repo input = withCargo repo (inputBookingId input) Cargo.linkRoute` の
ような 1 行に集約できる。永続化方式・監査ログ追加時の修正箇所が 5 → 1 になる。
-}
withCargo ::
  Monad m =>
  BookingRepository m ->
  BookingId ->
  (Cargo -> Either DomainError Cargo) ->
  m (Either DomainError Cargo)
withCargo repo bid transition = do
  mCargo <- findCargoById repo bid
  case mCargo of
    Nothing -> pure (Left (BookingNotFound (unBookingId bid)))
    Just cargo -> case transition cargo of
      Left e -> pure (Left e)
      Right updated -> do
        result <- updateBooking repo updated
        case result of
          Left e -> pure (Left e)
          Right () -> pure (Right updated)
