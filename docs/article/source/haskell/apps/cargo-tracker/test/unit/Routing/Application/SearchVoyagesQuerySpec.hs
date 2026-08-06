{-# LANGUAGE OverloadedStrings #-}

-- | SearchVoyagesQuery のテスト (US07, IT3)
module Routing.Application.SearchVoyagesQuerySpec (spec) where

import Data.Text (Text)
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Application.SearchVoyagesQuery
  ( SearchVoyagesInput (..),
    execute,
  )
import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( mkVoyageNumber,
    unVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage
  ( Voyage,
    mkVoyage,
    voyageNumber,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 8 1) (secondsToDiffTime 0)

cm :: Text -> Text -> UTCTime -> UTCTime -> CarrierMovement
cm dep arr depT arrT =
  CarrierMovement
    { departureLocation = UnLocode dep
    , arrivalLocation = UnLocode arr
    , departureTime = depT
    , arrivalTime = arrT
    }

mkV :: Text -> [CarrierMovement] -> Voyage
mkV vn ms = case mkVoyageNumber vn of
  Right v -> case mkVoyage v ms of
    Right voy -> voy
    Left e -> error (show e)
  Left e -> error (show e)

makeRepo :: [Voyage] -> VoyageRepository IO
makeRepo voys =
  VoyageRepository
    { findByVoyageNumber = \_ -> pure Nothing
    , saveVoyage = \_ -> pure ()
    , updateVoyage = \_ -> pure (Right ())
    , findAllVoyages = pure voys
    }

spec :: Spec
spec = describe "SearchVoyagesQuery (US07)" $ do
  let validInput =
        SearchVoyagesInput
          { inputOrigin = UnLocode "JPTYO"
          , inputDestination = UnLocode "USNYC"
          , inputFromDate = t0
          , inputToDate = addUTCTime (10 * 86400) t0
          }
      vMatch1 = mkV "V0001" [cm "JPTYO" "USNYC" t0 (addUTCTime 86400 t0)]
      vMatch2 = mkV "V0002" [cm "JPTYO" "USNYC" (addUTCTime 86400 t0) (addUTCTime (2 * 86400) t0)]
      vOther = mkV "V0003" [cm "JPOSA" "GBLON" t0 (addUTCTime 86400 t0)]

  it "条件にマッチする航海を出発時刻昇順で返す" $ do
    res <- execute (makeRepo [vMatch2, vMatch1, vOther]) validInput
    case res of
      Right xs -> map (unVoyageNumber . voyageNumber) xs `shouldBe` ["V0001", "V0002"]
      Left e -> expectationFailure ("expected Right but got " <> show e)

  it "該当 0 件のときも Right [] を返す" $ do
    res <- execute (makeRepo [vOther]) validInput
    res `shouldBe` Right []

  it "入力が不正 (from > to) は Left InvalidSearchPeriod" $ do
    let bad = validInput {inputFromDate = inputToDate validInput, inputToDate = t0}
    res <- execute (makeRepo []) bad
    case res of
      Left (InvalidSearchPeriod _ _) -> pure ()
      other -> expectationFailure ("unexpected: " <> show other)
