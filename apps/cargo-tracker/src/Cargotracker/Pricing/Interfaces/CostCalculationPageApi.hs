{-# LANGUAGE DataKinds #-}
{-# LANGUAGE DeriveGeneric #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | 料金算出画面 API (US21, IT6)

/pricing/calculate のフォーム表示と POST 処理を Servant で実装する。

- GET  /pricing/calculate       : 空フォームを返す (200)
- POST /pricing/calculate       : 入力を parse → CalculateShippingCostCommand
                                  → 結果ビューをレンダリング (200)
                                  parse 失敗時はエラーを結果に表示 (200)

DomainError は Pricing.Views.CostCalculationView 側で `ResultError` として
表示する。500 は返さず、ユーザーに理由を明示する。
-}
module Cargotracker.Pricing.Interfaces.CostCalculationPageApi
  ( costCalculationApp,
    CalculateFormRequest (..),
  ) where

import Control.Monad.IO.Class (liftIO)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime, getCurrentTime)
import GHC.Generics (Generic)
import Lucid (Html)
import Network.Wai (Application)
import Servant
import Servant.HTML.Lucid (HTML)
import Web.FormUrlEncoded (Form, FromForm (..), parseUnique)

import Cargotracker.Pricing.Application.CalculateShippingCostCommand
  ( CalculateShippingCostInput (..),
    execute,
  )
import Cargotracker.Pricing.Application.Ports
  ( CurrencyRateRepository,
    PricingRuleRepository,
  )
import Cargotracker.Pricing.Domain.Model.PricingRule (CargoCategory (..))
import Cargotracker.Pricing.Domain.Model.Value.Cost
  ( Cost (..),
    mkCurrency,
    unCurrency,
  )
import Cargotracker.Pricing.Domain.Model.Value.Discount (mkDiscount)
import Cargotracker.Pricing.Views.CostCalculationView
  ( CalculationResultView (..),
    costCalculationPage,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Application.Ports
  ( ShipperRepository,
    resolveDiscountPercentageByShipperId,
  )

-- | POST /pricing/calculate のフォーム受信体。
data CalculateFormRequest = CalculateFormRequest
  { formCargoCategory :: !Text
  , formDistanceKm :: !Text
  , formWeightKg :: !Text
  , formBaseCurrency :: !Text
  , formTargetCurrency :: !Text
  , formDiscountRate :: !Text
  , formShipperId :: !Text
  {- ^ US22 (IT7): 空文字なら手動 discountRate を使用、
  空でない場合 Shipper.contract_rank 由来の法人割引率で上書き
  -}
  }
  deriving stock (Eq, Show, Generic)

instance FromForm CalculateFormRequest where
  fromForm f =
    CalculateFormRequest
      <$> parseUnique "cargoCategory" f
      <*> parseUnique "distanceKm" f
      <*> parseUnique "weightKg" f
      <*> parseUnique "baseCurrency" f
      <*> parseUnique "targetCurrency" f
      <*> parseUnique "discountRate" f
      <*> parseOptional "shipperId" f

parseOptional :: Text -> Form -> Either Text Text
parseOptional key f = case parseUnique key f of
  Right t -> Right t
  Left _ -> Right ""

type CostCalculationApi =
  "pricing"
    :> "calculate"
    :> ( Get '[HTML] (Html ())
           :<|> ReqBody '[FormUrlEncoded] CalculateFormRequest
             :> Post '[HTML] (Html ())
       )

costCalculationApp ::
  PricingRuleRepository IO ->
  CurrencyRateRepository IO ->
  ShipperRepository IO ->
  Application
costCalculationApp ruleRepo rateRepo shipperRepo =
  serve
    (Proxy :: Proxy CostCalculationApi)
    (handlerGet :<|> handlerPost ruleRepo rateRepo shipperRepo)

handlerGet :: Handler (Html ())
handlerGet = pure (costCalculationPage Nothing)

handlerPost ::
  PricingRuleRepository IO ->
  CurrencyRateRepository IO ->
  ShipperRepository IO ->
  CalculateFormRequest ->
  Handler (Html ())
handlerPost ruleRepo rateRepo shipperRepo form = do
  now <- liftIO getCurrentTime
  case parseInputs form now of
    Left err -> pure (costCalculationPage (Just (ResultError err)))
    Right input -> do
      -- US22 (IT7): shipperId 指定時は法人契約割引率で override
      resolvedInput <-
        if T.null (T.strip (formShipperId form))
          then pure (Right input)
          else applyShipperDiscount shipperRepo (formShipperId form) input
      case resolvedInput of
        Left err -> pure (costCalculationPage (Just (ResultError err)))
        Right finalInput -> do
          result <- liftIO (execute ruleRepo rateRepo finalInput)
          pure (costCalculationPage (Just (renderResult result)))

{- | US22 (IT7): shipperId から契約割引率を解決し、CalculateShippingCostInput の
inputDiscount を上書きする。ADR-0015 に基づく contract_rank 由来の割引率を採用。
-}
applyShipperDiscount ::
  ShipperRepository IO ->
  Text ->
  CalculateShippingCostInput ->
  Handler (Either Text CalculateShippingCostInput)
applyShipperDiscount shipperRepo raw input = do
  resolved <- liftIO (resolveDiscountPercentageByShipperId shipperRepo raw)
  case resolved of
    Left (ShipperNotFound sid) ->
      pure (Left ("荷主が見つかりません: " <> sid))
    Left (InvalidShipperId reason) ->
      pure (Left ("荷主 ID が不正: " <> reason))
    Left other ->
      pure (Left (T.pack (show other)))
    Right percentage ->
      case mkDiscount percentage of
        Right d -> pure (Right (input {inputDiscount = d}))
        Left e -> pure (Left ("割引率の変換に失敗: " <> T.pack (show e)))

parseInputs ::
  CalculateFormRequest ->
  UTCTime ->
  Either Text CalculateShippingCostInput
parseInputs f now = do
  cat <- parseCategory (formCargoCategory f)
  distance <- parseIntNonNeg "distanceKm" (formDistanceKm f)
  weight <- parseIntNonNeg "weightKg" (formWeightKg f)
  base <- either (const (Left "基準通貨は 3 文字大文字")) Right (mkCurrency (formBaseCurrency f))
  target <- either (const (Left "対象通貨は 3 文字大文字")) Right (mkCurrency (formTargetCurrency f))
  rate <- parseIntNonNeg "discountRate" (formDiscountRate f)
  discount <-
    either (const (Left "割引率は 0-100 の整数")) Right (mkDiscount rate)
  pure
    CalculateShippingCostInput
      { inputCargoCategory = cat
      , inputDistanceKm = distance
      , inputWeightKg = weight
      , inputBaseCurrency = base
      , inputTargetCurrency = target
      , inputDiscount = discount
      , inputNow = now
      }

parseCategory :: Text -> Either Text CargoCategory
parseCategory "General" = Right General
parseCategory "Refrigerated" = Right Refrigerated
parseCategory "Hazardous" = Right Hazardous
parseCategory t = Left ("未対応の貨物カテゴリ: " <> t)

parseIntNonNeg :: Text -> Text -> Either Text Integer
parseIntNonNeg fieldName t =
  case reads (T.unpack t) :: [(Integer, String)] of
    [(n, "")] | n >= 0 -> Right n
    _ -> Left (fieldName <> " は 0 以上の整数")

renderResult :: Either DomainError Cost -> CalculationResultView
renderResult (Right c) = ResultSuccess (costAmount c) (unCurrency (costCurrency c))
renderResult (Left err) = ResultError (T.pack (show err))
