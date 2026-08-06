{- | 例外報告者 VO (US19/US20, IT7)

例外を登録する担当者情報を保持する VO。userId (Text) と role (Text) の
両方を Text で保持することで Rule 4 (BC 間 Domain 直接参照禁止) を遵守する
(Shared.Auth.Domain.User.Role 型に依存しない)。

想定 role 値: "Handler" / "Tracker" / "Admin" (RolePolicy と整合、
Interfaces 層で Session 情報から生成)
-}
module Cargotracker.Exception.Domain.Model.Reporter
  ( Reporter (..),
    mkReporter,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data Reporter = Reporter
  { reporterUserId :: !Text
  -- ^ 報告者 userId (Cross-BC 参照は Text = ADR-0004 Rule 4)
  , reporterRole :: !Text
  -- ^ 役割 (Handler / Tracker / Admin 等、Interfaces 層で Session から生成)
  }
  deriving stock (Eq, Show)

{- | Reporter のスマートコンストラクタ。

* userId が trim 後空文字 → InvalidReporter "empty user id"
* role が trim 後空文字 → InvalidReporter "empty role"
-}
mkReporter :: Text -> Text -> Either DomainError Reporter
mkReporter userId role
  | T.null trimmedUser = Left (InvalidReporter "empty user id")
  | T.null trimmedRole = Left (InvalidReporter "empty role")
  | otherwise = Right (Reporter trimmedUser trimmedRole)
  where
    trimmedUser = T.strip userId
    trimmedRole = T.strip role
