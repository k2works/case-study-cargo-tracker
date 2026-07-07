{- | 確認コード発行コマンド (US16, IT5)

BookingConfirmed (US13) を購読して確認コードを発行する。予約確定時に
IssueTrackingNumberCommand (US14) と並列で呼ばれる想定。

IT5 段階では 6 桁数字をランダム生成して mkConfirmationCode で VO を構築、
Postgres に upsert 保存する。IT6 で bcrypt hash 化 + 荷受人へのメール通知 (US26) を追加。

T-03 準拠: 乱数生成の IO は Application 呼び出し側で行い、Domain 層は
Text ベースの検証のみ担当する。
-}
module Cargotracker.Tracking.Application.IssueConfirmationCodeCommand
  ( IssueConfirmationCodeInput (..),
    execute,
    executeText,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.DomainError (DomainError)
import Cargotracker.Tracking.Application.ConfirmationCodePorts
  ( ConfirmationCodeRepository (..),
  )
import Cargotracker.Tracking.Domain.Model.ConfirmationCode
  ( ConfirmationCode (..),
    mkConfirmationCode,
  )

data IssueConfirmationCodeInput = IssueConfirmationCodeInput
  { inputBookingId :: !Text
  , inputCodeText :: !Text
  -- ^ 乱数生成済みの 6 桁数字 (Application 呼出側で IO)
  , inputNow :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  ConfirmationCodeRepository m ->
  IssueConfirmationCodeInput ->
  m (Either DomainError ConfirmationCode)
execute repo input = do
  mExisting <- findByBookingId repo (inputBookingId input)
  case mExisting of
    Just existing ->
      -- 冪等: 既発行なら既存を返す (At-Least-Once 対策)
      pure (Right existing)
    Nothing ->
      case mkConfirmationCode (inputNow input) (inputCodeText input) of
        Left err -> pure (Left err)
        Right cc -> do
          persist <- saveConfirmationCode repo (inputBookingId input) cc
          case persist of
            Left err -> pure (Left err)
            Right () -> pure (Right cc)

{- | Cross-BC helper (T7-E, IT8 / Rule 4 準拠): Handling BC 等の他 BC から
Text のみで呼べる版。新規発行時は Just codeText、既発行 (冪等パス) は
Nothing を返し、呼出側が「新規発行時のみ通知する」判断を Domain 型なしで
行えるようにする。
-}
executeText ::
  Monad m =>
  ConfirmationCodeRepository m ->
  IssueConfirmationCodeInput ->
  m (Either DomainError (Maybe Text))
executeText repo input = do
  result <- execute repo input
  pure (fmap classify result)
  where
    classify cc
      | ccValue cc == inputCodeText input = Just (inputCodeText input)
      | otherwise = Nothing
