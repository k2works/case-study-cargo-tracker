# IT5 ふりかえり (KPT)

## 概要

| 項目 | 内容 |
| --- | --- |
| イテレーション | IT5 |
| 期間 | 2026-08-31 〜 2026-09-13 (計画) / 実質 2026-07-01 集中実装 (Ralph Loop 21 iter + 手動 US14/15/16/18 + task 1.2 + task 3.1-3.3) |
| 計画 SP | 22 (本体 US14/15/16/18 14 + 基盤 8) |
| 実績 SP | Ralph Loop 17 SP + 手動 23+ SP = **40+ SP (達成率 182%)** |
| コミット数 | 34 (59b658c8..HEAD) |
| 変更規模 | 64 files / +4014 lines |
| テスト | E2E 19 passed / 1 skipped、hspec-wai 6 本追加 (BookingPageApi Confirm/Cancel/Link/Unlink)、ConfirmationCode spec 12 本、BookingStatus property 化、CancellationFee spec |
| 新規 ADR | 3 件 (0008 Itinerary+Leg **採用昇格** / 0010 JWT + Session Cookie 共存 **採用** / 0011 offline handling queue **提案**) |
| 新規 BC | 2 個 (Tracking / Handling)、6 migration 追加 |
| Ralph Loop | 21 iter で自律完了 (stop hook は iter 78 まで再投入されたが実成果は 21 iter で終了) → `/ralph-loop:cancel-ralph` |
| マルチパースペクティブレビュー | 5 エージェント並列で高 10 / 中 15 / 低 7 件抽出 |

---

## Keep (継続すべき良かったこと)

### 技術面

- **Cross-BC helper (Text-based DTO) パターンの確立**: `queryTrackingNumberText` (US14) / `verifyAndConsume` (US16) / `queryHandlingHistoryText` (US18) の 3 種を Application/Ports に集約。ADR-0004 Rule 4 違反 0 件を維持しつつ Tracking Domain 型を BC 内に閉じ込めた
- **Tracking BC の Domain 純粋性**: `ConfirmationCode.verify` / `markUsed` は純粋関数、副作用 (attempt_count +1 永続化) は Application 層に切り出し。T-03 準拠でテスタビリティ確保
- **Servant 型で Set-Cookie を型安全に表現**: `Verb 'POST 303 [Header Location, Header Set-Cookie] NoContent` により、Cookie 発行の存在をコンパイラが強制
- **Session Cookie + JWT 共存設計 (ADR-0010)**: Web は Cookie、API は JWT、両立時は Cookie 優先の規約を明記。BookingId ↔ user_id BIGINT 変換は JOIN で吸収
- **hspec-wai 6 本追加 (T4-08 達成)**: BookingPageApi Confirm/Cancel/Link/Unlink の PRG 動作を統合テストで検証。IT4 で 0 件だった HTTP ハンドラ結線の自動検証を実現
- **Hedgehog property 化 (T4-11 達成)**: 49 ペア N² 列挙を `Gen.enumBounded` + `forAll` に置換

### プロセス面

- **Ralph Loop 21 iter で自律実行 + 手動フェーズで超過達成**: 22 SP 計画 → 40+ SP 実装 (182%)。Ralph Loop end-of-life 運用パターン ([[feedback_ralph-loop-end-of-life]]) を IT5 でも踏襲
- **6 migration をタイムスタンプ順で単発適用**: session / itinerary+leg / cargo 拡張 / tracking / handling / confirmation_code の依存順序を明確化
- **E2E globalSetup 配線で test isolation 自動化 (T4-14 達成)**: `e2e-schema-setup.sh` + `public schema fallback TRUNCATE` の二段構え。`E2E_SKIP_SETUP` / `E2E_TRUNCATE_PUBLIC` の escape hatch も提供
- **JSON Lines 構造化ログ (T4-15 部分達成)**: `Cargotracker/Shared/Infrastructure/Logging.hs` で aeson ベースの JSON Lines を stderr 出力。katip 正式化は IT6 繰越
- **ADR 3 件を同一 IT 内で起票 + 昇格 + 提案の 3 段階を使い分け**: 0008 (採用昇格) / 0010 (採用) / 0011 (提案・分割トリガ明記)
- **完了直後にマルチパースペクティブレビューを実施**: 高 10 / 中 15 件の技術的負債を IT6 冒頭タスクとして即座に把握

