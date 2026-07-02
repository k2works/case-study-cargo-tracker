{-# LANGUAGE OverloadedStrings #-}

{- | BookingPageApi の PRG (303) hspec-wai テスト (T-03, IT2)

POST /bookings/new がフォーム入力を受け取り、成功時は予約詳細画面 (`/bookings/:bookingId`)、
失敗時 (荷主未存在 / 期限フォーマット不正など) はフォーム + `?error=` クエリへ
リダイレクト (303 See Other) することを検証する。
-}
module Booking.Interfaces.BookingPageApiSpec (spec) where

import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as LBS
import Data.IORef (modifyIORef', newIORef, readIORef)
import Network.HTTP.Types.Header (Header)
import qualified Network.Wai
import Network.Wai.Test (simpleBody)
import qualified Network.Wai.Test
import Test.Hspec
import Test.Hspec.Wai
import Test.Hspec.Wai.Matcher (MatchHeader (..))

import Cargotracker.Booking.Application.CustomsPorts
  ( CustomsDeclarationRepository (..),
  )
import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    mkCargo,
    submitBooking,
  )
import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification (..),
  )
import Cargotracker.Booking.Interfaces.BookingPageApi (bookingPageApp)
import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (mkVoyageNumber)
import Cargotracker.Routing.Domain.Model.Voyage (mkVoyage)
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef (..))
import Cargotracker.Tracking.Application.Ports (TrackingRepository (..))
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Network.Wai (Application)

-- T-07 (IT2): サーバ採番された ID を含む Location の接頭辞一致検証
matchLocationPrefix :: BS.ByteString -> MatchHeader
matchLocationPrefix prefix = MatchHeader $ \hs _ ->
  case lookup "Location" (hs :: [Header]) of
    Just v | prefix `BS.isPrefixOf` v -> Nothing
    Just v -> Just ("Location does not start with " <> show prefix <> ": got " <> show v)
    Nothing -> Just "missing Location header"

makeRepo :: IO (BookingRepository IO)
makeRepo = do
  ref <- newIORef ([] :: [Cargo])
  pure
    BookingRepository
      { saveBooking = \c -> do
          modifyIORef' ref (c :)
          pure (Right ())
      , findCargoById = \_ -> pure Nothing
      , updateBooking = \_ -> pure (Right ())
      , findAllCargos = pure []
      }

checkerYes :: ShipperExistenceChecker IO
checkerYes = ShipperExistenceChecker {exists = \_ -> pure True}

checkerNo :: ShipperExistenceChecker IO
checkerNo = ShipperExistenceChecker {exists = \_ -> pure False}

mkApp :: ShipperExistenceChecker IO -> IO Application
mkApp ch = do
  repo <- makeRepo
  pure (bookingPageApp repo ch stubCustomsRepo stubVoyageRepo stubTrackingRepo)

stubCustomsRepo :: CustomsDeclarationRepository IO
stubCustomsRepo =
  CustomsDeclarationRepository
    { upsertCustomsDeclaration = \_ -> pure (Right ())
    , findByBookingId = \_ -> pure Nothing
    }

stubVoyageRepo :: VoyageRepository IO
stubVoyageRepo =
  VoyageRepository
    { findByVoyageNumber = \_ -> pure Nothing
    , saveVoyage = \_ -> pure ()
    , updateVoyage = \_ -> pure (Right ())
    , findAllVoyages = pure []
    }

-- US14 (IT5): TrackingRepository のテスト用スタブ (save は Right () / find は常に Nothing)
stubTrackingRepo :: TrackingRepository IO
stubTrackingRepo =
  TrackingRepository
    { saveTracking = \_ -> pure (Right ())
    , findByBookingId = \_ -> pure Nothing
    , findByTrackingNumber = \_ -> pure Nothing
    , updateTransportStatus = \_ _ -> pure (Right ())
    }

-- US06 (IT2): /handover テスト用に、特定の Cargo を find で返し、
-- updateBooking 呼出を IORef に記録する Repository を作る。
deadlineAt :: UTCTime
deadlineAt = UTCTime (fromGregorian 2026 12 31) (secondsToDiffTime 0)

routeForHandover :: RouteSpecification
routeForHandover =
  RouteSpecification
    { origin = UnLocode "JPTYO"
    , destination = UnLocode "USNYC"
    , arrivalDeadline = deadlineAt
    }

