{- | ConfirmationCode 出力ポート (US16, IT5)

Tracking BC が確認コードを永続化するためのインタフェース。
booking_id を業務キーとして 1 予約 = 0..1 コードを保持する。
-}
module Cargotracker.Tracking.Application.ConfirmationCodePorts
  ( ConfirmationCodeRepository (..),
    verifyAndConsume,
    verifyAndConsumeWith,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Security.ConstantTime (constantTimeEqText)
import Cargotracker.Tracking.Domain.Model.ConfirmationCode
  ( ConfirmationCode (..),
    Verifier,
    markUsed,
    verifyWith,
  )

data ConfirmationCodeRepository m = ConfirmationCodeRepository
  { saveConfirmationCode :: Text -> ConfirmationCode -> m (Either DomainError ())
  -- ^ booking_id とペアで保存 (INSERT ON CONFLICT (booking_id) DO UPDATE で冪等)
  , findByBookingId :: Text -> m (Maybe ConfirmationCode)
  , updateAfterVerify :: Text -> ConfirmationCode -> m (Either DomainError ())
  -- ^ 検証成功後の usedAt 反映 or 失敗時の attempt_count +1
  }

{- | Cross-BC ヘルパー (US16 / Rule 4 準拠): 他 BC (Handling BC など) から呼ばれる
確認コード検証の唯一の窓口。verify + markUsed + attempt_count 更新の副作用を
Tracking BC 内に閉じ込め、呼出側は Bool 相当の Either だけを受け取る。
-}
verifyAndConsumeWith ::
  Monad m =>
  Verifier ->
  ConfirmationCodeRepository m ->
  -- | 予約 ID
  Text ->
  -- | 入力コード (6 桁数字)
  Text ->
  -- | 現在時刻 (markUsed 用)
  UTCTime ->
  m (Either DomainError ())
verifyAndConsumeWith checker repo bid inputCode now = do
  mExisting <- findByBookingId repo bid
  case mExisting of
    Nothing -> pure (Left (HandlingBookingNotFound bid))
    Just cc ->
      case verifyWith checker inputCode cc of
        Left err -> do
          _ <-
            updateAfterVerify
              repo
              bid
              cc {ccAttemptCount = ccAttemptCount cc + 1}
          pure (Left err)
        Right verified -> do
          let used = markUsed now verified
          _ <- updateAfterVerify repo bid used
          pure (Right ())

{- | 互換性維持: 平文比較 (constantTimeEqText) を使う従来 API。

DB が平文の 6 桁数字を保存している IT5 実装の呼出側が使う。
T5-02 Phase 3 (code_hash 移行) 完了後は `verifyAndConsumeWith verifySecret`
に切り替える。
-}
verifyAndConsume ::
  Monad m =>
  ConfirmationCodeRepository m ->
  Text ->
  Text ->
  UTCTime ->
  m (Either DomainError ())
verifyAndConsume = verifyAndConsumeWith constantTimeEqText
