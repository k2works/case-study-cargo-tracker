{- | 貨物種別 値オブジェクト (US05, IT2)

DB CHECK 制約 (`cargo_type IN ('GENERAL','HAZARDOUS','REFRIGERATED')`) と
整合させ、追加情報の有無を型レベルで強制する sum type。

- General: 追加情報なし
- Hazardous: HazardousDeclaration 必須
- Refrigerated: TemperatureRequirement 必須

スマートコンストラクタが Domain 不変条件を保証するため、
「種別 = 危険物だが宣言なし」のような無効な組み合わせは型エラーとなる。
-}
module Cargotracker.Booking.Domain.Model.Value.CargoType
  ( CargoType (..),
    cargoTypeToText,
  ) where

import Data.Text (Text)

import Cargotracker.Booking.Domain.Model.Value.HazardousDeclaration
  ( HazardousDeclaration,
  )
import Cargotracker.Booking.Domain.Model.Value.TemperatureRequirement
  ( TemperatureRequirement,
  )

data CargoType
  = General
  | Hazardous !HazardousDeclaration
  | Refrigerated !TemperatureRequirement
  deriving stock (Eq, Show)

-- | DB CHECK 制約 (大文字) に整合する文字列表現
cargoTypeToText :: CargoType -> Text
cargoTypeToText General = "GENERAL"
cargoTypeToText (Hazardous _) = "HAZARDOUS"
cargoTypeToText (Refrigerated _) = "REFRIGERATED"
