{-# LANGUAGE OverloadedStrings #-}

-- | ExceptionListPageApi の hspec-wai 統合テスト (US19/US20, IT7)
module Exception.Interfaces.ExceptionListPageApiSpec (spec) where

import qualified Data.ByteString.Lazy as BSL
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Network.Wai
import Test.Hspec
import Test.Hspec.Wai

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Interfaces.ExceptionListPageApi (exceptionListApp)

emptyRepo :: ExceptionRepository IO
emptyRepo =
  ExceptionRepository
    { saveException = \_ -> pure (Right ())
    , findExceptionById = \_ -> pure Nothing
    , findExceptionsByTrackingNumber = \_ -> pure []
    , updateExceptionResolution = \_ _ -> pure (Right ())
    }

app :: IO Network.Wai.Application
app = pure (exceptionListApp emptyRepo)

bodyContainsText :: T.Text -> MatchBody
bodyContainsText needle =
  MatchBody $ \_ body ->
    let bodyText = TE.decodeUtf8 (BSL.toStrict body)
     in if needle `T.isInfixOf` bodyText
          then Nothing
          else Just ("body does not contain " <> T.unpack needle)

spec :: Spec
spec = describe "ExceptionListPageApi (US19/US20, IT7)" $ do
  describe "GET /exceptions" $
    with app $ do
      it "200 を返す" $
        get "/exceptions" `shouldRespondWith` 200

      it "trackingNumber 未指定時は empty-state を返す" $
        get "/exceptions"
          `shouldRespondWith` 200 {matchBody = bodyContainsText "現在、記録されている輸送例外はありません"}

      it "3 種登録ボタン (Delay/Damage/Loss) を含む" $ do
        get "/exceptions"
          `shouldRespondWith` 200 {matchBody = bodyContainsText "遅延を登録"}
        get "/exceptions"
          `shouldRespondWith` 200 {matchBody = bodyContainsText "破損を登録"}
        get "/exceptions"
          `shouldRespondWith` 200 {matchBody = bodyContainsText "紛失を登録"}

  describe "GET /exceptions?trackingNumber=TR-XXXX" $
    with app $ do
      it "空の findExceptionsByTrackingNumber 結果でも 200 を返す" $
        get "/exceptions?trackingNumber=TR-A1B2C3D4" `shouldRespondWith` 200
