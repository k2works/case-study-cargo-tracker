{- | 汎用 bcrypt ハッシュヘルパ (T5-02 Phase 2, SEC-04, IT6)

ConfirmationCode などのシークレットを DB に保存する際の bcrypt ハッシュ化と
検証を提供する。Auth 専用の `BcryptVerifier` と重複するが、Domain 非依存の
Text I/O API に統一することで、非パスワード用途 (確認コード / API キー等) からも
再利用できる。

コスト係数は開発時間短縮のため fast (cost=4) を使用する。本番では環境変数
`BCRYPT_COST` から `slowerBcryptHashingPolicy` (cost=12) に切り替える方針
(BcryptVerifier と同じ、IT3+ で環境変数対応)。

`verifySecret` は入力が bcrypt 形式でなくても例外を投げず False を返す。
これにより DB データ破損時や未 hash 化データ混在時にも防御的に動作する。
-}
module Cargotracker.Shared.Security.BcryptHash
  ( hashSecret,
    verifySecret,
  ) where

import qualified Crypto.BCrypt as BC
import Data.Text (Text)
import Data.Text.Encoding (encodeUtf8)
import qualified Data.Text.Encoding as TE

{- | bcrypt でハッシュ化する。fastBcryptHashingPolicy (cost=4) を使用。

ランダムソルトが含まれるため、同じ平文でも呼び出しごとに異なる出力になる。
-}
hashSecret :: Text -> IO Text
hashSecret plain = do
  let bs = encodeUtf8 plain
  mResult <- BC.hashPasswordUsingPolicy BC.fastBcryptHashingPolicy bs
  case mResult of
    Just hashedBs -> pure (TE.decodeUtf8 hashedBs)
    Nothing -> error "hashSecret: hashPasswordUsingPolicy returned Nothing"

{- | 入力平文と bcrypt ハッシュを比較する。

`Crypto.BCrypt.validatePassword` の内部で bcrypt 独自の定数時間比較を行う。
入力ハッシュが bcrypt 形式でない場合は例外を投げず False を返す
(validatePassword の実装依存だが Bool を保証)。
-}
verifySecret :: Text -> Text -> Bool
verifySecret plain hash =
  BC.validatePassword (encodeUtf8 hash) (encodeUtf8 plain)
