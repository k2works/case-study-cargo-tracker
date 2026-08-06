{- | 通関情報 Application 層のポート (US27, IT3)

CustomsDeclarationRepository: 予約 1 件に対して通関申告は 0..1 で対応する
(data-model.md §customs_declaration)。`upsertByBookingId` で挿入・更新を
冪等に処理する。

ADR-0002 T-02: Repository は IO のみ。トランザクション境界 (`withTransaction`)
は Application 層が張る。
-}
module Cargotracker.Booking.Application.CustomsPorts
  ( CustomsDeclarationRepository (..),
  ) where

import Cargotracker.Booking.Domain.Model.CustomsDeclaration (CustomsDeclaration)
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data CustomsDeclarationRepository m = CustomsDeclarationRepository
  { upsertCustomsDeclaration :: CustomsDeclaration -> m (Either DomainError ())
  -- ^ 既存通関情報があれば更新、なければ挿入。BookingId が一意キー。
  , findByBookingId :: BookingId -> m (Maybe CustomsDeclaration)
  -- ^ 予約詳細画面・編集画面でのプリフィル用
  }
