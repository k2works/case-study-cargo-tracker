{- | RegisterBookingCommand のテスト (IT1 US04 3.2)

Application 層:
1. 各 Text/UTCTime を値オブジェクトに構築 (Domain 検証)
2. ShipperExistenceChecker で荷主存在を ACL 経由で検証
3. Cargo 集約を Draft で生成
4. BookingRepository.save で永続化

ポート:
- BookingRepository: save (リポジトリ)
- ShipperExistenceChecker: exists (ACL = 他 BC への参照を抽象化)
-}
module Booking.Application.RegisterBookingCommandSpec (spec) where

import Data.IORef (modifyIORef', newIORef, readIORef)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Booking.Application.Ports
  ( BookingRepository (..),
    ShipperExistenceChecker (..),
  )
import Cargotracker.Booking.Application.RegisterBookingCommand
  ( CargoTypeInput (..),
    RegisterBookingInput (..),
    execute,
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    cargoBookingId,
  )
import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

deadline :: UTCTime
deadline = UTCTime (fromGregorian 2026 12 31) (secondsToDiffTime 0)

makeCheckerYes :: ShipperExistenceChecker IO
makeCheckerYes = ShipperExistenceChecker {exists = \_ -> pure True}

makeCheckerNo :: ShipperExistenceChecker IO
makeCheckerNo = ShipperExistenceChecker {exists = \_ -> pure False}

makeRepo :: IO (BookingRepository IO, IO [Cargo])
makeRepo = do
  ref <- newIORef []
  let r =
        BookingRepository
          { saveBooking = \c -> do
              modifyIORef' ref (c :)
              pure (Right ())
          , findCargoById = \_ -> pure Nothing
          , updateBooking = \_ -> pure (Right ())
          }
  pure (r, readIORef ref)

-- リポジトリ側で「保存対象の荷主がサロゲートキー解決できない」状況を再現するフェイク
makeRepoShipperNotFound :: IO (BookingRepository IO)
makeRepoShipperNotFound = do
  pure
    BookingRepository
      { saveBooking = \c ->
          let bid = case cargoBookingId c of BookingId t -> t
           in pure (Left (ShipperNotFound ("repo-resolve-failed:" <> bid)))
      , findCargoById = \_ -> pure Nothing
      , updateBooking = \_ -> pure (Right ())
      }

validInput :: RegisterBookingInput
validInput =
  RegisterBookingInput
    { inputBookingId = "BK-A1B2C3"
    , inputShipperId = "SHP-X1Y2Z3"
    , inputOrigin = "JPTYO"
    , inputDestination = "USNYC"
    , inputDeadline = deadline
    , inputCargoType = InputGeneral
    }

spec :: Spec
spec = do
  describe "execute (Happy)" $ do
    it "存在する荷主の予約を Draft で保存する" $ do
      (repo, get) <- makeRepo
      result <- execute repo makeCheckerYes validInput
      case result of
        Right bid -> do
          bid `shouldBe` BookingId "BK-A1B2C3"
          saved <- get
          length saved `shouldBe` 1
          case saved of
            (c : _) -> cargoStatus c `shouldBe` Draft
            _ -> expectationFailure "no saved cargo"
        Left e -> expectationFailure ("expected Right but got " <> show e)

  describe "execute (バリデーション)" $ do
    it "BookingId が不正なら InvalidBookingId" $ do
      (repo, _) <- makeRepo
      result <- execute repo makeCheckerYes validInput {inputBookingId = "INVALID"}
      result `shouldBe` Left (InvalidBookingId "expected BK-XXXXXX")

    it "ShipperId が不正なら InvalidShipperId" $ do
      (repo, _) <- makeRepo
      result <- execute repo makeCheckerYes validInput {inputShipperId = "INVALID"}
      result `shouldBe` Left (InvalidShipperId "expected SHP-XXXXXX")

    it "出発港が不正な UN/LOCODE なら InvalidUnLocode" $ do
      (repo, _) <- makeRepo
      result <- execute repo makeCheckerYes validInput {inputOrigin = "ABC"}
      result `shouldBe` Left (InvalidUnLocode "expected 5 chars")

    it "到着港が不正な UN/LOCODE なら InvalidUnLocode" $ do
      (repo, _) <- makeRepo
      result <- execute repo makeCheckerYes validInput {inputDestination = "ABC"}
      result `shouldBe` Left (InvalidUnLocode "expected 5 chars")

  describe "execute (ACL)" $ do
    it "存在しない荷主は ShipperNotFound" $ do
      (repo, get) <- makeRepo
      result <- execute repo makeCheckerNo validInput
      result `shouldBe` Left (ShipperNotFound "SHP-X1Y2Z3")
      saved <- get
      length saved `shouldBe` 0 -- 永続化されない
  describe "execute (T-01: Repository が Left を返す経路)" $ do
    it "Repository.saveBooking が ShipperNotFound を返すと例外化せず Left を伝播する" $ do
      repo <- makeRepoShipperNotFound
      result <- execute repo makeCheckerYes validInput
      case result of
        Left (ShipperNotFound _) -> pure ()
        other ->
          expectationFailure
            ("expected Left (ShipperNotFound _) but got " <> show other)

  describe "execute (US05 CargoType)" $ do
    it "Hazardous 入力で危険物予約を保存できる" $ do
      (repo, get) <- makeRepo
      let i =
            validInput
              { inputCargoType = InputHazardous "3" "1203" "Gasoline"
              }
      result <- execute repo makeCheckerYes i
      case result of
        Right _ -> pure ()
        Left e -> expectationFailure ("expected Right but got " <> show e)
      saved <- get
      length saved `shouldBe` 1

    it "Hazardous の UN 番号不正は Left InvalidBookingId" $ do
      (repo, _) <- makeRepo
      let i =
            validInput
              { inputCargoType = InputHazardous "3" "ABC" "Gasoline"
              }
      result <- execute repo makeCheckerYes i
      case result of
        Left (InvalidBookingId _) -> pure ()
        other -> expectationFailure ("unexpected: " <> show other)

    it "Refrigerated 入力で冷凍予約を保存できる" $ do
      (repo, _) <- makeRepo
      let i =
            validInput
              { inputCargoType = InputRefrigerated (-20) (-10) "C"
              }
      result <- execute repo makeCheckerYes i
      case result of
        Right _ -> pure ()
        Left e -> expectationFailure ("expected Right but got " <> show e)

    it "Refrigerated の温度逆転は Left InvalidBookingId" $ do
      (repo, _) <- makeRepo
      let i = validInput {inputCargoType = InputRefrigerated 10 (-5) "C"}
      result <- execute repo makeCheckerYes i
      case result of
        Left (InvalidBookingId _) -> pure ()
        other -> expectationFailure ("unexpected: " <> show other)

    it "Refrigerated の温度単位不正は Left InvalidBookingId" $ do
      (repo, _) <- makeRepo
      let i = validInput {inputCargoType = InputRefrigerated (-20) (-10) "K"}
      result <- execute repo makeCheckerYes i
      case result of
        Left (InvalidBookingId _) -> pure ()
        other -> expectationFailure ("unexpected: " <> show other)
