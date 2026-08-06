{- | 割引率 VO (US23, IT8, Billing BC)

domain-model.md §6 ビジネスルール 2「法人荷主には最大 30% の割引を適用」。
ADR-0015 (contract_rank 由来の Integer 百分率) と同じ表現を採用し、
0〜30 の Integer 百分率で保持する。
-}
module Cargotracker.Billing.Domain.Model.Value.DiscountRate
  ( DiscountRate,
    mkDiscountRate,
    noDiscountRate,
    unDiscountRate,
    maxDiscountPercentage,
  ) where

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype DiscountRate = DiscountRate {unDiscountRate :: Integer}
  deriving stock (Eq, Show)

-- | 法人割引の上限 (domain-model.md §6 ビジネスルール 2)
maxDiscountPercentage :: Integer
maxDiscountPercentage = 30

mkDiscountRate :: Integer -> Either DomainError DiscountRate
mkDiscountRate p
  | p < 0 || p > maxDiscountPercentage = Left (InvalidDiscountRate p)
  | otherwise = Right (DiscountRate p)

noDiscountRate :: DiscountRate
noDiscountRate = DiscountRate 0
