{- | Pricing BC の出力ポート (US21, IT6)

Application 層コマンドが料金算出時に参照する Repository の型定義。
Postgres 実装は Infrastructure 層で提供する (今回は Domain 完結、DB 実装は別 commit)。

T-02 準拠: 全て IO のみ。Tx 境界は Application が管理する。
-}
module Cargotracker.Pricing.Application.Ports
  ( PricingRuleRepository (..),
    CurrencyRateRepository (..),
  ) where

import Data.Time (UTCTime)

import Cargotracker.Pricing.Domain.Model.PricingRule (PricingRule)
import Cargotracker.Pricing.Domain.Model.Value.Cost (Currency)
import Cargotracker.Pricing.Domain.Model.Value.CurrencyRate (CurrencyRate)

-- | 料金ルール参照ポート。通貨単位に 1 つのルールを想定 (シンプル化)。
newtype PricingRuleRepository m = PricingRuleRepository
  { findByCurrency :: Currency -> m (Maybe PricingRule)
  }

{- | 通貨レート参照ポート。

`findValidRate from to now` は「now 時点で有効な from → to 換算レート」を返す。
存在しない場合や期限外の場合は Nothing。
-}
newtype CurrencyRateRepository m = CurrencyRateRepository
  { findValidRate :: Currency -> Currency -> UTCTime -> m (Maybe CurrencyRate)
  }