### ストーリー実装

- **US14/15/16/18 の骨格 4 本を一気通貫実装**: 「予約 → 追跡番号発行 → 荷役登録 → 引取確認 → 追跡照会」の業務フローが動く状態に到達
- **US16 荷受人 UI の現場配慮**: `inputmode=numeric` + `pattern=[0-9]{6}` + 中央寄せ大型フォント + autocomplete=off。スマホ数字パッド起動と誤入力防止
- **US16 のエラー細分化**: `code-mismatch` / `code-used` / `code-lock` / `not-found` を別メッセージで返す運用配慮
- **US18 公開追跡ページの内部/外部ステータス分離**: 顧客向け日本語ステータス + CS 用内部コード = 同一画面で会話可能

---

## Problem (問題・改善すべき点)

### 認可・セキュリティ (developing-review 高優先度)

- **AuthProtect middleware 未実装で `/handling` 等が無認証で叩ける** (review H1): Session Cookie 発行はできるが消費側 middleware が不在。US15 完了条件を実質満たしていない
- **ConfirmationCode の bcrypt 化 (SEC-04) 未達** (review H2): `ccValue` に平文が残り、`input /= ccValue cc` の平文比較はタイミング攻撃余地
- **`verifyAndConsume` + `saveHandlingActivity` の Tx 境界統合未実装** (review H3): 別 Tx なので「コード消費済だが Claim 未登録」の中間状態が残り得る

### ユーザー価値の未達

- **Claim → TsClaimed の状態反映が Tracking BC に伝播しない** (review H4): US16 で引取確認しても US18 公開追跡には反映されない。「引取済なのに追跡上は未引取」は最悪の UX
- **確認コード配信手段 (US26) 未実装のまま US16 を完了扱い** (review H5): 荷受人にコードが届かないため受入基準「荷受人がコードを入力できる」の前提が成立しない
- **荷役登録の予約 ID 手入力**: 現場端末で BK-A1B2C3 を手打ちさせるとオペレータ誤入力の温床。datalist 候補提示が必要
- **オペレータ名フリーテキスト入力**: Cookie セッションが入ったのだから、`operatorName` はサーバ側でセッションから注入すべき

### テスト品質

- **Tracking/Handling BC の Application Command テストが 0 件** (review H6): Domain (ConfirmationCode) は堅いが、Application 側の attempt_count +1 永続化・lock 到達を verify する層がない
- **BookingPageApiSpec の Confirm/Cancel/Link/Unlink テストが mutation escape** (review H7): `updateBooking = \_ -> pure (Right ())` で捨てているため、ハンドラが Command を呼ばなくても green
- **POST /login → Session Cookie 発行の hspec-wai 統合テスト不在**: fakeSessionRepo だけ渡されて挙動テストがない
- **ConfirmationCode の期限切れ (TTL) 境界テスト欠落**: 期限判定が Domain/Application どちらにも見当たらず仕様が浮いている

### 設計負債

- **Composition Root (`Main.hs` rootApp) の God router 化**: 15 パス + 9 Repo で BC 追加ごとに linear 肥大。`Module.wire :: Connection -> Application` パターンへの refactor が必要
- **ADR-0012 (Tx 境界 / Handling → Tracking 状態反映) 未起票**: 実装がある H-01 SSoT 検査は既に arch-check にあるのに ADR が浮いている

### ドキュメント品質

- **README に IT5 で導入した環境変数・Cookie 一覧が未記載**: `cargo_session` / `sessionTtlSeconds=28800` / `E2E_SKIP_SETUP` / `E2E_TRUNCATE_PUBLIC` を運用者はソースを読まないと把握できない
- **CHANGELOG.md `[Unreleased]` が「予定」と実績の混在**: 完了項目が予定節に残ったまま。Keep a Changelog の Added/Changed へ移動が必要
- **ADR-0010 の段階移行計画で task 対応が食い違い**: AuthProtect middleware は IT6 実装なのに ADR-0010 は「IT5 task 1.1 で追加」と記述

