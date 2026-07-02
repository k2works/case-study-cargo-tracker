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
    Verifier,
    mkConfirmationCode,
    verify,
    verifyWith,
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

{- | 入力コードと保存値を比較する純粋な関数の型。

- constantTimeEqText: DB が平文の 6 桁数字を保存する場合 (IT5 移行前)
- verifySecret (bcrypt): DB が code_hash を保存する場合 (T5-02 Phase 3 移行後)

呼出側 (Repository または Application) が選択して `verifyWith` に渡す。
-}
type Verifier = Text -> Text -> Bool

{- | 入力コードで検証する。失敗時は理由に応じた `DomainError` を返す。

規約:
  * `ccAttemptCount` が上限に達している場合は `ConfirmationCodeMaxAttemptsExceeded`
  * 既に使用済の場合は `ConfirmationCodeAlreadyUsed`
  * 検証関数が False を返した場合は `ConfirmationCodeMismatch`

呼び出し元 (Application 層) は失敗時に `ccAttemptCount` を +1 して永続化する
責務を負う (Domain 純粋関数のため副作用は返さない)。
-}
verifyWith :: Verifier -> Text -> ConfirmationCode -> Either DomainError ConfirmationCode
verifyWith checker input cc
  | ccAttemptCount cc >= maxAttempts =
      Left (ConfirmationCodeMaxAttemptsExceeded maxAttempts)
  | isJust (ccUsedAt cc) = Left ConfirmationCodeAlreadyUsed
  | not (checker input (ccValue cc)) = Left ConfirmationCodeMismatch
  | otherwise = Right cc

{- | `verifyWith constantTimeEqText` のショートカット。

DB が平文 6 桁数字を保存している IT5 実装からの互換性維持のため残す。
T5-02 Phase 3 (Repository の code_hash 移行) 完了後は
`verifyWith verifySecret` に切り替える。
-}
verify :: Text -> ConfirmationCode -> Either DomainError ConfirmationCode
verify = verifyWith constantTimeEqText

{- | 検証成功後の `ccUsedAt` を確定する。既に使用済の場合は上書きしない
(実質べき等)。
-}
markUsed :: UTCTime -> ConfirmationCode -> ConfirmationCode
markUsed usedAt cc = case ccUsedAt cc of
  Just _ -> cc
  Nothing -> cc {ccUsedAt = Just usedAt}
