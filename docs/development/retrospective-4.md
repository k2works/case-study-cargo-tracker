# IT4 ふりかえり (KPT)

## 概要

| 項目 | 内容 |
| --- | --- |
| イテレーション | IT4 |
| 期間 | 2026-06-30 (Ralph Loop 18 反復 + クロージング + E2E 修復 + ADR 起票 + ふりかえり) |
| 計画 SP | 20 (本体 11 + IT3 繰越 7 + 拡張 2) |
| 実績 SP | 19 完了 / 1 IT5 繰越 (達成率 95%) |
| テスト | 443 examples / 0 failures / 10 pending (Haskell hspec) + hedgehog 18 プロパティ × 100 = 1,800 ケース + Playwright E2E 19 passed / 1 skipped |
| コミット数 | 約 30 (IT3 末以降、レビュー対応含む) |
| 新規 ADR | 3 件 (0007 CancellationPolicy / 0008 Itinerary+Leg / 0009 BookingStatus 状態機械 SSoT) |
| HPC カバレッジ | 74.89% (gate 74% / target 75%) |

実績内訳:

- **本体完了 (11 SP)**: US08b (3) / US09 (3) / US11 (2) / US13 (3) すべて Domain + Application + UI レイヤ完成
- **IT3 繰越完了 (4 SP)**: U-04 arch-check Phase 2 Rule 6 / Phase 3 T-01/T-02/T-03 (shell ベース実装で CI gate 化)
- **拡張完了 (1 SP)**: U-15 HPC ゲート 70 → 74 段階引き上げ
- **IT5 繰越 (1 SP)**: U-08 Playwright (Browser 必要) / U-12 testcontainers (Docker) / WM-01 WireMock (Docker) / HTTP ハンドラ Servant 結線 / セッション Cookie 配線

---

## Keep (継続すべき良かったこと)

### 技術面

- **Phase A (純粋ドメイン) を最優先で完了**: CancellationPolicy.calculate / RouteEvaluator.evaluate / Itinerary / BookingStatus 状態機械を IO 非依存の純粋関数として実装し、hedgehog プロパティテストで境界値を網羅。Ralph Loop の高速反復と TDD が相性抜群
- **ADR-0004 Cross-BC 規約の徹底**: Estimation BC を Routing BC の VoyageNumber / UnLocode から完全独立化し、Text 識別子で Cross-BC 連携。arch-check Rule 4 違反 0 件を維持
- **hedgehog プロパティテスト 12 件追加 (600 ケース)**: CancellationPolicy 6 プロパティ + RouteEvaluator 6 プロパティ。境界値・整合性・恒等性を網羅し、リファクタの安全網を確立
- **shell ベース arch-check Phase 2/3 採用**: haskell-src-exts AST バイナリではなく grep ベースで Rule 6 + T-01/T-02/T-03 を即座に CI gate 化。haskell-src-exts への昇格は IT5 で再検討 (ROI ベース判断)
- **マルチパースペクティブレビュー → 即時リファクタの往復**: H-01 (canTransitionTo SSoT 違反) + M-01 (5 Command execute 同型重複) を同日中にリファクタ。Cargo.hs 161 行 → 119 行、Command 群 +113/-215 行
- **withCargo 共通ヘルパで新 Command の追加コストを 5 → 1 に縮減**: load → transition → save パターンを Ports.hs に集約。次の Command 追加時は 1 行 execute で済む

### プロセス面

- **Ralph Loop 18 反復で AI 完結タスク消化 → end-of-life 自然遷移**: 第 18 反復まで価値の高いタスクを連続消化し、外部依存タスクのみ残った時点で `/ralph-loop:cancel-ralph` で計画的終了 ([[feedback_ralph-loop-end-of-life]] 反復改善)
- **2 段運用 (Ralph 中の self-review + 完了後の正式 developing-review) が機能**: IT3 確立パターンを IT4 でも踏襲。本セッションでは IT4 完了後のマルチパースペクティブレビューで 22 件を抽出し、H-01/M-01/H-03 を即時対応
- **GitHub Issue 同期を Ralph Loop 中ではなく完了後にまとめて実施**: コメント記入と Close を 1 回のセッションで完結。Issue オーバーヘッドを最小化
- **iteration_plan-4.md を IT3 と同等の詳細度に拡充 (1034 行)**: PlantUML + 型定義 + DDL + シーケンス図 + Tx 境界 + hedgehog プロパティ + CI 統合を網羅。後続イテレーションの参照価値が高い
- **E2E 4 件失敗 → 19/20 passed まで根気よく root cause 追跡**: dbmate migration 未適用 / option value 大文字小文字 / DB seed 蓄積 / セッション未配線 — 全 4 件を独立に解明し記録
- **ADR 3 件を完了後に起票**: ADR-0007 (CancellationPolicy 採用) / 0008 (Itinerary+Leg 提案) / 0009 (Booking 状態機械 SSoT 採用)。iteration_plan-4 からの参照リンク切れを即解消

