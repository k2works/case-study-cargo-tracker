{-# LANGUAGE OverloadedStrings #-}

{- | RouteFinder の criterion ベンチ (US08a タスク 4.6, IT3)

性能ゲート: 1000 Voyage に対する findRoutes が <500ms (リスク表 §設計)。

実行方法:

> nix-shell ../../ops/nix/shells/shell.nix --run "stack bench routing-bench"

結果は `bench-results/` (criterion デフォルト) に HTML / CSV で出力される。
CI への組み込みは IT4 で検討する (iteration_plan-3.md §CI 統合)。
-}
module Main (main) where

import Criterion.Main
import qualified Data.Text as T
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)

import Cargotracker.Routing.Domain.Model.Value.CarrierMovement
  ( CarrierMovement (..)
  )
import Cargotracker.Routing.Domain.Model.Value.VoyageNumber
  ( mkVoyageNumber
  )
import Cargotracker.Routing.Domain.Model.Voyage (Voyage, mkVoyage)
import Cargotracker.Routing.Domain.Service.RouteFinder (findRoutes)
import Cargotracker.Shared.Domain.Common.UnLocode (UnLocode (..))

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 9 1) (secondsToDiffTime 0)

day :: Int -> UTCTime
day n = addUTCTime (fromIntegral n * 86400) t0

-- 6 港を回転させてランダムっぽい接続パターンを作る (純粋関数なので決定的)
allPorts :: [T.Text]
allPorts = ["JPTYO", "SGSIN", "HKHKG", "USLAX", "USNYC", "GBLON"]

-- N 件の Voyage 集合を生成する。1/6 は直行便 (JPTYO -> USNYC)、
-- 残りは港回転による接続性を持つ多様な経路の一部となる。
genVoyages :: Int -> [Voyage]
genVoyages n = [v | i <- [0 .. n - 1], Just v <- [genOne i]]
  where
    genOne i =
      let dep = allPorts !! (i `mod` length allPorts)
          arrIdx = (i + 2) `mod` length allPorts
          arr = allPorts !! arrIdx
          depDay = i `mod` 30 -- 0..29 日のいずれか
          arrDay = depDay + 1 + (i `mod` 14) -- 1..15 日後
       in case mkVoyageNumber (T.pack ("V" <> show i)) of
            Right vn -> case mkVoyage vn [movement dep arr (day depDay) (day arrDay)] of
              Right v -> Just v
              Left _ -> Nothing
            Left _ -> Nothing

movement :: T.Text -> T.Text -> UTCTime -> UTCTime -> CarrierMovement
movement dep arr depT arrT =
  CarrierMovement
    { departureLocation = UnLocode dep
    , arrivalLocation = UnLocode arr
    , departureTime = depT
    , arrivalTime = arrT
    }

main :: IO ()
main = do
  let v100 = genVoyages 100
      v500 = genVoyages 500
      v1000 = genVoyages 1000
      origin = UnLocode "JPTYO"
      destination = UnLocode "USNYC"
      deadline = day 60
  defaultMain
    [ bgroup
        "findRoutes/JPTYO->USNYC/<=60d"
        [ bench "100 voyages" $ whnf (length . findRoutes origin destination deadline) v100
        , bench "500 voyages" $ whnf (length . findRoutes origin destination deadline) v500
        , bench "1000 voyages" $ whnf (length . findRoutes origin destination deadline) v1000
        ]
    ]
