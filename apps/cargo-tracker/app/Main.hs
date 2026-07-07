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
- DATABASE_URL     : PostgreSQL 接続文字列 (必須)
- JWT_SECRET       : JWT 署名鍵 (必須、32 文字以上推奨)
- JWT_TTL_SECONDS  : JWT 有効期間 (秒、未設定なら 3600)
- APP_ENV          : "production" 指定時は必須 env 未設定で fail-fast
- PORT             : リスンポート (デフォルト 8080)
-}
module Main (main) where

import qualified Data.ByteString.Char8 as BC
import qualified Data.ByteString.Lazy.Char8 as LBC
import qualified Data.Text as T
import Data.Time (getCurrentTime)
import Data.Time.Clock.POSIX (getPOSIXTime)
import Database.PostgreSQL.Simple
  ( Connection,
    connectPostgreSQL,
  )
import Network.HTTP.Types (status200, status404, status500)
import Network.HTTP.Types.Method (methodGet, methodPost)
import Network.Wai (Application, pathInfo, requestMethod, responseLBS)
import Network.Wai.Handler.Warp (run)
import System.Environment (lookupEnv)
import System.Exit (exitFailure)
import System.IO (hPutStrLn, stderr)

import Cargotracker.Booking.Infrastructure.PostgresBookingRepository
  ( newPostgresBookingRepository,
  )
import Cargotracker.Booking.Infrastructure.PostgresCustomsDeclarationRepository
  ( newPostgresCustomsDeclarationRepository,
  )
import Cargotracker.Booking.Infrastructure.PostgresShipperExistenceChecker
  ( newPostgresShipperExistenceChecker,
  )
import Cargotracker.Booking.Interfaces.BookingApi (bookingApp)
import Cargotracker.Booking.Interfaces.BookingPageApi (bookingPageApp)
import Cargotracker.Estimation.Infrastructure.PostgresEstimateRepository
  ( newPostgresEstimateRepository,
  )
import Cargotracker.Estimation.Interfaces.EstimatePageApi (estimatePageApp)
import Cargotracker.Exception.Infrastructure.PostgresExceptionRepository
  ( newPostgresExceptionRepository,
  )
import Cargotracker.Exception.Interfaces.ExceptionListPageApi
  ( exceptionListApp,
  )
import Cargotracker.Handling.Infrastructure.PostgresHandlingActivityRepository
  ( newPostgresHandlingActivityRepository,
  )
import Cargotracker.Handling.Interfaces.HandlingPageApi (handlingPageApp)
import Cargotracker.Notification.Infrastructure.LogDeliveryPort
  ( newLogDeliveryPort,
  )
import Cargotracker.Notification.Infrastructure.PostgresNotificationRepository
  ( newPostgresNotificationRepository,
  )
import Cargotracker.Notification.Interfaces.NotificationListPageApi
  ( notificationListApp,
  )
import Cargotracker.Pricing.Infrastructure.PostgresCurrencyRateRepository
  ( newPostgresCurrencyRateRepository,
  )
import Cargotracker.Pricing.Infrastructure.PostgresPricingRuleRepository
  ( newPostgresPricingRuleRepository,
  )
import Cargotracker.Pricing.Interfaces.CostCalculationPageApi
  ( costCalculationApp,
  )
import Cargotracker.Routing.Infrastructure.PostgresVoyageRepository
  ( newPostgresVoyageRepository,
  )
import Cargotracker.Routing.Interfaces.VoyageApi (voyageApp)
import Cargotracker.Routing.Interfaces.VoyageMovementRowApi
  ( voyageMovementRowApp,
  )
import Cargotracker.Routing.Interfaces.VoyagePageApi (voyagePageApp)
import Cargotracker.Shared.Application.TxRunner (newPostgresTxRunner)
import Cargotracker.Shared.Auth.Infrastructure.BcryptVerifier (newBcryptVerifier)
import Cargotracker.Shared.Auth.Infrastructure.JwtIssuer
  ( JwtSecret (..),
    JwtTtlSeconds (..),
  )
import Cargotracker.Shared.Auth.Infrastructure.PostgresSessionRepository
  ( newPostgresSessionRepository,
  )
import Cargotracker.Shared.Auth.Infrastructure.PostgresUserRepository
  ( findRolesByUserId,
    newPostgresUserRepository,
  )
