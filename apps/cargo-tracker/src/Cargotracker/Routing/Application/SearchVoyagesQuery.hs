{- | 航海スケジュール検索ユースケース (US07, IT3)

業務フロー:
1. SearchVoyagesInput から VoyageSearchCriteria を構築 (検証あり)
2. VoyageRepository.findAllVoyages で全件取得 (IT3 暫定、IT4 で SQL 化)
3. VoyageQuery.matchesCriteria で純粋関数フィルタ
4. VoyageQuery.sortByDeparture で出発時刻昇順に整列
5. 該当 0 件のときも Right [] を返す (UI 側でメッセージ切替)

ADR-0002 T-01: SearchVoyagesQuery は read-only のためトランザクション不要
(withReadOnly があれば望ましいが現状は trivial に findAllVoyages を呼ぶ)。
-}
module Cargotracker.Routing.Application.SearchVoyagesQuery
  ( SearchVoyagesInput (..),
    execute,
  ) where

import Data.Time (UTCTime)

import Cargotracker.Routing.Application.Ports (VoyageRepository (..))
import Cargotracker.Routing.Domain.Model.Value.VoyageSearchCriteria
  ( mkVoyageSearchCriteria,
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage)
import Cargotracker.Routing.Domain.Service.VoyageQuery
  ( matchesCriteria,
    sortByDeparture,
  )
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data SearchVoyagesInput = SearchVoyagesInput
  { inputOrigin :: !UnLocode
  , inputDestination :: !UnLocode
  , inputFromDate :: !UTCTime
  , inputToDate :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  VoyageRepository m ->
  SearchVoyagesInput ->
  m (Either DomainError [Voyage])
execute repo input =
  case mkVoyageSearchCriteria
    (inputOrigin input)
    (inputDestination input)
    (inputFromDate input)
    (inputToDate input) of
    Left e -> pure (Left e)
    Right crit -> do
      all_ <- findAllVoyages repo
      pure (Right (sortByDeparture (filter (matchesCriteria crit) all_)))
