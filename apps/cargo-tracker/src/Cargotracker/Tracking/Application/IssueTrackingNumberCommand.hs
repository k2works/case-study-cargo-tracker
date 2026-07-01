{- | 追跡番号発行コマンド (US14, IT5)

BookingConfirmed (US13) を購読して起動する。1 予約 = 0..1 追跡活動 の関係を守り、
既に発行済みの場合は冪等に既存 TrackingNumber を返す。

業務フロー:
1. BookingId + 生成済み TrackingNumber (Text) を受け取る
   (推測困難化のため乱数生成は Application 呼び出し側で行い、Domain 層は
    受け取った文字列を mkTrackingNumber で検証するのみ = T-03 準拠)
2. TrackingRepository.findByBookingId で重複チェック
3. 未発行なら TrackingActivity を保存

T-01 規約: Tx 境界は Application 内。saveTracking は Repository の IO のみで完結。
-}
module Cargotracker.Tracking.Application.IssueTrackingNumberCommand
  ( IssueTrackingNumberInput (..),
    execute,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError)
import Cargotracker.Tracking.Application.Ports (TrackingRepository (..))
import Cargotracker.Tracking.Domain.Model.TrackingActivity
  ( TrackingActivity (..),
    initialActivity,
  )
import Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( TrackingNumber,
    mkTrackingNumber,
  )

data IssueTrackingNumberInput = IssueTrackingNumberInput
  { inputBookingId :: !Text
  , inputTrackingNumberText :: !Text
  {- ^ 乱数生成済みの候補文字列 (8 文字英数大文字)。Application 呼び出し側で
  IO を伴う乱数生成を行い、Domain 純粋層で形式検証する分離。
  -}
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  TrackingRepository m ->
  IssueTrackingNumberInput ->
  m (Either DomainError TrackingNumber)
execute repo input = do
  mExisting <- findByBookingId repo (inputBookingId input)
  case mExisting of
    Just existing ->
      -- 冪等: 既発行なら既存 TrackingNumber を返す (BookingConfirmed の
      -- 二重配信対策 = At-Least-Once イベント配信の耐性)
      pure (Right (taTrackingNumber existing))
    Nothing ->
      case mkTrackingNumber (inputTrackingNumberText input) of
        Left err -> pure (Left err)
        Right tn -> do
          let activity = initialActivity tn (inputBookingId input)
          persist <- saveTracking repo activity
          case persist of
            Left err -> pure (Left err)
            Right () -> pure (Right tn)
