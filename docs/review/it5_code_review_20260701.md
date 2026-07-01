# IT5 開発成果物マルチパースペクティブレビュー

- 実施日: 2026-07-01
- 対象コミット範囲: `59b658c8..HEAD` (34 コミット / 64 ファイル / +4014 行)
- 対象イテレーション: IT5 (US14/15/16/18 + Session Cookie 認証 + Tracking/Handling BC 新規追加)
- レビュー方式: 5 エージェント並列 (xp-programmer, xp-tester, xp-architect, xp-technical-writer, xp-user-representative)

## 総合評価

IT5 は Tracking / Handling BC の新規実装と Session Cookie 認証を **Cross-BC helper (Text-based DTO) と ADR-0004 Rule 4 を軸に規律よく統合**できた重要イテレーション。Domain 純粋性、Smart Constructor、レイヤ分離いずれも高水準。一方で **(1) 認可 middleware 未実装のため /handling などが無認証で叩ける、(2) 確認コードの bcrypt 化 + 定数時間比較 (SEC-04) 未達、(3) verifyAndConsume と saveHandlingActivity の Tx 境界統合が未達で失敗時中間状態が残り得る、(4) Application Command の unit テスト空白**  という 4 つの技術的負債が集中しており、IT6 冒頭で優先対応すべき。ユーザー価値観点でも「引取確認したのに公開追跡に反映されない」「確認コードの配信手段未実装」で US16 の完了条件を実質満たしていない。

## 改善提案 (重要度順)

### 高 (IT6 冒頭で対応すべき)

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| H1 | AuthProtect middleware を IT6 冒頭で実装。IT5 内で最低限 `Shared/Auth/Interfaces/SessionMiddleware.hs` の Ports interface を切っておく | Main.hs:157 / LoginPageApi.hs:69 | architect / user-rep | Session Cookie 発行はできるが消費側 middleware 不在で `/handling` `/bookings` が無認証で叩ける。US15 の完了条件を満たしていない |
| H2 | ConfirmationCode の bcrypt 化 + 定数時間比較 (SEC-04)。`ccValue` を `HashedCode` newtype に、`verify` 内で `constantTimeCompare` | Tracking/Domain/Model/ConfirmationCode.hs:76 | programmer | 現状は Domain 層に平文があり、タイミング攻撃余地。SEC-04 未達 |
| H3 | `verifyAndConsume` + `saveHandlingActivity` の Tx 境界統合 (Outbox / Saga)。ADR-0012 起票 | Handling/Application/VerifyClaimAndRegisterCommand.hs:56 | programmer | 現状は別 Tx で「コード消費済だが Claim 未登録」の中間状態が残り得る |
| H4 | Handling → Tracking の状態反映 (Claim 登録時に TransportStatus を TsClaimed に更新) | Handling/Application/RegisterHandlingEventCommand.hs | user-rep | US16 で引取確認しても US18 公開追跡に反映されない。「引取済なのに追跡上は未引取」は最悪の UX |
| H5 | 確認コード配信手段 (US26) の暫定対応。管理者ビューにコード表示 or 電話口頭伝達運用の注記 | ClaimFormView.hs (US16) | user-rep | 配信手段なしでは US16 受入基準「荷受人がコードを入力できる」の前提が成立しない |
| H6 | Tracking BC の Application Command テスト (`IssueTrackingNumberCommand`, `IssueConfirmationCodeCommand`, `VerifyClaimAndRegisterCommand`) を IORef fakeRepo で追加 | test/unit/Tracking/Application/ (空白) | tester | Domain は堅いが Application 側の attempt_count +1 永続化・lock 到達を verify する層がない |
| H7 | BookingPageApiSpec の Confirm/Cancel/Link/Unlink テストを IORef で `updateBooking` 引数捕捉して副作用検証 | test/unit/Booking/Interfaces/BookingPageApiSpec.hs:453-563 | tester | 現状は 303 と Location しか見ておらず、ハンドラが Command を呼ばなくても green (mutation escape) |
| H8 | README に環境変数・Cookie 早見表を追加 (`cargo_session`, `sessionTtlSeconds=28800`, `E2E_SKIP_SETUP`, `E2E_TRUNCATE_PUBLIC`) | README.md | tech-writer | 運用者がソースを読まないと動作を把握できない |
| H9 | ADR-0010 の段階移行計画を修正 (AuthProtect middleware は IT6 実装、IT5 は Cookie 発行のみ) | docs/adr/0010-session-cookie-auth.md:56 | tech-writer | iteration_plan と ADR で task 対応が食い違い |
| H10 | 予約 ID 手入力の代わりに datalist 候補提示 (直近入港予定貨物) | HandlingFormView.hs:35-43 | user-rep | 現場での誤入力 (CLAIM を LOAD と誤る等) の温床 |

