{-# LANGUAGE OverloadedStrings #-}

-- | 引取通知印刷用ビューの単体テスト (T5-05, IT6)
module Tracking.Views.ClaimNotificationViewSpec (spec) where

import qualified Data.ByteString.Lazy.Char8 as LBC
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Lucid (renderBS)
import Test.Hspec

import Cargotracker.Tracking.Views.ClaimNotificationView
  ( ClaimNotificationPayload (..),
    claimNotificationPage,
  )

samplePayload :: ClaimNotificationPayload
samplePayload =
  ClaimNotificationPayload
    { cnpBookingId = "BK-A1B2C3"
    , cnpConfirmationCode = "789012"
    , cnpLocationUnlocode = "JPTYO"
    , cnpConsigneeName = Just "山田 太郎"
    }

renderText :: ClaimNotificationPayload -> T.Text
renderText = TE.decodeUtf8 . LBC.toStrict . renderBS . claimNotificationPage

contains :: T.Text -> T.Text -> Bool
contains needle hay = needle `T.isInfixOf` hay

spec :: Spec
spec = describe "claimNotificationPage (T5-05)" $ do
  let html = renderText samplePayload

  it "BookingId をレンダリングする" $
    html `shouldSatisfy` contains "BK-A1B2C3"

  it "確認コードを大きく表示する" $
    html `shouldSatisfy` contains "789012"

  it "確認コード表示に data-testid が付いている (E2E 用)" $
    html `shouldSatisfy` contains "data-testid=\"confirmation-code\""

  it "引取場所 (UN/LOCODE) が含まれる" $
    html `shouldSatisfy` contains "JPTYO"

  it "荷受人名が含まれる" $
    html `shouldSatisfy` contains "山田 太郎"

  it "印刷ボタンが含まれる (no-print クラスで印刷時は消える)" $ do
    html `shouldSatisfy` contains "window.print()"
    html `shouldSatisfy` contains "no-print"

  it "第三者開示禁止の警告が含まれる" $
    html `shouldSatisfy` contains "第三者に開示しないでください"

  it "consigneeName が Nothing のとき荷受人行は出力されない" $ do
    let html2 = renderText (samplePayload {cnpConsigneeName = Nothing})
    not ("山田 太郎" `T.isInfixOf` html2) `shouldBe` True
