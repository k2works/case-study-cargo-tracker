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
    verifyWith,
    isExpiredAt,
    markUsed,
    maxAttempts,
    ttlSeconds,
  ) where

import Data.Char (isDigit)
import Data.Maybe (isJust)
import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (NominalDiffTime, UTCTime, diffUTCTime)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Security.ConstantTime (Verifier, constantTimeEqText)

-- | 検証失敗の上限。5 回超過で `ConfirmationCodeMaxAttemptsExceeded` を返す。
maxAttempts :: Int
maxAttempts = 5

{- | 確認コードの有効期限 (秒)。発行時刻から本値を経過したら
`ConfirmationCodeExpired` として扱う。

24 時間 = 86400 秒。荷主 → 荷受人へ引取通知を配信し、当日から翌日にかけて
引取窓口を訪れる想定期間として設定。IT7 以降で環境変数化を検討する。
-}
ttlSeconds :: NominalDiffTime
ttlSeconds = 24 * 60 * 60

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
Verifier 型自体は Shared/Security/ConstantTime に定義 (Cross-BC 共有カーネル)。
-}

{- | 現在時刻 `now` において確認コードが有効期限切れかを判定する純粋関数 (T5-11)。

境界: `now - ccIssuedAt` が `ttlSeconds` 以上なら True。
`>=` を使うため、ちょうど TTL 経過時点でも期限切れと判定する。
-}
isExpiredAt :: UTCTime -> ConfirmationCode -> Bool
isExpiredAt now cc = diffUTCTime now (ccIssuedAt cc) >= ttlSeconds

{- | 入力コードで検証する。失敗時は理由に応じた `DomainError` を返す。

規約 (優先順位):
  1. `ccAttemptCount` が上限に達している場合は `ConfirmationCodeMaxAttemptsExceeded`
  2. 既に使用済の場合は `ConfirmationCodeAlreadyUsed`
  3. 発行から `ttlSeconds` を経過している場合は `ConfirmationCodeExpired` (T5-11)
  4. 検証関数が False を返した場合は `ConfirmationCodeMismatch`

呼び出し元 (Application 層) は失敗時に `ccAttemptCount` を +1 して永続化する
責務を負う (Domain 純粋関数のため副作用は返さない)。
-}
verifyWith :: Verifier -> UTCTime -> Text -> ConfirmationCode -> Either DomainError ConfirmationCode
verifyWith checker now input cc
  | ccAttemptCount cc >= maxAttempts =
      Left (ConfirmationCodeMaxAttemptsExceeded maxAttempts)
  | isJust (ccUsedAt cc) = Left ConfirmationCodeAlreadyUsed
  | isExpiredAt now cc = Left ConfirmationCodeExpired
  | not (checker input (ccValue cc)) = Left ConfirmationCodeMismatch
  | otherwise = Right cc

{- | `verifyWith constantTimeEqText` のショートカット (発行直後を想定した現在時刻 = 発行時刻)。

DB が平文 6 桁数字を保存している IT5 実装からの互換性維持のため残す。
T5-02 Phase 3 (Repository の code_hash 移行) 完了後は
`verifyWith verifySecret` に切り替える。

T5-11 対応で TTL 判定用の `now` は `ccIssuedAt` をそのまま使う (未経過扱い)。
時刻を明示したい呼出は `verifyWith constantTimeEqText now input cc` を推奨。
-}
verify :: Text -> ConfirmationCode -> Either DomainError ConfirmationCode
verify input cc = verifyWith constantTimeEqText (ccIssuedAt cc) input cc

{- | 検証成功後の `ccUsedAt` を確定する。既に使用済の場合は上書きしない
(実質べき等)。
-}
markUsed :: UTCTime -> ConfirmationCode -> ConfirmationCode
markUsed usedAt cc = case ccUsedAt cc of
  Just _ -> cc
  Nothing -> cc {ccUsedAt = Just usedAt}