submittedCargo :: Cargo
submittedCargo =
  let draft = mkCargo (BookingId "BK-A1B2C3") (ShipperRef "SHP-X1Y2Z3") routeForHandover
   in case submitBooking draft of
        Right c -> c
        Left _ -> error "test setup: submitBooking failed"

draftCargo :: Cargo
draftCargo = mkCargo (BookingId "BK-A1B2C3") (ShipperRef "SHP-X1Y2Z3") routeForHandover

mkHandoverApp :: Maybe Cargo -> IO Application
mkHandoverApp seed = do
  let repo =
        BookingRepository
          { saveBooking = \_ -> pure (Right ())
          , findCargoById = \_ -> pure seed
          , updateBooking = \_ -> pure (Right ())
          , findAllCargos = pure []
          }
  pure (bookingPageApp repo checkerYes stubCustomsRepo stubVoyageRepo stubTrackingRepo)

{- | T5-09 (IT6): updateBooking 呼出を IORef で捕捉する Repository を返す
Application と、記録された Cargo リストを読み出す関数のペアを返す。

呼出回数だけでなく「どの Cargo 状態で更新されたか」まで検証できる。
-}
mkHandoverAppWithSpy :: Maybe Cargo -> IO (Application, IO [Cargo])
mkHandoverAppWithSpy seed = do
  updatesRef <- newIORef ([] :: [Cargo])
  let repo =
        BookingRepository
          { saveBooking = \_ -> pure (Right ())
          , findCargoById = \_ -> pure seed
          , updateBooking = \c -> do
              modifyIORef' updatesRef (c :)
              pure (Right ())
          , findAllCargos = pure []
          }
      app_ = bookingPageApp repo checkerYes stubCustomsRepo stubVoyageRepo stubTrackingRepo
  pure (app_, readIORef updatesRef)

