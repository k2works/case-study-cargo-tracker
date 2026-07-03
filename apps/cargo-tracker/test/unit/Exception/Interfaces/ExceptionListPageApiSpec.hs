{-# LANGUAGE OverloadedStrings #-}

-- | ExceptionListPageApi の hspec-wai 統合テスト (US19/US20, IT7)
module Exception.Interfaces.ExceptionListPageApiSpec (spec) where

import qualified Data.ByteString.Lazy as BSL
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import qualified Network.Wai
import Test.Hspec
import Test.Hspec.Wai

import Data.IORef (modifyIORef', newIORef, readIORef)

import Cargotracker.Exception.Application.Ports (ExceptionRepository (..))
import Cargotracker.Exception.Domain.Model.DelayException (mkDelayException)
import Cargotracker.Exception.Domain.Model.ExceptionRecord
  ( ExceptionRecord (..),
    mkExceptionRecord,
  )
import Cargotracker.Exception.Domain.Model.ExceptionSeverity
  ( ExceptionSeverity (..),
    Level (..),
  )
import Cargotracker.Exception.Domain.Model.ExceptionType (ExceptionType (..))
import Cargotracker.Exception.Domain.Model.Reporter (mkReporter)
import Cargotracker.Exception.Interfaces.ExceptionListPageApi (exceptionListApp)
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)

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

  describe "POST /exceptions/:id/resolve" $ do
    with (fmap exceptionListApp repoWithRecord) $
      it "未解決レコードは 303 で /exceptions?flash=resolved に戻る" $
        request "POST" "/exceptions/EX-0001/resolve" [] ""
          `shouldRespondWith` 303 {matchHeaders = ["Location" <:> "/exceptions?flash=resolved"]}

    with app $
      it "存在しないレコードは 303 で ?error=not-found を返す" $
        request "POST" "/exceptions/EX-NONE/resolve" [] ""
          `shouldRespondWith` 303 {matchHeaders = ["Location" <:> "/exceptions?error=not-found&id=EX-NONE"]}

  describe "POST /exceptions/delay (US19)" $
    with app $ do
      it "正常なフォーム値は 303 flash=delay-recorded" $
        request
          "POST"
          "/exceptions/delay"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "exceptionId=EX-D001&trackingNumber=TR000001&delayHours=48&reason=%E6%B8%AF%E6%B9%BE%E9%81%85%E5%BB%B6&severity=HIGH&reporterUserId=user-42&reporterRole=Handler"
          `shouldRespondWith` 303 {matchHeaders = ["Location" <:> "/exceptions?flash=delay-recorded"]}

      it "不正な delayHours は 303 で ?error=invalid-delay-hours" $
        request
          "POST"
          "/exceptions/delay"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "exceptionId=EX-D002&trackingNumber=TR000001&delayHours=abc&reason=%E6%B8%AF%E6%B9%BE&severity=HIGH&reporterUserId=user-42&reporterRole=Handler"
          `shouldRespondWith` 303 {matchHeaders = ["Location" <:> "/exceptions?error=invalid-delay-hours"]}

      it "不正な severity は 303 で ?error=invalid-severity" $
        request
          "POST"
          "/exceptions/delay"
          [("Content-Type", "application/x-www-form-urlencoded")]
          "exceptionId=EX-D003&trackingNumber=TR000001&delayHours=24&reason=%E6%B8%AF%E6%B9%BE&severity=URGENT&reporterUserId=user-42&reporterRole=Handler"
          `shouldRespondWith` 303 {matchHeaders = ["Location" <:> "/exceptions?error=invalid-severity"]}

reportedAt :: UTCTime
reportedAt = UTCTime (fromGregorian 2026 9 28) (secondsToDiffTime 3600)

repoWithRecord :: IO (ExceptionRepository IO)
repoWithRecord = do
  ref <- newIORef initialRecords
  pure
    ExceptionRepository
      { saveException = \_ -> pure (Right ())
      , findExceptionById = \eid -> do
          xs <- readIORef ref
          pure (case [r | r <- xs, erExceptionId r == eid] of (x : _) -> Just x; [] -> Nothing)
      , findExceptionsByTrackingNumber = \_ -> pure []
      , updateExceptionResolution = \eid updated -> do
          modifyIORef' ref (map (\r -> if erExceptionId r == eid then updated else r))
          pure (Right ())
      }
  where
    initialRecords =
      case (mkDelayException 24 "港湾遅延", mkReporter "user-42" "Tracker") of
        (Right d, Right rp) -> case mkExceptionRecord "EX-0001" (Delay d) (ExceptionSeverity High) rp reportedAt "TR000001" of
          Right r -> [r]
          Left _ -> []
        _ -> []