### ストーリー実装

- **本体 4 ストーリー全 Domain + Application + UI 完成**: US08b/US09/US11/US13 すべて GitHub Issue Close。Postgres 永続化と HTTP ハンドラ結線は IT5 残るが、業務ロジックは完成
- **CancellationFee 3 段階ルール (Free/Partial/Full) の境界値完全網羅**: 7 日 / 1 日 / 当日の boundary を例ベース 8 件 + hedgehog 6 プロパティ (600 ケース) で網羅。`expect(168h) = Free` / `expect(24h) = Partial` / `expect(23h) = Full` 等の business 仕様を機械検証
- **BookingStatus 7 状態 × 7 ペア = 49 ペアの遷移網羅**: canTransitionTo 純粋関数で 10 許可 / 39 拒否を全網羅。新 status 追加時の修正箇所が 1 関数のみ

---

## Problem (問題・改善すべき点)

### スコープ管理

- **「外部依存タスクのみ残る」状態が予期可能だった**: U-08 (Browser) / U-12 (Docker testcontainers) / WM-01 (Docker WireMock) は IT4 計画策定時点で Ralph Loop 適性外と判別可能だった。IT3 retrospective の T3-11 (Ralph 適性で分類) が IT4 計画に反映されておらず、終盤の no-op 反復は発生しなかったものの IT5 繰越が予期せぬ 1 SP として残った
- **HTTP ハンドラ Servant 結線の優先順位ミス**: Application + View が揃っているのに HTTP 結線が後回し。受入条件 Gherkin の E2E が手動検証不能な状態が長期化。Phase C を「UI 着手前に最小 HTTP 結線」に分割すべきだった
- **iteration_plan-4 で「ADR-0007/0008/0009 提案予定」と明記したが本体未作成のまま実装**: リンク切れ状態で実装が先行。事後追認の ADR となり、設計判断の根拠が空中浮遊する期間が発生 (developing-review H-03 で指摘)

### コード品質 (developing-review で顕在化)

- **状態遷移ルールの Single Source of Truth 違反** (H-01): `canTransitionTo` がテストでしか使われず、Cargo の遷移関数群がパターンマッチで状態判定を二重定義。本セッションで即時リファクタ (commit 08eecbba) で解消したが、新状態追加時の乖離リスクを IT4 中に検出できていれば不要な技術的負債を防げた
- **5 Application Command の execute 同型コード** (M-01): 65 行の機械的重複。本セッションで `withCargo` 共通ヘルパに抽出 (commit 08eecbba)。3 個目の Command 実装時 (LinkRouteCommand) で重複に気付くべきだったが、Ralph Loop の高速反復で見過ごした
- **`CancelBookingInput.inputDepartureTime :: Maybe UTCTime` 型安全性不足** (H-05): Confirmed なのに `Nothing` を渡せば Free になる runtime バグの温床。Itinerary 永続化を後回しにした暫定 API がそのまま残った
- **ADR-0004 Text 化が BC 内部まで広がりすぎ** (M-03 / C-02): RouteEvaluator/EvaluateRouteCandidatesCommand が全面 Text。Estimation BC 内部ですら型化されておらず、BC 内部の型安全性まで犠牲。BC 境界のみ Text、内部は型化が原則

### テスト品質

