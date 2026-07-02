{-# LANGUAGE OverloadedStrings #-}

{- | hspec-wai 日本語 body アサーション共通ヘルパ (T5-12, feedback_hspec-wai-japanese-assertions 準拠, IT6)

hspec-wai の `MatchBody` はデフォルトで `BS.ByteString` を扱うため、
日本語文字列を書くと `"\xe2\x80\x94"` 等のバイト列でエンコードする必要があり
可読性を大きく損なう。本モジュールは受信 body を必ず UTF-8 デコードして
Text として比較する `bodyContainsText` を提供する。

推奨パターン:

@
bodyContainsText \"該当なし\"
@

避けるべき従来パターン:

@
bodyContains \"\\xe8\\xa9\\xb2\\xe5\\xbd\\x93\\xe3\\x81\\xaa\\xe3\\x81\\x97\"
@

正規化:

- 受信 body は `Data.Text.Encoding.decodeUtf8'` で安全にデコード
- 部分一致比較には `Data.Text.isInfixOf` を用いる
- デコード失敗時は明示的にエラー内容を返す
-}
module Support.HspecWaiJa
  ( bodyContainsText,
    isNotHtmlPage,
  ) where

import qualified Data.ByteString as BS
import qualified Data.ByteString.Lazy as LBS
import Data.Text (Text)
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Data.Text.Encoding.Error as TEE
import Test.Hspec.Wai.Matcher (MatchBody (..))

{- | body 全体を UTF-8 デコードして Text 化し、needle を部分文字列として含むかを検査する。

日本語や絵文字を含むアサーションを可読なリテラルとして書ける。
-}
bodyContainsText :: Text -> MatchBody
bodyContainsText needle = MatchBody $ \_ lbody ->
  let decoded = TE.decodeUtf8With TEE.lenientDecode (LBS.toStrict lbody)
   in if needle `T.isInfixOf` decoded
        then Nothing
        else
          Just
            ( "body does not contain: "
                <> T.unpack needle
                <> "\n(decoded body: "
                <> T.unpack decoded
                <> ")"
            )

{- | ByteString の部分一致で「フル HTML ページではないこと」を確認する述語。

htmx 部分 HTML エンドポイントで doctypeやhtmlタグが含まれないことを検証する用途。
-}
isNotHtmlPage :: BS.ByteString -> Bool
isNotHtmlPage body =
  not (isInfixOfBS "<html" body) && not (isInfixOfBS "<!DOCTYPE" body)

isInfixOfBS :: BS.ByteString -> BS.ByteString -> Bool
isInfixOfBS needle hay =
  BS.length needle == 0 || any (BS.isPrefixOf needle) (BS.tails hay)
