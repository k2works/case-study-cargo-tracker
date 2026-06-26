{- | Cargo Tracker (Haskell 版) のエントリポイント

IT1 で本格実装する。現時点は最小の Warp スタブとして、
ローカル開発の動作確認のみを目的とする。
-}
module Main (main) where

import qualified Data.ByteString.Lazy.Char8 as LBSC
import Network.HTTP.Types (status200)
import Network.Wai (responseLBS)
import Network.Wai.Handler.Warp (run)
import System.Environment (lookupEnv)

import Cargotracker (greet)

main :: IO ()
main = do
  port <- maybe 8080 read <$> lookupEnv "PORT"
  putStrLn $ "Cargo Tracker (Haskell) starting on port " <> show port
  run port $ \_req respond ->
    respond $
      responseLBS
        status200
        [("Content-Type", "application/json")]
        (LBSC.pack ("{\"status\":\"UP\",\"message\":\"" <> greet "world" <> "\"}"))
