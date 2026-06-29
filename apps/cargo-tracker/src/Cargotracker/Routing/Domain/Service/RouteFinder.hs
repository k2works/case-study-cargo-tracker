{- | 経路候補算出ドメインサービス (US08a, IT3 タスク 4.1)

出発地 / 目的地 / 期限 + 全 Voyage の集合から、接続性と期限を満たす経路
候補を最大 5 件算出する。純粋関数で、IO / Repository に依存しない。

アルゴリズム:

* DFS で「先頭 Voyage の出発地 = origin」から探索開始
* 各 Voyage の到着港が次の Voyage の出発港と一致する場合に乗継ぎ成立
* 末尾 Voyage の到着港 = destination で経路成立
* 探索深さ上限 5 (= 4 回乗継ぎ) で性能担保
* 期限超過 (最終到着時刻 > deadline) は除外
* 直行便 (1 区間) は rank=0、乗継ぎ便は接続港少ない順 + 到着時刻早い順で rank 付与

返り値は最大 5 件、rank 昇順。
-}
module Cargotracker.Routing.Domain.Service.RouteFinder
  ( FoundRoute (..),
    findRoutes,
  ) where

import Data.List (sortOn)
import Data.Time (UTCTime)

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..),
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( VoyageNumber,
    unVoyageNumber,
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage (..))
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode)

-- | 探索結果の中間表現 (Application 層が Estimation.RouteCandidate に変換)
data FoundRoute = FoundRoute
  { frRank :: !Int
  -- ^ 0 = 直行便 (最優先)、乗継ぎ便は 1.. の昇順
  , frVoyageNumbers :: ![VoyageNumber]
  -- ^ 経路を構成する Voyage の業務識別子 (先頭 = 出発便)
  , frFirstDeparture :: !UTCTime
  -- ^ 経路全体の出発時刻 (= 先頭 CarrierMovement.departureTime)
  , frLastArrival :: !UTCTime
  -- ^ 経路全体の到着時刻 (= 末尾 CarrierMovement.arrivalTime)
  , frNumSegments :: !Int
  -- ^ 区間数 (1 = 直行便)
  }
  deriving stock (Eq, Show)

-- | 探索深さ上限 (経路を構成する Voyage 数の最大値)
maxDepth :: Int
maxDepth = 5

-- | 結果件数上限
maxResults :: Int
maxResults = 5

{- | 経路候補を最大 5 件、rank 昇順で返す。

* `origin`: 出発地
* `destination`: 目的地
* `deadline`: 到着期限 (これ以前に着く経路のみ採用)
* `allVoyages`: 探索対象の Voyage 集合
-}
findRoutes ::
  UnLocode ->
  UnLocode ->
  UTCTime ->
  [Voyage] ->
  [FoundRoute]
findRoutes origin destination deadline allVoyages =
  let
    -- DFS 開始点: 出発港が origin と一致する Voyage を起点に探索
    starters = filter (startsAt origin) allVoyages
    paths = concatMap (dfs allVoyages destination deadline maxDepth []) starters
    withinDeadline = filter ((<= deadline) . frLastArrival . fst) paths
    -- 直行便 (1 区間) を rank 0 候補に
    direct = filter ((== 1) . frNumSegments . fst) withinDeadline
    transit = filter ((> 1) . frNumSegments . fst) withinDeadline
    -- 直行便を時刻昇順で取り出す (rank 0 から付ける)
    sortedDirect = map fst (sortOn (frFirstDeparture . fst) direct)
    sortedTransit =
      map fst $
        sortOn
          (\(f, _) -> (frNumSegments f, frLastArrival f))
          transit
    -- 直行便を優先、続いて経由便、最大 maxResults 件で打ち切り
    ranked = zipWith assignRank [0 ..] (take maxResults (sortedDirect <> sortedTransit))
   in
    ranked
  where
    assignRank r f = f {frRank = r}

-- | Voyage が指定した港から出発するか
startsAt :: UnLocode -> Voyage -> Bool
startsAt loc v = case carrierMovements v of
  (m : _) -> departureLocation m == loc
  [] -> False

-- | Voyage の出発地
firstDeparture :: Voyage -> Maybe (UnLocode, UTCTime)
firstDeparture v = case carrierMovements v of
  (m : _) -> Just (departureLocation m, departureTime m)
  [] -> Nothing

-- | Voyage の到着地
lastArrival :: Voyage -> Maybe (UnLocode, UTCTime)
lastArrival v = case carrierMovements v of
  [] -> Nothing
  ms -> let m = last ms in Just (arrivalLocation m, arrivalTime m)

{- | DFS 本体。

* `allVoyages`: 全探索対象 (枝刈り用に毎呼び出し検査)
* `destination`: 目的地
* `deadline`: 期限 (枝刈りに使用)
* `depthLeft`: 残り深さ
* `visited`: 既訪 Voyage 番号 (循環防止)
* `currentVoyage`: 今回追加候補
-}
dfs ::
  [Voyage] ->
  UnLocode ->
  UTCTime ->
  Int ->
  [VoyageNumber] ->
  Voyage ->
  [(FoundRoute, [Voyage])]
dfs allVoyages destination deadline depthLeft visited currentVoyage
  | depthLeft <= 0 = []
  | voyageNumber currentVoyage `elem` visited = []
  | otherwise = case (firstDeparture currentVoyage, lastArrival currentVoyage) of
      (Just (_, dep), Just (arr, arrT))
        | arrT > deadline -> [] -- 期限超過は枝刈り
        | arr == destination ->
            let path = [currentVoyage]
                fr =
                  FoundRoute
                    { frRank = 0
                    , frVoyageNumbers = map voyageNumber path
                    , frFirstDeparture = dep
                    , frLastArrival = arrT
                    , frNumSegments = length path
                    }
             in [(fr, path)]
        | otherwise ->
            -- 接続候補: 現在の到着港から出発する別 Voyage
            let connectors =
                  [ v
                  | v <- allVoyages
                  , voyageNumber v /= voyageNumber currentVoyage
                  , voyageNumber v `notElem` visited
                  , startsAt arr v
                  , -- 接続時刻は次便の出発が現便の到着以降
                  case firstDeparture v of
                    Just (_, nextDep) -> nextDep >= arrT
                    Nothing -> False
                  ]
                nextResults =
                  concatMap
                    ( dfs
                        allVoyages
                        destination
                        deadline
                        (depthLeft - 1)
                        (voyageNumber currentVoyage : visited)
                    )
                    connectors
             in map (prepend dep currentVoyage) nextResults
      _ -> []

-- | DFS で見つかった部分経路の先頭に現在の Voyage を継ぎ足す
prepend :: UTCTime -> Voyage -> (FoundRoute, [Voyage]) -> (FoundRoute, [Voyage])
prepend dep v (fr, path) =
  let newPath = v : path
   in ( fr
          { frVoyageNumbers = voyageNumber v : frVoyageNumbers fr
          , frFirstDeparture = dep
          , frNumSegments = length newPath
          }
      , newPath
      )

-- 補助: VoyageNumber を文字列化 (将来 UI で使う際の export 用)
_voyageNumbersToText :: [VoyageNumber] -> [String]
_voyageNumbersToText = map (show . unVoyageNumber)
