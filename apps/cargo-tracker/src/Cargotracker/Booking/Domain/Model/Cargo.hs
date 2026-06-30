{-# LANGUAGE PatternSynonyms #-}

{- | Cargo 集約ルート (IT1 US04)

予約の中心エンティティ。荷主 (`ShipperRef`) を ACL 参照として保持し、
状態遷移は `BookingStatus` 経由で管理する。

楽観ロックは `cargoVersion` で表現 (新規 = 1、各更新で +1)。
-}
module Cargotracker.Booking.Domain.Model.Cargo
  ( Cargo (..),
    mkCargo,
    mkCargoWithType,
    submitBooking,
    requestRouting,
    linkRoute,
    unlinkRoute,
    confirmBooking,
    cancelBooking,
  ) where

import qualified Data.Text as T

import Cargotracker.Booking.Domain.Error (pattern InvalidStateTransition)
import Cargotracker.Booking.Domain.Model.State.BookingStatus
  ( BookingStatus (..),
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Booking.Domain.Model.Value.CargoType (CargoType (..))
import Cargotracker.Booking.Domain.Model.Value.RouteSpecification
  ( RouteSpecification,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError)
import Cargotracker.Shared.Domain.Reference.ShipperRef (ShipperRef)

data Cargo = Cargo
  { cargoBookingId :: !BookingId
  , cargoShipperRef :: !ShipperRef
  , cargoRouteSpec :: !RouteSpecification
  , cargoStatus :: !BookingStatus
  , cargoType :: !CargoType
  {- ^ US05 (IT2): General / Hazardous / Refrigerated。
  sum type で追加情報の有無を型レベルで強制する。
  -}
  , cargoVersion :: !Int
  }
  deriving stock (Eq, Show)

-- | 一般貨物 (cargoType = General) を Draft 状態で構築する (IT1 後方互換)。
mkCargo :: BookingId -> ShipperRef -> RouteSpecification -> Cargo
mkCargo bid sid route = mkCargoWithType bid sid route General

{- | 任意の CargoType を指定して Cargo を構築する (US04+US05, IT2)。

CargoType に Hazardous / Refrigerated を渡せば、その追加情報も型レベルで
保持される。スマートコンストラクタ層で「種別 = 危険物だが宣言なし」を
排除しているため Domain 不変条件を満たす。
-}
mkCargoWithType ::
  BookingId -> ShipperRef -> RouteSpecification -> CargoType -> Cargo
mkCargoWithType bid sid route ctype =
  Cargo
    { cargoBookingId = bid
    , cargoShipperRef = sid
    , cargoRouteSpec = route
    , cargoStatus = Draft
    , cargoType = ctype
    , cargoVersion = 1
    }

{- | 予約を確定送信する (Draft → Submitted)。
すでに Submitted 以降の状態にあるとエラー。
-}
submitBooking :: Cargo -> Either DomainError Cargo
submitBooking cargo = case cargoStatus cargo of
  Draft ->
    Right
      cargo
        { cargoStatus = Submitted
        , cargoVersion = cargoVersion cargo + 1
        }
  other ->
    Left
      ( InvalidStateTransition
          (T.pack (show other))
          (T.pack (show Submitted))
      )

{- | 予約を経路設計者に引き渡す (Submitted → RouteProposed) (US06, IT2)。

Submitted 以外の状態からは InvalidStateTransition を返し、
二重引き渡し・順序違反 (Draft からの直接引き渡し等) を防ぐ。
-}
requestRouting :: Cargo -> Either DomainError Cargo
requestRouting cargo = case cargoStatus cargo of
  Submitted ->
    Right
      cargo
        { cargoStatus = RouteProposed
        , cargoVersion = cargoVersion cargo + 1
        }
  other ->
    Left
      ( InvalidStateTransition
          (T.pack (show other))
          (T.pack (show RouteProposed))
      )

{- | 経路を予約に紐付ける (RouteProposed → RouteAssigned) (US11, IT4)。
他の状態からは InvalidStateTransition。
-}
linkRoute :: Cargo -> Either DomainError Cargo
linkRoute = transitionFromTo RouteProposed RouteAssigned

{- | 経路紐付けを解除する (RouteAssigned → Draft) (US11, IT4)。
確定済 (Confirmed) からは戻せず InvalidStateTransition。
-}
unlinkRoute :: Cargo -> Either DomainError Cargo
unlinkRoute = transitionFromTo RouteAssigned Draft

{- | 予約を確定する (RouteAssigned → Confirmed) (US13, IT4)。
経路紐付け前 (Draft / Submitted / RouteProposed) からの確定は不可。
-}
confirmBooking :: Cargo -> Either DomainError Cargo
confirmBooking = transitionFromTo RouteAssigned Confirmed

{- | 予約をキャンセルする (US13, IT4)。
Submitted / RouteProposed / RouteAssigned / Confirmed から Cancelled へ。
キャンセル料の算定は Application 層で CancellationPolicy を呼び出す。
本関数は状態遷移のみを担当する。
-}
cancelBooking :: Cargo -> Either DomainError Cargo
cancelBooking cargo = case cargoStatus cargo of
  s
    | s `elem` [Submitted, RouteProposed, RouteAssigned, Confirmed] ->
        Right
          cargo
            { cargoStatus = Cancelled
            , cargoVersion = cargoVersion cargo + 1
            }
  other ->
    Left (InvalidStateTransition (T.pack (show other)) (T.pack (show Cancelled)))

-- | 「指定 from 状態のみ to 状態へ遷移」を行う汎用ヘルパ。
transitionFromTo ::
  BookingStatus -> BookingStatus -> Cargo -> Either DomainError Cargo
transitionFromTo from to cargo
  | cargoStatus cargo == from =
      Right
        cargo
          { cargoStatus = to
          , cargoVersion = cargoVersion cargo + 1
          }
  | otherwise =
      Left
        ( InvalidStateTransition
            (T.pack (show (cargoStatus cargo)))
            (T.pack (show to))
        )