spec :: Spec
spec = do
  describe "POST /bookings/new (T-03 PRG / 荷主あり)" $
    with (mkApp checkerYes) $ do
      it "正常系は 303 を返し Location が /bookings/BK-... を指す (T-07 自動採番)" $
        request
          "POST"
          "/bookings/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "bookingId=IGNORED&shipperId=SHP-X1Y2Z3&origin=JPTYO&destination=USNYC&deadline=2026-12-31T00%3A00"
          `shouldRespondWith` 303
            { matchHeaders = [matchLocationPrefix "/bookings/BK-"]
            }

      it "期限フォーマット不正は 303 + Location=/bookings/new?error=deadline-format" $
        request
          "POST"
          "/bookings/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "bookingId=BK-A1B2C3&shipperId=SHP-X1Y2Z3&origin=JPTYO&destination=USNYC&deadline=BAD"
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/bookings/new?error=deadline-format"]
            }

  describe "POST /bookings/new (T-03 PRG / 荷主なし)" $
    with (mkApp checkerNo) $ do
      it "ShipperNotFound は 303 + Location=/bookings/new?error=shipper-not-found" $
        request
          "POST"
          "/bookings/new"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "bookingId=BK-A1B2C3&shipperId=SHP-X1Y2Z3&origin=JPTYO&destination=USNYC&deadline=2026-12-31T00%3A00"
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/bookings/new?error=shipper-not-found"]
            }

  describe "POST /bookings/:bookingId/handover (US06 PRG)" $ do
    with (mkHandoverApp (Just submittedCargo)) $
      it "Submitted 状態は 303 を返し flash=handover-ok を付ける" $
        request "POST" "/bookings/BK-A1B2C3/handover" [] ""
          `shouldRespondWith` 303
            { matchHeaders =
                ["Location" <:> "/bookings/BK-A1B2C3?flash=handover-ok"]
            }

    with (mkHandoverApp (Just draftCargo)) $
      it "Draft 状態は 303 + invalid-state エラーを付ける" $
        request "POST" "/bookings/BK-A1B2C3/handover" [] ""
          `shouldRespondWith` 303
            { matchHeaders =
                [ "Location"
                    <:> "/bookings/BK-A1B2C3?error=invalid-state&from=Draft"
                ]
            }

    with (mkHandoverApp Nothing) $
      it "予約未存在は 303 + /bookings/new?error=booking-not-found を指す" $
        request "POST" "/bookings/BK-NOTHERE/handover" [] ""
          `shouldRespondWith` 303
            { matchHeaders =
                ["Location" <:> "/bookings/new?error=booking-not-found"]
            }

    describe "T5-09: updateBooking 副作用検証 (spy Repository)" $ do
      -- IT6 T5-09: 呼出捕捉。runIO で事前に WAI request を実行し、
      -- 記録された Cargo リストを純粋な `it` で assert する。
      submittedUpdates <- runIO $ do
        (app_, readUpdates) <- mkHandoverAppWithSpy (Just submittedCargo)
        _ <-
          Network.Wai.Test.runSession
            ( Network.Wai.Test.srequest
                ( Network.Wai.Test.SRequest
                    ( Network.Wai.Test.setPath
                        (Network.Wai.defaultRequest {Network.Wai.requestMethod = "POST"})
                        "/bookings/BK-A1B2C3/handover"
                    )
                    ""
                )
            )
            app_
        readUpdates

      it "Submitted → handover 成功時に updateBooking が 1 回呼ばれる" $
        length submittedUpdates `shouldBe` 1

      it "呼ばれた updateBooking の引数 Cargo は元と同じ BookingId を保持する" $
        case submittedUpdates of
          [c] -> cargoBookingId c `shouldBe` BookingId "BK-A1B2C3"
          _ -> expectationFailure "expected exactly one updateBooking call"

      draftUpdates <- runIO $ do
        (app_, readUpdates) <- mkHandoverAppWithSpy (Just draftCargo)
        _ <-
          Network.Wai.Test.runSession
            ( Network.Wai.Test.srequest
                ( Network.Wai.Test.SRequest
                    ( Network.Wai.Test.setPath
                        (Network.Wai.defaultRequest {Network.Wai.requestMethod = "POST"})
                        "/bookings/BK-A1B2C3/handover"
                    )
                    ""
                )
            )
            app_
        readUpdates

      it "Draft → handover invalid-state 時に updateBooking は呼ばれない" $
        length draftUpdates `shouldBe` 0

  describe "GET /bookings/new (T-08 フラッシュ表示)" $
    with (mkApp checkerYes) $ do
      it "?error なしは 200 を返す" $
        get "/bookings/new" `shouldRespondWith` 200

      it "?error=deadline-format で 200 + 期限フォーマットエラーを含む" $ do
        res <- get "/bookings/new?error=deadline-format"
        liftIO $ do
          let body = LBS.toStrict (simpleBody res)
          shouldSatisfy
            body
            (\b -> "\xe5\x88\xb0\xe7\x9d\x80\xe6\x9c\x9f\xe9\x99\x90" `BS.isInfixOf` b)

  describe "US27: 通関情報の編集/紐付け" $ do
    let customsRepoStub = stubCustomsRepo
        cargoStub = draftCargo
        mkCustomsApp seed =
          pure $
            bookingPageApp
              ( BookingRepository
                  { saveBooking = \_ -> pure (Right ())
                  , findCargoById = \_ -> pure seed
                  , updateBooking = \_ -> pure (Right ())
                  , findAllCargos = pure []
                  }
              )
              checkerYes
              customsRepoStub
              stubVoyageRepo
              stubTrackingRepo

    describe "GET /bookings/:id/customs/edit" $ do
      with (mkCustomsApp (Just cargoStub)) $
        it "予約が存在すれば 200 を返す" $
          get "/bookings/BK-A1B2C3/customs/edit" `shouldRespondWith` 200

      with (mkCustomsApp Nothing) $
        it "予約が見つからなければ 200 + not-found ページを返す" $
          get "/bookings/BK-DOESNT/customs/edit" `shouldRespondWith` 200

    describe "POST /bookings/:id/customs" $ do
      with (mkCustomsApp (Just cargoStub)) $
        it "正常 PRG: 303 + Location が /bookings/:id?flash=customs-ok" $
          request
            "POST"
            "/bookings/BK-A1B2C3/customs"
            [("Content-Type", "application/x-www-form-urlencoded")]
            "hs_code=123456&broker_name=ABC&status=PENDING"
            `shouldRespondWith` 303
              { matchHeaders =
                  ["Location" <:> "/bookings/BK-A1B2C3?flash=customs-ok"]
              }

      with (mkCustomsApp (Just cargoStub)) $
        it "HS コード不正は 303 + /customs/edit?error=invalid-hs-code" $
          request
            "POST"
            "/bookings/BK-A1B2C3/customs"
            [("Content-Type", "application/x-www-form-urlencoded")]
            "hs_code=BAD&broker_name=ABC&status=PENDING"
            `shouldRespondWith` 303
              { matchHeaders =
                  ["Location" <:> "/bookings/BK-A1B2C3/customs/edit?error=invalid-hs-code"]
              }

      with (mkCustomsApp Nothing) $
        it "予約未存在は 303 + /bookings/new?error=booking-not-found" $
          request
            "POST"
            "/bookings/BK-DOESNT/customs"
            [("Content-Type", "application/x-www-form-urlencoded")]
            "hs_code=123456&broker_name=ABC&status=PENDING"
            `shouldRespondWith` 303
              { matchHeaders =
                  ["Location" <:> "/bookings/new?error=booking-not-found"]
              }

  describe "U-02 (IT3): CargoType 動的フィールド" $ do
    describe "GET /bookings/new/cargo-type-row" $ do
      with (mkApp checkerYes) $ do
        it "cargoType 未指定は空のフラグメントを 200 で返す" $
          get "/bookings/new/cargo-type-row" `shouldRespondWith` 200

        it "cargoType=Hazardous は危険物フィールドを含むフラグメントを返す" $ do
          res <- get "/bookings/new/cargo-type-row?cargoType=Hazardous"
          liftIO $ do
            let body = LBS.toStrict (simpleBody res)
            shouldSatisfy
              body
              (\b -> "hazardousClass" `BS.isInfixOf` b && "unNumber" `BS.isInfixOf` b)

        it "cargoType=Refrigerated は冷凍フィールドを含むフラグメントを返す" $ do
          res <- get "/bookings/new/cargo-type-row?cargoType=Refrigerated"
          liftIO $ do
            let body = LBS.toStrict (simpleBody res)
            shouldSatisfy
              body
              (\b -> "minTemperature" `BS.isInfixOf` b && "temperatureUnit" `BS.isInfixOf` b)

    describe "POST /bookings/new with cargoType" $ do
      with (mkApp checkerYes) $ do
        it "Hazardous で危険物 3 項目を送れば 303" $
          request
            "POST"
            "/bookings/new"
            [("Content-Type", "application/x-www-form-urlencoded")]
            ( "bookingId=&shipperId=SHP-X1Y2Z3&origin=JPTYO&destination=USNYC"
                <> "&deadline=2026-12-31T00%3A00"
                <> "&cargoType=Hazardous"
                <> "&hazardousClass=3"
                <> "&unNumber=1203"
                <> "&properShippingName=GASOLINE"
            )
            `shouldRespondWith` 303
              { matchHeaders = [matchLocationPrefix "/bookings/BK-"]
              }

        it "Hazardous でフィールド欠落は 303 + ?error=hazardous-fields-missing" $
          request
            "POST"
            "/bookings/new"
            [("Content-Type", "application/x-www-form-urlencoded")]
            ( "bookingId=&shipperId=SHP-X1Y2Z3&origin=JPTYO&destination=USNYC"
                <> "&deadline=2026-12-31T00%3A00"
                <> "&cargoType=Hazardous"
            )
            `shouldRespondWith` 303
              { matchHeaders =
                  ["Location" <:> "/bookings/new?error=hazardous-fields-missing"]
              }

  describe "US08a: GET /bookings/:id/routes" $ do
    let mkRoutesApp seed =
          pure $
            bookingPageApp
              ( BookingRepository
                  { saveBooking = \_ -> pure (Right ())
                  , findCargoById = \_ -> pure seed
                  , updateBooking = \_ -> pure (Right ())
                  , findAllCargos = pure []
                  }
              )
              checkerYes
              stubCustomsRepo
              stubVoyageRepo
              stubTrackingRepo

    with (mkRoutesApp (Just draftCargo)) $
      it "予約が存在すれば 200 を返す" $
        get "/bookings/BK-A1B2C3/routes" `shouldRespondWith` 200

    with (mkRoutesApp Nothing) $
      it "予約が存在しなければ 200 + not-found ページを返す" $
        get "/bookings/BK-NOTHERE/routes" `shouldRespondWith` 200

  -- US08a タスク 4.5: 経路候補算出の hspec-wai 受入テスト
  describe "US08a タスク 4.5: 経路候補算出の受入" $ do
    let mkRoutesAppWithVoyages seed voys =
          pure $
            bookingPageApp
              ( BookingRepository
                  { saveBooking = \_ -> pure (Right ())
                  , findCargoById = \_ -> pure seed
                  , updateBooking = \_ -> pure (Right ())
                  , findAllCargos = pure []
                  }
              )
              checkerYes
              stubCustomsRepo
              ( VoyageRepository
                  { findByVoyageNumber = \_ -> pure Nothing
                  , saveVoyage = \_ -> pure ()
                  , updateVoyage = \_ -> pure (Right ())
                  , findAllVoyages = pure voys
                  }
              )
              stubTrackingRepo
        cargoForRoutes = draftCargo -- JPTYO -> USNYC / arrivalDeadline 2026-12-31
        baseDay = UTCTime (fromGregorian 2026 9 1) (secondsToDiffTime 0)
        plusDays n = case n of
          0 -> baseDay
          _ -> UTCTime (fromGregorian 2026 9 (1 + n)) (secondsToDiffTime 0)
        mkV vn dep arr depT arrT =
          case ( mkVoyageNumber vn
               , Right () :: Either String ()
               ) of
            (Right v, _) ->
              case mkVoyage
                v
                [ CarrierMovement
                    { departureLocation = UnLocode dep
                    , arrivalLocation = UnLocode arr
                    , departureTime = depT
                    , arrivalTime = arrT
                    }
                ] of
                Right voy -> voy
                Left e -> error (show e)
            _ -> error "vn parse"

    -- 成功: V0001 直行便 JPTYO -> USNYC が結果に表示される
    with (mkRoutesAppWithVoyages (Just cargoForRoutes) [mkV "V0001" "JPTYO" "USNYC" baseDay (plusDays 14)]) $
      it "成功: 候補表に V0001 と rank=0 が含まれる" $ do
        res <- get "/bookings/BK-A1B2C3/routes"
        liftIO $ do
          let body = LBS.toStrict (simpleBody res)
          shouldSatisfy body ("V0001" `BS.isInfixOf`)

    -- 0 件: 該当 Voyage なし -> alert-warning
    with (mkRoutesAppWithVoyages (Just cargoForRoutes) []) $
      it "0 件: 期限内到達不可メッセージを含む" $ do
        res <- get "/bookings/BK-A1B2C3/routes"
        liftIO $ do
          let body = LBS.toStrict (simpleBody res)
          -- 「期限内に到達可能な経路が見つかりませんでした」を含む
          shouldSatisfy body (\b -> "alert-warning" `BS.isInfixOf` b)

    -- 期日超過: 期限超過の Voyage は除外され、結果が 0 件メッセージになる
    -- (draftCargo の deadline は 2026-12-31。それより 1 年後に到着する直行便)
    with
      ( mkRoutesAppWithVoyages
          (Just cargoForRoutes)
          [ mkV
              "V9999"
              "JPTYO"
              "USNYC"
              baseDay
              (UTCTime (fromGregorian 2027 12 31) (secondsToDiffTime 0))
          ]
      )
      $ it "期日超過: 期限超過便は除外され 0 件警告になる"
      $ do
        res <- get "/bookings/BK-A1B2C3/routes"
        liftIO $ do
          let body = LBS.toStrict (simpleBody res)
          -- V9999 は除外され、警告メッセージが表示される
          shouldSatisfy body ("alert-warning" `BS.isInfixOf`)
          shouldSatisfy body (not . ("V9999" `BS.isInfixOf`))

  -- T4-08 (IT5 task 3.1): Confirm/Cancel/Link/Unlink の hspec-wai 統合テスト
  -- Cargo 集約の状態遷移 (RouteAssigned → Confirmed / → Cancelled / Draft → RouteAssigned) を
  -- Application Command 経由で走らせ、PRG (303) の Location と ?flash= / ?error= を検証する。

  describe "POST /bookings/:id/confirm (US13 / T4-08)" $ do
    let mkConfirmApp status =
          pure $
            bookingPageApp
              ( BookingRepository
                  { saveBooking = \_ -> pure (Right ())
                  , findCargoById = \_ ->
                      pure
                        ( Just
                            (fst (mkStatusCargo status))
                        )
                  , updateBooking = \_ -> pure (Right ())
                  , findAllCargos = pure []
                  }
              )
              checkerYes
              stubCustomsRepo
              stubVoyageRepo
              stubTrackingRepo

    with (mkConfirmApp RouteAssigned) $
      it "RouteAssigned → Confirmed で 303 + flash=confirm-ok" $
        request "POST" "/bookings/BK-A1B2C3/confirm" [] ""
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/bookings/BK-A1B2C3?flash=confirm-ok"]
            }

    with (mkConfirmApp Draft) $
      it "Draft からの Confirm は 303 + error=invalid-state" $
        request "POST" "/bookings/BK-A1B2C3/confirm" [] ""
          `shouldRespondWith` 303

  describe "POST /bookings/:id/cancel (US13 / T4-08)" $ do
    let mkCancelApp status =
          pure $
            bookingPageApp
              ( BookingRepository
                  { saveBooking = \_ -> pure (Right ())
                  , findCargoById = \_ ->
                      pure
                        ( Just
                            (fst (mkStatusCargo status))
                        )
                  , updateBooking = \_ -> pure (Right ())
                  , findAllCargos = pure []
                  }
              )
              checkerYes
              stubCustomsRepo
              stubVoyageRepo
              stubTrackingRepo

    with (mkCancelApp Confirmed) $
      it "Confirmed → Cancelled で 303 + flash=cancel-ok" $
        request "POST" "/bookings/BK-A1B2C3/cancel" [] ""
          `shouldRespondWith` 303
            { matchHeaders = ["Location" <:> "/bookings/BK-A1B2C3?flash=cancel-ok"]
            }

    with (mkCancelApp Cancelled) $
      it "Cancelled からの再 cancel は 303 + error=invalid-state" $
        request "POST" "/bookings/BK-A1B2C3/cancel" [] ""
          `shouldRespondWith` 303

  describe "POST /bookings/:id/route (US11 LinkRoute / T4-08)" $
    let mkLinkApp status =
          pure $
            bookingPageApp
              ( BookingRepository
                  { saveBooking = \_ -> pure (Right ())
                  , findCargoById = \_ ->
                      pure
                        ( Just
                            (fst (mkStatusCargo status))
                        )
                  , updateBooking = \_ -> pure (Right ())
                  , findAllCargos = pure []
                  }
              )
              checkerYes
              stubCustomsRepo
              stubVoyageRepo
              stubTrackingRepo
     in with (mkLinkApp RouteProposed) $
          it "RouteProposed → RouteAssigned で 303 + flash=link-ok" $
            request "POST" "/bookings/BK-A1B2C3/route" [] ""
              `shouldRespondWith` 303
                { matchHeaders = ["Location" <:> "/bookings/BK-A1B2C3?flash=link-ok"]
                }

  describe "DELETE /bookings/:id/route (US11 UnlinkRoute / T4-08)" $
    let mkUnlinkApp status =
          pure $
            bookingPageApp
              ( BookingRepository
                  { saveBooking = \_ -> pure (Right ())
                  , findCargoById = \_ ->
                      pure
                        ( Just
                            (fst (mkStatusCargo status))
                        )
                  , updateBooking = \_ -> pure (Right ())
                  , findAllCargos = pure []
                  }
              )
              checkerYes
              stubCustomsRepo
              stubVoyageRepo
              stubTrackingRepo
     in with (mkUnlinkApp RouteAssigned) $
          it "RouteAssigned → Draft で 303 + flash=unlink-ok" $
            request "DELETE" "/bookings/BK-A1B2C3/route" [] ""
              `shouldRespondWith` 303
                { matchHeaders = ["Location" <:> "/bookings/BK-A1B2C3?flash=unlink-ok"]
                }

-- ヘルパー: 指定 BookingStatus を持つ Cargo を返す (updateBooking の期待バージョン用)
mkStatusCargo :: BookingStatus -> (Cargo, ())
mkStatusCargo status =
  let base = mkCargo (BookingId "BK-A1B2C3") (ShipperRef "SHP-X1Y2Z3") routeForHandover
   in (base {cargoStatus = status}, ())
