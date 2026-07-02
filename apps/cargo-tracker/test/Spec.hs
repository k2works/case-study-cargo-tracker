{- | テストエントリポイント (hspec)

各 Bounded Context の Spec モジュールをここから集約する。
hspec-discover を使わず明示的に列挙する方針 (CI の出力が明確になるため)。
-}
module Main (main) where

import Test.Hspec

import Cargotracker (greet)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

import qualified Booking.Application.AttachCustomsDeclarationCommandSpec
import qualified Booking.Application.CancelBookingCommandSpec
import qualified Booking.Application.ConfirmBookingCommandSpec
import qualified Booking.Application.ConfirmRouteCommandSpec
import qualified Booking.Application.HandOverToRouterCommandSpec
import qualified Booking.Application.LinkRouteCommandSpec
import qualified Booking.Application.RegisterBookingCommandSpec
import qualified Booking.Application.SubmitBookingCommandSpec
import qualified Booking.Application.UnlinkRouteCommandSpec
import qualified Booking.Domain.Model.CargoSpec
import qualified Booking.Domain.Model.CustomsDeclarationSpec
import qualified Booking.Domain.Model.ItinerarySpec
import qualified Booking.Domain.Model.State.BookingStatusPropertiesSpec
import qualified Booking.Domain.Model.State.BookingStatusSpec
import qualified Booking.Domain.Model.Value.CancellationFeeSpec
import qualified Booking.Domain.Model.Value.CargoTypeSpec
import qualified Booking.Domain.Service.CancellationPolicyPropertiesSpec
import qualified Booking.Domain.Service.CancellationPolicySpec
import qualified Booking.Infrastructure.PostgresBookingRepositorySpec
import qualified Booking.Interfaces.BookingApiSpec
import qualified Booking.Interfaces.BookingPageApiSpec
import qualified Booking.Views.CancellationFeeViewSpec
import qualified Booking.Views.RouteConfirmViewSpec
import qualified Domain.PropertiesSpec
import qualified Estimation.Application.CreateEstimateCommandSpec
import qualified Estimation.Application.EvaluateRouteCandidatesCommandSpec
import qualified Estimation.Domain.Model.EstimateSpec
import qualified Estimation.Domain.Service.RouteEvaluatorPropertiesSpec
import qualified Estimation.Domain.Service.RouteEvaluatorSpec
import qualified Estimation.Interfaces.EstimatePageApiSpec
import qualified Estimation.Views.RouteEvaluationViewSpec
import qualified Routing.Application.ComputeRouteCandidatesQuerySpec
import qualified Routing.Application.RegisterVoyageCommandSpec
import qualified Routing.Application.SearchVoyagesQuerySpec
import qualified Routing.Application.UpdateVoyageCommandSpec
import qualified Routing.Domain.Model.VoyageSpec
import qualified Routing.Domain.Service.RouteFinderPropertiesSpec
import qualified Routing.Domain.Service.RouteFinderSpec
import qualified Routing.Domain.Service.VoyageQuerySpec
import qualified Routing.Infrastructure.PostgresVoyageRepositorySpec
import qualified Routing.Interfaces.VoyageApiSpec
import qualified Routing.Interfaces.VoyageMovementRowApiSpec
import qualified Routing.Interfaces.VoyagePageApiSpec
import qualified Shared.Application.TxRunnerSpec
import qualified Shared.Auth.Application.LoginCommandSpec
import qualified Shared.Auth.Domain.UserSpec
import qualified Shared.Auth.Infrastructure.BcryptVerifierSpec
import qualified Shared.Auth.Infrastructure.JwtIssuerSpec
import qualified Shared.Auth.Infrastructure.PostgresUserRepositorySpec
import qualified Shared.Auth.Interfaces.LoginApiSpec
import qualified Shared.Auth.Interfaces.LoginPageApiSpec
import qualified Shared.Auth.Interfaces.ProtectedSpec
import qualified Shared.Auth.Interfaces.SessionAuthMiddlewareSpec
import qualified Shared.Auth.Interfaces.SessionAuthSpec
import qualified Shared.Domain.Common.UnLocodeSpec
import qualified Shared.Infrastructure.IdGeneratorSpec
import qualified Shared.Security.BcryptHashSpec
import qualified Shared.Security.ConstantTimeSpec
import qualified Shared.Web.LayoutSpec
import qualified Shipper.Application.RegisterShipperCommandSpec
import qualified Shipper.Domain.Model.ShipperSpec
import qualified Shipper.Infrastructure.PostgresShipperRepositorySpec
import qualified Shipper.Interfaces.ShipperApiSpec
import qualified Shipper.Interfaces.ShipperPageApiSpec
import qualified Shipper.Interfaces.ShipperSearchApiSpec
import qualified Tracking.Application.MarkClaimedSpec
import qualified Tracking.Domain.Model.ConfirmationCodeSpec
import qualified Tracking.Views.ClaimNotificationViewSpec

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
    "Cargotracker.Shared.Auth.Interfaces.SessionAuth"
    Shared.Auth.Interfaces.SessionAuthSpec.spec

  describe
    "Cargotracker.Shared.Auth.Interfaces.SessionAuth (middleware)"
    Shared.Auth.Interfaces.SessionAuthMiddlewareSpec.spec

  describe
    "Cargotracker.Shipper.Domain.Model.Shipper"
    Shipper.Domain.Model.ShipperSpec.spec

  describe
    "Cargotracker.Shared.Domain.Common.UnLocode"
    Shared.Domain.Common.UnLocodeSpec.spec

  describe
    "Cargotracker.Shared.Security.ConstantTime"
    Shared.Security.ConstantTimeSpec.spec

  describe
    "Cargotracker.Shared.Security.BcryptHash"
    Shared.Security.BcryptHashSpec.spec

  describe
    "Cargotracker.Shared.Application.TxRunner"
    Shared.Application.TxRunnerSpec.spec

  describe
    "Cargotracker.Shared.Infrastructure.IdGenerator"
    Shared.Infrastructure.IdGeneratorSpec.spec

  describe
    "Cargotracker.Shared.Web.Layout"
    Shared.Web.LayoutSpec.spec

  describe
    "Cargotracker.Booking.Domain.Model.Cargo"
    Booking.Domain.Model.CargoSpec.spec

  describe
    "Cargotracker.Booking.Domain.Model.Value.CargoType"
    Booking.Domain.Model.Value.CargoTypeSpec.spec

  describe
    "Cargotracker.Booking.Domain.Model.CustomsDeclaration"
    Booking.Domain.Model.CustomsDeclarationSpec.spec

  describe
    "Cargotracker.Booking.Views.CancellationFeeView"
    Booking.Views.CancellationFeeViewSpec.spec

  describe
    "Cargotracker.Booking.Views.RouteConfirmView"
    Booking.Views.RouteConfirmViewSpec.spec

  describe
    "Cargotracker.Booking.Domain.Service.CancellationPolicy"
    Booking.Domain.Service.CancellationPolicySpec.spec

  describe
    "Cargotracker.Booking.Domain.Service.CancellationPolicy (hedgehog)"
    Booking.Domain.Service.CancellationPolicyPropertiesSpec.spec

  describe
    "Cargotracker.Booking.Domain.Model.Value.CancellationFee"
    Booking.Domain.Model.Value.CancellationFeeSpec.spec

  describe
    "Cargotracker.Tracking.Domain.Model.ConfirmationCode"
    Tracking.Domain.Model.ConfirmationCodeSpec.spec

  describe
    "Cargotracker.Tracking.Application.markClaimedByBookingId"
    Tracking.Application.MarkClaimedSpec.spec

  describe
    "Cargotracker.Tracking.Views.ClaimNotificationView"
    Tracking.Views.ClaimNotificationViewSpec.spec

  describe
    "Cargotracker.Booking.Domain.Model.Itinerary"
    Booking.Domain.Model.ItinerarySpec.spec

  describe
    "Cargotracker.Booking.Domain.Model.State.BookingStatus"
    Booking.Domain.Model.State.BookingStatusSpec.spec

  describe
    "Cargotracker.Booking.Domain.Model.State.BookingStatus (hedgehog)"
    Booking.Domain.Model.State.BookingStatusPropertiesSpec.spec

  describe
    "Cargotracker.Estimation.Domain.Service.RouteEvaluator"
    Estimation.Domain.Service.RouteEvaluatorSpec.spec

  describe
    "Cargotracker.Estimation.Domain.Service.RouteEvaluator (hedgehog)"
    Estimation.Domain.Service.RouteEvaluatorPropertiesSpec.spec

  describe
    "Cargotracker.Estimation.Application.EvaluateRouteCandidatesCommand"
    Estimation.Application.EvaluateRouteCandidatesCommandSpec.spec

  describe
    "Cargotracker.Estimation.Views.RouteEvaluationView"
    Estimation.Views.RouteEvaluationViewSpec.spec

  describe
    "Cargotracker.Estimation.Domain.Model.Estimate"
    Estimation.Domain.Model.EstimateSpec.spec

  describe
    "Cargotracker.Estimation.Application.CreateEstimateCommand"
    Estimation.Application.CreateEstimateCommandSpec.spec

  describe
    "Cargotracker.Estimation.Interfaces.EstimatePageApi"
    Estimation.Interfaces.EstimatePageApiSpec.spec

  describe
    "Cargotracker.Routing.Domain.Model.Voyage"
    Routing.Domain.Model.VoyageSpec.spec

  describe
    "Cargotracker.Routing.Domain.Service.VoyageQuery"
    Routing.Domain.Service.VoyageQuerySpec.spec

  describe
    "Cargotracker.Routing.Domain.Service.RouteFinder"
    Routing.Domain.Service.RouteFinderSpec.spec

  describe
    "Cargotracker.Routing.Domain.Service.RouteFinder (hedgehog)"
    Routing.Domain.Service.RouteFinderPropertiesSpec.spec

  describe
    "Cargotracker.Shipper.Application.RegisterShipperCommand"
    Shipper.Application.RegisterShipperCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.RegisterBookingCommand"
    Booking.Application.RegisterBookingCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.HandOverToRouterCommand"
    Booking.Application.HandOverToRouterCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.SubmitBookingCommand"
    Booking.Application.SubmitBookingCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.ConfirmBookingCommand"
    Booking.Application.ConfirmBookingCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.ConfirmRouteCommand"
    Booking.Application.ConfirmRouteCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.CancelBookingCommand"
    Booking.Application.CancelBookingCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.LinkRouteCommand"
    Booking.Application.LinkRouteCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.UnlinkRouteCommand"
    Booking.Application.UnlinkRouteCommandSpec.spec

  describe
    "Cargotracker.Booking.Application.AttachCustomsDeclarationCommand"
    Booking.Application.AttachCustomsDeclarationCommandSpec.spec

  describe
    "Cargotracker.Routing.Application.RegisterVoyageCommand"
    Routing.Application.RegisterVoyageCommandSpec.spec

  describe
    "Cargotracker.Routing.Application.UpdateVoyageCommand"
    Routing.Application.UpdateVoyageCommandSpec.spec

  describe
    "Cargotracker.Routing.Application.SearchVoyagesQuery"
    Routing.Application.SearchVoyagesQuerySpec.spec

  describe
    "Cargotracker.Routing.Application.ComputeRouteCandidatesQuery"
    Routing.Application.ComputeRouteCandidatesQuerySpec.spec

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

  describe
    "Cargotracker.Shipper.Interfaces.ShipperSearchApi"
    Shipper.Interfaces.ShipperSearchApiSpec.spec

  describe
    "Cargotracker.Routing.Interfaces.VoyageMovementRowApi"
    Routing.Interfaces.VoyageMovementRowApiSpec.spec

  Domain.PropertiesSpec.spec
