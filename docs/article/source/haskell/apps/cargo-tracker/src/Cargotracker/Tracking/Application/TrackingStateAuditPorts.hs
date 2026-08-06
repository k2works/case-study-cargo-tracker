{- | TrackingStateAudit Application 層ポート (US17, IT7)

手動状態更新の監査ログを永続化する Repository ポート。
Postgres 実装は Infrastructure 層で提供する (次反復以降で追加)。

T-02 規約: Repository 関数は IO のみ。Tx 境界は Application が管理する。
-}
module Cargotracker.Tracking.Application.TrackingStateAuditPorts
  ( TrackingStateAuditRepository (..),
  ) where

import Cargotracker.Shared.Domain.DomainError (DomainError)
import Cargotracker.Tracking.Domain.Model.TrackingStateAudit (TrackingStateAudit)
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber (TrackingNumber)

data TrackingStateAuditRepository m = TrackingStateAuditRepository
  { saveAudit :: TrackingStateAudit -> m (Either DomainError ())
  , findAuditsByTrackingNumber :: TrackingNumber -> m [TrackingStateAudit]
  -- ^ US17 監査履歴タブ用。changed_at DESC 順で返却する。
  }
