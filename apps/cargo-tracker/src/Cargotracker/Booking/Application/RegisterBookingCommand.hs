{- | 貨物予約登録コマンド (IT1 US04 3.2)

入力検証 → ACL (荷主存在) → Cargo 集約生成 (Draft) → 永続化。

トランザクション境界 (T-01) は Infrastructure 層の Repository が
`withTransaction` でラップする想定。Application 層は純粋に
ビジネスロジックの記述に集中する。
-}
module Cargotracker.Booking.Application.RegisterBookingCommand
  ( RegisterBookingInput (..),
    CargoTypeInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo (cargoBookingId, mkCargoWithType)
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId, mkBookingId)
import Cargotracker.Booking.Domain.Model.Value.CargoType (CargoType (..))
import Cargotracker.Booking.Domain.Model.Value.HazardousDeclaration
  ( mkHazardousDeclaration,
  )
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Booking.Domain.Model.Value.TemperatureRequirement
  ( mkTemperatureRequirement,
    parseTemperatureUnit,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (mkUnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..), mkShipperRef)

-- US04+US05 (IT2): フォーム / API 入力から CargoType を構築するための DTO。
-- HTML form の cargoType select + 条件付きフィールドに対応する。
data CargoTypeInput
  = InputGeneral
  | -- | hazardousClass, unNumber, properShippingName
    InputHazardous !Text !Text !Text
  | -- | minTemperature, maxTemperature, temperatureUnit ("C" or "F")
    InputRefrigerated !Double !Double !Text
  deriving stock (Eq, Show)

data RegisterBookingInput = RegisterBookingInput
  { inputBookingId :: !Text
  , inputShipperId :: !Text
  , inputOrigin :: !Text
  , inputDestination :: !Text
  , inputDeadline :: !UTCTime
  , inputCargoType :: !CargoTypeInput
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
  Right (bid, sid, route, ctype) -> do
    okShipper <- exists checker sid
    if not okShipper
      then pure (Left (ShipperNotFound (unShipperRef sid)))
      else do
        let cargo = mkCargoWithType bid sid route ctype
        saveResult <- saveBooking repo cargo
        case saveResult of
          Left e -> pure (Left e)
          Right () -> pure (Right (cargoBookingId cargo))
  where
    validate i = do
      bid <- mkBookingId (inputBookingId i)
      sid <- mkShipperRef (inputShipperId i)
      origin <- mkUnLocode (inputOrigin i)
      destination <- mkUnLocode (inputDestination i)
      ctype <- buildCargoType (inputCargoType i)
      let route =
            RouteSpecification
              { origin = origin
              , destination = destination
              , arrivalDeadline = inputDeadline i
              }
      Right (bid, sid, route, ctype)

    buildCargoType :: CargoTypeInput -> Either DomainError CargoType
    buildCargoType InputGeneral = Right General
    buildCargoType (InputHazardous cls un name) = do
      decl <- mkHazardousDeclaration cls un name
      Right (Hazardous decl)
    buildCargoType (InputRefrigerated lo hi unitText) = do
      unit <- parseTemperatureUnit unitText
      req <- mkTemperatureRequirement lo hi unit
      Right (Refrigerated req)
