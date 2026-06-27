# IT2 マルチパースペクティブレビュー結果 (2026-06-27)

## レビュー対象

- 範囲: IT1 完了コミット `ebb537dc` 以降 (IT2 全成果物、24 + 5 コミット、本体 4/4 + Try 10/10 + 一覧画面 + E2E + 完了報告/ふりかえり)
- ファイル: 91 files changed, +5385 / -186
- テスト: 207 unit / 0 failures / 10 pending、hedgehog 6 props、E2E 15/15、HPC 62%
- 実施: `developing-review` スキルで XP 5 エージェント並列レビュー

## 総合評価

5 視点を統合すると **B+ (合格、IT3 で要是正)**。Domain 層 (sum type 化、Either DomainError、純粋関数化、ACL ライト版) と TDD 規律 (0 failures × 21 反復、テストファースト) は IT1 から明確に前進し、A 評価相当。一方で **業務ユーザ視点では「US01 UI 不在 + US06 Submit ボタン不在 + ロール認可未実装」で内部デモが実質困難**、テスト視点では **integration / 認可 / Domain 別カバレッジが薄く砂時計型**、設計視点では **ALLOWLIST 運用と Booking エラーの Shared 配置が技術的負債を放置するリスク**、ドキュメント視点では **ベロシティ表記が 3 通りに分かれ読者を混乱させる**、コード視点では **`submitBooking` のエラー型不整合と `IdGenerator` の partial 関数** が本日対応必須。IT3 Week 1 序盤に高優先度 7 件を返済すれば Release 0.1 Internal Alpha のストーリーが成立する。

## 重要度別サマリー

| 重要度 | 件数 | 対応期限 |
| :--- | ---: | :--- |
| 高 (本日 / IT3 Week 1 序盤) | 12 | 当日コミット or IT3 Day 1-3 |
| 中 (IT3 着手前 〜 IT3 内) | 14 | IT3 Week 1-2 |
| 低 (IT3 内 〜 IT4) | 12 | IT3 内 or IT4 計画 |

## 改善提案 (重要度順)

### 高 (マージ前 / IT3 Week 1 序盤に対応)

| # | 提案 | 箇所 | 指摘元 | 理由 |
| ---: | :--- | :--- | :--- | :--- |
| H-01 | `submitBooking` のエラー型を `InvalidStateTransition` に修正 (現状 `ConcurrentModification (unShipperId ...)` で `requestRouting` と不整合) | `Booking/Domain/Model/Cargo.hs:75` | programmer | 状態遷移エラーが楽観ロックエラーに化けると UI 文言と監査ログが誤誘導 |
| H-02 | `IdGenerator.hs:43` の `alphaNumTable !! i` を partial 関数除去 + `UNIQUE` 制約 + retry ループ追加。誕生日パラドックス補正済の衝突確率コメントに修正 | `Shared/Infrastructure/IdGenerator.hs:43` | programmer | 22 億通りでも 1 万件で 0.002% 衝突。本番採番の安全性を上げる |
| H-03 | US06 Draft → Submitted の Submit ボタンを実装 (`Booking.Application.SubmitBookingCommand` + `BookingShowView` の Draft 状態時ボタン) | `Booking/Interfaces/BookingPageApi.hs`, `Booking/Views/BookingShowView.hs`, 新規 `SubmitBookingCommand.hs` | user-rep | 「予約はできるが渡せない」は内部デモで説明不能 |
| H-04 | US01 `/estimates/new` フォーム + 候補表示 + 「この見積で予約する」リンク (IT3 Week 1 最優先) | 新規 `Estimation/Interfaces/EstimatePageApi.hs` + Lucid View | user-rep | 営業担当者の主要業務が API でしか実行できない状態を解消 |
| H-05 | US04+US05 危険物予約フォーム動的フィールド (htmx) を最低限 (UN番号 / 引火点 / 温度帯) で実装 | `Booking/Views/BookingFormView.hs`, htmx `/bookings/new/cargo-type-row` | user-rep | 営業の差別化機能。API のみは経営層への説明が破綻 |
| H-06 | ロール認可の最低実装 (営業 / 経路設計者 / マスタ管理者 / 荷受人 / 追跡管理者 等のメニュー出し分け + URL 直叩き 403) | Layout.hs / 各 PageApi handler | user-rep, architect | コンプライアンス視点で監査破綻リスク。T-13 の認証必須フロー統合テストも同時に追加 |
| H-07 | `BookingNotFound` / `InvalidStateTransition` を `Cargotracker.Booking.Domain.Error` に分離 (Shared から移動)。ADR を起票して BC 境界規約を確定 | `Shared/Domain/DomainError.hs` | architect, programmer | Booking 固有エラーが Shared に常駐すると BC 境界が崩れる。新規 BC 追加時に判断が割れる |
| H-08 | HPC カバレッジゲートを「全体 60%」から「変更モジュール 80% or Domain 層 85% の層別」に変更 | `scripts/check-coverage.sh`, `.github/workflows/ci.yml` | architect, tester | 62% で gate 60% は実質無効。新規コードの品質を担保できない |
| H-09 | ベロシティ表記の不一致を是正: `iteration_report-2.md` (180%) / `retrospective-2.md` (100%) / `release_plan.md` (180%) — 「計画 10 (本体) + Try 8 = 18、純粋達成率 100%、当初本体目標 10 比 180%」を 3 ドキュメント全てに統一注記 | 3 ドキュメント | technical-writer | 同じ数字 3 解釈で読者を確実に混乱させる |
| H-10 | リリースノート `v0.1.0-alpha` ドラフトを `docs/release/` 配下に作成。エンドユーザ言語に翻訳 (新規 BC の技術用語ではなく「危険物予約」「航海更新」など業務語) | 新規 `docs/release/v0.1.0-alpha.md` + `CHANGELOG.md` | technical-writer | タグ付け前提条件。「sum type で型レベル排除」はユーザ向けではない |
| H-11 | DATABASE_URL pending 10 件を testcontainers-hs で常時実行化、各 pending テストの本来カバレッジを一覧化 | `test/integration/*Spec.hs`, CI | tester | pending は「書いた気になる」最も危険な状態。設計問題 (Repository が DB 抽象に閉じていない) の表面化 |
| H-12 | US25 ロールバック integration test (実 DB で Voyage 更新中の例外で `carrier_movement` 部分更新が発生しないことを保証) | 新規 `test/integration/Routing/Infrastructure/UpdateRollbackSpec.hs` | tester | Application unit のみではトランザクション境界の整合性は検証不能 |

