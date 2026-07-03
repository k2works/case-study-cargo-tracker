{- | checkTransitionForException の hedgehog property テスト (ADR-0014, IT7)

3 つの不変条件をプロパティテストで検証する:

* P-1: 禁止 3 状態 (TsNotReceived / TsClaimed / TsInException) からは必ず
  Left InvalidTrackingTransition
* P-2: 許可 6 状態からは必ず Right ()
* P-3: from / to テキストは対応する TransportStatus.transportStatusToText
  と一致 (エラー内容の整合性)
-}
module Tracking.Application.CheckTransitionForExceptionPropertiesSpec (spec) where

import Hedgehog (Gen, Property, check, forAll, property, (===))
import qualified Hedgehog.Gen as Gen
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.TransportStatus
  ( TransportStatus (..),
    transportStatusToText,
  )
import Cargotracker.Tracking.Application.Ports (checkTransitionForException)

genForbidden :: Gen TransportStatus
genForbidden = Gen.element [TsNotReceived, TsClaimed, TsInException]

genAllowed :: Gen TransportStatus
genAllowed =
  Gen.element
    [ TsReceived
    , TsLoaded
    , TsOnboardCarrier
    , TsUnloaded
    , TsAwaitingClaim
    , TsUnknown
    ]

prop_forbiddenAlwaysLeft :: Property
prop_forbiddenAlwaysLeft = property $ do
  s <- forAll genForbidden
  case checkTransitionForException s of
    Left (InvalidTrackingTransition _ _) -> pure ()
    other -> fail ("expected InvalidTrackingTransition, got " <> show other)

prop_allowedAlwaysRight :: Property
prop_allowedAlwaysRight = property $ do
  s <- forAll genAllowed
  checkTransitionForException s === Right ()

prop_errorMessageMatchesTextEncoding :: Property
prop_errorMessageMatchesTextEncoding = property $ do
  s <- forAll genForbidden
  case checkTransitionForException s of
    Left (InvalidTrackingTransition fromText toText) -> do
      fromText === transportStatusToText s
      toText === "TsInException"
    other -> fail ("expected InvalidTrackingTransition, got " <> show other)

spec :: Spec
spec = describe "checkTransitionForException hedgehog properties (ADR-0014, IT7)" $ do
  it "P-1: 禁止 3 状態からは必ず InvalidTrackingTransition" $
    check prop_forbiddenAlwaysLeft >>= assertTrue
  it "P-2: 許可 6 状態からは必ず Right ()" $
    check prop_allowedAlwaysRight >>= assertTrue
  it "P-3: エラーメッセージの from Text は transportStatusToText と一致、to は TsInException" $
    check prop_errorMessageMatchesTextEncoding >>= assertTrue
  where
    assertTrue True = pure ()
    assertTrue False = expectationFailure "hedgehog property failed"
