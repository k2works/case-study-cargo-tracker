{- | 認証ユーザー集約 (IT1 AUTH 1.1)

スマートコンストラクタで不正値を構築不能にする (ADR 0002)。
8 ロールは domain-model.md のロール定義に従う。
-}
module Cargotracker.Shared.Auth.Domain.User
  ( UserId (..),
    Email (..),
    PasswordHash (..),
    Role (..),
    User (..),
    mkUserId,
    mkEmail,
    mkPasswordHash,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype UserId = UserId {unUserId :: Text}
  deriving stock (Eq, Show)

newtype Email = Email {unEmail :: Text}
  deriving stock (Eq, Show)

newtype PasswordHash = PasswordHash {unPasswordHash :: Text}
  deriving stock (Eq, Show)

{- | 業務ロール (8 種)。
domain-model.md のロール定義に対応。
-}
data Role
  = Shipper
  | Consignee
  | Sales
  | Router
  | Tracker
  | Handler
  | Accountant
  | MasterAdmin
  deriving stock (Eq, Show, Enum, Bounded)

data User = User
  { userId :: !UserId
  , userEmail :: !Email
  , userPasswordHash :: !PasswordHash
  , userRole :: !Role
  }
  deriving stock (Eq, Show)

-- | UserId スマートコンストラクタ。空文字列を弾く。
mkUserId :: Text -> Either DomainError UserId
mkUserId t
  | T.null t = Left (InvalidUserId "empty")
  | otherwise = Right (UserId t)

{- | Email スマートコンストラクタ。最小限の構造検証 (IT1 段階)。
RFC 5322 完全準拠は IT7 で本格化。
-}
mkEmail :: Text -> Either DomainError Email
mkEmail t = case T.splitOn "@" t of
  [local, domain]
    | T.null local -> Left (InvalidEmail "empty local part")
    | T.null domain -> Left (InvalidEmail "empty domain")
    | otherwise -> Right (Email t)
  [_] -> Left (InvalidEmail "no @ symbol")
  _ -> Left (InvalidEmail "multiple @ symbols")

{- | PasswordHash スマートコンストラクタ。bcrypt のフォーマット (60 文字) のみ検証。
ハッシュ生成自体は Infrastructure 層 (bcrypt ライブラリ) で行う。
-}
mkPasswordHash :: Text -> Either DomainError PasswordHash
mkPasswordHash t
  | T.length t == 60 = Right (PasswordHash t)
  | otherwise = Left (InvalidPasswordHash "expected 60 chars")