- **HTTP ハンドラ Servant 結線未完で E2E のロール別認可シナリオが検証不能** (H-04 / H-02): hspec-wai 統合テストが IT4 でゼロ件。E2E (Playwright) で navigation-lists が常に未認証 navbar をレンダリングし、IT3 U-07 実装の実機検証が完了していなかったことが本セッションの E2E 実行で初めて判明
- **Lucid view テストの T.isInfixOf 依存** (M-04): リファクタ耐性が低い。Bootstrap クラス変更や属性順入替で false positive が発生する設計。`hasClass` / `hasAttr` ヘルパまたは html-conduit パースへの移行が必要
- **CancellationFee VO の直接単体テスト欠落** (M-07): Service spec 経由の間接カバーのみ。VO は不変条件の砦のため独立 spec が必要
- **HPC カバレッジ 75% target 未達** (74.89%): あと 23 expressions 不足。BookingStatus Enum/Bounded テスト追加で +0.1pt 改善したが target 到達せず。HTTP ハンドラ追加で Phase B 着手時に +1% 回収する想定

### プロセス品質

- **E2E が IT3 完了時に実行されておらず IT4 開始時に root cause 不明の失敗 5 件**: dbmate migration 011 未適用 (IT3 完成タスク) は IT3 で E2E 実行していれば即座に検出されていた。IT4 では本セッションで明示的に実行するまで誰も気付かなかった
- **DB seed データ蓄積が test isolation を破壊**: JPTYO/USNYC ペアに 23 件 voyage が積もり、US08a 候補 top 5 から新規 voyage が押し出される。E2E 専用 schema または truncate fixture が必要
- **ghcid stderr アクセス不能でデバッグ非効率**: `/bookings/{id}` 500 の根本原因 (customs_declaration テーブル不在) を特定するまで、ghcid を停止して stack exec を foreground で起動する必要があった。構造化ログ (katip) を全例外ハンドラに導入することで開発体験が改善する

### ドキュメント品質

- **iteration_report-4.md / retrospective-4.md が IT4 完了時点で未作成 (本ドキュメントで解消)**: IT1-IT3 で確立した慣習を破ると、ベロシティ実績データの蓄積が途切れ計画精度が低下する
- **arch-check Rule 番号の欠番 (Rule 5)**: 1-4 + 6 で Rule 5 が欠番。意図 (元 Rule 5 を統合した経緯等) を明示するか連番化が必要
- **5 Command 共通パターンの集約ドキュメント不在** (L-03): 6 個目の Command 追加時に各 Haddock を個別に書く非効率。`docs/development/application-command-pattern.md` への集約が必要

---

## Try (次に試すこと)

### スコープ管理

| ID | アクション | 期待効果 |
| :-- | :-- | :-- |
| T4-01 | IT5 計画策定時にタスクを「AI 完結可」「Browser 必要」「Docker 必要」「人手作業」で分類し、Ralph Loop で消化可能な範囲のみを Ralph 対象にする | 終盤の予期せぬ繰越削減 (T3-11 を IT5 で確実に実装) |
| T4-02 | Phase 配分を「Domain → Application → 最小 HTTP 結線 → UI」に変更し、UI 着手前に手動 E2E 検証可能な状態を作る | 受入条件の早期検証 + フィードバックサイクル短縮 |
| T4-03 | ADR は「提案予定」段階で先に空ファイル + テンプレート起票し、実装と並行して内容を充填する | リンク切れ防止 + 設計判断の根拠が常に参照可能 |

### コード品質

| ID | アクション |
| :-- | :-- |
| T4-04 | 同型コードを 3 つ以上書く前に共通ヘルパへの抽出を検討する (Rule of Three の半分で警戒) |
| T4-05 | `Maybe` でドメイン制約を表現する API は型 sum type への移行を計画化する (H-05 を IT5 で解消) |
| T4-06 | ADR-0004 Text 化規約を改訂し「BC 境界のみ Text、BC 内部は型化」を明示 (M-03) |
| T4-07 | 新 Domain 関数追加時に既存 SSoT (canTransitionTo 等) を呼び出すか確認するチェックリストを developing-review に追加 |

### テスト品質

| ID | アクション |
| :-- | :-- |
| T4-08 | IT5 で hspec-wai 統合テスト最低 5 本 (Confirm/Cancel/Link/Unlink/EvaluateRoute) を導入し HTTP ハンドラ結線の自動検証を実現 (H-04) |
| T4-09 | Lucid view テストヘルパ `hasClass` / `hasAttr` を新設、または html-conduit パースに移行 (M-04) |
| T4-10 | CancellationFee VO 単体テスト 5-6 件を追加 (M-07) |
| T4-11 | 49 ペア網羅テストを `forAll allStatusPairs` で property 化し N² 爆発を回避 (L-04) |
| T4-12 | HPC カバレッジ gate を 74 → 75% に引き上げ (HTTP ハンドラ追加で +1% 回収の前提) |

