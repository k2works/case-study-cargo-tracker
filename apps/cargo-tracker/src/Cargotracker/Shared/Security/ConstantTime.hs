{- | 定数時間比較ユーティリティ (T5-02, SEC-04, IT6)

シークレット (ConfirmationCode、SessionToken、CSRF トークンなど) の比較で
タイミング攻撃を防ぐため、文字ごとの XOR 論理和で必ず全長を走査する
`constantTimeEqText` を提供する。

早期リターン (`&&` の短絡評価や `Data.Text.==` の Byte 比較最適化) を避け、
一致でも不一致でも同じ量の演算を行うことで、実行時間から差異が漏れることを
防ぐ。ただし長さが異なる場合は即座に False を返す (プロトコル上の固定長を
前提とし、呼び出し側でスマートコンストラクタが長さを検証する)。

参考: https://codahale.com/a-lesson-in-timing-attacks/
-}
module Cargotracker.Shared.Security.ConstantTime
  ( constantTimeEqText,
  ) where

import Data.Bits (xor, (.|.))
import Data.Char (ord)
import Data.Text (Text)
import qualified Data.Text as T

{- | Text の定数時間等価比較。

長さが異なる場合は False を返す。長さが同じ場合は各文字の Unicode コードポイントを
XOR 論理和で畳み込み、結果が 0 なら True。早期リターンしないため、一致・不一致で
実行時間が変わらない (少なくとも Haskell の遅延評価が畳み込みを最適化しない限り)。

固定長プロトコル (6 桁数字コード、44 文字トークン等) の比較に使用する。
-}
constantTimeEqText :: Text -> Text -> Bool
constantTimeEqText a b
  | T.length a /= T.length b = False
  | otherwise = go 0 (T.zip a b) == 0
  where
    go :: Int -> [(Char, Char)] -> Int
    go acc [] = acc
    go acc ((x, y) : rest) = go (acc .|. (ord x `xor` ord y)) rest
