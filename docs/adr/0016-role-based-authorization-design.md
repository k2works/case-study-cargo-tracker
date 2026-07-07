# 0016 Role ベース認可の Domain/Interfaces 分離設計

Role ベース認可を RolePolicy (Domain 純粋関数) と RoleGate (Interfaces
統合ヘルパー) の 2 層に分離し、Servant Combinator による型レベル認可は
採用しない

日付: 2026-07-07

## ステータス

採用 (2026-07-07、IT8 Release 2.0 GA で確立。T7-D 起票 → IT8 レビュー H-05 で採用に更新)

RolePolicy (`7dac8db6`、10 テスト) と RoleGate (`34f663fe`) は IT7 で
実装済。IT8 で `/billing/*` (`canManageBilling` = Accountant/MasterAdmin) と
US17 手動更新 API (`canManualStateUpdate`) に RoleGate を配線し、
本 ADR の設計 (Policy/Gate 2 層分離) が実装で確立した。

## コンテキスト

IT6 の T6-09 (AuthProtect 適用範囲拡張) で Role-based 認可が必要になった。
US17 (手動状態更新) は Tracker/MasterAdmin のみ、US19/US20 (例外登録) は
Handler も含む、といった「どのロールがどの操作をできるか」は業務ルール
であり、以下の設計選択肢があった。

- (A) **Servant Combinator (型レベル)**: `AuthProtect "session-tracker"` の
  ように認可条件をエンドポイント型に埋め込む
- (B) **Policy/Gate 2 層分離**: 判定ロジックを Domain 純粋関数
  (`RolePolicy`)、HTTP 結線を Interfaces ヘルパー (`RoleGate`) に分離し、
  handler 冒頭で明示的に呼ぶ
- (C) **handler 内に直接記述**: 各 handler で `authRoles` を直接検査する

制約:

- 業務ルール (US 由来の権限マトリクス) は SessionAuth の実装詳細から
  独立してユニットテストできる必要がある (arch-check T-03: Domain は
  IO 完全排除)
- IT8 以降も BC 追加のたびに認可対象エンドポイントが増える
  (`/billing/*` は Accountant/MasterAdmin)。追加コストは低く保ちたい

## 決定

**(B) Policy/Gate 2 層分離を採用する。**

- **RolePolicy** (`Cargotracker.Shared.Auth.Domain.RolePolicy`):
  `canManualStateUpdate :: [Role] -> Bool` のような US 単位の純粋述語。
  業務ルールの Single Source of Truth であり、hspec で網羅する
- **RoleGate** (`Cargotracker.Shared.Auth.Interfaces.RoleGate`):
  `requireRoleGate repo lookupRoles nowM policy mCookie` の 1 呼び出しで
  Cookie 認証 (未認証 401) → Policy 判定 (不足 403) を完結させる。
  handler は返された `AuthenticatedUser` で業務処理に集中する
- Servant Combinator 案 (A) は不採用: ロール条件ごとに `AuthProtect`
  タグと `AuthHandler` インスタンスが増殖し、権限マトリクスが型定義に
  分散する。テストも hspec-wai 経由でしか書けない
- handler 直接記述案 (C) は不採用: 判定の重複と漏れ (US17 で実際に
  発生した「配線漏れ」) を招く
- DI (`SessionRepository` / `UserId -> IO [Role]` / `IO UTCTime`) は
  AppDeps レコード (T7-F) 経由で渡し、`IO Text` 系引数の取り違えを防ぐ

## 結果

### 肯定的

- 権限マトリクスが RolePolicy 1 モジュールに集約され、10 件の純粋
  ユニットテストで US17/US19/US20 の業務ルールを網羅済
- 新 BC への適用は「Policy 述語 1 関数 + handler 冒頭 1 行」で済む
  (IT8 で `/billing/*` に `canManageBilling` を追加済)
- 401/403 の区別 (認証 vs 認可) が RoleGate 1 箇所で統一される

### 否定的

- 型レベル保証がないため「handler が RoleGate を呼び忘れる」ことを
  コンパイラは検出しない。CI の RolePolicy 適用検出 grep
  (iteration_plan-7 §CI 統合) と hspec-wai の 403 マトリクステストで補完する
- エラー body が文字列リテラル構築 (T7-N: `Aeson.encode` 化は低優先 backlog)

## 準拠

- ADR-0004 (arch-check): RolePolicy は Domain 層 (IO なし)、RoleGate は
  Interfaces 層に配置し Rule 1-3 に準拠
- ADR-0010 (セッション認証): Cookie → AuthenticatedUser の解決は
  SessionAuth を再利用し、RoleGate はその上に Policy 判定を積む
