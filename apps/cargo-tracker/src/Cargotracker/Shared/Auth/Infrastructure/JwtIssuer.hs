{- | JWT 発行・検証実装 (IT1 AUTH 1.3 一部)

HS256 (HMAC-SHA256) で署名する最小実装。
servant-auth-server に置き換える余地を残すため、本モジュールでは
発行 / 検証関数のみ公開し、Servant 層からは port を介して呼び出す。

JWT 構造 (3 つのドット区切りパート):
  base64url(header) . base64url(payload) . base64url(HMAC-SHA256(header.payload, secret))

ペイロード (Claims) の JSON 例:
  { "sub": "alice", "email": "alice@example.com", "role": "Sales", "exp": 1234567890 }
-}
module Cargotracker.Shared.Auth.Infrastructure.JwtIssuer
  ( JwtSecret (..),
    JwtTtlSeconds (..),
    Claims (..),
    issue,
    verifyAndDecode,
    computeExpiry,
  ) where

import qualified Crypto.Hash.SHA256 as SHA256
import Data.Aeson (FromJSON (..), ToJSON (..), eitherDecodeStrict, encode, object, withObject, (.:), (.=))
import qualified Data.ByteString as BS
import qualified Data.ByteString.Base64.URL as B64U
import qualified Data.ByteString.Lazy as LBS
import Data.Text (Text)
import qualified Data.Text as T
import Data.Text.Encoding (decodeUtf8', encodeUtf8)
import Data.Time.Clock.POSIX (POSIXTime)

import Cargotracker.Shared.Auth.Domain.User
  ( Email (..),
    Role (..),
    UserId (..),
  )

newtype JwtSecret = JwtSecret {unJwtSecret :: Text}
  deriving stock (Eq, Show)

{- | JWT 有効期間 (秒)。

T-02 (IT2) で導入。発行時点の POSIX 時刻 + ttl を exp とすることで、
固定の遠未来値による「永続有効トークン」リスクを排除する。
-}
newtype JwtTtlSeconds = JwtTtlSeconds {unJwtTtlSeconds :: Integer}
  deriving stock (Eq, Show)

{- | 現在時刻 (POSIX 秒) と TTL から exp を算出する。

`floor` で切り捨て、Integer の秒精度に統一。
-}
computeExpiry :: POSIXTime -> JwtTtlSeconds -> Integer
computeExpiry now (JwtTtlSeconds ttl) = floor (toRational now) + ttl

{- | JWT クレーム。
exp は UNIX 秒。
-}
data Claims = Claims
  { claimsUserId :: !UserId
  , claimsEmail :: !Email
  , claimsRole :: !Role
  , claimsExp :: !Integer
  }
  deriving stock (Eq, Show)

instance ToJSON Claims where
  toJSON c =
    object
      [ "sub" .= unUserId (claimsUserId c)
      , "email" .= unEmail (claimsEmail c)
      , "role" .= showRole (claimsRole c)
      , "exp" .= claimsExp c
      ]

instance FromJSON Claims where
  parseJSON = withObject "Claims" $ \o -> do
    sub <- o .: "sub"
    email <- o .: "email"
    roleText <- o .: "role"
    role <- parseRole roleText
    expVal <- o .: "exp"
    pure (Claims (UserId sub) (Email email) role expVal)
    where
      parseRole :: MonadFail m => Text -> m Role
      parseRole t = case t of
        "Shipper" -> pure Shipper
        "Consignee" -> pure Consignee
        "Sales" -> pure Sales
        "Router" -> pure Router
        "Tracker" -> pure Tracker
        "Handler" -> pure Handler
        "Accountant" -> pure Accountant
        "MasterAdmin" -> pure MasterAdmin
        other -> fail ("unknown role: " <> T.unpack other)

showRole :: Role -> Text
showRole Shipper = "Shipper"
showRole Consignee = "Consignee"
showRole Sales = "Sales"
showRole Router = "Router"
showRole Tracker = "Tracker"
showRole Handler = "Handler"
showRole Accountant = "Accountant"
showRole MasterAdmin = "MasterAdmin"

-- HS256 ヘッダ: {"alg":"HS256","typ":"JWT"} を base64url 化したもの
hs256Header :: BS.ByteString
hs256Header = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"

{- | JWT を発行する。
HS256 アルゴリズムで HMAC-SHA256 署名を付与する。
-}
issue :: JwtSecret -> Claims -> IO Text
issue (JwtSecret secret) claims = do
  let payloadJson = LBS.toStrict (encode claims)
      payloadB64 = b64urlNoPad payloadJson
      signingInput = hs256Header <> "." <> payloadB64
      signature = SHA256.hmac (encodeUtf8 secret) signingInput
      signatureB64 = b64urlNoPad signature
      token = signingInput <> "." <> signatureB64
  case decodeUtf8' token of
    Right t -> pure t
    Left e -> error ("JWT encoding error: " <> show e)

{- | JWT を検証して Claims を復号する。
署名検証失敗、または JSON パース失敗で Left を返す。
-}
verifyAndDecode :: JwtSecret -> Text -> Either Text Claims
verifyAndDecode (JwtSecret secret) token = do
  let parts = T.splitOn "." token
  case parts of
    [headerB64, payloadB64, signatureB64] -> do
      let signingInput = encodeUtf8 (headerB64 <> "." <> payloadB64)
          expectedSig = SHA256.hmac (encodeUtf8 secret) signingInput
          expectedSigB64 = b64urlNoPad expectedSig
          actualSigBs = encodeUtf8 signatureB64
      if actualSigBs == expectedSigB64
        then case B64U.decodeUnpadded (encodeUtf8 payloadB64) of
          Right payloadJson ->
            case eitherDecodeStrict payloadJson of
              Right c -> Right c
              Left err -> Left (T.pack ("payload JSON decode failed: " <> err))
          Left err -> Left (T.pack ("base64 decode failed: " <> show err))
        else Left "signature mismatch"
    _ -> Left "JWT must have 3 parts"

-- | base64url (パディングなし) エンコード。RFC 7519 §3 に従う。
b64urlNoPad :: BS.ByteString -> BS.ByteString
b64urlNoPad = B64U.encodeUnpadded
