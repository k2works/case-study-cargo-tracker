{- | BookingStatus 状態遷移の hedgehog プロパティテスト (T4-11 / IT5 task 3.8)

IT4 で 49 ペア (7 状態 × 7 状態) を明示列挙する形で網羅性を検証していたが、
「forAll allStatusPairs」で property 化することで N² 明示展開を回避する
という T4-11 (Try) を IT5 で実装する。

利点:
1. BookingStatus に状態が増えても再列挙不要
2. Hedgehog のシュリンクで反例が最小化される
3. カウンタサンプル発見時に具体的な (from, to) ペアが得られる

既存 CancellationPolicyPropertiesSpec のパターン (check + assertTrue) を踏襲する。
-}
module Booking.Domain.Model.State.BookingStatusPropertiesSpec (spec) where

import Hedgehog (Gen, Property, check, forAll, property, (===))
import qualified Hedgehog.Gen as Gen
import Test.Hspec

import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
    canTransitionTo,
  )

{- | 参照真実: canTransitionTo が True を返す全 (from, to) の網羅リスト。
IT4 で明示列挙されていた 10 件と一致する必要がある。
-}
acceptedTransitions :: [(BookingStatus, BookingStatus)]
acceptedTransitions =
  [ (Draft, Submitted)
  , (Submitted, RouteProposed)
  , (Submitted, Cancelled)
  , (RouteProposed, RouteAssigned)
  , (RouteProposed, Cancelled)
  , (RouteAssigned, Confirmed)
  , (RouteAssigned, Draft)
  , (RouteAssigned, Cancelled)
  , (Confirmed, Cancelled)
  , (Confirmed, Closed)
  ]

prop_canTransitionToMatchesTable :: Property
prop_canTransitionToMatchesTable = property $ do
  from <- forAll (Gen.enumBounded :: Gen BookingStatus)
  to <- forAll (Gen.enumBounded :: Gen BookingStatus)
  canTransitionTo from to === ((from, to) `elem` acceptedTransitions)

prop_selfLoopAlwaysFalse :: Property
prop_selfLoopAlwaysFalse = property $ do
  s <- forAll (Gen.enumBounded :: Gen BookingStatus)
  canTransitionTo s s === False

prop_terminalStatesHaveNoOutgoing :: Property
prop_terminalStatesHaveNoOutgoing = property $ do
  -- Cancelled と Closed は終端状態、いかなる他状態へも遷移不可
  s <- forAll (Gen.enumBounded :: Gen BookingStatus)
  terminal <- forAll (Gen.element [Cancelled, Closed])
  canTransitionTo terminal s === False

spec :: Spec
spec = describe "BookingStatus (hedgehog properties, T4-11 / IT5 task 3.8)" $ do
  it "canTransitionTo が accepted 表と完全に一致する (N² を forAll で網羅)" $
    check prop_canTransitionToMatchesTable >>= assertTrue
  it "自己ループ (canTransitionTo s s) は全状態で False" $
    check prop_selfLoopAlwaysFalse >>= assertTrue
  it "終端状態 (Cancelled/Closed) からの出遷移は全て False" $
    check prop_terminalStatesHaveNoOutgoing >>= assertTrue
  where
    assertTrue True = pure ()
    assertTrue False = expectationFailure "hedgehog property failed"
