{- | テストエントリポイント (hspec)

各 Bounded Context の Spec モジュールをここから集約する。
hspec-discover を使わず明示的に列挙する方針 (CI の出力が明確になるため)。
-}
module Main (main) where

import Test.Hspec

import Cargotracker (greet)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

import qualified Shared.Auth.Domain.UserSpec

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
