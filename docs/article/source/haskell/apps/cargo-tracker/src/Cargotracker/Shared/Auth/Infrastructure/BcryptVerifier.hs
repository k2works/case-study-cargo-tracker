{- | bcrypt によるパスワード検証実装 (IT1 AUTH 1.3 一部)

`bcrypt` ライブラリの `validatePassword` / `hashPasswordUsingPolicy` を
ラップする。`PasswordVerifier` ポートを実装。

bcrypt のコスト係数は本番 12、テストでは検証速度のため 4 を許容する
ライブラリ既定値 (`slowerBcryptHashingPolicy`) を本番では使用する想定。
IT1 段階では `fastBcryptHashingPolicy` を採用し、テスト実行時間を抑える。
本番値は IT3 で環境変数経由で切替可能にする。
-}
module Cargotracker.Shared.Auth.Infrastructure.BcryptVerifier
  ( newBcryptVerifier,
    hashPassword,
  ) where

import qualified Crypto.BCrypt as BC
import Data.Text (Text)
import Data.Text.Encoding (decodeUtf8, encodeUtf8)

import Cargotracker.Shared.Auth.Application.Ports (PasswordVerifier (..))
import Cargotracker.Shared.Auth.Domain.User (PasswordHash (..))

{- | bcrypt によるハッシュ生成。
IT1 では fastBcryptHashingPolicy (cost=4) を使用。本番化時に
slowerBcryptHashingPolicy (cost=12) に切替予定。
-}
hashPassword :: Text -> IO PasswordHash
hashPassword plain = do
  let bs = encodeUtf8 plain
  mResult <- BC.hashPasswordUsingPolicy BC.fastBcryptHashingPolicy bs
  case mResult of
    Just hashedBs -> pure (PasswordHash (decodeUtf8 hashedBs))
    Nothing -> error "bcrypt: hashPasswordUsingPolicy returned Nothing"

{- | PasswordVerifier ポートの bcrypt 実装。
Application 層では `verify` をモナディックに呼び出す。
-}
newBcryptVerifier :: PasswordVerifier IO
newBcryptVerifier =
  PasswordVerifier
    { verify = \plain hash -> do
        let plainBs = encodeUtf8 plain
            hashBs = encodeUtf8 (unPasswordHash hash)
        pure (BC.validatePassword hashBs plainBs)
    }
