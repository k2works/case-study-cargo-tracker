{- | ID 自動採番ユーティリティ (T-07, IT2)

ShipperId / BookingId のような業務 ID をサーバ側で生成する。
英数字大文字 6 桁ランダムを `<PREFIX>-XXXXXX` の形式で組み立てる。

IT1 ではフォームでユーザが手入力していたが、業務上の誤予約温床
となるため自動採番に切り替える (retrospective P-7)。

衝突は確率的に十分小さい (約 1/2.18B per attempt)。実運用 (IT5+) では
DB のサロゲートキーを使う方が確実だが、業務キーはユーザ向け表示用なので
当面は random 採番で運用する。
-}
module Cargotracker.Shared.Infrastructure.IdGenerator
  ( generateBookingIdText,
    generateShipperIdText,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import System.Random (randomRIO)

-- | "BK-A1B2C3" のような ID 文字列を生成する。
generateBookingIdText :: IO Text
generateBookingIdText = do
  body <- randomAlphaNum 6
  pure ("BK-" <> body)

-- | "SHP-A1B2C3" のような ID 文字列を生成する。
generateShipperIdText :: IO Text
generateShipperIdText = do
  body <- randomAlphaNum 6
  pure ("SHP-" <> body)

randomAlphaNum :: Int -> IO Text
randomAlphaNum n = do
  chars <- mapM (const randomAlphaNumChar) [1 .. n]
  pure (T.pack chars)

randomAlphaNumChar :: IO Char
randomAlphaNumChar = do
  -- 36 通り (0-9 + A-Z) から 1 文字を一様サンプル
  i <- randomRIO (0, 35 :: Int)
  pure (alphaNumTable !! i)

alphaNumTable :: [Char]
alphaNumTable = ['0' .. '9'] <> ['A' .. 'Z']
