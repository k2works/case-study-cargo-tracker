{- | RouteEvaluator の hedgehog プロパティテスト (US08b, IT4)

不変条件:

* P-1: noConstraint なら常に accepted
* P-2: rcHazardous=True のとき、全寄港港が allowed セットの部分集合なら必ず accepted
* P-3: rcReeferRequired=True のとき、全 voyage が reefer セットの部分集合なら必ず accepted
* P-4: 違反理由の件数は寄港港 + voyage 数の合計以下 (重複違反は発生しない)
* P-5: rejected の理由は 1 件以上
-}
module Estimation.Domain.Service.RouteEvaluatorPropertiesSpec (spec) where

import qualified Data.Set as Set
import Data.Text (Text)
import qualified Data.Text as T
import Hedgehog (Gen, Property, assert, check, forAll, property, (===))
import qualified Hedgehog.Gen as Gen
import qualified Hedgehog.Range as Range
import Test.Hspec

import Cargotracker.Estimation.Domain.Model.Value.RouteConstraint
  ( ConstraintEvaluation (..),
    RouteConstraint (..),
    noConstraint,
  )
import Cargotracker.Estimation.Domain.Service.RouteEvaluator
  ( EvaluationInput (..),
    evaluate,
  )

-- 識別子生成 (有効な UnLocode 風 5 文字 / VoyageNumber 風 V + 3 桁)
genPort :: Gen Text
genPort = do
  cc <- Gen.text (Range.singleton 2) Gen.upper
  loc <- Gen.text (Range.singleton 3) (Gen.choice [Gen.upper, Gen.digit])
  pure (cc <> loc)

genVoyage :: Gen Text
genVoyage = do
  n <- Gen.integral (Range.linear 1 9999)
  pure ("V" <> T.pack (show n))

genConstraint :: Gen RouteConstraint
genConstraint =
  RouteConstraint
    <$> Gen.bool
    <*> Gen.bool
    <*> Gen.bool

-- | 全寄港港が allowed セットの部分集合になる入力を生成
genAllAllowedInput :: Gen EvaluationInput
genAllAllowedInput = do
  ports <- Gen.list (Range.linear 1 5) genPort
  voyages <- Gen.list (Range.linear 1 3) genVoyage
  pure
    EvaluationInput
      { eiRoutePorts = ports
      , eiRouteVoyages = voyages
      , eiHazardousAllowedPorts = Set.fromList ports -- 必ず部分集合
      , eiReeferCapableVoyages = Set.fromList voyages
      }

-- | ランダムな入力 (allowed セットは独立に生成)
genRandomInput :: Gen EvaluationInput
genRandomInput = do
  ports <- Gen.list (Range.linear 1 5) genPort
  voyages <- Gen.list (Range.linear 1 3) genVoyage
  allowedPorts <- Gen.set (Range.linear 0 5) genPort
  reeferVoy <- Gen.set (Range.linear 0 3) genVoyage
  pure
    EvaluationInput
      { eiRoutePorts = ports
      , eiRouteVoyages = voyages
      , eiHazardousAllowedPorts = allowedPorts
      , eiReeferCapableVoyages = reeferVoy
      }

prop_noConstraintAlwaysAccepted :: Property
prop_noConstraintAlwaysAccepted = property $ do
  input <- forAll genRandomInput
  let r = evaluate noConstraint input
  ceAccepted r === True
  ceReasons r === []

prop_allowedHazardousImpliesAccepted :: Property
prop_allowedHazardousImpliesAccepted = property $ do
  input <- forAll genAllAllowedInput
  let r = evaluate (RouteConstraint True False False) input
  ceAccepted r === True

prop_allowedReeferImpliesAccepted :: Property
prop_allowedReeferImpliesAccepted = property $ do
  input <- forAll genAllAllowedInput
  let r = evaluate (RouteConstraint False True False) input
  ceAccepted r === True

prop_reasonsCountBounded :: Property
prop_reasonsCountBounded = property $ do
  input <- forAll genRandomInput
  constraint <- forAll genConstraint
  let r = evaluate constraint input
      maxReasons = length (eiRoutePorts input) + length (eiRouteVoyages input)
  assert (length (ceReasons r) <= maxReasons)

prop_rejectedHasReasons :: Property
prop_rejectedHasReasons = property $ do
  input <- forAll genRandomInput
  constraint <- forAll genConstraint
  let r = evaluate constraint input
  if ceAccepted r
    then ceReasons r === []
    else assert (not (null (ceReasons r)))

prop_directPreferredAloneNeverRejects :: Property
prop_directPreferredAloneNeverRejects = property $ do
  input <- forAll genRandomInput
  let r = evaluate (RouteConstraint False False True) input
  ceAccepted r === True

spec :: Spec
spec = describe "RouteEvaluator.evaluate hedgehog プロパティ (US08b / IT4)" $ do
  it "P-1: noConstraint は常に accepted" $ check prop_noConstraintAlwaysAccepted >>= assertTrue
  it "P-2: 全寄港港が allowed → Hazardous True でも accepted" $
    check prop_allowedHazardousImpliesAccepted >>= assertTrue
  it "P-3: 全 voyage が reefer → ReeferRequired True でも accepted" $
    check prop_allowedReeferImpliesAccepted >>= assertTrue
  it "P-4: 違反理由数 <= ports + voyages の合計" $
    check prop_reasonsCountBounded >>= assertTrue
  it "P-5: rejected なら必ず理由 1 件以上、accepted なら理由 0 件" $
    check prop_rejectedHasReasons >>= assertTrue
  it "P-6: rcDirectPreferred のみ True は常に accepted (本サービスでは評価しない)" $
    check prop_directPreferredAloneNeverRejects >>= assertTrue
  where
    assertTrue True = pure ()
    assertTrue False = expectationFailure "hedgehog property failed"
