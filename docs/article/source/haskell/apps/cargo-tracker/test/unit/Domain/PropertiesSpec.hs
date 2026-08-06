{-# LANGUAGE OverloadedStrings #-}

{- | ドメイン値オブジェクトのプロパティテスト (T-05, IT2)

hedgehog でランダム生成された入力に対し、スマートコンストラクタの不変条件を検証する。
IT1 では hedgehog 依存だけ追加され `forAll` が皆無 (境界値が検証されていない) だった。
-}
module Domain.PropertiesSpec (spec) where

import Control.Monad.IO.Class (liftIO)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Hedgehog (Gen, Property, check, forAll, property, (===))
import qualified Hedgehog.Gen as Gen
import qualified Hedgehog.Range as Range
import Test.Hspec

import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    mkCargo,
    requestRouting,
    submitBooking,
  )
import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..), mkBookingId)
import Cargotracker.Booking.Domain.Model.Value.HsCode (mkHsCode)
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Booking.Domain.Model.Value.TemperatureRequirement
  ( TemperatureUnit (..),
    mkTemperatureRequirement,
  )
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( VoyageNumber,
    mkVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage (mkVoyage)
import Cargotracker.Shared.Domain.Common.UnLocode
  ( UnLocode (..),
    mkUnLocode,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))

-- ---------------------------------------------------------------
-- ジェネレータ
-- ---------------------------------------------------------------

genUpperLetter :: Gen Char
genUpperLetter = Gen.enum 'A' 'Z'

genAlphaNumUpper :: Gen Char
genAlphaNumUpper = Gen.choice [genUpperLetter, Gen.enum '0' '9']

genValidUnLocode :: Gen Text
genValidUnLocode = do
  c1 <- genUpperLetter
  c2 <- genUpperLetter
  l1 <- genAlphaNumUpper
  l2 <- genAlphaNumUpper
  l3 <- genAlphaNumUpper
  pure (T.pack [c1, c2, l1, l2, l3])

genValidBookingId :: Gen Text
genValidBookingId = do
  chars <- traverse (const genAlphaNumUpper) [1 .. (6 :: Int)]
  pure ("BK-" <> T.pack chars)

epoch :: UTCTime
epoch = UTCTime (fromGregorian 2026 1 1) (secondsToDiffTime 0)

mkMovement :: Text -> Text -> CarrierMovement
mkMovement dep arr =
  CarrierMovement
    { departureLocation = UnLocode dep
    , arrivalLocation = UnLocode arr
    , departureTime = epoch
    , arrivalTime = epoch
    }

unsafeVn :: Text -> VoyageNumber
unsafeVn t = case mkVoyageNumber t of
  Right v -> v
  Left e -> error ("test setup: invalid VoyageNumber " <> show e)

-- ---------------------------------------------------------------
-- Properties
-- ---------------------------------------------------------------

prop_unlocode_length_invariant :: Property
prop_unlocode_length_invariant = property $ do
  -- 5 文字以外は必ず Left (InvalidUnLocode "expected 5 chars")
  t <- forAll (Gen.text (Range.linear 0 10) Gen.alpha)
  if T.length t == 5
    then pure ()
    else mkUnLocode t === Left (InvalidUnLocode "expected 5 chars")

prop_unlocode_valid_roundtrip :: Property
prop_unlocode_valid_roundtrip = property $ do
  -- 規約に合致する 5 文字は Right で同じ文字列を保持
  t <- forAll genValidUnLocode
  mkUnLocode t === Right (UnLocode t)

prop_bookingid_format :: Property
prop_bookingid_format = property $ do
  -- BK- 接頭辞 + 英数大文字 6 桁は Right
  t <- forAll genValidBookingId
  case mkBookingId t of
    Right _ -> pure ()
    Left _ -> fail "expected Right for valid BK-XXXXXX"

