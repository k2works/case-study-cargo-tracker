{-# LANGUAGE OverloadedStrings #-}

{- | セッション Cookie ベース AuthProtect middleware の解決ロジックのテスト (T5-01, IT6)

Cookie ヘッダの "cargo_session=<token>" から SessionToken を抽出し、
SessionRepository で Session を引き当て、有効期限を検証して
AuthenticatedUser (UserId + [Role]) を返すまでの純粋な流れを検証する。

失敗系:
- Cookie ヘッダなし → Nothing
- cargo_session Cookie なし → Nothing
- 不正な token 形式 → Nothing
- Session が DB にない → Nothing
- Session が有効期限切れ → Nothing

正常系:
- 有効な Cookie → Just (AuthenticatedUser uid roles)
-}
module Shared.Auth.Interfaces.SessionAuthSpec (spec) where

import Data.IORef (newIORef, readIORef)
import qualified Data.Text as T
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Shared.Auth.Application.SessionPorts (SessionRepository (..))
import Cargotracker.Shared.Auth.Domain.Session (Session (..))
import Cargotracker.Shared.Auth.Domain.SessionToken (SessionToken (..), unsafeSessionToken)
import Cargotracker.Shared.Auth.Domain.User (Role (..), UserId (..))
import Cargotracker.Shared.Auth.Interfaces.SessionAuth
  ( AuthenticatedUser (..),
    resolveCookieUser,
  )

--------------------------------------------------------------------------------
-- テストフィクスチャ
--------------------------------------------------------------------------------

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 7 2) (secondsToDiffTime 43200) -- 12:00 UTC

sampleToken :: SessionToken
sampleToken = unsafeSessionToken (T.replicate 43 "a" <> "b")

sampleUser :: UserId
sampleUser = UserId "user-001"

futureSession :: Session
futureSession =
  Session
    { sessionToken = sampleToken
    , sessionUserId = sampleUser
    , sessionExpiresAt = UTCTime (fromGregorian 2026 7 3) 0 -- 明日
    , sessionLastUsedAt = sampleNow
    }

expiredSession :: Session
expiredSession =
  futureSession {sessionExpiresAt = UTCTime (fromGregorian 2026 7 1) 0} -- 昨日

sessionRepoWith :: [(SessionToken, Session)] -> SessionRepository IO
sessionRepoWith kvs =
  SessionRepository
    { saveSession = \_ -> pure (Right ())
    , findByToken = \t -> pure (lookup t kvs)
    , deleteByToken = \_ -> pure (Right ())
    , touchLastUsed = \_ -> pure (Right ())
    }

-- ロール解決関数のスタブ (UserId → [Role])
rolesFor :: UserId -> IO [Role]
rolesFor (UserId "user-001") = pure [Sales]
rolesFor _ = pure []

--------------------------------------------------------------------------------
-- spec
--------------------------------------------------------------------------------

spec :: Spec
spec = describe "resolveCookieUser (T5-01)" $ do
  it "Cookie ヘッダなしなら Nothing" $ do
    let repo = sessionRepoWith [(sampleToken, futureSession)]
    result <- resolveCookieUser repo rolesFor Nothing sampleNow
    result `shouldBe` Nothing

  it "cargo_session Cookie が含まれない Cookie ヘッダなら Nothing" $ do
    let repo = sessionRepoWith [(sampleToken, futureSession)]
    result <- resolveCookieUser repo rolesFor (Just "other=xyz") sampleNow
    result `shouldBe` Nothing

  it "不正な形式の token は Nothing (Session lookup も走らない)" $ do
    ref <- newIORef (0 :: Int)
    let repo =
          (sessionRepoWith [])
            { findByToken = \_ -> do
                _ <- readIORef ref
                pure Nothing
            }
    result <- resolveCookieUser repo rolesFor (Just "cargo_session=too-short") sampleNow
    result `shouldBe` Nothing

  it "有効な token だが DB に Session がなければ Nothing" $ do
    let repo = sessionRepoWith []
        cookieHeader = "cargo_session=" <> unSessionToken sampleToken
    result <- resolveCookieUser repo rolesFor (Just cookieHeader) sampleNow
    result `shouldBe` Nothing

  it "有効期限切れの Session は Nothing" $ do
    let repo = sessionRepoWith [(sampleToken, expiredSession)]
        cookieHeader = "cargo_session=" <> unSessionToken sampleToken
    result <- resolveCookieUser repo rolesFor (Just cookieHeader) sampleNow
    result `shouldBe` Nothing

  it "有効な Session なら AuthenticatedUser を返す" $ do
    let repo = sessionRepoWith [(sampleToken, futureSession)]
        cookieHeader = "cargo_session=" <> unSessionToken sampleToken
    result <- resolveCookieUser repo rolesFor (Just cookieHeader) sampleNow
    result `shouldBe` Just (AuthenticatedUser sampleUser [Sales])

  it "複数 Cookie が並んでいても cargo_session を抽出できる" $ do
    let repo = sessionRepoWith [(sampleToken, futureSession)]
        cookieHeader = "foo=bar; cargo_session=" <> unSessionToken sampleToken <> "; baz=qux"
    result <- resolveCookieUser repo rolesFor (Just cookieHeader) sampleNow
    result `shouldBe` Just (AuthenticatedUser sampleUser [Sales])