### 中 (IT3 着手前 〜 IT3 内)

| # | 提案 | 箇所 | 指摘元 |
| ---: | :--- | :--- | :--- |
| M-01 | `PostgresBookingRepository.hs` の 14-tuple 重複を `type CargoRow = (...)` + `rowToCargo :: CargoRow -> Cargo` で DRY 化 (findCargo / listCargos 共通化) | `Booking/Infrastructure/PostgresBookingRepository.hs` | programmer |
| M-02 | `textToCargoType _ _ _ _ _ _ _ = General` の silent fallback を `Either DomainError CargoType` または WARN ログに変更 | 同上 L174 | programmer |
| M-03 | `BookingFormView` の hidden `shipperId=""` 死コード削除 (T-07 後の互換 hack に TODO コメントなし) | `Booking/Views/BookingFormView.hs` | programmer |
| M-04 | ACL パターン統一方針 ADR 起票: 「新規 BC は Text 化必須、既存 7 件は段階的に `Shared.Domain.Reference.{ShipperRef, CargoTypeRef}` VO へ移行」 | 新規 `docs/adr/0003-cross-bc-acl-pattern.md` | architect |
| M-05 | US25 既存値プリフィル (`voyageEditPage` に input value で既存 movements を埋める) | `Routing/Views/VoyageFormView.hs` `voyageEditPage` | user-rep |
| M-06 | 法人番号・契約ランクの一覧表示を伏字 or ロール別表示に変更 (営業所内派遣スタッフから見えるリスク) | `Shipper/Views/ShipperListView.hs` | user-rep |
| M-07 | 荷主一覧に住所列、貨物予約一覧に納期・期限列を追加 (業務最頻出ニーズ) | `Shipper/Views/ShipperListView.hs`, `Booking/Views/BookingListView.hs` | user-rep |
| M-08 | 楽観ロック衝突メッセージ強化 (「誰が・いつ更新したか」+「再読込」ボタン) | flash 規約 + Layout.hs | user-rep |
| M-09 | ナビバーの業務メニュー / 運用メニュー分離 (Health は業務ユーザ非表示、Login は認証時非表示) | `Shared/Web/Layout.hs` | user-rep |
| M-10 | HandOverToRouterCommand の ConcurrentModification 伝播テスト追加 (Stub Repository で `Left ConcurrentModification`) | `test/unit/Booking/Application/HandOverToRouterCommandSpec.hs` | tester |
| M-11 | Domain 別カバレッジ計測 (`hpc report --per-module`) + Domain/Application/Interfaces 層別 gate 設定 | `scripts/check-coverage.sh` | tester |
| M-12 | arch-check Rule 4 ALLOWLIST に失効期限 (target IT) を frontmatter で付与 — 期限切れで fail | `scripts/arch-check.sh` | tester |
| M-13 | `Voyage.updateMovements` Application 層トランザクション境界 (`withTransaction` を Command Handler 側で開く方式) を ADR 化 | `Routing/Application/UpdateVoyageCommand.hs` + ADR | architect |
| M-14 | `domain-model.md` / `data-model.md` 同期を IT2 クロージングタスクに格上げ (Try U-09 を IT3 から外す) | `docs/design/{domain-model.md, data-model.md}` | technical-writer |

