{- | テストエントリポイント (hspec)

各 Bounded Context の Spec モジュールをここから集約する。
hspec-discover を使わず明示的に列挙する方針 (CI の出力が明確になるため)。
-}
module Main (main) where

import Test.Hspec

import Cargotracker (greet)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

import qualified Booking.Application.RegisterBookingCommandSpec
import qualified Booking.Domain.Model.CargoSpec
import qualified Booking.Infrastructure.PostgresBookingRepositorySpec
import qualified Booking.Interfaces.BookingApiSpec
import qualified Booking.Interfaces.BookingPageApiSpec
import qualified Routing.Application.RegisterVoyageCommandSpec
import qualified Routing.Domain.Model.VoyageSpec
import qualified Routing.Infrastructure.PostgresVoyageRepositorySpec
import qualified Routing.Interfaces.VoyageApiSpec
import qualified Routing.Interfaces.VoyagePageApiSpec
import qualified Shared.Auth.Application.LoginCommandSpec
import qualified Shared.Auth.Domain.UserSpec
import qualified Shared.Auth.Infrastructure.BcryptVerifierSpec
import qualified Shared.Auth.Infrastructure.JwtIssuerSpec
import qualified Shared.Auth.Infrastructure.PostgresUserRepositorySpec
import qualified Shared.Auth.Interfaces.LoginApiSpec
import qualified Shared.Auth.Interfaces.LoginPageApiSpec
import qualified Shared.Auth.Interfaces.ProtectedSpec
import qualified Shared.Domain.Common.UnLocodeSpec
import qualified Shipper.Application.RegisterShipperCommandSpec
import qualified Shipper.Domain.Model.ShipperSpec
import qualified Shipper.Infrastructure.PostgresShipperRepositorySpec
import qualified Shipper.Interfaces.ShipperApiSpec
import qualified Shipper.Interfaces.ShipperPageApiSpec

main :: IO ()
main = hspec $ do
  describe "Cargotracker (stub)" $
    it "greet で起動メッセージを返す" $
      greet "world" `shouldBe` "Hello, world! Cargo Tracker (Haskell) is alive."

  describe "DomainError" $ do
    it "InvalidBookingId と InvalidUnLocode は別エラー" $
      InvalidBookingId "x" `shouldNotBe` InvalidUnLocode "x"
    it "ConcurrentModification の Show 表現が読める" $
      show (ConcurrentModification "BK-A1B2C3")
        `shouldBe` "ConcurrentModification \"BK-A1B2C3\""

  describe "Cargotracker.Shared.Auth.Domain.User" Shared.Auth.Domain.UserSpec.spec

  describe
    "Cargotracker.Shared.Auth.Application.LoginCommand"
    Shared.Auth.Application.LoginCommandSpec.spec

  describe
    "Cargotracker.Shared.Auth.Infrastructure.BcryptVerifier"
    Shared.Auth.Infrastructure.BcryptVerifierSpec.spec

  describe
    "Cargotracker.Shared.Auth.Infrastructure.JwtIssuer"
    Shared.Auth.Infrastructure.JwtIssuerSpec.spec

  describe
    "Cargotracker.Shared.Auth.Infrastructure.PostgresUserRepository"
    Shared.Auth.Infrastructure.PostgresUserRepositorySpec.spec

  describe
    "Cargotracker.Shared.Auth.Interfaces.LoginApi"
    Shared.Auth.Interfaces.LoginApiSpec.spec

  describe
    "Cargotracker.Shared.Auth.Interfaces.Protected"
    Shared.Auth.Interfaces.ProtectedSpec.spec

  describe
    "Cargotracker.Shared.Auth.Interfaces.LoginPageApi"
    Shared.Auth.Interfaces.LoginPageApiSpec.spec

  describe
    "Cargotracker.Shipper.Domain.Model.Shipper"
    Shipper.Domain.Model.ShipperSpec.spec

  describe
    "Cargotracker.Shared.Domain.Common.UnLocode"
    Shared.Domain.Common.UnLocodeSpec.spec

  describe
    "Cargotracker.Booking.Domain.Model.Cargo"
    Booking.Domain.Model.CargoSpec.spec

  describe
    "Cargotracker.Routing.Domain.Model.Voyage"
    Routing.Domain.Model.VoyageSpec.spec

  describe
    "Cargotracker.Shipper.Application.RegisterShipperCommand"
    Shipper.Application.RegisterShipperCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.RegisterBookingCommand"
    Booking.Application.RegisterBookingCommandSpec.spec

  describe
    "Cargotracker.Routing.Application.RegisterVoyageCommand"
    Routing.Application.RegisterVoyageCommandSpec.spec

  describe
    "Cargotracker.Shipper.Interfaces.ShipperApi"
    Shipper.Interfaces.ShipperApiSpec.spec

  describe
    "Cargotracker.Shipper.Infrastructure.PostgresShipperRepository"
    Shipper.Infrastructure.PostgresShipperRepositorySpec.spec

  describe
    "Cargotracker.Booking.Interfaces.BookingApi"
    Booking.Interfaces.BookingApiSpec.spec

  describe
    "Cargotracker.Booking.Infrastructure.PostgresBookingRepository"
    Booking.Infrastructure.PostgresBookingRepositorySpec.spec

  describe
    "Cargotracker.Routing.Interfaces.VoyageApi"
    Routing.Interfaces.VoyageApiSpec.spec

  describe
    "Cargotracker.Routing.Infrastructure.PostgresVoyageRepository"
    Routing.Infrastructure.PostgresVoyageRepositorySpec.spec

  describe
    "Cargotracker.Shipper.Interfaces.ShipperPageApi"
    Shipper.Interfaces.ShipperPageApiSpec.spec

  describe
    "Cargotracker.Booking.Interfaces.BookingPageApi"
    Booking.Interfaces.BookingPageApiSpec.spec

  describe
    "Cargotracker.Routing.Interfaces.VoyagePageApi"
    Routing.Interfaces.VoyagePageApiSpec.spec
