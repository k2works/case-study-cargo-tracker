{- | 経路制約評価 VO (US08b, IT4)

US08a で算出した経路候補に対して適用する制約条件と評価結果を表す純粋値オブジェクト。

制約の種類:

* `rcHazardous`: 危険物貨物のため危険物受入不可港を含む経路を除外
* `rcReeferRequired`: 冷凍貨物のため冷凍船 (Reefer 対応) でない航海を含む経路を除外
* `rcDirectPreferred`: 直行便優先 (rank の付け替えは Application 層で行う、本 VO はフラグのみ)

評価結果:

* `ceAccepted`: True なら採用、False なら除外
* `ceReasons`: 除外理由のリスト (採用時は空)

ADR-0004 Cross-BC 参照規約: Routing BC の `VoyageNumber` / `UnLocode` を直接 import せず、
業務識別子は `Text` として受け取り、Application 層が変換責務を持つ。
-}
module Cargotracker.Estimation.Domain.Model.Value.RouteConstraint
  ( RouteConstraint (..),
    ExclusionReason (..),
    ConstraintEvaluation (..),
    noConstraint,
    accepted,
    rejected,
  ) where

import Data.Text (Text)

-- | 経路候補に適用する制約条件
data RouteConstraint = RouteConstraint
  { rcHazardous :: !Bool
  , rcReeferRequired :: !Bool
  , rcDirectPreferred :: !Bool
  }
  deriving stock (Eq, Show)

{- | 経路候補が除外された理由

業務識別子は `Text` で保持し、表示時に他 BC の型表現へ変換する。
-}
data ExclusionReason
  = -- | 危険物受入不可港の UnLocode (5 文字)
    HazardousPortViolation !Text
  | -- | 冷凍非対応の VoyageNumber
    ReeferUnavailable !Text
  deriving stock (Eq, Show)

-- | 制約評価の結果
data ConstraintEvaluation = ConstraintEvaluation
  { ceAccepted :: !Bool
  , ceReasons :: ![ExclusionReason]
  }
  deriving stock (Eq, Show)

-- | 制約を一切課さない (全候補を採用)
noConstraint :: RouteConstraint
noConstraint = RouteConstraint False False False

-- | 採用結果 (理由なし)
accepted :: ConstraintEvaluation
accepted = ConstraintEvaluation True []

-- | 除外結果 (理由を 1 つ以上保持)
rejected :: [ExclusionReason] -> ConstraintEvaluation
rejected = ConstraintEvaluation False
