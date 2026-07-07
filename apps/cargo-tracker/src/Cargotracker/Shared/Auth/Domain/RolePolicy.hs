{- | Role-based ポリシー (T6-09, IT7)

各 BC / エンドポイントごとに「どのロールがアクセスできるか」を判定する
純粋関数群。Interfaces 層は本モジュールを import してリクエストの
Role 群と付き合わせ、403 / 200 を決める。

Domain 層に置く理由: 業務ルール (US17 は Tracker/MasterAdmin のみ、
US19/US20 は Handler も含む、等) を SessionAuth の実装詳細から
独立させ、ユニットテストしやすくする。
-}
module Cargotracker.Shared.Auth.Domain.RolePolicy
  ( canManualStateUpdate,
    canRecordException,
    canResolveException,
    canManageBilling,
  ) where

import Cargotracker.Shared.Auth.Domain.User (Role (..))

{- | US17 手動状態更新の権限判定。
Tracker / MasterAdmin のみ許可 (Handler は 403)。
-}
canManualStateUpdate :: [Role] -> Bool
canManualStateUpdate = any isAllowed
  where
    isAllowed Tracker = True
    isAllowed MasterAdmin = True
    isAllowed _ = False

{- | US19/US20 例外登録の権限判定。
Handler / Tracker / MasterAdmin を許可 (現場作業員も遅延 / 破損 /
紛失を第一報できる)。
-}
canRecordException :: [Role] -> Bool
canRecordException = any isAllowed
  where
    isAllowed Handler = True
    isAllowed Tracker = True
    isAllowed MasterAdmin = True
    isAllowed _ = False

{- | US19/US20 例外解決の権限判定。
Tracker / MasterAdmin のみ許可 (解決は監督者判断が必要)。
-}
canResolveException :: [Role] -> Bool
canResolveException = any isAllowed
  where
    isAllowed Tracker = True
    isAllowed MasterAdmin = True
    isAllowed _ = False

{- | US23 精算管理 (請求書発行・入金確認) の権限判定 (IT8)。
Accountant / MasterAdmin のみ許可 (ui_design.md 画面一覧の利用者ロール)。
-}
canManageBilling :: [Role] -> Bool
canManageBilling = any isAllowed
  where
    isAllowed Accountant = True
    isAllowed MasterAdmin = True
    isAllowed _ = False
