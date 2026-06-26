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
  ( RegisterBookingInput (..),
    execute,
  )
import Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
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
  let r = BookingRepository {saveBooking = \c -> modifyIORef' ref (c :)}
  pure (r, readIORef ref)

validInput :: RegisterBookingInput
validInput =
  RegisterBookingInput
    { inputBookingId = "BK-A1B2C3"
    , inputShipperId = "SHP-X1Y2Z3"
    , inputOrigin = "JPTYO"
    , inputDestination = "USNYC"
    , inputDeadline = deadline
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
