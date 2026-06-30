{- | CancellationPolicy の hedgehog プロパティテスト (US13, IT4)

3 つの不変条件をプロパティテストで検証する:

* P-1: 出航 7 日以上前は必ず Free (rate = 0)
* P-2: 1 日以上 7 日未満は必ず Partial (rate = 30/100)
* P-3: 1 日未満 (出航後含む) は必ず Full (rate = 100/100)
* P-4: cfCalculatedAt は引数の now と一致する
* P-5: tier と rate の対応は決定的 (Free->0 / Partial->0.3 / Full->1.0)
-}
module Booking.Domain.Service.CancellationPolicyPropertiesSpec (spec) where

import Data.Ratio ((%))
import Data.Time
  ( UTCTime (..),
    addUTCTime,
    fromGregorian,
    secondsToDiffTime,
  )
import Hedgehog (Gen, Property, assert, check, forAll, property, (===))
import qualified Hedgehog.Gen as Gen
import qualified Hedgehog.Range as Range
import Test.Hspec

import Cargotracker.Booking.Domain.Model.Value.CancellationFee
  ( CancellationFee (..),
    CancellationTier (..),
    tierRate,
  )
import Cargotracker.Booking.Domain.Service.CancellationPolicy (calculate)

-- | 固定の基準出航日時 (2026-09-10 12:00:00 UTC)
departure :: UTCTime
departure = UTCTime (fromGregorian 2026 9 10) (secondsToDiffTime (12 * 3600))

-- | 出航前の秒数を生成 (0 秒〜30 日 = 2,592,000 秒)
genSecondsBefore :: Gen Integer
genSecondsBefore = Gen.integral (Range.linear 0 (30 * 24 * 3600))

nowAt :: Integer -> UTCTime
nowAt secondsBefore = addUTCTime (fromIntegral (negate secondsBefore)) departure

-- 各ティアの境界値を満たす秒数レンジ
sevenDaysSec, oneDaySec :: Integer
sevenDaysSec = 7 * 24 * 3600
oneDaySec = 1 * 24 * 3600

prop_freeTierBoundary :: Property
prop_freeTierBoundary = property $ do
  secs <- forAll (Gen.integral (Range.linear sevenDaysSec (30 * 24 * 3600)))
  let fee = calculate (nowAt secs) departure
  cfTier fee === Free
  cfRate fee === (0 % 100)

prop_partialTierBoundary :: Property
prop_partialTierBoundary = property $ do
  secs <- forAll (Gen.integral (Range.linear oneDaySec (sevenDaysSec - 1)))
  let fee = calculate (nowAt secs) departure
  cfTier fee === Partial
  cfRate fee === (30 % 100)

prop_fullTierBoundary :: Property
prop_fullTierBoundary = property $ do
  -- 0 秒前 (出航時刻と同時) 〜 1 日未満
  secs <- forAll (Gen.integral (Range.linear 0 (oneDaySec - 1)))
  let fee = calculate (nowAt secs) departure
  cfTier fee === Full
  cfRate fee === (100 % 100)

prop_calculatedAtEqualsNow :: Property
prop_calculatedAtEqualsNow = property $ do
  secs <- forAll genSecondsBefore
  let now = nowAt secs
      fee = calculate now departure
  cfCalculatedAt fee === now

prop_tierAndRateAreConsistent :: Property
prop_tierAndRateAreConsistent = property $ do
  secs <- forAll genSecondsBefore
  let fee = calculate (nowAt secs) departure
  cfRate fee === tierRate (cfTier fee)

prop_afterDepartureIsAlwaysFull :: Property
prop_afterDepartureIsAlwaysFull = property $ do
  -- 出航後 0 秒〜30 日後
  secsAfter <- forAll (Gen.integral (Range.linear 0 (30 * 24 * 3600)))
  let now = addUTCTime (fromIntegral secsAfter) departure
      fee = calculate now departure
  cfTier fee === Full
  cfRate fee === (100 % 100)

spec :: Spec
spec = describe "CancellationPolicy.calculate hedgehog プロパティ (US13 / IT4)" $ do
  it "P-1: 出航 7 日以上前は必ず Free / 0%" $ check prop_freeTierBoundary >>= assertTrue
  it "P-2: 1 日以上 7 日未満は必ず Partial / 30%" $ check prop_partialTierBoundary >>= assertTrue
  it "P-3: 1 日未満は必ず Full / 100%" $ check prop_fullTierBoundary >>= assertTrue
  it "P-4: cfCalculatedAt == now (恒等性)" $ check prop_calculatedAtEqualsNow >>= assertTrue
  it "P-5: rate == tierRate tier (整合性)" $ check prop_tierAndRateAreConsistent >>= assertTrue
  it "P-6: 出航後 (now > departure) は必ず Full" $ check prop_afterDepartureIsAlwaysFull >>= assertTrue
  where
    assertTrue True = pure ()
    assertTrue False = expectationFailure "hedgehog property failed"
