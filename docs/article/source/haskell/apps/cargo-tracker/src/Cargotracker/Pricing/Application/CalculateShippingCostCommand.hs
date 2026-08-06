{- | 輸送料金算出コマンド (US21, IT6)

貨物カテゴリ・距離・重量・基準通貨・対象通貨・現在時刻を受け取り、
以下の 3 段階で最終料金を返す。

1. PricingRule 参照 (Repository) → 存在しなければ PricingRuleNotFound
2. `PricingRule.calculate` で基本料金を算出
3. `applyDiscount` で割引を適用
4. 対象通貨が基準通貨と異なる場合、`CurrencyRate.convert` で換算
   - レート未取得なら CurrencyRateNotFound
   - 期限切れなら CurrencyRateExpired (convert 側で発生)

T-01 準拠: Tx 境界は本 Command 内で管理 (Read-Only なため今回は Tx 不要)。
T-03 準拠: `calculate` / `applyDiscount` / `convert` は Domain 純粋関数。
-}
module Cargotracker.Pricing.Application.CalculateShippingCostCommand
  ( CalculateShippingCostInput (..),
    execute,
  ) where

import Data.Time (UTCTime)

import Cargotracker.Pricing.Application.Ports
  ( CurrencyRateRepository (..),
    PricingRuleRepository (..),
  )
import Cargotracker.Pricing.Domain.Model.PricingRule
  ( CargoCategory,
    calculate,
  )
import Cargotracker.Pricing.Domain.Model.Value.Cost (Cost, Currency, unCurrency)
import Cargotracker.Pricing.Domain.Model.Value.CurrencyRate (convert)
import Cargotracker.Pricing.Domain.Model.Value.Discount
  ( Discount,
    applyDiscount,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data CalculateShippingCostInput = CalculateShippingCostInput
  { inputCargoCategory :: !CargoCategory
  , inputDistanceKm :: !Integer
  , inputWeightKg :: !Integer
  , inputBaseCurrency :: !Currency
  -- ^ PricingRule 参照キー (JPY 円建てなど)
  , inputTargetCurrency :: !Currency
  -- ^ 呼出側に返却する通貨 (同じなら換算不要)
  , inputDiscount :: !Discount
  -- ^ 適用する割引 (法人契約割引・プロモーション等、上流で決定)
  , inputNow :: !UTCTime
  -- ^ CurrencyRate 有効期限判定用の現在時刻
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  PricingRuleRepository m ->
  CurrencyRateRepository m ->
  CalculateShippingCostInput ->
  m (Either DomainError Cost)
execute ruleRepo rateRepo input = do
  mRule <- findByCurrency ruleRepo (inputBaseCurrency input)
  case mRule of
    Nothing -> pure (Left (PricingRuleNotFound (unCurrency (inputBaseCurrency input))))
    Just rule ->
      case calculate rule (inputCargoCategory input) (inputDistanceKm input) (inputWeightKg input) of
        Left err -> pure (Left err)
        Right baseCost -> do
          let discounted = applyDiscount (inputDiscount input) baseCost
          if inputBaseCurrency input == inputTargetCurrency input
            then pure (Right discounted)
            else do
              mRate <-
                findValidRate
                  rateRepo
                  (inputBaseCurrency input)
                  (inputTargetCurrency input)
                  (inputNow input)
              case mRate of
                Nothing ->
                  pure
                    ( Left
                        ( CurrencyRateNotFound
                            (unCurrency (inputBaseCurrency input))
                            (unCurrency (inputTargetCurrency input))
                        )
                    )
                Just rate -> pure (convert rate (inputNow input) discounted)
