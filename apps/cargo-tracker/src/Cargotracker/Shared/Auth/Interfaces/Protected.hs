{-# LANGUAGE DataKinds #-}
{-# LANGUAGE OverloadedStrings #-}
{-# LANGUAGE TypeOperators #-}

{- | RBAC 保護エンドポイント (IT1 AUTH 1.5)

`requireRole` ヘルパは Authorization: Bearer ヘッダから JWT を取り出し、
署名検証して claims.role が許可リストに含まれているか確認する。

- 401 (Unauthorized): ヘッダ欠落、形式不正、署名検証失敗
- 403 (Forbidden): 検証成功したがロールが許可リストにない
- 200 (OK): 検証成功、ロール許可

型レベル `RequireRole '[..]` への昇格は IT2 で検討。
-}
module Cargotracker.Shared.Auth.Interfaces.Protected
  ( requireRole,
    protectedApp,
  ) where

import Data.Aeson (ToJSON (..), object, (.=))
import qualified Data.Text as T
import Network.Wai (Application)
import Servant

import Cargotracker.Shared.Auth.Domain.User (Role)
import Cargotracker.Shared.Auth.Infrastructure.JwtIssuer
  ( Claims (..),
    JwtSecret,
    verifyAndDecode,
  )

-- | Authorization: Bearer ヘッダから JWT を取り出し検証してロールを判定する。
requireRole ::
  JwtSecret ->
  [Role] ->
  Maybe T.Text -> -- Authorization ヘッダの値
  Handler Claims
requireRole secret allowed mHeader =
  case mHeader of
    Nothing -> throwError err401 {errBody = "{\"error\":\"missing Authorization header\"}"}
    Just headerVal -> case T.stripPrefix "Bearer " headerVal of
      Nothing -> throwError err401 {errBody = "{\"error\":\"expected Bearer scheme\"}"}
      Just tok -> case verifyAndDecode secret tok of
        Left _ -> throwError err401 {errBody = "{\"error\":\"invalid token\"}"}
        Right claims ->
          if claimsRole claims `elem` allowed
            then pure claims
            else throwError err403 {errBody = "{\"error\":\"insufficient role\"}"}

-- テスト用に最小の保護エンドポイントを公開する WAI Application を作る
type ProtectedApi =
  "protected"
    :> Header "Authorization" T.Text
    :> Get '[JSON] ProtectedResponse

newtype ProtectedResponse = ProtectedResponse {message :: T.Text}
  deriving stock (Show, Eq)

instance ToJSON ProtectedResponse where
  toJSON (ProtectedResponse m) = object ["message" .= m]

protectedApp :: JwtSecret -> [Role] -> Application
protectedApp secret allowed =
  serve (Proxy :: Proxy ProtectedApi) handler
  where
    handler :: Maybe T.Text -> Handler ProtectedResponse
    handler mAuth = do
      _ <- requireRole secret allowed mAuth
      pure (ProtectedResponse "ok")
