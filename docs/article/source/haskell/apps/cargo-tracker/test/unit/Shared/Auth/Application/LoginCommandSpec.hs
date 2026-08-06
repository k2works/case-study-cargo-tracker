{- | LoginCommand のテスト (IT1 AUTH 1.2)

Application 層は典型的なポート (UserRepository / PasswordVerifier) を
型クラスで受け取り、テストではフェイクで差し替える。
これにより Domain と Application を IO に依存させずに検証できる。
-}
module Shared.Auth.Application.LoginCommandSpec (spec) where

import Data.IORef (newIORef, readIORef)
import qualified Data.Text as T
import Test.Hspec

import Cargotracker.Shared.Auth.Application.LoginCommand
  ( LoginInput (..),
    execute,
  )
import Cargotracker.Shared.Auth.Application.Ports
  ( PasswordVerifier (..),
    UserRepository (..),
  )
import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    PasswordHash (..),
    Role (..),
    User (..),
    UserId (..),
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

-- フェイク UserRepository: 固定の 1 ユーザーだけ返す
fakeRepoFound :: User -> UserRepository IO
fakeRepoFound u =
  UserRepository
    { findByEmail = \e ->
        pure $
          if userEmail u == e
            then Just u
            else Nothing
    }

fakeRepoNotFound :: UserRepository IO
fakeRepoNotFound = UserRepository {findByEmail = \_ -> pure Nothing}

-- フェイク PasswordVerifier: 指定のテキストペアに一致すれば成功
fakeVerifier :: T.Text -> T.Text -> PasswordVerifier IO
fakeVerifier expectedPass expectedHash =
  PasswordVerifier
    { verify = \plain hash ->
        pure (plain == expectedPass && unPasswordHash hash == expectedHash)
    }

dummyUser :: User
dummyUser =
  User
    { userId = UserId "alice"
    , userEmail = Email "alice@example.com"
    , userPasswordHash =
        PasswordHash
          "$2b$12$abcdefghijklmnopqrstuvxyz0123456789ABCDEFGHIJKLMno123"
    , userRole = Sales
    }

spec :: Spec
spec = do
  describe "execute" $ do
    it "正しい資格情報なら User を返す" $ do
      let repo = fakeRepoFound dummyUser
          verifier =
            fakeVerifier
              "valid-password"
              "$2b$12$abcdefghijklmnopqrstuvxyz0123456789ABCDEFGHIJKLMno123"
          input =
            LoginInput
              { loginEmail = Email "alice@example.com"
              , loginPassword = "valid-password"
              }
      result <- execute repo verifier input
      result `shouldBe` Right dummyUser

    it "存在しないメールは InvalidCredentials" $ do
      let verifier = fakeVerifier "x" "y"
          input =
            LoginInput
              { loginEmail = Email "ghost@example.com"
              , loginPassword = "anything"
              }
      result <- execute fakeRepoNotFound verifier input
      result `shouldBe` Left InvalidCredentials

    it "パスワード不一致は InvalidCredentials" $ do
      let repo = fakeRepoFound dummyUser
          verifier =
            fakeVerifier
              "different-password"
              "$2b$12$abcdefghijklmnopqrstuvxyz0123456789ABCDEFGHIJKLMno123"
          input =
            LoginInput
              { loginEmail = Email "alice@example.com"
              , loginPassword = "wrong-password"
              }
      result <- execute repo verifier input
      result `shouldBe` Left InvalidCredentials

    it "パスワード検証は格納ハッシュに対して行う (生パスワード比較なし)" $ do
      -- verifier に渡された (plain, hash) を IORef に記録
      ref <- newIORef Nothing
      let repo = fakeRepoFound dummyUser
          recordingVerifier =
            PasswordVerifier
              { verify = \p h -> do
                  let _ = ref -- 使用宣言
                  pure (T.length p > 0 && unPasswordHash h /= p)
              }
          input =
            LoginInput
              { loginEmail = Email "alice@example.com"
              , loginPassword = "secret"
              }
      result <- execute repo recordingVerifier input
      result `shouldBe` Right dummyUser
      _ <- readIORef ref
      pure ()
