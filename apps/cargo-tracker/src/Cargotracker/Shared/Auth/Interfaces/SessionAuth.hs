{-# LANGUAGE OverloadedStrings #-}

{- | セッション Cookie ベース AuthProtect middleware (T5-01, IT6, ADR-0010 段階移行完了)

Servant のリクエストごとに Cookie ヘッダから cargo_session を抽出し、
DB (SessionRepository) から Session を引き当てて有効期限を検証し、
AuthenticatedUser (UserId + [Role]) を解決する。

責務:

- Cookie ヘッダから cargo_session=<token> を抽出 (複数 Cookie 対応)
- SessionToken のスマートコンストラクタで形式検証
- SessionRepository.findByToken で Session 取得
- Session.isExpired で有効期限判定
- ロール解決関数で AuthenticatedUser を構築

非責務:

- Servant AuthHandler の組み立て (respective handler で `case resolveCookieUser` で分岐)
- Cookie 発行 (LoginPageApi の責務)
- 認可 (呼び出し側で `Role` に応じて 403 判定)
-}
module Cargotracker.Shared.Auth.Interfaces.SessionAuth
  ( AuthenticatedUser (..),
    resolveCookieUser,
    extractCargoSessionToken,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import Data.Time (UTCTime)

import Cargotracker.Shared.Auth.Application.SessionPorts (SessionRepository (..))
import Cargotracker.Shared.Auth.Domain.Session (Session (..), isExpired)
import Cargotracker.Shared.Auth.Domain.SessionToken (SessionToken, mkSessionToken)
import Cargotracker.Shared.Auth.Domain.User (Role, UserId)

-- | Cookie 検証を通過したユーザー。
data AuthenticatedUser = AuthenticatedUser
  { authUserId :: !UserId
  , authRoles :: ![Role]
  }
  deriving stock (Eq, Show)

{- | Cookie ヘッダ全体から cargo_session=<value> の value を取り出す純粋関数。

複数 Cookie が "; " で連結されている想定 (RFC 6265)。
cargo_session が存在しなければ Nothing。
-}
extractCargoSessionToken :: Text -> Maybe Text
extractCargoSessionToken header =
  let pairs = fmap T.strip (T.splitOn ";" header)
      isCargoSession p = "cargo_session=" `T.isPrefixOf` p
   in case filter isCargoSession pairs of
        (p : _) -> Just (T.drop (T.length ("cargo_session=" :: Text)) p)
        [] -> Nothing

{- | Cookie ヘッダから AuthenticatedUser を解決する。

引数:

- SessionRepository IO: findByToken 実装
- (UserId -> IO [Role]): ロール解決関数 (呼び出し側で UserRepository / role キャッシュを差し込む)
- Maybe Text: Cookie ヘッダ生値 (Servant の `Header "Cookie" Text` から得られる Just / Nothing)
- UTCTime: 現在時刻 (有効期限判定用)

戻り値: Just AuthenticatedUser (認証済) / Nothing (未認証・全失敗ケース)
-}
resolveCookieUser ::
  SessionRepository IO ->
  (UserId -> IO [Role]) ->
  Maybe Text ->
  UTCTime ->
  IO (Maybe AuthenticatedUser)
resolveCookieUser repo lookupRoles mHeader now =
  case mHeader >>= extractCargoSessionToken of
    Nothing -> pure Nothing
    Just raw -> case mkSessionToken raw of
      Left _ -> pure Nothing
      Right tok -> do
        mSession <- findByToken repo tok
        case mSession of
          Nothing -> pure Nothing
          Just s
            | isExpired now s -> pure Nothing
            | otherwise -> do
                roles <- lookupRoles (sessionUserId s)
                pure (Just (AuthenticatedUser (sessionUserId s) roles))
