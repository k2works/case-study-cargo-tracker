{- | 荷役履歴照会クエリ (US18 拡張, IT5)

Cross-BC ヘルパー (ADR-0004 準拠、Rule 4 遵守): Tracking BC など他 BC の
Interfaces 層 (PublicTrackingApi) が荷役履歴を表示するために呼び出す。
Handling BC の Domain 型 (HandlingActivity / HandlingType) を境界外に漏らさず、
Text ベースの DTO のみ返す。
-}
module Cargotracker.Handling.Application.QueryHandlingHistoryQuery
  ( HandlingEventView (..),
    queryHandlingHistoryText,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Handling.Application.Ports
  ( HandlingActivityRepository (..),
  )
import Cargotracker.Handling.Domain.Model.HandlingActivity
  ( HandlingActivity (..),
  )
import Cargotracker.Handling.Domain.Model.HandlingType
  ( handlingTypeToText,
  )

-- | 公開追跡ページ向けの荷役イベント DTO (Text ベース)。
data HandlingEventView = HandlingEventView
  { hevEventType :: !Text
  -- ^ "RECEIVE" / "LOAD" / "UNLOAD" / "CUSTOMS" / "CLAIM"
  , hevCompletionTime :: !UTCTime
  , hevLocationUnlocode :: !Text
  , hevVoyageNumber :: !(Maybe Text)
  , hevOperatorName :: !Text
  }
  deriving stock (Eq, Show)

{- | 予約 ID に紐付く荷役履歴を時系列 (ASC) で公開向け DTO として返す。
Handling BC の Domain 型は境界内に閉じる。
-}
queryHandlingHistoryText ::
  Monad m =>
  HandlingActivityRepository m ->
  Text ->
  m [HandlingEventView]
queryHandlingHistoryText repo bid = do
  activities <- findByBookingId repo bid
  pure (map toView activities)
  where
    toView a =
      HandlingEventView
        { hevEventType = handlingTypeToText (haEventType a)
        , hevCompletionTime = haCompletionTime a
        , hevLocationUnlocode = haLocationUnlocode a
        , hevVoyageNumber = haVoyageNumber a
        , hevOperatorName = haOperatorName a
        }
