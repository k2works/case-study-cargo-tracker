{- | 経路制約評価ドメインサービス (US08b, IT4)

経路の寄港港一覧と航海番号一覧に対して `RouteConstraint` に基づき
受入可否を判定する純粋関数。T-03 規約: IO 非依存。

ADR-0004 Cross-BC 参照規約: 業務識別子は `Text` で受け取り、Routing BC の
`VoyageNumber` / `UnLocode` / `FoundRoute` を直接 import しない。
Application 層が `FoundRoute` → `EvaluationInput` への変換責務を持つ。

評価ロジック:

1. `rcHazardous = True` の場合、経路の全寄港港が `eiHazardousAllowedPorts` に含まれることを要求。
   含まれない港がある場合は `HazardousPortViolation` 理由で除外。
2. `rcReeferRequired = True` の場合、経路の全 Voyage が `eiReeferCapableVoyages` に含まれることを要求。
   含まれない Voyage がある場合は `ReeferUnavailable` 理由で除外。
3. `rcDirectPreferred` は本サービスでは評価せず、Application 層の rank 並び替え用フラグ。
4. 制約条件の評価はすべての制約に対して独立に行い、複数の違反理由を蓄積して返す。
-}
module Cargotracker.Estimation.Domain.Service.RouteEvaluator
  ( EvaluationInput (..),
    evaluate,
  ) where

import qualified Data.Set as Set
import Data.Text (Text)

import Cargotracker.Estimation.Domain.Model.Value.RouteConstraint
  ( ConstraintEvaluation,
    ExclusionReason (..),
    RouteConstraint (..),
    accepted,
    rejected,
  )

{- | 評価の入力一式 (Application 層が `FoundRoute` から構築)

* `eiRoutePorts`: 経路を構成する全寄港港 UnLocode (Text)
* `eiRouteVoyages`: 経路を構成する全航海番号 (Text)
-}
data EvaluationInput = EvaluationInput
  { eiRoutePorts :: ![Text]
  , eiRouteVoyages :: ![Text]
  , eiHazardousAllowedPorts :: !(Set.Set Text)
  , eiReeferCapableVoyages :: !(Set.Set Text)
  }
  deriving stock (Eq, Show)

-- | 制約を経路に適用し評価結果を返す。
evaluate :: RouteConstraint -> EvaluationInput -> ConstraintEvaluation
evaluate constraint EvaluationInput {..} =
  let hazardousViolations =
        if rcHazardous constraint
          then
            [ HazardousPortViolation p
            | p <- eiRoutePorts
            , not (Set.member p eiHazardousAllowedPorts)
            ]
          else []
      reeferViolations =
        if rcReeferRequired constraint
          then
            [ ReeferUnavailable v
            | v <- eiRouteVoyages
            , not (Set.member v eiReeferCapableVoyages)
            ]
          else []
      allReasons = hazardousViolations <> reeferViolations
   in if null allReasons then accepted else rejected allReasons
