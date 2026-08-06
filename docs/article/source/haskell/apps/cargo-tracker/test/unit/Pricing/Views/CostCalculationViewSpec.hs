{-# LANGUAGE OverloadedStrings #-}

-- | CostCalculationView の単体テスト (US21, IT6)
module Pricing.Views.CostCalculationViewSpec (spec) where

import qualified Data.ByteString.Lazy.Char8 as LBC
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Lucid (renderBS)
import Test.Hspec

import Cargotracker.Pricing.Views.CostCalculationView
  ( CalculationResultView (..),
    costCalculationPage,
  )

render :: Maybe CalculationResultView -> T.Text
render = TE.decodeUtf8 . LBC.toStrict . renderBS . costCalculationPage

contains :: T.Text -> T.Text -> Bool
contains needle hay = needle `T.isInfixOf` hay

spec :: Spec
spec = describe "costCalculationPage (US21)" $ do
  let empty = render Nothing

  it "タイトルに輸送料金算出が含まれる" $
    empty `shouldSatisfy` contains "輸送料金算出"

  it "form action /pricing/calculate と method=post を含む" $ do
    empty `shouldSatisfy` contains "action=\"/pricing/calculate\""
    empty `shouldSatisfy` contains "method=\"post\""

  it "貨物カテゴリ 3 種を select で提供する" $ do
    empty `shouldSatisfy` contains "value=\"General\""
    empty `shouldSatisfy` contains "value=\"Refrigerated\""
    empty `shouldSatisfy` contains "value=\"Hazardous\""

  it "距離 / 重量 / 割引率 / 通貨の入力フィールドがある" $ do
    empty `shouldSatisfy` contains "name=\"distanceKm\""
    empty `shouldSatisfy` contains "name=\"weightKg\""
    empty `shouldSatisfy` contains "name=\"discountRate\""
    empty `shouldSatisfy` contains "name=\"baseCurrency\""
    empty `shouldSatisfy` contains "name=\"targetCurrency\""

  it "算出前は結果カードが含まれない" $ do
    empty `shouldNotSatisfy` contains "calc-result-success"
    empty `shouldNotSatisfy` contains "calc-result-error"

  it "算出成功時は amount と currency を表示する" $ do
    let html = render (Just (ResultSuccess 12500 "JPY"))
    html `shouldSatisfy` contains "calc-result-success"
    html `shouldSatisfy` contains "12500"
    html `shouldSatisfy` contains "JPY"

  it "算出失敗時はエラーメッセージを表示する" $ do
    let html = render (Just (ResultError "PricingRule not found: JPY"))
    html `shouldSatisfy` contains "calc-result-error"
    html `shouldSatisfy` contains "PricingRule not found: JPY"
