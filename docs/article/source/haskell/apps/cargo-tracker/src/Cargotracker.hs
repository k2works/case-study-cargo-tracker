{- | Cargo Tracker トップレベルモジュール

IT1 で本格実装する。現時点は最小のスタブのみ。
-}
module Cargotracker
  ( greet,
  )
where

-- | スタブ用の挨拶関数。Main.hs の起動確認用。
greet :: String -> String
greet name = "Hello, " <> name <> "! Cargo Tracker (Haskell) is alive."
