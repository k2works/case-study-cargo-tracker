{-# LANGUAGE OverloadedStrings #-}

{- | ログ配信実装 (US26, IT6, T5-05 の暫定策)

`NotificationDeliveryPort` の実装のうち LogChannel を担当する。
`Cargotracker.Shared.Infrastructure.Logging.logInfo` で JSON Lines 形式に
Notification 内容を書き出す。CloudWatch や jq で検索可能。

暫定範囲 (T5-05):
- 実 SMTP / SMS 配信は Notification BC 本格実装で対応
- 本モジュールは開発 / 監査ログ用途
- LogChannel 以外 (EmailMock / PrintableHtml) は将来の別実装が担う想定
-}
module Cargotracker.Notification.Infrastructure.LogDeliveryPort
  ( newLogDeliveryPort,
  ) where

import Data.Aeson (Value, toJSON)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Notification.Application.Ports
  ( DeliveryResult (..),
    NotificationDeliveryPort (..),
  )
import Cargotracker.Notification.Domain.Model.Notification
  ( Notification (..),
    NotificationChannel (..),
    NotificationContent (..),
  )
import Cargotracker.Shared.Infrastructure.Logging (logInfo)

{- | LogChannel を扱う DeliveryPort。他 Channel が指定された場合は
DeliverySucceeded を返しつつ「ログには残らない」ことを示す。将来的に
Channel 別の実装ディスパッチが必要になったら Ports レベルで分割する。
-}
newLogDeliveryPort :: NotificationDeliveryPort IO
newLogDeliveryPort =
  NotificationDeliveryPort
    { deliver = \n -> do
        case nChannel n of
          LogChannel -> do
            logInfo "notification:log-delivered" (payload n)
            pure DeliverySucceeded
          _ -> do
            -- 他 Channel は未実装のため、暫定として成功扱いにしつつ
            -- ログには「skip」で残す (監査追跡可能)
            logInfo "notification:channel-skipped" (payload n)
            pure DeliverySucceeded
    }
  where
    payload :: Notification -> [(Text, Value)]
    payload n =
      [ ("booking_id", toJSON (nBookingId n))
      , ("channel", toJSON (T.pack (show (nChannel n))))
      , ("subject", toJSON (ncSubject (nContent n)))
      , ("body", toJSON (ncBody (nContent n)))
      ]