### 低 (IT3 内 〜 IT4)

| # | 提案 | 箇所 | 指摘元 |
| ---: | :--- | :--- | :--- |
| L-01 | arch-check を haskell-src-exts ベース AST バイナリへ移行 (Phase 2、shell sed/awk のメンテ性) | `arch-check/Main.hs` 新規 | programmer, architect |
| L-02 | テストフェイク `findAll* = pure []` を IORef ベース「保存したものが返る」に統一 | `test/unit/**/*Spec.hs` | programmer |
| L-03 | `cargoTypeColumns` `case temperatureUnit r of` を `temperatureUnitToText` ヘルパに抽出 (read 側との対称性) | `Booking/Infrastructure/PostgresBookingRepository.hs` | programmer |
| L-04 | `Postgres 14 列 SELECT` を `CargoRow` 型 + `toCargo :: CargoRow -> Either Error Cargo` のアンチコラプションマッピング層に分離 | 同上 | architect |
| L-05 | `requestRouting` プロパティテスト (任意の状態 → Submitted 以外なら必ず `InvalidStateTransition`) | `test/unit/Domain/PropertiesSpec.hs` | architect |
| L-06 | E2E htmx タイミングを `page.waitForResponse(/\/shippers\/search/)` に置換 (flaky 化要因の予防) | `e2e/src/tests/booking-registration.spec.ts` | tester |
| L-07 | E2E US06 Draft ブロックテスト名を `should_disable_handover_button_when_status_is_draft` に仕様化 | 同上 | tester |
| L-08 | `iteration_plan-2.md` §3 「目標」「Definition of Done」に arch-check Phase 2 繰越決定を反映 (現状 §6 のみ更新) | `docs/development/iteration_plan-2.md` L25, L33, L818 | technical-writer |
| L-09 | `arch-check.sh` ALLOWLIST 解消パスへの参照コメント追加 (`retrospective-2.md` U-05 / Issue 番号) | `scripts/arch-check.sh` L21-29 | technical-writer |
| L-10 | journal `## 学び` を memory `feedback_validating-iteration-plan-effectiveness.md` として永続化 | `~/.claude/.../memory/` | technical-writer |
| L-11 | `findAllCargos` / `findAllVoyages` をページネーション付き API (`findCargosPaged`) に移行検討 | 各 Repository ポート | architect |
| L-12 | 一覧画面の検索・ソート・ページング (Alpha 後の Beta 前必須、50 件超で使えなくなる) | 各 ListView | user-rep |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
| ---: | :--- | :--- | :--- | :--- |
| C-01 | architect: ACL Text 化は良い (新規 BC で前例) | programmer: `shipperIdText :: Text` は型レベル保証なし、ACL として弱い | Estimation の cross-BC 参照を Text のままにするか VO 化するか | IT3 で `Shared.Domain.Reference.ShipperRef` newtype を導入 (M-04)、Estimation は `ShipperRef` を保持。Text 化は new BC の暫定形として残しつつ強化 |
| C-02 | user-rep: 採番ルール (`SHP-2026-0001`) を画面に説明すべき | programmer: ランダム採番は衝突回避が主目的、ルール開示は限定的 | 自動採番の透明性 vs シンプル性 | UI には「自動採番」程度の説明、内部仕様は ADR で記録。L-09 と統合 |
| C-03 | tester: pending 10 件は危険、testcontainers-hs で常時実行化 | architect: pending は Repository 抽象が DB に閉じていない設計問題の表面化 | 短期は testcontainers でカバー、長期は In-Memory + Postgres の両 Repository 実装で抽象を強化するか | H-11 で短期対応、その後 IT4 で In-Memory Repository 追加検討 |

## エージェント別フィードバック詳細

<details>
<summary>xp-programmer (高: 2 / 中: 3 / 低: 3)</summary>

評価: CargoType sum type 化、状態遷移を Either で表現、JWT TTL 注入、T-01 partial 関数除去、Rule 4 ALLOWLIST 等は IT1 レトロを着実に反映。一方で `submitBooking` のエラー型不整合 (`ConcurrentModification (unShipperId ...)` で requestRouting と非対称)、`IdGenerator` の `(!!)` partial、`textToCargoType _ _ _ _ _ _ _ = General` silent fallback、`BookingFormView` の hidden `shipperId=""` 死コードに改善余地。重要度高 2 件 (H-01 / H-02)、中 3 件 (M-01 / M-02 / M-03)。

