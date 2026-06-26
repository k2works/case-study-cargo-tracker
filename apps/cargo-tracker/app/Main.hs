{-# LANGUAGE OverloadedStrings #-}

{- | Cargo Tracker (Haskell 版) エントリポイント

Composition Root: Postgres 接続を確立し、全 Repository / Verifier /
JwtIssuer を組み立て、各 BC の WAI Application をパスで分岐して
1 つのアプリケーションに統合する。

エンドポイント:
- GET  /health        : ヘルスチェック
- POST /login         : 認証 (AUTH)
- POST /shippers      : 荷主登録 (US02/US03)
- POST /bookings      : 貨物予約 (US04)
- POST /voyages       : 航海登録 (US24)

環境変数:
- DATABASE_URL : PostgreSQL 接続文字列 (必須)
- JWT_SECRET   : JWT 署名鍵 (必須、32 文字以上推奨)
- PORT         : リスンポート (デフォルト 8080)
-}
module Main (main) where

import qualified Data.ByteString.Char8 as BC
import qualified Data.ByteString.Lazy.Char8 as LBC
import qualified Data.Text as T
import Database.PostgreSQL.Simple
  ( Connection,
    connectPostgreSQL,
  )
import Network.HTTP.Types (status200, status404, status500)
import Network.Wai (Application, pathInfo, responseLBS)
import Network.Wai.Handler.Warp (run)
import System.Environment (lookupEnv)

import Cargotracker.Booking.Infrastructure.PostgresBookingRepository
  ( newPostgresBookingRepository,
  )
import Cargotracker.Booking.Infrastructure.PostgresShipperExistenceChecker
  ( newPostgresShipperExistenceChecker,
  )
import Cargotracker.Booking.Interfaces.BookingApi (bookingApp)
import Cargotracker.Routing.Infrastructure.PostgresVoyageRepository
  ( newPostgresVoyageRepository,
  )
import Cargotracker.Routing.Interfaces.VoyageApi (voyageApp)
import Cargotracker.Shared.Auth.Infrastructure.BcryptVerifier (newBcryptVerifier)
import Cargotracker.Shared.Auth.Infrastructure.JwtIssuer (JwtSecret (..))
import Cargotracker.Shared.Auth.Infrastructure.PostgresUserRepository
  ( newPostgresUserRepository,
  )
import Cargotracker.Shared.Auth.Interfaces.LoginApi (loginApp)
import Cargotracker.Shipper.Infrastructure.PostgresShipperRepository
  ( newPostgresShipperRepository,
  )
import Cargotracker.Shipper.Interfaces.ShipperApi (shipperApp)

main :: IO ()
main = do
  port <- maybe 8080 read <$> lookupEnv "PORT"
  mDbUrl <- lookupEnv "DATABASE_URL"
  mJwtSecret <- lookupEnv "JWT_SECRET"

  case (mDbUrl, mJwtSecret) of
    (Nothing, _) -> do
      putStrLn "ERROR: DATABASE_URL is not set. Running stub server."
      run port stubApp
    (_, Nothing) -> do
      putStrLn "ERROR: JWT_SECRET is not set. Running stub server."
      run port stubApp
    (Just dbUrl, Just secret) -> do
      putStrLn "Connecting to PostgreSQL..."
      conn <- connectPostgreSQL (BC.pack dbUrl)
      putStrLn $ "Cargo Tracker (Haskell) starting on port " <> show port
      run port (rootApp conn (JwtSecret (T.pack secret)))

{- | パスの 1 階層目で各 BC の Application に分岐する。

シンプルな WAI レベルのルーター。Servant の型レベル結合より
柔軟で、各 API モジュールに変更を加えず統合できる。
-}
rootApp :: Connection -> JwtSecret -> Application
rootApp conn jwtSecret req respond =
  case pathInfo req of
    ["health"] -> healthHandler req respond
    "login" : _ -> loginApp userRepo verifier jwtSecret req respond
    "shippers" : _ -> shipperApp shipperRepo req respond
    "bookings" : _ -> bookingApp bookingRepo shipperChecker req respond
    "voyages" : _ -> voyageApp voyageRepo req respond
    _ ->
      respond $
        responseLBS
          status404
          [("Content-Type", "application/json")]
          "{\"error\":\"not found\"}"
  where
    userRepo = newPostgresUserRepository conn
    verifier = newBcryptVerifier
    shipperRepo = newPostgresShipperRepository conn
    bookingRepo = newPostgresBookingRepository conn
    shipperChecker = newPostgresShipperExistenceChecker conn
    voyageRepo = newPostgresVoyageRepository conn

healthHandler :: Application
healthHandler _req respond =
  respond $
    responseLBS
      status200
      [("Content-Type", "application/json")]
      "{\"status\":\"UP\",\"message\":\"Cargo Tracker (Haskell) is alive\"}"

stubApp :: Application
stubApp _req respond =
  respond $
    responseLBS
      status500
      [("Content-Type", "application/json")]
      (LBC.pack "{\"status\":\"DOWN\",\"reason\":\"missing env vars (DATABASE_URL/JWT_SECRET)\"}")