### 中 (IT6 中盤までに)

| # | 提案 | 箇所 | 指摘元 | 理由 |
|---|------|------|--------|------|
| M1 | `HandlingPageApi.handlerClaimPost` の 5 分岐 case を `domainErrorToFlash :: DomainError -> Text` に純粋関数抽出 | HandlingPageApi.hs:183-192 | programmer | DRY / handler 縮小 |
| M2 | Session 発行失敗時の空 Cookie を `err500 + flash` に変更 (現状は Set-Cookie: cargo_session=; となる) | LoginPageApi.hs:129-136 | programmer | 意図を型/挙動で明示 |
| M3 | `PublicTrackingApi.handlerSearch` の `Just tn` 分岐を 302 で `/public/tracking/:tn` へ redirect (URL 正規化) | PublicTrackingApi.hs:64-66 | programmer | ブラウザ履歴・SEO |
| M4 | ConfirmationCode の期限切れ (TTL) 境界テスト追加 | test/unit/Tracking/Domain/Model/ConfirmationCodeSpec.hs | tester | 期限判定が Domain/Application どちらにも見当たらず仕様が浮いている |
| M5 | Composition Root (Main.hs rootApp) を `Module` 型に refactor し BC ごとに `wire :: Connection -> Application` 化 | app/Main.hs:152-188 | architect | 15 パス + 9 Repo で God router 化しつつある |
| M6 | ADR-0012 (TransportStatus SSoT / Handling BC の Booking Domain 参照ポリシー) 起票 | docs/adr/0012-*.md 未起票 | architect / tech-writer | 実装 (arch-check H-01) は既にあるのに ADR が未起票 |
| M7 | Hedgehog property test を `hspec-hedgehog` の `hedgehog` combinator に置き換え shrink 反例を hspec レポートに出す | test/unit/Booking/Domain/Model/State/BookingStatusPropertiesSpec.hs:42-46 | tester | 現状は `check` + `assertTrue` 橋渡しで shrink 情報がロスト |
| M8 | POST /login → Session Cookie 発行の hspec-wai 統合テスト追加 (TTL 28800, HttpOnly, SameSite=Lax の境界検証) | test/unit/Shared/Auth/Interfaces/LoginPageApiSpec.hs | tester | fakeSessionRepo だけ渡されているが挙動テストがない |
| M9 | hspec-wai の日本語 body assertion を `TE.decodeUtf8 . BSL.toStrict . simpleBody` + `T.isInfixOf` に統一 | test/unit/Booking/Interfaces/BookingPageApiSpec.hs:214-217, 293, 411, 442 | tester | memory (`hspec-wai-japanese-assertions`) の feedback と一致 |
| M10 | Handling BC の unit テスト追加 (`HandlingType` Enum/parse, `HandlingActivity` 集約不変条件) | test/unit/Handling/ (空白) | tester | アイスクリームコーン兆候 |
| M11 | 荷役登録の `eventType` に応じた `voyage_number` の必須切替を JS 無しでも UI に反映 | HandlingFormView.hs:78-86 | user-rep | LOAD/UNLOAD のみ voyage_number 必須が UI に出ない |
| M12 | 公開追跡ページから予約 ID 露出を再検討 (マスクか非表示) | PublicTrackingView.hs:66 | user-rep | 追跡番号を知る第三者に予約 ID まで見せると攻撃面拡大 |
| M13 | 荷役登録の `operatorName` をフォーム入力からセッション由来に変更 | HandlingFormView.hs:87-95 | user-rep | 監査で使えるオペレータ特定に必要 |
| M14 | `hevCompletionTime` を「2026-07-01 14:30 JST」形式で整形して表示 | PublicTrackingView.hs:103 | user-rep | ISO 生形式は顧客向けに不適 |
| M15 | CHANGELOG.md `[Unreleased]` を「予定」から実績ベースの Added/Changed/Deprecated に更新 | CHANGELOG.md:8-36 | tech-writer | Keep a Changelog 準拠、v0.3.0 リリース時の手戻り防止 |

