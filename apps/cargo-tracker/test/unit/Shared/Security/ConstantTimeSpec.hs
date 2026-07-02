{-# LANGUAGE OverloadedStrings #-}

{- | 定数時間比較ヘルパのテスト (T5-02, SEC-04, IT6)

ConfirmationCode.verify などのシークレット比較でタイミング攻撃を防ぐため、
文字ごとの XOR 論理和で必ず全長を走査する `constantTimeEqText` の挙動を
機能面で検証する (時間計測はしない)。

長さが異なる場合の扱い:
  6 桁数字などプロトコル上の固定長シークレット比較を想定するため、
  長さ不一致は即座に False を返して良い。呼び出し側は長さ検証を
  スマートコンストラクタ (mkConfirmationCode) で行う。
-}
module Shared.Security.ConstantTimeSpec (spec) where

import Test.Hspec

import Cargotracker.Shared.Security.ConstantTime (constantTimeEqText)

spec :: Spec
spec = describe "constantTimeEqText (T5-02 SEC-04)" $ do
  it "完全一致の 6 桁数字なら True" $
    constantTimeEqText "123456" "123456" `shouldBe` True

  it "1 文字違いの 6 桁数字なら False" $
    constantTimeEqText "123456" "123457" `shouldBe` False

  it "先頭 1 文字違いの 6 桁数字なら False (早期リターンしない実装であること)" $
    constantTimeEqText "923456" "123456" `shouldBe` False

  it "長さが異なるなら False" $
    constantTimeEqText "12345" "123456" `shouldBe` False

  it "空文字同士なら True" $
    constantTimeEqText "" "" `shouldBe` True

  it "空文字と非空なら False" $
    constantTimeEqText "" "123456" `shouldBe` False

  it "非ASCII (日本語) でも一致判定できる" $
    constantTimeEqText "秘密" "秘密" `shouldBe` True

  it "非ASCII の差異も検出できる" $
    constantTimeEqText "秘密" "秘蜜" `shouldBe` False
