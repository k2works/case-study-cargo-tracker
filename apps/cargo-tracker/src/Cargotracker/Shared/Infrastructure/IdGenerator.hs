{- | ID 自動採番ユーティリティ (T-07, IT2 / H-02, IT3)

ShipperId / BookingId のような業務 ID をサーバ側で生成する。
英数字大文字 6 桁ランダムを `<PREFIX>-XXXXXX` の形式で組み立てる。

IT1 ではフォームでユーザが手入力していたが、業務上の誤予約温床
となるため自動採番に切り替える (retrospective P-7)。

衝突確率 (H-02 で誕生日パラドックス補正):

36^6 = 約 21.8 億通り。誕生日パラドックスで近似すると、N 件登録時の
衝突確率は概ね N(N-1) / (2 * 36^6)。

- 1,000 件: 約 2.3 × 10^-4 (0.023%)
- 10,000 件: 約 0.023 (2.3%)
- 100,000 件: 約 2.3 (実質確定衝突)

単発 attempt の確率 (1 / 21.8 億) ではなく累積確率で評価する必要がある。
本実装は DB の UNIQUE 制約 (data-model.md: shipper.shipper_code,
cargo.booking_id) で最終防御し、上位レイヤで挿入失敗時の再試行を行う。

実運用 (IT5+) では DB のサロゲートキー (BIGSERIAL) を使う方が確実だが、
業務キーはユーザ向け表示用なので当面は random 採番で運用する。
-}
module Cargotracker.Shared.Infrastructure.IdGenerator
  ( generateBookingIdText,
    generateShipperIdText,
    generateSessionTokenText,
    generateTrackingNumberText,
    generateNotificationIdText,
    generateSixDigitCodeText,
    intToAlphaNumChar,
  ) where

import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.UUID as UUID
import qualified Data.UUID.V4 as UUIDv4
import System.Random (randomRIO)

-- | "BK-A1B2C3" のような ID 文字列を生成する。
generateBookingIdText :: IO Text
generateBookingIdText = do
  body <- randomAlphaNum 6
  pure ("BK-" <> body)

-- | "SHP-A1B2C3" のような ID 文字列を生成する。
generateShipperIdText :: IO Text
generateShipperIdText = do
  body <- randomAlphaNum 6
  pure ("SHP-" <> body)

{- | "TRA1B2C3D" のような 8 文字英数大文字の追跡番号を生成する (US14, IT5)。
domain-model.md §4 と一致 (先頭 "TR" + 続く 6 文字は英数大文字ランダム)。
-}
generateTrackingNumberText :: IO Text
generateTrackingNumberText = do
  body <- randomAlphaNum 6
  pure ("TR" <> body)

{- | ADR-0013 Phase 2/3: UUID v4 の Text 表現を生成する (Notification の
サロゲート識別子用)。`Data.UUID.V4.nextRandom` を利用して衝突確率を
実質ゼロにする。出力例: "550e8400-e29b-41d4-a716-446655440000"
-}
generateNotificationIdText :: IO Text
generateNotificationIdText = UUID.toText <$> UUIDv4.nextRandom

{- | T7-01 (IT7): 6 桁数字の確認コード (Text 表現) を生成する。
`IssueConfirmationCodeCommand` の inputCodeText に渡す前提。
先頭 0 を許容するため `T.justifyRight 6 '0'` でパディングする。
-}
generateSixDigitCodeText :: IO Text
generateSixDigitCodeText = do
  n <- randomRIO (0 :: Int, 999999)
  pure (T.justifyRight 6 '0' (T.pack (show n)))

{- | 44 文字のセッショントークンを生成する (task 1.2, ADR-0010, IT5)。

真の base64url ではなく英数大文字 44 文字で代用 (IT5 段階の簡略実装)。
IT6 で crypto-random + base64url に置換予定。
-}
generateSessionTokenText :: IO Text
generateSessionTokenText = randomAlphaNum 44

randomAlphaNum :: Int -> IO Text
randomAlphaNum n = do
  chars <- mapM (const randomAlphaNumChar) [1 .. n]
  pure (T.pack chars)

randomAlphaNumChar :: IO Char
randomAlphaNumChar = do
  -- 36 通り (0-9 + A-Z) から 1 文字を一様サンプル
  i <- randomRIO (0, 35 :: Int)
  pure (intToAlphaNumChar i)

{- | 0-35 の Int を英数大文字 1 字に変換する total 関数 (H-02)。

`alphaNumTable !! i` の partial 性を排除するため、`toEnum` ベースの
直接計算に置換した。範囲外 (i < 0 or i > 35) はガード句で '0' に丸める
(`randomRIO (0, 35)` を介す本実装では到達不能)。
-}
intToAlphaNumChar :: Int -> Char
intToAlphaNumChar i
  | i < 0 = '0'
  | i < 10 = toEnum (fromEnum '0' + i)
  | i < 36 = toEnum (fromEnum 'A' + i - 10)
  | otherwise = '0'
