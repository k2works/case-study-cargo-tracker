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

import Cargotracker.Booking.Domain.Model.Value.BookingId (mkBookingId)
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