</details>

<details>
<summary>xp-tester (高: 3 / 中: 3 / 低: 2)</summary>

評価: B+ (Domain 層 A、統合層 C+)。Hedgehog 6 件 + CargoTypeSpec 15 ケース + EstimateSpec 17 ケースは仕様書として読める水準。PRG パターンの hspec-wai が flash まで検証していて UI 振る舞いも担保。一方で DATABASE_URL pending 10 件取りこぼし、US25 ロールバック integration 欠落、HTTP 経路の認可テスト不在 (T-13 繰越) が IT3 構造的リスク。テストピラミッドが砂時計型に近い。重要度高 3 件 (H-11 / H-12 / 認可テスト)、中 3 件 (M-10 / M-11 / M-12)、低 2 件 (L-06 / L-07)。

</details>

<details>
<summary>xp-architect (高: 3 / 中: 4 / 低: 2)</summary>

評価: B (合格だが要是正)。Estimation BC 独立判断・ACL ライト版・楽観ロック規約汎用化・Rule 4 + ALLOWLIST は方向性として正しい。一方で `BookingNotFound` の Shared 配置 (BC 境界違反)、ACL 二重基準 (Booking 直接 import / Estimation Text 化)、`HandOverToRouterCommand` の認可未実装 (計画書 §3.2 落ち)、HPC ゲート 60% 実質無効、ALLOWLIST のファイルパス脆弱性が問題。重要度高 3 件 (H-06 / H-07 / H-08)、中 4 件 (M-04 / M-13 / Repository 分割兆候 / `withTransaction` 配置)、低 2 件 (L-04 / L-11)。

</details>

<details>
<summary>xp-technical-writer (高: 2 / 中: 5 / 低: 3)</summary>

評価: 「設計と実装の往復跡」を高解像度で残せている点で IT1 から明確に前進。journal の判断経緯記録、iteration_plan のチェックボックス精度 (28/13)、報告書の率直さ (△ 3 件) は将来読者にとって有益。Haddock も「なぜ」を残せている。一方で 3 ドキュメント間のベロシティ表記不一致 (180% vs 100%)、リリースノートドラフト不在、Try の担当・期限が抽象的、§6 以外の arch-check Phase 2 繰越未反映、domain-model.md / data-model.md 遅延同期、README 未更新が課題。重要度高 2 件 (H-09 / H-10)、中 5 件 (M-04 / M-13 / M-14 / Try 粒度 / ALLOWLIST 解消パス参照)、低 3 件 (L-08 / L-09 / L-10)。

</details>

<details>
<summary>xp-user-representative (高: 4 / 中: 5 / 低: 2)</summary>

評価: 内部 Alpha デモの「形」は整いつつあるが、営業担当者の主要業務 (見積作成 / 危険物予約) が画面から実行できないため部分デモに留まる。US06 Draft ブロック (引き渡しボタン非表示) は内部デモで致命的。一方で自動採番・エラー日本語化・連続性違反検知・JWT 実時刻化は現場価値が高い。重要度高 4 件 (H-03 / H-04 / H-05 / H-06)、中 5 件 (M-05 / M-06 / M-07 / M-08 / M-09)、低 2 件 (L-12 / 採番フォーマット可視化)。スコープ外: 通貨表記規約・監査ログ未設計。

</details>

## 次のステップ

IT3 Week 1 序盤 (Day 1-3) で高優先度 12 件を返済 → Release 0.1 Internal Alpha のストーリー成立 → タグ付け。順序推奨:

1. **Day 1**: H-01 (submitBooking エラー型) + H-02 (IdGenerator partial) + H-09 (ベロシティ表記統一) — 本日中対応可能
2. **Day 1-2**: H-07 (DomainError 分離 ADR) + H-08 (HPC ゲート見直し)
3. **Day 2-3**: H-03 (US06 Submit) + H-04 (US01 UI) を最優先で並行実装
4. **Day 3-4**: H-05 (危険物フォーム) + H-06 (ロール認可)
5. **Day 4-5**: H-10 (リリースノート) + H-11 (testcontainers) + H-12 (ロールバック integration)

中優先度 14 件は IT3 内 (Week 1-2) で消化、低優先度 12 件は IT3 内または IT4 計画へ。

## 関連ドキュメント

- 計画: [iteration_plan-2.md](../development/iteration_plan-2.md)
- 完了報告: [iteration_report-2.md](../development/iteration_report-2.md)
- ふりかえり: [retrospective-2.md](../development/retrospective-2.md)
- IT1 レビュー: [it1_code_review_20260626.md](./it1_code_review_20260626.md)
- ジャーナル: [journal/20260627.md](../journal/20260627.md)
