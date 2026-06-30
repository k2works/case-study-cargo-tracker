-- | CancellationFeeView のテスト (US13, IT4)
module Booking.Views.CancellationFeeViewSpec (spec) where

import Data.Ratio ((%))
import qualified Data.Text as T
import Data.Text.Lazy (toStrict)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Lucid (Html, renderText)
import Test.Hspec

import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId (..))
import Cargotracker.Booking.Domain.Model.Value.CancellationFee
  ( CancellationFee (..),
    CancellationTier (..),
  )
import Cargotracker.Booking.Views.CancellationFeeView
  ( cancelConfirmButton,
    feePreview,
    feePreviewFragment,
  )

bid :: BookingId
bid = BookingId "BK-A1B2C3"

now :: UTCTime
now = UTCTime (fromGregorian 2026 9 5) (secondsToDiffTime 0)

freeFee, partialFee, fullFee :: CancellationFee
freeFee = CancellationFee Free (0 % 100) now
partialFee = CancellationFee Partial (30 % 100) now
fullFee = CancellationFee Full (100 % 100) now

render :: Html () -> T.Text
render = toStrict . renderText

spec :: Spec
spec = describe "CancellationFeeView (US13 / IT4)" $ do
  describe "feePreview (予約詳細埋め込み)" $ do
    it "BookingId を含む hx-get 属性を出力" $ do
      let html = render (feePreview bid)
      html `shouldSatisfy` T.isInfixOf "hx-get=\"/bookings/BK-A1B2C3/cancel/preview\""

    it "ターゲット要素 #cancellation-fee-modal を含む" $ do
      let html = render (feePreview bid)
      html `shouldSatisfy` T.isInfixOf "id=\"cancellation-fee-modal\""

    it "「キャンセル料を確認」ボタンを含む" $ do
      let html = render (feePreview bid)
      html `shouldSatisfy` T.isInfixOf "キャンセル料を確認"

  describe "feePreviewFragment (htmx 戻り fragment)" $ do
    it "Free ティアは alert-success クラス + 「無料」を含む" $ do
      let html = render (feePreviewFragment bid freeFee)
      html `shouldSatisfy` T.isInfixOf "alert-success"
      html `shouldSatisfy` T.isInfixOf "無料"
      html `shouldSatisfy` T.isInfixOf "0%"

    it "Partial ティアは alert-warning クラス + 「30%」を含む" $ do
      let html = render (feePreviewFragment bid partialFee)
      html `shouldSatisfy` T.isInfixOf "alert-warning"
      html `shouldSatisfy` T.isInfixOf "30%"

    it "Full ティアは alert-danger クラス + 「100%」を含む" $ do
      let html = render (feePreviewFragment bid fullFee)
      html `shouldSatisfy` T.isInfixOf "alert-danger"
      html `shouldSatisfy` T.isInfixOf "100%"

  describe "cancelConfirmButton" $ do
    it "POST /bookings/:id/cancel を action とする form を生成" $ do
      let html = render (cancelConfirmButton bid)
      html `shouldSatisfy` T.isInfixOf "action=\"/bookings/BK-A1B2C3/cancel\""
      html `shouldSatisfy` T.isInfixOf "method=\"post\""

    it "data-confirm 属性で確認ダイアログを促す" $ do
      let html = render (cancelConfirmButton bid)
      html `shouldSatisfy` T.isInfixOf "data-confirm"
      html `shouldSatisfy` T.isInfixOf "本当にキャンセルしますか"
