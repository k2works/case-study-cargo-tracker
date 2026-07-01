{- | 荷役イベント登録コマンド (US15, IT5)

業務フロー:
1. 荷役作業員が /handling/new フォームから種別・時刻・場所を送信
2. mkHandlingActivity で Domain 値を構築 (現在時刻より過去、LOAD/UNLOAD 時 voyage 必須、operator 非空)
3. HandlingActivityRepository.saveHandlingActivity で永続化

T-03 純粋: バリデーションは Domain 層 (mkHandlingActivity)、IO は Repository のみ。

IT5 段階の簡略化:
- Booking 存在確認、Voyage 存在確認、Itinerary 整合性検証は IT6 で追加
- 順序制約 (前イベントとの整合) も IT6
-}
module Cargotracker.Handling.Application.RegisterHandlingEventCommand
  ( RegisterHandlingEventInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Handling.Application.Ports
  ( HandlingActivityRepository (..),
  )
import Cargotracker.Handling.Domain.Model.HandlingActivity (mkHandlingActivity)
import Cargotracker.Handling.Domain.Model.HandlingType (HandlingType)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data RegisterHandlingEventInput = RegisterHandlingEventInput
  { inputBookingId :: !Text
  , inputEventType :: !HandlingType
  , inputCompletionTime :: !UTCTime
  , inputLocationUnlocode :: !Text
  , inputVoyageNumber :: !(Maybe Text)
  , inputOperatorName :: !Text
  , inputNow :: !UTCTime
  -- ^ 未来時刻検証用 (Application 呼び出し側で getCurrentTime を注入)
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  HandlingActivityRepository m ->
  RegisterHandlingEventInput ->
  m (Either DomainError ())
execute repo input = do
  case mkHandlingActivity
    (inputNow input)
    (inputBookingId input)
    (inputEventType input)
    (inputCompletionTime input)
    (inputLocationUnlocode input)
    (inputVoyageNumber input)
    (inputOperatorName input) of
    Left err -> pure (Left err)
    Right activity -> saveHandlingActivity repo activity
