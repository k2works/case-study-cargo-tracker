# コードレビュー結果 - Ralph Loop 2 週目 (iter 1-8)

## レビュー対象

- **期間**: 2026-07-03 Ralph Loop 2 週目 iter 1-8
- **コミット範囲**: `e9a3dc5c..6edb4463` (Haskell 変更のみ 4 コミット、+222/-7 / 7 ファイル)
- **主要変更**:
  - T7-01: `HandlingPageApi.handlerPost` UNLOAD → `IssueConfirmationCodeCommand` 発火
  - T6-09: `RolePolicy` (Domain 純粋関数) + `RoleGate` (Interfaces ヘルパー)
  - T6-07: `newCorrelationId` (UUID v4)
  - `generateSixDigitCodeText` (6 桁確認コード生成)

## 総合評価

Ralph Loop 2 週目の 4 主要機能は **アーキテクチャ規約 (ADR-0002 Rule 4/6, ADR-0013 DI パターン) を遵守** し、Domain 純粋関数を軸としたテスト容易な構造で実装されている。ただし **Interfaces 層 (RoleGate / handler UNLOAD 分岐) のテストが薄く**、また **RolePolicy 未配線により US17 手動更新が実質的に無認可でアクセス可能** な状態がユーザー影響の観点で重大なリスクとして指摘された。

## 改善提案 (重要度順)

### 高 (マージ / リリース前に対応すべき)

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 1 | US17 手動状態更新 API に RoleGate を先行配線するか、または当該エンドポイントをフィーチャーフラグで一時無効化 | `ManualUpdatePageApi.hs` | user-representative | 現状 Tracker/MasterAdmin 限定の意図が API に反映されず、監査・コンプライアンス上受け入れ不可 |
| 2 | UNLOAD 完了時の確認コード通知チャネル (US26) 未接続。単体では業務価値ゼロなので US26 とセット納品を計画 | `HandlingPageApi.hs` | user-representative | 荷受人がコードを受け取れず引取フローが機能しない |
| 3 | `generateSixDigitCodeText` の hedgehog プロパティテスト追加 (常に長さ 6 かつ全て数字) | `IdGenerator.hs` | tester | 先頭 0 パディングは典型的な欠陥混入ポイント。0/5/99999/999999 の境界値も明示的に検証 |
| 4 | `handlerPost` UNLOAD 分岐の副作用検証テスト追加 (fake `ConfirmationCodeRepository` を注入し、`Unload` の時のみ `IssueCode.execute` が呼ばれ、冪等性が保証されることを spy で確認) | `HandlingPageApi.hs:158` | tester | Cross-BC 発火は業務仕様の核心。Postgres 依存を理由にせず Application 層まで hspec で検証すべき |

### 中 (対応推奨)

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 5 | `handlingPageApp` の DI 引数 8 個を `AppDeps` レコードにまとめる | `HandlingPageApi.hs:113` | programmer | 可読性・拡張性向上。`IO Text` 2 つ (`genNid` / `genCode`) の取り違えリスクも軽減 (technical-writer 提案の newtype ラップも代替案) |
| 6 | UNLOAD 分岐を `case ht of Unload -> ...; _ -> pure success` から `when (ht == Unload)` に置換 | `HandlingPageApi.hs:158` | programmer | DRY (成功レスポンスの重複除去) |
| 7 | `RoleGate` の 3 分岐 (401/403/200) に hspec テストを追加 (fake `SessionRepository` で純粋にテスト可能) | `RoleGate.hs` | tester | 未認証と権限不足を取り違えるバグは典型的 |
| 8 | ADR-0016 として「Role ベース認可の Domain/Interfaces 分離」設計判断を起票 | `docs/adr/` | technical-writer | 今後の BC 追加時に判断根拠が必要 |
| 9 | Cross-BC 境界の `Application Input record は Text-only を維持` を ADR-0002 に追記 | `docs/adr/0002-*.md` | architect | 現状は `IssueConfirmationCodeInput` が Text 構成で実質準拠だが、明文化されていないと将来 Domain 型混入時に Rule 4 違反リスク |