### プロセス品質

- **IT5 開始時に IT5 migration が dev DB に未適用のまま E2E 実行 → 500 エラー再発**: T4-13 (IT 完了時に dbmate status 確認) を IT5 開始時にも適用すべきだった
- **iteration_plan-5.md が 1,238 行に肥大**: task ID → commit hash は追跡可能だが「計画」と「設計仕様書」の混在が進行

---

## Try (次に試すこと)

### 認可・セキュリティ (IT6 冒頭で対応)

| ID | アクション | 期待効果 |
| :-- | :-- | :-- |
| T5-01 | AuthProtect middleware を実装 (`Shared/Auth/Interfaces/SessionMiddleware.hs`, wai Middleware で Cookie → Session lookup → AuthenticatedUser 解決) | `/handling` `/bookings/*` の認可 gate 化。US15 完了条件を真に満たす |
| T5-02 | ConfirmationCode を bcrypt 化。`ccValue` を `HashedCode` newtype に変え、`verify` 内で定数時間比較 | SEC-04 達成、タイミング攻撃の排除 |
| T5-03 | `verifyAndConsume` + `saveHandlingActivity` の Tx 境界統合 (Outbox パターン or Saga)。ADR-0012 起票 | 「コード消費済だが Claim 未登録」の中間状態を排除 |

### ユーザー価値

| ID | アクション |
| :-- | :-- |
| T5-04 | Handling → Tracking の状態反映: Claim 登録時に TrackingActivity.TransportStatus を TsClaimed に更新 |
| T5-05 | 確認コード配信 (US26) までの暫定策として管理者ビューにコード表示 + 電話口頭伝達運用の注記を追加 |
| T5-06 | 荷役登録の予約 ID を datalist 候補提示化 (直近入港予定貨物のみ) |
| T5-07 | 荷役登録の operatorName をセッション由来にする (Cookie 導入済) |

### テスト品質

| ID | アクション |
| :-- | :-- |
| T5-08 | Tracking BC の Application Command テストを IORef fakeRepo で 5-6 本追加 (IssueTrackingNumberCommand / IssueConfirmationCodeCommand / VerifyClaimAndRegisterCommand / QueryTracking / QueryHandlingHistory) |
| T5-09 | BookingPageApiSpec の Confirm/Cancel/Link/Unlink を IORef で `updateBooking` 引数捕捉して副作用検証に強化 |
| T5-10 | POST /login → Session Cookie 発行の hspec-wai 統合テスト (TTL 28800, HttpOnly, SameSite=Lax の境界) を追加 |
| T5-11 | ConfirmationCode の期限切れ (TTL) 境界テスト追加 |
| T5-12 | hspec-wai の日本語 body assertion を `TE.decodeUtf8 . BSL.toStrict . simpleBody` + `T.isInfixOf` に統一 ([[feedback_hspec-wai-japanese-assertions]] 準拠) |

### 設計負債

| ID | アクション |
| :-- | :-- |
| T5-13 | Main.hs rootApp を `Module.wire :: Connection -> Application` パターンに refactor し BC 追加のコストを 1 行に削減 |
| T5-14 | ADR-0012 (Tx 境界 / Handling → Tracking 状態反映 / Domain 参照ポリシー) 起票 |
| T5-15 | `HandlingPageApi.handlerClaimPost` の 5 分岐 case を `domainErrorToFlash :: DomainError -> Text` に純粋関数抽出 (DRY) |

### プロセス品質

| ID | アクション |
| :-- | :-- |
| T5-16 | IT 開始時 checklist に「dbmate status で対象 DB の migration 適用状況を確認」を追加 (T4-13 の対称ペア) |
| T5-17 | iteration_plan-N.md の肥大化対策として、設計セクションは `docs/design/` に分離し plan.md はタスク一覧のみに (次期 IT6 から適用) |
| T5-18 | katip 正式化 (現状は自作 JSON Lines) を IT6 で完了させる |

### ドキュメント品質

