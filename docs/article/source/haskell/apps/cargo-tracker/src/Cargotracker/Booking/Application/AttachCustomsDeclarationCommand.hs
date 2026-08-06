{-# LANGUAGE PatternSynonyms #-}

{- | 通関情報を予約に紐付けるユースケース (US27, IT3)

業務フロー:
1. BookingRepository.findCargoById で予約存在を検証 (なければ BookingNotFound)
2. mkCustomsDeclaration で HS / broker / status を検証
3. CustomsDeclarationRepository.upsertCustomsDeclaration で永続化 (PRG)

ADR-0002 T-01: Application 層がトランザクション境界を張る (本実装は単一 SQL
なので暫定的に Repository の `withTransaction` に委譲する想定)。
ADR-0005 (BCE-03): BookingNotFound は Booking.Domain.Error 経由のパターンで返却。
-}
module Cargotracker.Booking.Application.AttachCustomsDeclarationCommand
  ( AttachCustomsInput (..),
    execute,
  ) where

import Data.Text (Text)

import Cargotracker.Booking.Application.CustomsPorts
  ( CustomsDeclarationRepository (..),
  )
import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
  )
import Cargotracker.Booking.Domain.Error (pattern BookingNotFound)
import Cargotracker.Booking.Domain.Model.CustomsDeclaration
  ( CustomsDeclaration,
    mkCustomsDeclaration,
  )
import Cargotracker.Booking.Domain.Model.State.DeclarationStatus
  ( declarationStatusFromText,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId
  ( BookingId,
    unBookingId,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError)

data AttachCustomsInput = AttachCustomsInput
  { inputBookingId :: !BookingId
  , inputHsCode :: !Text
  , inputBrokerName :: !Text
  , inputStatusText :: !Text
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  CustomsDeclarationRepository m ->
  AttachCustomsInput ->
  m (Either DomainError CustomsDeclaration)
execute bookingRepo customsRepo input = do
  let bid = inputBookingId input
  mCargo <- findCargoById bookingRepo bid
  case mCargo of
    Nothing -> pure (Left (BookingNotFound (unBookingId bid)))
    Just _ ->
      case parseInput input of
        Left e -> pure (Left e)
        Right decl -> do
          result <- upsertCustomsDeclaration customsRepo decl
          case result of
            Left e -> pure (Left e)
            Right () -> pure (Right decl)

parseInput :: AttachCustomsInput -> Either DomainError CustomsDeclaration
parseInput input = do
  status <- declarationStatusFromText (inputStatusText input)
  mkCustomsDeclaration
    (inputBookingId input)
    (inputHsCode input)
    (inputBrokerName input)
    status
