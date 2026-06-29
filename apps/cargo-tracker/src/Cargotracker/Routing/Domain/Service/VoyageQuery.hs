{- | 航海検索ドメインサービス (US07, IT3)

純粋関数で `VoyageSearchCriteria` と `Voyage` のマッチング判定 + ソートを行う。
IO/Repository への依存はないため、Application 層が SQL で粗フィルタした結果を
最終フィルタとして使う、あるいは In-Memory 全件に対する検索の両方で再利用できる。

マッチング規約:

* 出発地: voyage の最初の `CarrierMovement.departureLocation`
* 目的地: voyage の最後の `CarrierMovement.arrivalLocation`
* 出発期間: 最初の `departureTime` が [fromDate..toDate]

並び順: 最初の出発時刻昇順。
-}
module Cargotracker.Routing.Domain.Service.VoyageQuery
  ( matchesCriteria,
    sortByDeparture,
  ) where

import Data.List (sortOn)

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageSearchCriteria
  ( VoyageSearchCriteria (..),
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..))

-- | Voyage が条件にマッチするかを判定する純粋関数
matchesCriteria :: VoyageSearchCriteria -> Voyage -> Bool
matchesCriteria crit voy = case carrierMovements voy of
  [] -> False
  (first : _) ->
    let firstDep = departureLocation first
        lastArr = arrivalLocation (last (carrierMovements voy))
        depTime = departureTime first
     in firstDep == vscOrigin crit
          && lastArr == vscDestination crit
          && depTime >= vscFromDate crit
          && depTime <= vscToDate crit

-- | 最初の区間の出発時刻昇順にソートする
sortByDeparture :: [Voyage] -> [Voyage]
sortByDeparture = sortOn firstDeparture
  where
    firstDeparture v = case carrierMovements v of
      (m : _) -> Just (departureTime m)
      [] -> Nothing