prop_bookingid_rejects_bad_prefix :: Property
prop_bookingid_rejects_bad_prefix = property $ do
  -- 接頭辞が "BK-" 以外なら必ず Left
  prefix <- forAll (Gen.element ["XX-", "AB-", "BKK", ""])
  rest <- forAll (Gen.text (Range.linear 0 8) Gen.alphaNum)
  mkBookingId (prefix <> rest) === Left (InvalidBookingId "expected BK-XXXXXX")

prop_voyage_continuity :: Property
prop_voyage_continuity = property $ do
  -- 区間が連続 (前 arrival == 次 departure) なら Right
  n <- forAll (Gen.int (Range.linear 1 4))
  ports <- forAll (Gen.list (Range.singleton (n + 1)) genValidUnLocode)
  let movements = zipWith mkMovement ports (drop 1 ports)
  case mkVoyage (unsafeVn "V-PROP1") movements of
    Right _ -> pure ()
    Left e -> fail ("expected continuous voyage but got " <> show e)

prop_voyage_rejects_discontinuity :: Property
prop_voyage_rejects_discontinuity = property $ do
  -- 連続性違反 (前 arrival != 次 departure) は必ず Left LegContinuityViolation
  let mvs =
        [ mkMovement "JPTYO" "USNYC"
        , mkMovement "GBLON" "DEHAM" -- USNYC とつながらない
        ]
  case mkVoyage (unsafeVn "V-DISC1") mvs of
    Left (LegContinuityViolation _) -> pure ()
    other -> fail ("expected Left LegContinuityViolation but got " <> show other)

-- U-13 (IT3): HsCode のプロパティ
prop_hscode_valid_digits :: Property
prop_hscode_valid_digits = property $ do
  -- 6-10 桁の数字は必ず Right
  n <- forAll (Gen.int (Range.linear 6 10))
  digits <- forAll (T.pack <$> Gen.list (Range.singleton n) (Gen.element ['0' .. '9']))
  case mkHsCode digits of
    Right _ -> pure ()
    Left e -> fail ("expected Right for valid HS but got " <> show e)

prop_hscode_rejects_non_digit :: Property
prop_hscode_rejects_non_digit = property $ do
  -- 英字を 1 文字含むと必ず Left InvalidHsCode (長さ 6-10 でも)
  n <- forAll (Gen.int (Range.linear 6 10))
  base <- forAll (T.pack <$> Gen.list (Range.singleton (n - 1)) (Gen.element ['0' .. '9']))
  letter <- forAll (T.singleton <$> Gen.element ['A' .. 'Z'])
  let bad = base <> letter
  case mkHsCode bad of
    Left (InvalidHsCode _) -> pure ()
    other -> fail ("expected InvalidHsCode but got " <> show other)

prop_hscode_rejects_wrong_length :: Property
prop_hscode_rejects_wrong_length = property $ do
  -- 5 桁以下 / 11 桁以上は必ず Left
  n <- forAll (Gen.element [0, 1, 5, 11, 12, 15])
  digits <- forAll (T.pack <$> Gen.list (Range.singleton n) (Gen.element ['0' .. '9']))
  case mkHsCode digits of
    Left (InvalidHsCode _) -> pure ()
    other -> fail ("expected InvalidHsCode for length " <> show n <> " but got " <> show other)

-- U-13 (IT3): TemperatureRequirement のプロパティ
prop_temperature_min_le_max :: Property
prop_temperature_min_le_max = property $ do
  -- min <= max なら必ず Right
  minT <- forAll (Gen.double (Range.linearFrac (-50) 30))
  delta <- forAll (Gen.double (Range.linearFrac 0 30))
  unit <- forAll (Gen.element [Celsius, Fahrenheit])
  let maxT = minT + delta
  case mkTemperatureRequirement minT maxT unit of
    Right _ -> pure ()
    Left e -> fail ("expected Right for min<=max but got " <> show e)

