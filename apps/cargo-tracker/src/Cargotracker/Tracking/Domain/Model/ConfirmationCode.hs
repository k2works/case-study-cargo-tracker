{- | 引取確認コード VO (US16, IT5 task 6.1)

Tracking Context の集約内 VO。TrackingActivity が Maybe ConfirmationCode を持ち、
US16 の「引取時に確認コード検証成功時のみ CLAIM イベントを発行」を実現する。

型の役割:
  * ccValue        : 6 桁数字の平文 (Domain 層。DB には bcrypt ハッシュのみ保存 = SEC-04)
  * ccIssuedAt     : 発行時刻
  * ccUsedAt       : Nothing = 未使用 / Just t = 使用済 (t = 使用時刻)
  * ccAttemptCount : 検証失敗の累積回数 (5 で lock)

T-03 準拠: 全関数は純粋 (`Either DomainError ...`)。bcrypt / randomDigits の IO
処理は Application 層で `verifyClaim` を呼び出す前後にラップする。
-}
module Cargotracker.Tracking.Domain.Model.ConfirmationCode
  ( ConfirmationCode (..),
    mkConfirmationCode,
    verify,
    markUsed,
    maxAttempts,
  ) where

import Data.Char (isDigit)
import Data.Maybe (isJust)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Security.ConstantTime (constantTimeEqText)

-- | 検証失敗の上限。5 回超過で `ConfirmationCodeMaxAttemptsExceeded` を返す。
maxAttempts :: Int
maxAttempts = 5

data ConfirmationCode = ConfirmationCode
  { ccValue :: !Text
  -- ^ 6 桁数字の平文 (Domain 層のみ、DB は bcrypt ハッシュ)
  , ccIssuedAt :: !UTCTime
  , ccUsedAt :: !(Maybe UTCTime)
  , ccAttemptCount :: !Int
  }
  deriving stock (Eq, Show)

{- | スマートコンストラクタ。6 桁数字のみを受理する。

>>> import Data.Time
>>> ((mkConfirmationCode <$> pure (read "2026-07-01 00:00:00 UTC")) <*> pure "123456") :: Maybe (Either DomainError ConfirmationCode)
-}
mkConfirmationCode :: UTCTime -> Text -> Either DomainError ConfirmationCode
mkConfirmationCode now raw
  | T.length raw == 6 && T.all isDigit raw =
      Right
        ConfirmationCode
          { ccValue = raw
          , ccIssuedAt = now
          , ccUsedAt = Nothing
          , ccAttemptCount = 0
          }
  | otherwise = Left (InvalidConfirmationCodeFormat raw)

{- | 入力コードで検証する。失敗時は理由に応じた `DomainError` を返す。

規約:
  * `ccAttemptCount` が上限に達している場合は `ConfirmationCodeMaxAttemptsExceeded`
  * 既に使用済の場合は `ConfirmationCodeAlreadyUsed`
  * 平文比較で不一致の場合は `ConfirmationCodeMismatch`

呼び出し元 (Application 層) は失敗時に `ccAttemptCount` を +1 して永続化する
責務を負う (Domain 純粋関数のため副作用は返さない)。
-}
verify :: Text -> ConfirmationCode -> Either DomainError ConfirmationCode
verify input cc
  | ccAttemptCount cc >= maxAttempts =
      Left (ConfirmationCodeMaxAttemptsExceeded maxAttempts)
  | isJust (ccUsedAt cc) = Left ConfirmationCodeAlreadyUsed
  | not (constantTimeEqText input (ccValue cc)) = Left ConfirmationCodeMismatch
  | otherwise = Right cc

{- | 検証成功後の `ccUsedAt` を確定する。既に使用済の場合は上書きしない
(実質べき等)。
-}
markUsed :: UTCTime -> ConfirmationCode -> ConfirmationCode
markUsed usedAt cc = case ccUsedAt cc of
  Just _ -> cc
  Nothing -> cc {ccUsedAt = Just usedAt}
