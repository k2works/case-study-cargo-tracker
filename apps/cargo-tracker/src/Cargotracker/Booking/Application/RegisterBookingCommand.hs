{- | 貨物予約登録コマンド (IT1 US04 3.2)

入力検証 → ACL (荷主存在) → Cargo 集約生成 (Draft) → 永続化。

トランザクション境界 (T-01) は Infrastructure 層の Repository が
`withTransaction` でラップする想定。Application 層は純粋に
ビジネスロジックの記述に集中する。
-}
module Cargotracker.Booking.Application.RegisterBookingCommand
  ( RegisterBookingInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo (cargoBookingId, mkCargo)
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId, mkBookingId)
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Shared.Domain.Common.UnLocode (mkUnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (mkShipperId, unShipperId)

data RegisterBookingInput = RegisterBookingInput
  { inputBookingId :: !Text
  , inputShipperId :: !Text
  , inputOrigin :: !Text
  , inputDestination :: !Text
  , inputDeadline :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  BookingRepository m ->
  ShipperExistenceChecker m ->
  RegisterBookingInput ->
  m (Either DomainError BookingId)
execute repo checker input = case validate input of
  Left e -> pure (Left e)
  Right (bid, sid, route) -> do
    okShipper <- exists checker sid
    if not okShipper
      then pure (Left (ShipperNotFound (unShipperId sid)))
      else do
        let cargo = mkCargo bid sid route
        saveBooking repo cargo
        pure (Right (cargoBookingId cargo))
  where
    validate i = do
      bid <- mkBookingId (inputBookingId i)
      sid <- mkShipperId (inputShipperId i)
      origin <- mkUnLocode (inputOrigin i)
      destination <- mkUnLocode (inputDestination i)
      let route =
            RouteSpecification
              { origin = origin
              , destination = destination
              , arrivalDeadline = inputDeadline i
              }
      Right (bid, sid, route)
