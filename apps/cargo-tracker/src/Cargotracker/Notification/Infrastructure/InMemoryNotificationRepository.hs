{- | Notification 用インメモリ Repository (US26, IT6)

Postgres 実装が未整備 (T5-05 暫定範囲) の間、単一プロセス内で
Notification を保持するための IORef ベース実装。

用途:
- ローカル開発時の動作確認 (再起動で消える)
- 単体テスト (LogDeliveryPort + IORef で E2E に近いフローを組める)

本番配備前に Postgres 実装 (`PostgresNotificationRepository`) に切り替える。
-}
module Cargotracker.Notification.Infrastructure.InMemoryNotificationRepository
  ( newInMemoryNotificationRepository,
  ) where

import Data.IORef (atomicModifyIORef', newIORef, readIORef)

import Cargotracker.Notification.Application.Ports
  ( NotificationRepository (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
  )

-- | IORef で Notification のリストを保持する Repository。
newInMemoryNotificationRepository :: IO (NotificationRepository IO)
newInMemoryNotificationRepository = do
  ref <- newIORef ([] :: [Notification])
  pure
    NotificationRepository
      { saveNotification = \n -> do
          atomicModifyIORef' ref (\ns -> (n : ns, ()))
          pure (Right ())
      , findByBookingId = \bid -> do
          all_ <- readIORef ref
          pure (filter ((== bid) . nBookingId) all_)
      , updateNotification = \n -> do
          -- 単純化: nBookingId + nCreatedAt でユニーク性を仮定し置換
          atomicModifyIORef'
            ref
            ( \ns ->
                ( fmap
                    ( \existing ->
                        if nBookingId existing == nBookingId n
                          && nCreatedAt existing == nCreatedAt n
                          then n
                          else existing
                    )
                    ns
                , ()
                )
            )
          pure (Right ())
      }
