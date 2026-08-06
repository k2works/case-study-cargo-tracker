{- | ConfirmationCode VO 単体テスト (US16, IT5 task 6.1)

domain-model.md §4 Tracking Context の ConfirmationCode 設計
(mkConfirmationCode / verify / markUsed / maxAttempts) を網羅的に検証する。
-}
module Tracking.Domain.Model.ConfirmationCodeSpec (spec) where

import Data.Either (fromRight)
import Data.Maybe (isNothing)
import Data.Time (UTCTime (..), addUTCTime, fromGregorian, secondsToDiffTime)
import Test.Hspec

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Security.BcryptHash (hashSecret, verifySecret)
import Cargotracker.Tracking.Domain.Model.ConfirmationCode
  ( ConfirmationCode (..),
    isExpiredAt,
    markUsed,
    maxAttempts,
    mkConfirmationCode,
    ttlSeconds,
    verify,
    verifyWith,
  )

sampleNow :: UTCTime
sampleNow = UTCTime (fromGregorian 2026 7 1) (secondsToDiffTime 0)

sampleLater :: UTCTime
sampleLater = UTCTime (fromGregorian 2026 7 2) (secondsToDiffTime 0)

sampleCode :: ConfirmationCode
sampleCode = fromRight (error "unreachable") (mkConfirmationCode sampleNow "123456")

spec :: Spec
spec = describe "Cargotracker.Tracking.Domain.Model.ConfirmationCode (US16)" $ do
  describe "mkConfirmationCode" $ do
    it "6 桁数字を受理する" $ do
      let r = mkConfirmationCode sampleNow "123456"
      r `shouldSatisfy` \case
        Right cc ->
          ccValue cc == "123456"
            && isNothing (ccUsedAt cc)
            && ccAttemptCount cc == 0
        Left _ -> False

    it "5 桁は InvalidConfirmationCodeFormat" $
      mkConfirmationCode sampleNow "12345"
        `shouldBe` Left (InvalidConfirmationCodeFormat "12345")

    it "7 桁は InvalidConfirmationCodeFormat" $
      mkConfirmationCode sampleNow "1234567"
        `shouldBe` Left (InvalidConfirmationCodeFormat "1234567")

    it "非数字文字混在は InvalidConfirmationCodeFormat" $
      mkConfirmationCode sampleNow "12a456"
        `shouldBe` Left (InvalidConfirmationCodeFormat "12a456")

  describe "verify" $ do
    it "正しいコードで Right を返す" $
      verify "123456" sampleCode `shouldSatisfy` \case
        Right cc -> ccValue cc == "123456"
        Left _ -> False

    it "不一致コードで ConfirmationCodeMismatch" $
      verify "999999" sampleCode `shouldBe` Left ConfirmationCodeMismatch

    it "使用済 (ccUsedAt = Just) で ConfirmationCodeAlreadyUsed" $ do
      let used = sampleCode {ccUsedAt = Just sampleLater}
      verify "123456" used `shouldBe` Left ConfirmationCodeAlreadyUsed

    it "attemptCount が maxAttempts で ConfirmationCodeMaxAttemptsExceeded" $ do
      let locked = sampleCode {ccAttemptCount = maxAttempts}
      verify "123456" locked
        `shouldBe` Left (ConfirmationCodeMaxAttemptsExceeded maxAttempts)

    it "attemptCount が maxAttempts を超えた場合も同じ" $ do
      let over = sampleCode {ccAttemptCount = maxAttempts + 3}
      verify "123456" over
        `shouldBe` Left (ConfirmationCodeMaxAttemptsExceeded maxAttempts)

  describe "markUsed" $ do
    it "未使用コードに使用時刻を設定する" $ do
      let m = markUsed sampleLater sampleCode
      ccUsedAt m `shouldBe` Just sampleLater

    it "既に使用済のコードは上書きしない (idempotent)" $ do
      let first = markUsed sampleLater sampleCode
          second = markUsed (UTCTime (fromGregorian 2026 12 31) 0) first
      ccUsedAt second `shouldBe` Just sampleLater

  describe "verifyWith verifySecret (bcrypt モード, T5-02 Phase 3)" $ do
    it "bcrypt ハッシュ保存の ConfirmationCode を平文で検証成功" $ do
      hashed <- hashSecret "123456"
      let cc = sampleCode {ccValue = hashed}
      verifyWith verifySecret sampleNow "123456" cc `shouldSatisfy` \case
        Right c -> ccValue c == hashed
        _ -> False

    it "bcrypt ハッシュ保存で平文が異なれば Mismatch" $ do
      hashed <- hashSecret "123456"
      let cc = sampleCode {ccValue = hashed}
      verifyWith verifySecret sampleNow "999999" cc `shouldBe` Left ConfirmationCodeMismatch

    it "bcrypt モードでも attemptCount 上限は Mismatch より優先" $ do
      hashed <- hashSecret "123456"
      let cc = sampleCode {ccValue = hashed, ccAttemptCount = maxAttempts}
      verifyWith verifySecret sampleNow "123456" cc
        `shouldBe` Left (ConfirmationCodeMaxAttemptsExceeded maxAttempts)

  describe "TTL 境界 (T5-11, IT6)" $ do
    let issued = sampleNow -- 2026-07-01 00:00:00 UTC
        beforeTtl = addUTCTime (ttlSeconds - 1) issued
        atTtl = addUTCTime ttlSeconds issued
        afterTtl = addUTCTime (ttlSeconds + 1) issued
        cc = sampleCode {ccIssuedAt = issued}

    describe "isExpiredAt (純粋境界判定)" $ do
      it "発行時刻ちょうどでは未期限切れ" $
        isExpiredAt issued cc `shouldBe` False

      it "TTL の 1 秒手前では未期限切れ" $
        isExpiredAt beforeTtl cc `shouldBe` False

      it "TTL ちょうどで期限切れ (境界は >= で扱う)" $
        isExpiredAt atTtl cc `shouldBe` True

      it "TTL を過ぎたら期限切れ" $
        isExpiredAt afterTtl cc `shouldBe` True

    describe "verifyWith の TTL 統合" $ do
      let checker input value = input == value
      it "TTL 内の一致入力は Right" $
        verifyWith checker beforeTtl "123456" cc
          `shouldSatisfy` \case
            Right _ -> True
            _ -> False

      it "TTL 切れの一致入力は ConfirmationCodeExpired" $
        verifyWith checker atTtl "123456" cc `shouldBe` Left ConfirmationCodeExpired

      it "TTL 切れでも Mismatch より Expired が優先" $
        verifyWith checker afterTtl "999999" cc `shouldBe` Left ConfirmationCodeExpired

      it "使用済の判定は Expired より優先" $ do
        let usedCc = markUsed sampleLater cc
        verifyWith checker afterTtl "123456" usedCc
          `shouldBe` Left ConfirmationCodeAlreadyUsed

  describe "ttlSeconds" $
    it "24 時間 = 86400 秒" $
      ttlSeconds `shouldBe` 24 * 60 * 60

  describe "maxAttempts" $
    it "定数 5 (SEC-04 準拠)" $
      maxAttempts `shouldBe` 5