import Cargotracker.Shared.Auth.Interfaces.LoginApi (loginApp)
import Cargotracker.Shared.Auth.Interfaces.LoginPageApi (loginPageApp)
import Cargotracker.Shared.Infrastructure.IdGenerator
  ( generateNotificationIdText,
    generateSixDigitCodeText,
  )
import Cargotracker.Shared.Infrastructure.Logging (logInfo)
import Cargotracker.Shared.Web.HomeView (homeApp)
import Cargotracker.Shipper.Infrastructure.PostgresShipperRepository
  ( newPostgresShipperRepository,
  )
import Cargotracker.Shipper.Interfaces.ShipperApi (shipperApp)
import Cargotracker.Shipper.Interfaces.ShipperPageApi (shipperPageApp)
import Cargotracker.Shipper.Interfaces.ShipperSearchApi (shipperSearchApp)
import Cargotracker.Tracking.Infrastructure.PostgresConfirmationCodeRepository
  ( newPostgresConfirmationCodeRepository,
  )
import Cargotracker.Tracking.Infrastructure.PostgresTrackingRepository
  ( newPostgresTrackingRepository,
  )
import Cargotracker.Tracking.Infrastructure.PostgresTrackingStateAuditRepository
  ( newPostgresTrackingStateAuditRepository,
  )
import Cargotracker.Tracking.Interfaces.ManualUpdatePageApi
  ( auditHistoryApp,
    manualUpdateApp,
  )
import Cargotracker.Tracking.Interfaces.PublicTrackingApi (publicTrackingApp)
import Network.Wai.Middleware.RequestLogger (logStdoutDev)

main :: IO ()
main = do
  port <- maybe 8080 read <$> lookupEnv "PORT"
  mDbUrl <- lookupEnv "DATABASE_URL"
  mJwtSecret <- lookupEnv "JWT_SECRET"
  appEnv <- lookupEnv "APP_ENV"
  ttlSecs <- maybe 3600 read <$> lookupEnv "JWT_TTL_SECONDS"

  -- T-02 (IT2): production プロファイルでは必須 env 未設定で fail-fast し、
  -- 認証無効バイナリの本番混入リスクを排除する。
  case (mDbUrl, mJwtSecret) of
    (Nothing, _) -> failFastOrStub appEnv port "DATABASE_URL"
    (_, Nothing) -> failFastOrStub appEnv port "JWT_SECRET"
    (Just dbUrl, Just secret) -> do
      putStrLn "Connecting to PostgreSQL..."
      conn <- connectPostgreSQL (BC.pack dbUrl)
      putStrLn ""
      putStrLn "========================================================"
      putStrLn "  Cargo Tracker (Haskell) 起動完了"
      putStrLn "========================================================"
      putStrLn $ "  Listening on port: " <> show port
      putStrLn ""
      putStrLn "  利用可能なエンドポイント:"
      putStrLn $ "    Home          : http://localhost:" <> show port <> "/"
      putStrLn $ "    Health        : http://localhost:" <> show port <> "/health"
      putStrLn $ "    Login         : http://localhost:" <> show port <> "/login"
      putStrLn $ "    荷主登録      : http://localhost:" <> show port <> "/shippers/new"
      putStrLn $ "    貨物予約登録  : http://localhost:" <> show port <> "/bookings/new"
      putStrLn $ "    航海登録      : http://localhost:" <> show port <> "/voyages/new"
      putStrLn ""
      putStrLn "  停止: Ctrl+C"
      putStrLn "========================================================"
      putStrLn ""
      -- T4-15 (IT5 task 3.3): 起動時ログを JSON 構造化出力
      logInfo "app:started" []
      -- T4-15: 全リクエストに WAI ミドルウェアで method/path/status ログを付与
      run port (logStdoutDev (rootApp conn (JwtSecret (T.pack secret)) (JwtTtlSeconds ttlSecs)))

-- | APP_ENV=production なら fail-fast、それ以外 (dev/test) はスタブで継続。
failFastOrStub :: Maybe String -> Int -> String -> IO ()
failFastOrStub appEnv port name =
  case appEnv of
    Just "production" -> do
      hPutStrLn stderr ("FATAL: " <> name <> " is required when APP_ENV=production")
      exitFailure
    _ -> do
      putStrLn ("ERROR: " <> name <> " is not set. Running stub server (non-production).")
      run port stubApp

