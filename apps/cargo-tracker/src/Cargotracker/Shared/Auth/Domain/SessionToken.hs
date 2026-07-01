{- | セッショントークン VO (task 1.2, ADR-0010, IT5)

opaque Cookie ベースのセッション認証で使用する短命トークン。
256bit 乱数を base64url エンコードした 44 文字を保持する。

Cookie 値としてブラウザに配布され、DB (session テーブル) の primary lookup key。
乱数生成は Application 層で行い、Domain 層は形式検証のみ担当する (T-03 準拠)。
-}
module Cargotracker.Shared.Auth.Domain.SessionToken
  ( SessionToken (..),
    mkSessionToken,
    unsafeSessionToken,
  ) where

import Data.Char (isAlphaNum)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype SessionToken = SessionToken {unSessionToken :: Text}
  deriving stock (Eq, Ord, Show)

{- | スマートコンストラクタ。base64url 想定の 43-44 文字 (英数 + '-' + '_') を受理する。

- 256bit ランダム → base64url 44 文字 (末尾 '=' あり) または 43 文字 (padless)
- '-' と '_' は base64url 特有 (URL/Cookie 安全)
-}
mkSessionToken :: Text -> Either DomainError SessionToken
mkSessionToken t
  | len == 43 || len == 44
  , T.all validChar t =
      Right (SessionToken t)
  | otherwise = Left (InvalidTrackingNumberFormat t)
  where
    len = T.length t
    validChar c = isAlphaNum c || c == '-' || c == '_' || c == '='

-- | 検証済みトークンの復元用 (DB から読み出した値など)。
unsafeSessionToken :: Text -> SessionToken
unsafeSessionToken = SessionToken
