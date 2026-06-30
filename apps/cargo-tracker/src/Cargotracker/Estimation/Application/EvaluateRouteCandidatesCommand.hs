{- | 経路候補制約評価コマンド (US08b, IT4)

経路候補の rank + 寄港港 + 航海番号 (全て Text) を受け取り、
`RouteConstraint` を適用して評価結果を返す。

本コマンドは純粋関数のみで構成され、I/O は持たない (T-03 規約)。
Application 層に置く理由は、UI と RouteEvaluator (Estimation Domain) の
橋渡しのため。

ADR-0004 Cross-BC 規約: Routing BC の `FoundRoute` を直接受け取らず、
呼び出し側 (HTTP ハンドラ) が `RouteCandidateInput` に変換する。
これにより Estimation BC は Routing BC の型に依存しない。
-}
module Cargotracker.Estimation.Application.EvaluateRouteCandidatesCommand
  ( RouteCandidateInput (..),
    EvaluatedRoute (..),
    evaluateCandidates,
  ) where

import qualified Data.Set as Set
import Data.Text (Text)

import Cargotracker.Estimation.Domain.Model.Value.RouteConstraint
  ( ConstraintEvaluation,
    RouteConstraint,
  )
import Cargotracker.Estimation.Domain.Service.RouteEvaluator
  ( EvaluationInput (..),
    evaluate,
  )

{- | 経路候補の入力表現 (BC 非依存)。

`rank` は元の rank (FoundRoute.frRank 等) を保持し、評価後に UI で
ソート / 表示に利用する。
-}
data RouteCandidateInput = RouteCandidateInput
  { rciRank :: !Int
  , rciPorts :: ![Text]
  , rciVoyages :: ![Text]
  }
  deriving stock (Eq, Show)

{- | 経路候補とその制約評価結果のペア。

評価結果が accepted の場合は UI に表示、rejected の場合は除外理由を
別セクションで提示する。
-}
data EvaluatedRoute = EvaluatedRoute
  { evCandidate :: !RouteCandidateInput
  , evEvaluation :: !ConstraintEvaluation
  }
  deriving stock (Eq, Show)

-- | 経路一覧 + 制約マスタから評価結果を構築する純粋関数。
evaluateCandidates ::
  -- | 適用する制約 (荷主 / 営業担当者が指定)
  RouteConstraint ->
  -- | 危険物受入可能港 UnLocode (Voyage マスタから取得済)
  [Text] ->
  -- | 冷凍対応 Voyage 番号 (Voyage マスタから取得済)
  [Text] ->
  [RouteCandidateInput] ->
  [EvaluatedRoute]
evaluateCandidates constraint allowedPorts reeferVoyages routes =
  let allowedSet = Set.fromList allowedPorts
      reeferSet = Set.fromList reeferVoyages
   in [ EvaluatedRoute
          { evCandidate = c
          , evEvaluation =
              evaluate
                constraint
                EvaluationInput
                  { eiRoutePorts = rciPorts c
                  , eiRouteVoyages = rciVoyages c
                  , eiHazardousAllowedPorts = allowedSet
                  , eiReeferCapableVoyages = reeferSet
                  }
          }
      | c <- routes
      ]