{- | パスの 1 階層目で各 BC の Application に分岐する。

シンプルな WAI レベルのルーター。Servant の型レベル結合より
柔軟で、各 API モジュールに変更を加えず統合できる。
-}
rootApp :: Connection -> JwtSecret -> JwtTtlSeconds -> Application
rootApp conn jwtSecret jwtTtl req respond =
  case pathInfo req of
    [] -> homeApp req respond
    ["health"] -> healthHandler req respond
    ["login"] -> loginPageApp userRepo verifier sessionRepo req respond
    ["api", "login"] -> loginApp userRepo verifier jwtSecret jwtTtl getPOSIXTime req respond
    "api" : "shippers" : _ -> shipperApp shipperRepo req respond
    "api" : "bookings" : _ -> bookingApp bookingRepo shipperChecker req respond
    "api" : "voyages" : _ -> voyageApp voyageRepo req respond
    ["shippers", "search"] -> shipperSearchApp shipperRepo req respond
    ["voyages", "new", "movement-row"] -> voyageMovementRowApp req respond
    "shippers" : _ -> shipperPageApp shipperRepo req respond
    "bookings" : _ -> bookingPageApp bookingRepo shipperChecker customsRepo voyageRepo trackingRepo req respond
    "estimates" : _ -> estimatePageApp estimateRepo req respond
    "voyages" : _ -> voyagePageApp voyageRepo req respond
    "public" : "tracking" : _ -> publicTrackingApp trackingRepo handlingRepo req respond
    "tracking" : _ : "manual-update" : _ ->
      -- US17 手動状態更新 (T7-A: RoleGate 配線済、Tracker/MasterAdmin のみ。ADR-0016)
      manualUpdateApp trackingRepo auditRepo sessionRepo (findRolesByUserId conn) getCurrentTime req respond
    "tracking" : _ : "audit-history" : _ ->
      -- US17 監査履歴タブ (htmx フラグメント)
      auditHistoryApp auditRepo req respond
    "handling" : _ ->
      -- Postgres NotificationRepository を注入。Handling.claim 完了時の
      -- 通知発火は notification テーブルに保存され、/notifications で参照可能。
      handlingPageApp
        txRunner
        handlingRepo
        codeRepo
        trackingRepo
        notificationRepo
        newLogDeliveryPort
        generateNotificationIdText -- ADR-0013 Phase 3 UUID v4 生成器 DI
        generateSixDigitCodeText -- T7-01 UNLOAD 時の確認コード生成器 DI
        req
        respond
    "pricing" : "calculate" : _ ->
      -- US21 料金算出画面。Postgres PricingRule / CurrencyRate。
      costCalculationApp pricingRuleRepo currencyRateRepo shipperRepo req respond
    "notifications" : _ ->
      -- US26 通知一覧画面。Postgres 永続化のため Handling.claim の通知と共有。
      notificationListApp notificationRepo req respond
    "exceptions" : _ ->
      -- US19/US20 例外一覧画面 (IT7)。Postgres exception_record を照会。
      -- 詳細ページ / 登録フォーム 3 種 / 解決 API は次イテレーションで追加。
      exceptionListApp exceptionRepo req respond
    _ ->
      respond $
        responseLBS
          status404
          [("Content-Type", "application/json")]
          "{\"error\":\"not found\"}"
  where
    userRepo = newPostgresUserRepository conn
    sessionRepo = newPostgresSessionRepository conn
    verifier = newBcryptVerifier
    shipperRepo = newPostgresShipperRepository conn
    bookingRepo = newPostgresBookingRepository conn
    shipperChecker = newPostgresShipperExistenceChecker conn
    voyageRepo = newPostgresVoyageRepository conn
    customsRepo = newPostgresCustomsDeclarationRepository conn
    estimateRepo = newPostgresEstimateRepository conn
    trackingRepo = newPostgresTrackingRepository conn
    auditRepo = newPostgresTrackingStateAuditRepository conn
    handlingRepo = newPostgresHandlingActivityRepository conn
    codeRepo = newPostgresConfirmationCodeRepository conn
    txRunner = newPostgresTxRunner conn
    -- IT6 US21 / US26: Postgres Repository を InMemory 実装から切替
    pricingRuleRepo = newPostgresPricingRuleRepository conn
    exceptionRepo = newPostgresExceptionRepository conn
    currencyRateRepo = newPostgresCurrencyRateRepository conn
    notificationRepo = newPostgresNotificationRepository conn

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
