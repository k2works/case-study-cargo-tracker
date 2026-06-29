{- | 経路候補算出ユースケース (US08a タスク 4.3, IT3)

VoyageRepository から取得した Voyage 集合に対し RouteFinder.findRoutes を
呼び出して経路候補リストを返す薄いユースケース。検証は domain 層で完結
しているため、Application 層では入力検証 (VoyageSearchCriteria の構築) と
結果取得のみ。

cross-BC 関心事 (BookingId → RouteSpecification の解決) は呼び出し元
(HTTP ハンドラ) が責任を持つ。本ユースケースは Routing BC 内に閉じる。

ADR-0002 T-01: read-only クエリのためトランザクション境界は trivial に
findAllVoyages の SQL に委ねる。
-}
module Cargotracker.Routing.Application.ComputeRouteCandidatesQuery
  ( ComputeRouteCandidatesInput (..),
    execute,
  ) where

import Data.Time (UTCTime)

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Service.RouteFinder
  ( FoundRoute,
    findRoutes,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data ComputeRouteCandidatesInput = ComputeRouteCandidatesInput
  { inputOrigin :: !UnLocode
  , inputDestination :: !UnLocode
  , inputDeadline :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  VoyageRepository m ->
  ComputeRouteCandidatesInput ->
  m (Either DomainError [FoundRoute])
execute repo input
  | inputOrigin input == inputDestination input =
      -- 同一港の経路は意味を持たないため、ユーザに分かりやすいエラーで返す
      -- (UnLocode の Text を保持)
      pure (Left (SameOriginDestination "origin == destination"))
  | otherwise = do
      voys <- findAllVoyages repo
      let candidates =
            findRoutes
              (inputOrigin input)
              (inputDestination input)
              (inputDeadline input)
              voys
      pure (Right candidates)
