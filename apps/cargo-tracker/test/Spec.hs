-- | テストエントリポイント (hspec)
--
-- IT1 以降に各 Bounded Context の Spec を追加していく。
-- 現時点はビルド検証用の最小 Spec のみ。
module Main (main) where

import           Test.Hspec

import           Cargotracker                          (greet)
import           Cargotracker.Shared.Domain.DomainError (DomainError (..))

main :: IO ()
main = hspec $ do
  describe "Cargotracker (stub)" $ do
    it "greet で起動メッセージを返す" $
      greet "world" `shouldBe` "Hello, world! Cargo Tracker (Haskell) is alive."

  describe "DomainError (stub)" $ do
    it "InvalidBookingId と InvalidUnLocode は別エラー" $ do
      InvalidBookingId "x" `shouldNotBe` InvalidUnLocode "x"
    it "ConcurrentModification の Show 表現が読める" $
      show (ConcurrentModification "BK-A1B2C3")
        `shouldBe` "ConcurrentModification \"BK-A1B2C3\""
