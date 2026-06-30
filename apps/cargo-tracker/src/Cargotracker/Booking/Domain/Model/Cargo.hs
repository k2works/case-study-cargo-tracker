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
    canTransitionTo,
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

-- | 予約を確定送信する (Draft → Submitted)。
submitBooking :: Cargo -> Either DomainError Cargo
submitBooking = transitionTo Submitted

-- | 予約を経路設計者に引き渡す (Submitted → RouteProposed) (US06, IT2)。
requestRouting :: Cargo -> Either DomainError Cargo
requestRouting = transitionTo RouteProposed

-- | 経路を予約に紐付ける (RouteProposed → RouteAssigned) (US11, IT4)。
linkRoute :: Cargo -> Either DomainError Cargo
linkRoute = transitionTo RouteAssigned

-- | 経路紐付けを解除する (RouteAssigned → Draft) (US11, IT4)。
unlinkRoute :: Cargo -> Either DomainError Cargo
unlinkRoute = transitionTo Draft

-- | 予約を確定する (RouteAssigned → Confirmed) (US13, IT4)。
confirmBooking :: Cargo -> Either DomainError Cargo
confirmBooking = transitionTo Confirmed

{- | 予約をキャンセルする (US13, IT4)。

キャンセル料の算定は Application 層で CancellationPolicy を呼び出す。
本関数は状態遷移のみを担当する。
-}
cancelBooking :: Cargo -> Either DomainError Cargo
cancelBooking = transitionTo Cancelled

{- | 状態遷移の SSoT (H-01 リファクタ, IT4 レビュー指摘)。

`BookingStatus.canTransitionTo` を真実とし、許可された場合のみ
status 更新と version+1 を行う。許可ペアの一覧は BookingStatus.hs
の `canTransitionTo` を参照。
-}
transitionTo :: BookingStatus -> Cargo -> Either DomainError Cargo
transitionTo to cargo
  | canTransitionTo (cargoStatus cargo) to =
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
