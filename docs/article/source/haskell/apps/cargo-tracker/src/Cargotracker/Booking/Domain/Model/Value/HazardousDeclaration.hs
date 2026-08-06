{-# LANGUAGE MultiWayIf #-}

{- | 危険物申告 値オブジェクト (US05, IT2)

危険物貨物 (Cargo.cargoType == Hazardous) に必須の追加情報。
- hazardousClass: IMDG 分類 (1-9 のクラス番号、サブクラス含む)
- unNumber: 国連番号 (UN0001 〜 UN3540 等の 4 桁数字)
- properShippingName: 正式輸送品目名 (国連危険物リスト準拠)
-}
module Cargotracker.Booking.Domain.Model.Value.HazardousDeclaration
  ( HazardousDeclaration (..),
    mkHazardousDeclaration,
  ) where

import Data.Char (isDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data HazardousDeclaration = HazardousDeclaration
  { hazardousClass :: !Text
  , unNumber :: !Text
  , properShippingName :: !Text
  }
  deriving stock (Eq, Show)

mkHazardousDeclaration ::
  Text -> Text -> Text -> Either DomainError HazardousDeclaration
mkHazardousDeclaration hClassRaw unRaw nameRaw =
  let hClass = T.strip hClassRaw
      un = T.strip unRaw
      name = T.strip nameRaw
   in if
        | T.null hClass ->
            Left (InvalidBookingId "hazardous class must not be empty")
        | not (validUnNumber un) ->
            Left (InvalidBookingId "UN number must be 4 digits (e.g. 1203)")
        | T.null name ->
            Left (InvalidBookingId "proper shipping name must not be empty")
        | T.length name > 255 ->
            Left (InvalidBookingId "proper shipping name too long (max 255)")
        | otherwise ->
            Right
              HazardousDeclaration
                { hazardousClass = hClass
                , unNumber = un
                , properShippingName = name
                }
  where
    validUnNumber t = T.length t == 4 && T.all isDigit t
