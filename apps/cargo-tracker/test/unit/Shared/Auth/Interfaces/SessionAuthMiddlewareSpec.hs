{-# LANGUAGE OverloadedStrings #-}

{- | Servant AuthProtect middleware の hspec-wai 統合テスト (T5-01 Phase 2, IT6)

`cookieProtectedApp` を組み込んだ簡易保護 API に対して、
- Cookie ヘッダ欠落 → 401
- cargo_session Cookie が不正形式 → 401
- 有効な Session Cookie → 200 + AuthenticatedUser を返す
を検証する。

Phase 1 の純粋関数レベル (SessionAuthSpec) を WAI アダプタで包んだ層のテスト。
-}
module Shared.Auth.Interfaces.SessionAuthMiddlewareSpec (spec) where

import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import qualified Network.Wai
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Shared.Auth.Application.SessionPorts (SessionRepository (..))
import Cargotracker.Shared.Auth.Domain.Session (Session (..))
import Cargotracker.Shared.Auth.Domain.SessionToken (SessionToken (..), unsafeSessionToken)
import Cargotracker.Shared.Auth.Domain.User (Role (..), UserId (..))
import Cargotracker.Shared.Auth.Interfaces.SessionAuth (cookieProtectedApp)

--------------------------------------------------------------------------------
-- フィクスチャ
--------------------------------------------------------------------------------

fixedNow :: UTCTime
fixedNow = UTCTime (fromGregorian 2026 7 2) (secondsToDiffTime 43200)

validToken :: SessionToken
validToken = unsafeSessionToken (T.replicate 43 "a" <> "b")

validSession :: Session
validSession =
  Session
    { sessionToken = validToken
    , sessionUserId = UserId "user-001"
    , sessionExpiresAt = UTCTime (fromGregorian 2026 7 3) 0
    , sessionLastUsedAt = fixedNow
    }

repoWithValid :: SessionRepository IO
repoWithValid =
  SessionRepository
    { saveSession = \_ -> pure (Right ())
    , findByToken = \t ->
        if t == validToken then pure (Just validSession) else pure Nothing
    , deleteByToken = \_ -> pure (Right ())
    , touchLastUsed = \_ -> pure (Right ())
    }

rolesForUser :: UserId -> IO [Role]
rolesForUser (UserId "user-001") = pure [Sales]
rolesForUser _ = pure []

app :: IO Network.Wai.Application
app = pure (cookieProtectedApp repoWithValid rolesForUser (pure fixedNow))

--------------------------------------------------------------------------------
-- spec
--------------------------------------------------------------------------------

spec :: Spec
spec = with app $ do
  describe "GET /me (Cookie 保護)" $ do
    it "Cookie ヘッダなしなら 401" $
      get "/me" `shouldRespondWith` 401

    it "cargo_session Cookie が含まれない場合は 401" $
      request "GET" "/me" [("Cookie", "other=abc")] "" `shouldRespondWith` 401

    it "cargo_session の形式が不正なら 401" $
      request "GET" "/me" [("Cookie", "cargo_session=xxx")] "" `shouldRespondWith` 401

    it "有効な Cookie なら 200" $ do
      let cookie = "cargo_session=" <> TE.encodeUtf8 (unSessionToken validToken)
      request "GET" "/me" [("Cookie", cookie)] "" `shouldRespondWith` 200
