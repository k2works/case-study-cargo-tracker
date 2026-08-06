{-# LANGUAGE OverloadedStrings #-}

-- | ExceptionListView の単体テスト (US19/US20, IT7)
module Exception.Views.ExceptionListViewSpec (spec) where

import qualified Data.ByteString.Lazy.Char8 as LBC
import qualified Data.Text as T
import qualified Data.Text.Encoding as TE
import Data.Time (UTCTime (..), fromGregorian, secondsToDiffTime)
import Lucid (renderBS)
import Test.Hspec

import Cargotracker.Exception.Views.ExceptionListView
  ( ExceptionRow (..),
    exceptionListPage,
  )

render :: [ExceptionRow] -> T.Text
render rs = TE.decodeUtf8 (LBC.toStrict (renderBS (exceptionListPage rs)))

contains :: T.Text -> T.Text -> Bool
contains needle hay = needle `T.isInfixOf` hay

t0 :: UTCTime
t0 = UTCTime (fromGregorian 2026 9 28) (secondsToDiffTime 36000)

t1 :: UTCTime
t1 = UTCTime (fromGregorian 2026 9 29) (secondsToDiffTime 43200)

unresolvedDelay :: ExceptionRow
unresolvedDelay =
  ExceptionRow
    { erRowId = "EX-0001"
    , erRowTrackingNumber = "TR-A1B2C3D4"
    , erRowType = "DELAY"
    , erRowSeverity = "HIGH"
    , erRowReporter = "user-42 (Handler)"
    , erRowReportedAt = t0
    , erRowResolvedAt = Nothing
    }

resolvedDamage :: ExceptionRow
resolvedDamage =
  ExceptionRow
    { erRowId = "EX-0002"
    , erRowTrackingNumber = "TR-Z9Y8X7W6"
    , erRowType = "DAMAGE"
    , erRowSeverity = "CRITICAL"
    , erRowReporter = "user-13 (Tracker)"
    , erRowReportedAt = t0
    , erRowResolvedAt = Just t1
    }

spec :: Spec
spec = describe "ExceptionListView.exceptionListPage (US19/US20, IT7)" $ do
  it "空リスト時は empty-state を表示し、テーブルは表示しない" $ do
    let html = render []
    contains "data-testid=\"empty-state\"" html `shouldBe` True
    contains "data-testid=\"exception-list\"" html `shouldBe` False
    contains "現在、記録されている輸送例外はありません" html `shouldBe` True

  it "3 種登録ボタン (Delay/Damage/Loss) を全て表示する" $ do
    let html = render []
    contains "data-testid=\"record-delay\"" html `shouldBe` True
    contains "data-testid=\"record-damage\"" html `shouldBe` True
    contains "data-testid=\"record-loss\"" html `shouldBe` True
    contains "＋ 遅延を登録" html `shouldBe` True
    contains "＋ 破損を登録" html `shouldBe` True
    contains "＋ 紛失を登録" html `shouldBe` True

  it "行があるとき exception-list テーブルと exception-row が表示される" $ do
    let html = render [unresolvedDelay]
    contains "data-testid=\"exception-list\"" html `shouldBe` True
    contains "data-testid=\"exception-row\"" html `shouldBe` True
    contains "EX-0001" html `shouldBe` True
    contains "TR-A1B2C3D4" html `shouldBe` True
    contains "DELAY" html `shouldBe` True
    contains "HIGH" html `shouldBe` True
    contains "user-42 (Handler)" html `shouldBe` True

  it "未解決レコードには解決ボタンが表示される" $ do
    let html = render [unresolvedDelay]
    contains "data-testid=\"resolve-button\"" html `shouldBe` True
    contains "/exceptions/EX-0001/resolve" html `shouldBe` True
    contains "未解決" html `shouldBe` True

  it "解決済レコードには解決ボタンが表示されない" $ do
    let html = render [resolvedDamage]
    contains "data-testid=\"resolve-button\"" html `shouldBe` False
    contains "/exceptions/EX-0002/resolve" html `shouldBe` False
    contains "解決済" html `shouldBe` True

  it "CRITICAL 重要度は bg-danger バッジで表示される" $ do
    let html = render [resolvedDamage]
    contains "bg-danger" html `shouldBe` True

  it "詳細リンクは各行に対して生成される" $ do
    let html = render [unresolvedDelay, resolvedDamage]
    contains "/exceptions/EX-0001" html `shouldBe` True
    contains "/exceptions/EX-0002" html `shouldBe` True