### 低 (改善の余地あり)

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| 10 | `RolePolicy.hs` の 3 関数で重複する `isAllowed` パターンを `hasAnyRole :: [Role] -> [Role] -> Bool` に抽象化 | `RolePolicy.hs` | programmer | DRY |
| 11 | `RoleGate.hs:47` の JSON エラー body を `Aeson.encode` で構築 | `RoleGate.hs` | programmer | 型安全性 |
| 12 | 403 メッセージを `"insufficient role"` → `"insufficient permissions"` に変更 | `RoleGate.hs:47` | technical-writer | UI 表示時の意味明確化 |
| 13 | `requireRoleGate` → `requireRole` にリネーム (Servant 慣習) | `RoleGate.hs` | technical-writer | 簡潔化 (Gate はモジュール名で表現済) |
| 14 | `withNewCorrelationId :: IO a -> IO a` ヘルパーで `newCorrelationId` + `withCorrelationId` を統合 | `Logging.hs` | programmer | 呼出パターンの定型化 |
| 15 | `RolePolicy` テストをテーブル駆動 (`forM_` で全 `Role` を列挙) に | `RolePolicySpec.hs` | tester | 新 `Role` 追加時の抜けを検知しやすい |
| 16 | `generateSixDigitCodeText` の 100 万通り衝突リスクを ConfirmationCodeCommand 側の一意制約で確認 | `IssueConfirmationCodeCommand.hs` | architect | US26 のスケールでは衝突可能 |

## 矛盾事項

矛盾する指摘なし。5 エージェントは以下の点で一致:

- Domain 層に純粋関数として `RolePolicy` を配置した設計は妥当
- ADR-0002 Rule 4/6 は遵守されている
- 実装は tested と untested の境界がはっきりしており、追加テストで埋めやすい

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer (高: 0 / 中: 2 / 低: 3)</summary>

- 良い点: Domain 純粋関数 + Interfaces 統合の SRP、`justifyRight 6 '0'` の意図明確、Rule 6 遵守
- 改善: DI 引数 8 個の `AppDeps` レコード化、`when (ht == Unload)` 化、`isAllowed` 抽象化、`Aeson.encode` 化、`withNewCorrelationId` ヘルパー

</details>

<details>
<summary>xp-tester (高: 2 / 中: 1 / 低: 1)</summary>

- 良い点: `RolePolicy` 10 テストは境界網羅、`newCorrelationId` 2 テストで形式 + 衝突
- 改善: **`generateSixDigitCodeText` の hedgehog プロパティ**、**`handlerPost` UNLOAD 分岐の副作用 spy**、`RoleGate` 3 分岐テスト、`RolePolicy` テーブル駆動化。Interfaces 層のテストが薄く「アイスクリームコーン化」の兆候

</details>

<details>
<summary>xp-architect (高: 0 / 中: 1 / 低: 2)</summary>

- 良い点: `IO Text` DI が ADR-0013 と一貫、Domain 純粋関数 + Interfaces ヘルパーの構造は変更容易性を確保
- 改善: Cross-BC Application Input を Text-only 維持する明文化 (ADR-0002 追記)、`generateSixDigitCodeText` の衝突懸念、`lookupRoles` の DI 点を ADR 化

</details>

<details>
<summary>xp-technical-writer (高: 0 / 中: 2 / 低: 3)</summary>

- 良い点: Haddock は充実、iteration_plan/release_plan は整合
- 改善: **ADR-0016 起票 (Role ベース認可の Domain/Interfaces 分離)**、newtype ラップで `IO Text` 取り違え防止、`requireRole` リネーム、403 メッセージ改善

</details>

<details>
<summary>xp-user-representative (高: 2 / 中: 1 / 低: 0)</summary>

- 良い点: UNLOAD 自動化は現場負担軽減、権限モデルは業界慣行に整合、コード平文非保存 (SEC-04)
- 改善: **US17 手動更新の RoleGate 未配線は監査 / コンプライアンス上受け入れ不可**、**UNLOAD 時のコード通知 (US26) 未接続で業務価値ゼロ**

</details>

## 対応方針

### 即時対応 (次イテレーション先頭)
- 高 #1 US17 の RoleGate 先行配線 (最低 1 API のみでも)
- 高 #3 `generateSixDigitCodeText` の hedgehog プロパティ (30 分程度で追加可能)
- 高 #4 UNLOAD 分岐の副作用テスト (fake Repository で 1 時間程度)

### 計画的対応 (IT7 完了までに)
- 高 #2 US26 通知チャネル接続 (別イテレーション、UI 込みで規模大)
- 中 #5-9 DI レコード化 / ADR 起票

### 承認 / 見送り
- 低 #10-16 は継続的な改善候補として backlog に追加、優先度低

## 保存先

`docs/review/ralph-loop-week2_review_20260703.md`