### プロセス品質

| ID | アクション |
| :-- | :-- |
| T4-13 | IT 完了時の checklist に「E2E 全件パスを確認」「dbmate status で migration 適用状況を確認」を追加 |
| T4-14 | E2E 専用 schema (cargo_tracker_e2e) + truncate fixture を IT5 で導入し test isolation を確立 |
| T4-15 | 構造化ログ (katip) を Servant 例外ハンドラに導入し、500 エラーの root cause を stderr ではなく構造化ログで取得可能化 |
| T4-16 | ALLOWLIST (Rule 4 / Rule 6 / T-01+T-02) に sunset 日付コメントを必須化 (M-02) |

### ドキュメント品質

| ID | アクション |
| :-- | :-- |
| T4-17 | 各 Command の Haddock を `docs/development/application-command-pattern.md` (新規) に集約し、個別 Haddock は差分のみ記述 (L-03) |
| T4-18 | arch-check Rule 番号を整理 (Rule 5 欠番の意図明示 or 連番化) (L-05) |
| T4-19 | v0.2.0 CHANGELOG / Release Note のドラフトを IT5 着手時に起票し、ストーリー完了ごとに追記 |

---

## メトリクス

| 指標 | IT1 | IT2 | IT3 | IT4 | 推移 |
| :-- | --: | --: | --: | --: | :-- |
| 計画 SP | 13 | 18 | 29 | 20 | 安定範囲 (18-20 SP) |
| 実績 SP | 20 | 18 | 22 | 19 | 平均 19.75 SP/IT |
| 達成率 | 154% | 100% | 76% | 95% | IT3 の繰越過多を IT4 で解消 |
| テスト数 (hspec) | 約 90 | 207 | 300 | 443 | +143 (IT4 で 47% 増) |
| hedgehog プロパティ | 0 | 0 | 6 | 18 | IT4 で 3 倍に拡張 |
| コミット数 | 11 | 24 | 48 | 約 30 | ペース安定 |
| 新規 ADR | 1 | 1 | 3 | 3 | 累計 8 件 (0001-0009、0003 欠番) |
| HPC カバレッジ (全体) | 約 50% | 62% | 70% | 74.89% | gate 74% 達成、target 75% は IT5 で |
| arch-check Rule 違反 | 6 | 6 | 0 | 5 (ALLOWLIST 化) | Rule 4 完全解消、Rule 6 + T-01/T-02 既存 5 件は IT5 段階解消 |

ベロシティ (実績 SP / 期間):

- IT1: 20 SP / 1 日 Ralph Loop
- IT2: 18 SP / 1 日 Ralph Loop
- IT3: 22 SP / 1 日 Ralph Loop (51 反復、繰越 7 SP 込み)
- IT4: 19 SP / 1 日 Ralph Loop (18 反復)

4 IT 通算で **「Ralph Loop 1 日 = 19-22 SP」が安定値**。IT5 計画は **20 SP を基準** とし、外部依存タスクは Ralph Loop 対象外として明確に分離する。

---

## IT4 で得られた知見の memory への追加候補

- **dbmate migration 適用は本番デプロイ + E2E パイプラインの前提条件**: IT3 で追加された migration 011 が dev 環境に未適用のまま残っていた。IT 完了 checklist に `dbmate status` を組み込む
- **E2E test isolation には専用 schema または truncate fixture が必須**: DB seed データ蓄積が「new fixture が top N に入らない」という再現性のないテスト失敗を引き起こす
- **Servant の例外貫通で 500 になる UX 問題**: SqlException が ServerError に変換されず "Something went wrong" で出る。グローバル例外ハンドラ + 構造化ログ (katip) が IT5 改善候補

---

## 関連ドキュメント

- [IT4 計画](iteration_plan-4.md)
- [IT4 マルチパースペクティブレビュー](../review/it4_code_review_20260630.md)
- [ADR-0007 CancellationPolicy](../adr/0007-cancellation-fee-policy.md)
- [ADR-0008 Itinerary + Leg モデル](../adr/0008-itinerary-leg-model.md)
- [ADR-0009 Booking 状態機械 SSoT](../adr/0009-booking-state-machine.md)
- [リリース計画](release_plan.md)