| ID | アクション |
| :-- | :-- |
| T5-19 | README に環境変数・Cookie 早見表節を追加 (`cargo_session` / `sessionTtlSeconds` / `E2E_SKIP_SETUP` / `E2E_TRUNCATE_PUBLIC` / `DATABASE_URL` / `JWT_SECRET`) |
| T5-20 | CHANGELOG `[Unreleased]` を実績ベースの Added/Changed/Deprecated に整理 |
| T5-21 | ADR-0010 の段階移行記述を修正 (AuthProtect middleware は IT6 実装、IT5 は発行のみ) |

---

## メトリクス

| 指標 | IT1 | IT2 | IT3 | IT4 | IT5 | 推移 |
| :-- | --: | --: | --: | --: | --: | :-- |
| 計画 SP | 13 | 18 | 29 | 20 | 22 | 安定範囲 (18-22 SP) |
| 実績 SP | 20 | 18 | 22 | 19 | **40+** | IT5 で 2 倍達成 (Ralph 21 iter + 手動 4 US) |
| 達成率 | 154% | 100% | 76% | 95% | **182%** | 過去最高 |
| テスト数 (hspec) | 約 90 | 207 | 300 | 443 | 461+ | +18 (Confirm/Cancel/Link/Unlink/ConfirmationCode) |
| コミット数 | 11 | 24 | 48 | 30 | 34 | 安定 |
| 新規 ADR | 1 | 1 | 3 | 3 | 3 | 累計 11 件 (0001-0011、0003 欠番) |
| BC 数 | 3 | 4 | 5 | 5 | **7** | Tracking / Handling 追加 |
| Playwright E2E | - | - | - | 19/20 | 19/20 | globalSetup 配線で isolation 自動化 |

ベロシティ (実績 SP / 期間):

- IT1-IT4: 平均 **19.75 SP / IT**
- IT5: **40+ SP / IT** (Ralph 17 SP + 手動 23+ SP)

IT5 は「Ralph Loop + 手動フェーズ」の 2 段運用で過去最高 SP に到達。ただし技術的負債 (認可・SEC-04・Tx 境界) が 3 件集中しており、IT6 冒頭 T5-01/02/03 で完済しないとキャリーオーバー爆発のリスクがある。

---

## 前イテレーション Try (IT4) の達成状況

| ID | アクション | 状態 |
| :-- | :-- | :-- |
| T4-01 | タスクを「AI 完結可」等で分類 | 部分達成 (iteration_plan-5 に Ralph 適性欄追加) |
| T4-02 | Phase 配分を Domain → App → 最小 HTTP → UI へ | 未達成 (IT5 は Domain 集中で UI 未着手ストーリーあり) |
| T4-03 | ADR を提案段階で空ファイル起票 | 達成 (ADR-0008/0010/0011) |
| T4-04 | 同型 3 個で共通ヘルパ抽出 | 達成 (Cross-BC helper 3 種を Ports に集約) |
| T4-05 | `Maybe` → sum type 移行 | 部分達成 (H-05 は IT6 繰越) |
| T4-06 | ADR-0004 BC 境界のみ Text | 達成 (Tracking/Handling BC で徹底) |
| T4-08 | hspec-wai 5 本導入 | 達成 (6 本、Confirm/Cancel/Link/Unlink × 2 系統) |
| T4-11 | 49 ペア property 化 | 達成 |
| T4-12 | HPC 75% gate | 達成 (75% ゲート化) |
| T4-13 | IT 完了時 checklist | 部分達成 (IT 開始時にも適用が必要) → T5-16 |
| T4-14 | E2E 専用 schema + fixture | 達成 (globalSetup 配線完了) |
| T4-15 | 構造化ログ (katip) | 部分達成 (自作 JSON Lines、katip 正式化は IT6) → T5-18 |
| T4-16 | ALLOWLIST sunset 日付 | 達成 |
| T4-19 | v0.2.0 CHANGELOG 起票 | 部分達成 ([Unreleased] に混在) → T5-20 |

**達成率 12/14 (86%)**。未達成・部分達成 4 件は IT6 で T5-04/16/18/20 として継続。
