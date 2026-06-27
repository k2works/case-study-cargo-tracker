{- | 見積 ID 値オブジェクト (US01, IT2)

UUID 形式の識別子。Pricing/Estimation Context の Estimate 集約ルート ID。
本値オブジェクトでは UUID 文字列の形式 (8-4-4-4-12 桁) のみを検証し、
採番は Application 層 (CreateEstimateCommand) で `uuid` パッケージを用いて行う。
-}
module Cargotracker.Estimation.Domain.Model.Value.EstimateId
  ( EstimateId (..),
    mkEstimateId,
  ) where

import Data.Char (isHexDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype EstimateId = EstimateId {unEstimateId :: Text}
  deriving stock (Eq, Show, Ord)

-- UUID v4 文字列の形式チェック (8-4-4-4-12 桁の hex)
mkEstimateId :: Text -> Either DomainError EstimateId
mkEstimateId t
  | T.length t /= 36 =
      Left (InvalidBookingId "EstimateId: expected UUID (36 chars)")
  | not (validShape t) =
      Left (InvalidBookingId "EstimateId: invalid UUID format")
  | otherwise = Right (EstimateId t)
  where
    validShape s =
      let groups = T.splitOn "-" s
       in case groups of
            [g1, g2, g3, g4, g5] ->
              all (T.all isHexDigit) [g1, g2, g3, g4, g5]
                && T.length g1 == 8
                && T.length g2 == 4
                && T.length g3 == 4
                && T.length g4 == 4
                && T.length g5 == 12
            _ -> False
