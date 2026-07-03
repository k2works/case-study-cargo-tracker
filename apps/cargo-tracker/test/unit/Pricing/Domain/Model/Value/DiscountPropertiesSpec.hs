{- | Discount.applyDiscount の hedgehog プロパティテスト (US22, IT7)

割引適用の不変条件を網羅的に検証する:

* P-1: 割引後金額は常に元金額以下 (単調性)
* P-2: 0% 割引は恒等 (元金額と同じ)
* P-3: 100% 割引は 0
* P-4: 通貨は不変 (割引適用しても currency は変わらない)
* P-5: 割引率 r1 <= r2 なら 適用後 c1 >= c2 (割引率反単調)
-}
module Pricing.Domain.Model.Value.DiscountPropertiesSpec (spec) where

import Hedgehog (Gen, Property, assert, check, forAll, property, (===))
import qualified Hedgehog.Gen as Gen
import qualified Hedgehog.Range as Range
import Test.Hspec

import Cargotracker.Pricing.Domain.Model.Value.Cost (Cost (..), mkCost, mkCurrency)
import Cargotracker.Pricing.Domain.Model.Value.Discount (Discount (..), applyDiscount)

genAmount :: Gen Integer
genAmount = Gen.integral (Range.linear 0 1000000)

genRate :: Gen Integer
genRate = Gen.integral (Range.linear 0 100)

genCost :: Gen Cost
genCost = do
  amount <- genAmount
  case mkCurrency "JPY" of
    Right c -> case mkCost amount c of
      Right cost -> pure cost
      Left _ -> error "genCost: mkCost failed"
    Left _ -> error "genCost: mkCurrency failed"

prop_discountNeverIncreases :: Property
prop_discountNeverIncreases = property $ do
  rate <- forAll genRate
  cost <- forAll genCost
  let result = applyDiscount (Discount rate) cost
  assert (costAmount result <= costAmount cost)

prop_zeroDiscountIsIdentity :: Property
prop_zeroDiscountIsIdentity = property $ do
  cost <- forAll genCost
  let result = applyDiscount (Discount 0) cost
  costAmount result === costAmount cost
  costCurrency result === costCurrency cost

prop_fullDiscountIsZero :: Property
prop_fullDiscountIsZero = property $ do
  cost <- forAll genCost
  let result = applyDiscount (Discount 100) cost
  costAmount result === 0

prop_currencyIsPreserved :: Property
prop_currencyIsPreserved = property $ do
  rate <- forAll genRate
  cost <- forAll genCost
  let result = applyDiscount (Discount rate) cost
  costCurrency result === costCurrency cost

prop_higherRateGivesLowerOrEqualAmount :: Property
prop_higherRateGivesLowerOrEqualAmount = property $ do
  r1 <- forAll genRate
  r2 <- forAll genRate
  cost <- forAll genCost
  let (lower, higher) = if r1 <= r2 then (r1, r2) else (r2, r1)
      c1 = applyDiscount (Discount lower) cost
      c2 = applyDiscount (Discount higher) cost
  assert (costAmount c1 >= costAmount c2)

spec :: Spec
spec = describe "Discount hedgehog properties (US22, IT7)" $ do
  it "P-1: 割引適用後の金額は元金額以下" $
    check prop_discountNeverIncreases >>= assertTrue
  it "P-2: 0% 割引は恒等" $
    check prop_zeroDiscountIsIdentity >>= assertTrue
  it "P-3: 100% 割引は 0" $
    check prop_fullDiscountIsZero >>= assertTrue
  it "P-4: 通貨は割引適用後も不変" $
    check prop_currencyIsPreserved >>= assertTrue
  it "P-5: 高い割引率は必ずより少ない (以下の) 金額を返す" $
    check prop_higherRateGivesLowerOrEqualAmount >>= assertTrue
  where
    assertTrue True = pure ()
    assertTrue False = expectationFailure "hedgehog property failed"
