-- | RolePolicy のテスト (T6-09, IT7)
module Shared.Auth.Domain.RolePolicySpec (spec) where

import Test.Hspec

import Cargotracker.Shared.Auth.Domain.RolePolicy
  ( canManageBilling,
    canManualStateUpdate,
    canRecordException,
    canResolveException,
  )
import Cargotracker.Shared.Auth.Domain.User (Role (..))

spec :: Spec
spec = describe "RolePolicy (T6-09, IT7)" $ do
  describe "canManualStateUpdate (US17)" $ do
    it "Tracker は許可" $
      canManualStateUpdate [Tracker] `shouldBe` True

    it "MasterAdmin は許可" $
      canManualStateUpdate [MasterAdmin] `shouldBe` True

    it "Handler は拒否" $
      canManualStateUpdate [Handler] `shouldBe` False

    it "Shipper / Consignee / Sales / Router / Accountant は拒否" $
      any (canManualStateUpdate . (: [])) [Shipper, Consignee, Sales, Router, Accountant]
        `shouldBe` False

    it "空リストは拒否" $
      canManualStateUpdate [] `shouldBe` False

    it "複数ロールで 1 つでも許可があれば True" $
      canManualStateUpdate [Handler, Tracker] `shouldBe` True

  describe "canRecordException (US19/US20)" $ do
    it "Handler / Tracker / MasterAdmin は許可" $ do
      canRecordException [Handler] `shouldBe` True
      canRecordException [Tracker] `shouldBe` True
      canRecordException [MasterAdmin] `shouldBe` True

    it "Shipper は拒否" $
      canRecordException [Shipper] `shouldBe` False

  describe "canResolveException (US19/US20)" $ do
    it "Tracker / MasterAdmin は許可" $ do
      canResolveException [Tracker] `shouldBe` True
      canResolveException [MasterAdmin] `shouldBe` True

    it "Handler は拒否 (解決は監督者判断)" $
      canResolveException [Handler] `shouldBe` False

  describe "canManageBilling (US23, IT8)" $ do
    it "Accountant / MasterAdmin は許可" $
      all (canManageBilling . (: [])) [Accountant, MasterAdmin] `shouldBe` True

    it "Shipper / Consignee / Sales / Router / Tracker / Handler は拒否" $
      any (canManageBilling . (: [])) [Shipper, Consignee, Sales, Router, Tracker, Handler]
        `shouldBe` False

    it "空リストは拒否" $
      canManageBilling [] `shouldBe` False
