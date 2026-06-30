-- | RouteConfirmView のテスト (US09 + US11, IT4)
module Booking.Views.RouteConfirmViewSpec (spec) where

import qualified Data.Text as T
import Data.Text.Lazy (toStrict)
import Lucid (Html, renderText)
import Test.Hspec

import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Views.RouteConfirmView
  ( RouteOption (..),
    routeAssignedBadge,
    routeLinkSection,
    routeSelectionForm,
  )

render :: Html () -> T.Text
render = toStrict . renderText

bid :: BookingId
bid = BookingId "BK-A1B2C3"

sampleOption :: RouteOption
sampleOption =
  RouteOption
    { roId = "550e8400-e29b-41d4-a716-446655440000"
    , roRank = 0
    , roPortsLabel = "JPTYO → USNYC"
    , roVoyagesLabel = "V001"
    }

spec :: Spec
spec = describe "RouteConfirmView (US09 + US11 / IT4)" $ do
  describe "routeSelectionForm (US09)" $ do
    it "候補ありなら radio + 「経路を確定」ボタンを出力" $ do
      let html = render (routeSelectionForm bid [sampleOption] RouteProposed)
      html `shouldSatisfy` T.isInfixOf "name=\"selected_route\""
      html `shouldSatisfy` T.isInfixOf "type=\"radio\""
      html `shouldSatisfy` T.isInfixOf "経路を確定"
      html `shouldSatisfy` T.isInfixOf "action=\"/bookings/BK-A1B2C3/routes/confirm\""

    it "候補なしなら alert-info「候補がありません」を表示" $ do
      let html = render (routeSelectionForm bid [] RouteProposed)
      html `shouldSatisfy` T.isInfixOf "alert-info"
      html `shouldSatisfy` T.isInfixOf "選択可能な経路候補がありません"

    it "RouteAssigned 状態では radio と確定ボタンが disabled" $ do
      let html = render (routeSelectionForm bid [sampleOption] RouteAssigned)
      html `shouldSatisfy` T.isInfixOf "disabled"
      html `shouldSatisfy` T.isInfixOf "確定済"

    it "Confirmed 状態でも disabled" $ do
      let html = render (routeSelectionForm bid [sampleOption] Confirmed)
      html `shouldSatisfy` T.isInfixOf "disabled"

    it "Cancelled 状態でも disabled" $ do
      let html = render (routeSelectionForm bid [sampleOption] Cancelled)
      html `shouldSatisfy` T.isInfixOf "disabled"

  describe "routeLinkSection (US11)" $ do
    it "RouteProposed 状態は「経路を紐付け」ボタン (POST /route)" $ do
      let html = render (routeLinkSection bid RouteProposed)
      html `shouldSatisfy` T.isInfixOf "経路を紐付け"
      html `shouldSatisfy` T.isInfixOf "action=\"/bookings/BK-A1B2C3/route\""

    it "RouteAssigned 状態は「経路紐付けを解除」(_method=DELETE)" $ do
      let html = render (routeLinkSection bid RouteAssigned)
      html `shouldSatisfy` T.isInfixOf "経路紐付けを解除"
      html `shouldSatisfy` T.isInfixOf "name=\"_method\""
      html `shouldSatisfy` T.isInfixOf "value=\"DELETE\""

    it "Confirmed 状態は操作不可 (情報表示のみ)" $ do
      let html = render (routeLinkSection bid Confirmed)
      html `shouldSatisfy` T.isInfixOf "本状態では経路操作は不可"
      html `shouldSatisfy` (not . T.isInfixOf "経路紐付けを解除")

    it "Draft 状態も操作不可" $ do
      let html = render (routeLinkSection bid Draft)
      html `shouldSatisfy` T.isInfixOf "本状態では経路操作は不可"

  describe "routeAssignedBadge (BookingStatus -> Bootstrap バッジ)" $ do
    it "Draft -> bg-secondary" $ render (routeAssignedBadge Draft) `shouldSatisfy` T.isInfixOf "bg-secondary"
    it "Submitted -> bg-info" $ render (routeAssignedBadge Submitted) `shouldSatisfy` T.isInfixOf "bg-info"
    it "RouteAssigned -> bg-success" $ render (routeAssignedBadge RouteAssigned) `shouldSatisfy` T.isInfixOf "bg-success"
    it "Cancelled -> bg-danger" $ render (routeAssignedBadge Cancelled) `shouldSatisfy` T.isInfixOf "bg-danger"
    it "Closed -> bg-dark" $ render (routeAssignedBadge Closed) `shouldSatisfy` T.isInfixOf "bg-dark"
    it "バッジに状態テキストを含む (DB CHECK 整合)" $ do
      render (routeAssignedBadge RouteAssigned) `shouldSatisfy` T.isInfixOf "ROUTE_ASSIGNED"
      render (routeAssignedBadge Confirmed) `shouldSatisfy` T.isInfixOf "CONFIRMED"
