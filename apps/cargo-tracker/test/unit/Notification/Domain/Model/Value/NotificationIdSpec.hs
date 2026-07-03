-- | NotificationId 値オブジェクトのテスト (ADR-0013 Phase 2, IT7)
module Notification.Domain.Model.Value.NotificationIdSpec (spec) where

import Test.Hspec

import Cargotracker.Notification.Domain.Model.Value.NotificationId
  ( mkNotificationId,
    notificationIdToText,
  )
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

spec :: Spec
spec = describe "NotificationId (ADR-0013 Phase 2, IT7)" $ do
  it "非空文字列から NotificationId を構築でき Text で取り出せる" $ do
    case mkNotificationId "01234567-89ab-cdef-0123-456789abcdef" of
      Right nid ->
        notificationIdToText nid `shouldBe` "01234567-89ab-cdef-0123-456789abcdef"
      Left err ->
        expectationFailure ("expected Right, got Left " <> show err)

  it "空文字列は InvalidNotificationContent を返す" $
    case mkNotificationId "" of
      Left (InvalidNotificationContent _) -> pure ()
      other -> expectationFailure ("expected Left InvalidNotificationContent, got " <> show other)