prop_temperature_rejects_inverted :: Property
prop_temperature_rejects_inverted = property $ do
  -- min > max は必ず Left
  maxT <- forAll (Gen.double (Range.linearFrac (-50) 30))
  delta <- forAll (Gen.double (Range.linearFrac 0.01 30))
  unit <- forAll (Gen.element [Celsius, Fahrenheit])
  let minT = maxT + delta -- min > max
  case mkTemperatureRequirement minT maxT unit of
    Left _ -> pure ()
    Right _ -> fail "expected Left for inverted min/max"

-- L-05 (IT3): Cargo 状態遷移のプロパティ網羅
sampleCargo :: BookingStatus -> Cargo
sampleCargo s =
  let c0 = mkCargo (BookingId "BK-A1B2C3") (ShipperRef "SHP-X1Y2Z3") sampleRoute
   in c0 {cargoStatus = s}

sampleRoute :: RouteSpecification
sampleRoute =
  RouteSpecification
    { origin = UnLocode "JPTYO"
    , destination = UnLocode "USNYC"
    , arrivalDeadline = UTCTime (fromGregorian 2026 12 31) (secondsToDiffTime 0)
    }

prop_submitBooking_only_from_Draft :: Property
prop_submitBooking_only_from_Draft = property $ do
  -- Draft 以外の状態から submitBooking すると必ず InvalidStateTransition
  s <- forAll (Gen.element [Submitted, RouteProposed, Confirmed, Closed])
  case submitBooking (sampleCargo s) of
    Left (InvalidStateTransition _ _) -> pure ()
    other ->
      fail
        ("expected InvalidStateTransition from " <> show s <> " but got " <> show other)

prop_requestRouting_only_from_Submitted :: Property
prop_requestRouting_only_from_Submitted = property $ do
  -- Submitted 以外の状態から requestRouting すると必ず InvalidStateTransition
  s <- forAll (Gen.element [Draft, RouteProposed, Confirmed, Closed])
  case requestRouting (sampleCargo s) of
    Left (InvalidStateTransition _ _) -> pure ()
    other ->
      fail
        ("expected InvalidStateTransition from " <> show s <> " but got " <> show other)

-- ---------------------------------------------------------------
-- hspec wrapper
-- ---------------------------------------------------------------

runProp :: String -> Property -> Spec
runProp name p = it name $ do
  ok <- liftIO (check p)
  ok `shouldBe` True

spec :: Spec
spec = describe "Domain Properties (T-05 hedgehog)" $ do
  runProp "UnLocode: 長さが 5 文字でなければ必ず InvalidUnLocode" prop_unlocode_length_invariant
  runProp "UnLocode: 規約に合致する 5 文字は Right で保持される" prop_unlocode_valid_roundtrip
  runProp "BookingId: BK-XXXXXX (英数大文字 6 桁) は Right" prop_bookingid_format
  runProp "BookingId: 不正な接頭辞は必ず Left" prop_bookingid_rejects_bad_prefix
  runProp "Voyage: 連続区間 (前 arrival == 次 departure) は Right" prop_voyage_continuity
  runProp "Voyage: 連続性違反は必ず LegContinuityViolation" prop_voyage_rejects_discontinuity
  runProp "HsCode: 6-10 桁の数字は必ず Right (U-13)" prop_hscode_valid_digits
  runProp "HsCode: 英字混入は必ず InvalidHsCode (U-13)" prop_hscode_rejects_non_digit
  runProp "HsCode: 5 桁以下 / 11 桁以上は必ず InvalidHsCode (U-13)" prop_hscode_rejects_wrong_length
  runProp "TemperatureRequirement: min<=max は必ず Right (U-13)" prop_temperature_min_le_max
  runProp "TemperatureRequirement: min>max は必ず Left (U-13)" prop_temperature_rejects_inverted
  runProp "submitBooking: Draft 以外は必ず InvalidStateTransition (L-05)" prop_submitBooking_only_from_Draft
  runProp "requestRouting: Submitted 以外は必ず InvalidStateTransition (L-05)" prop_requestRouting_only_from_Submitted