### 低 (時間があれば)

| # | 提案 | 箇所 | 指摘元 |
|---|------|------|--------|
| L1 | `TrackingView.tvStatusText` / `tvStatus` の View 都合の両持ちを整理 | QueryTrackingByNumberQuery.hs:44 | programmer |
| L2 | `Session` Domain に `usernameFromEmail :: Email -> Username` の smart constructor | LoginPageApi.hs:123 | programmer |
| L3 | `handlingPageApp` の Repo 引数を `HandlingModuleDeps` レコード化 | Main.hs:169 | architect |
| L4 | TrackingNumber VO の形式検証 spec 追加 | test/unit/Tracking/Domain/Model/Value/ | tester |
| L5 | ジャーナルの主要コミット表を「iter 番号 → 得られた学び」対応表に変更し再利用性向上 | docs/journal/20260701.md:186-208 | tech-writer |
| L6 | HandlingType.hs に UI ラベルとの対応 Haddock 追加 | HandlingType.hs | tech-writer |
| L7 | US18 検索フォームに追跡番号形式 (TR プレフィックス + 6 文字) を例示 | PublicTrackingView.hs | user-rep |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| C1 | tester「globalSetup は setup 時 1 回 TRUNCATE、worker 並列で fixture 競合」 | (現状 fullyParallel: false, workers: 1) | 並列化時の isolation | IT6 で workers>1 化する時に worker 単位で schema 分離 (`E2E_SCHEMA_${WORKER_ID}`) を検討 |
| C2 | programmer「LoginPageApi の Set-Cookie 空文字は err500 化」 | user-rep「モバイル/複数タブの Cookie 運用を先に検証」 | 発行失敗時 UX | 現状の 303 + 空 Cookie は UX 上デッドロックを避けるための妥当な妥協。IT6 で AuthProtect middleware 導入時に合わせて err500 統一 |

## 対応方針

- **修正する (IT6 冒頭)**: H1 / H2 / H3 / H4 / H6 / H7 / H8 / H9 / H10
- **修正する (IT6 中盤)**: M1〜M15
- **許容する (IT7 以降)**: L1〜L7、C1
- **保留 (要別途 ADR)**: H5 (US26 メール配信の代替運用は US26 と一体で意思決定)

## エージェント別フィードバック件数

| エージェント | 高 | 中 | 低 | 主要指摘 |
|:---|:---:|:---:|:---:|:---|
| xp-programmer | 2 | 3 | 2 | SEC-04 (bcrypt) 未達、Tx 境界統合、Servant handler DRY |
| xp-tester | 2 | 4 | 2 | Application Command テスト空白、mutation escape、Handling unit 0 件 |
| xp-architect | 2 | 3 | 1 | Composition Root 膨張、AuthProtect middleware 未実装、ADR-0012 未起票 |
| xp-technical-writer | 2 | 2 | 2 | README 環境変数未記載、CHANGELOG 予定/実績混在 |
| xp-user-representative | 3 | 4 | 1 | US16 配信手段未実装、Claim → TsClaimed 未反映、予約 ID 手入力 |

## 次のアクション

1. IT5 完了報告書 (creating-iteration-report) で本レビュー結果を「IT6 引継ぎ」に反映
2. IT6 計画 (planning-releases --iteration 6) で高優先度指摘 H1-H10 をタスク化
3. ADR-0012 (Tx 境界 / Handling → Tracking 状態反映) 起票
