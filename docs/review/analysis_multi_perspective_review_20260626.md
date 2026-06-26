# 分析成果物 多視点レビュー (2026-06-26)

## レビュー対象

Haskell 版 take-1 の分析フェーズ全 13 ドキュメントを XP エージェント 5 体で並列レビュー。

| エージェント | 主担当ドキュメント |
| :--- | :--- |
| xp-product-manager | user_story.md, release_plan.md, system_usecase.md |
| xp-architect | architecture_{backend,frontend,infrastructure}.md, domain-model.md, data-model.md, tech_stack.md, ADR 0001 |
| xp-interaction-designer | ui_design.md, architecture_frontend.md |
| xp-tester | test_strategy.md, user_story.md, non_functional.md, domain-model.md |
| xp-user-representative | requirements_definition.md, user_story.md, system_usecase.md, ui_design.md, release_plan.md |

## 総合評価

5 エージェントすべてが「分析フェーズの基本品質は良好」と評価。
DDD + ヘキサゴナル + CQRS の Haskell への翻訳は自然で、Scala 版設計の核を保ちつつ言語特性 (`newtype` / sum type / 型クラスポート / ReaderT) を活かしている。リリース戦略 (4 段階・8 IT) も荷主価値を最優先する MVP 定義として妥当。

一方で**実装着手前に対応すべき高優先度の指摘 12 件**が検出された。特に:

- リリース計画内の数値不整合 (PM)
- 集約数の表記揺れ (Architect)
- 業務ドメイン (荷受人・通関) の要件不足 (UR)
- 受入基準のテスト可能性向上 (Tester)
- 荷役オフライン対応の前倒し (UX)

総合スコア: **B+ (改善後 A 相当)**

---

## 改善提案 (優先度別)

### 高 (実装着手前に対応推奨) — Sprint 0 で全 16 件対応完了 (2026-06-26)

| # | 指摘元 | 提案 | 対応状況 |
| :---: | :---: | :--- | :--- |
| H-01 | PM | リリース計画「合計 26 US」を 25 に修正、または横断要件を US26 として正式採番 | ✅ 27 US (US01-US27) + 横断要件 1 として再採番 (release_plan.md) |
| H-02 | PM | Phase 1 SP 合計 23 と IT1+IT2 のズレ解消 | ✅ IT1 を 13 SP に修正 (release_plan.md) |
| H-03 | PM | US10/US12 を「IT9 候補のストレッチ」と明示 | ✅ IT8 ストレッチとして明示、Release 2.0 判定基準もストレッチ目標に変更 |
| H-04 | PM | US08 を US08a (基本算出) / US08b (制約評価) に物理分割 | ✅ user_story.md / release_plan.md で物理分割 (US08a: 5 SP / US08b: 3 SP) |
| H-05 | Architect | architecture_backend.md と domain-model.md の集約数表記不整合 | ✅ architecture_backend.md を 7 集約 (Booking/Shipper/Routing/Tracking/Handling/Billing/Estimation) に統一 |
| H-06 | Architect | `arch-check` の判定アルゴリズム・実装手段を ADR 化 | ✅ ADR 0002 起票 (HLint + 自作 AST 解析のハイブリッド、4 段階導入計画) |
| H-07 | Architect | トランザクション境界と `Either` の組み合わせ規約統一 | ✅ architecture_backend.md にトランザクション境界規約 T-01〜T-03 を追加 |
| H-08 | Tester | P95 < 500ms 検証 (k6/wrk スモーク負荷) を CI に組み込み | ✅ test_strategy.md §9.1 CI 統合スモーク負荷テスト追加、release_plan.md 判定基準にも反映 |
| H-09 | Tester | 楽観ロックの真の並行テスト | ✅ test_strategy.md §9.4 `forConcurrently` ベースの並行更新テスト例を追加 |
| H-10 | Tester | 受入基準の BDD (Given/When/Then) 形式化 | ✅ test_strategy.md §3.5 BDD 規約 + hspec マッピング + US08a 実装例追加。US08a/US08b/US26/US27 は既に BDD 化 |
| H-11 | UR | 荷受人を一級アクターに昇格、引取通知ストーリーを Phase 3 に追加 | ✅ US26 を新規追加 (Phase 3 / IT6 / 2 SP)、release_plan.md 判定基準にも反映 |
| H-12 | UR | 通関 / 税関連携の最小要件を Phase 2 に追加 | ✅ US27 を新規追加 (Phase 2 / IT3 / 3 SP)、HS コード・通関業者・申告ステータス受入基準を BDD で定義 |
| H-13 | UR | US08 の受入基準に「危険物対応港・温度管理可能船の絞り込み」を明文化 | ✅ US08b として独立化、危険物クラス/冷凍コンテナ可否の受入基準を Gherkin で明示 |
| H-14 | UX | 荷役登録のオフライン対応 (Service Worker + IndexedDB) の前倒し | ✅ ui_design.md にオフライン設計方針追加、Release 1.0 MVP までに対応する判定基準に追加 |
| H-15 | UX | 24 画面のエラー UI 統一ハンドリング定義 | ✅ ui_design.md に「エラー状態統一ハンドリング」表 + htmx グローバルハンドラ実装例追加 |
| H-16 | UX | タッチターゲット 44x44px 以上の明示 | ✅ ui_design.md に WCAG 2.5.5 準拠規約 + Bootstrap 5 デフォルトテーマのコントラスト比検証表を追加 |

### 中

| # | 指摘元 | 提案 | 理由 |
| :---: | :---: | :--- | :--- |
| M-01 | PM | US18 (追跡照会) を IT5 で前倒し配置、IT5 末プレ MVP デモ可能化 | 荷主フィードバックの早期取得 |
| M-02 | PM | Release 0.1 判定基準に Application 層カバレッジ ≥ 80% を追加 | Domain 偏重防止 |
| M-03 | PM | US05 を US04 の拡張オプションとして統合 | 受入基準重複の解消 |
| M-04 | Architect | CSRF 実装を Lucid 関数引数で `CsrfToken -> Html ()` の型安全パターン化 | 型システム活用 |
| M-05 | Architect | `servant-auth-server` の廃止リスクに備え、代替 (`servant-auth-cookie` 等) との比較を ADR 備考に追記 | エコシステム変動 |
| M-06 | Architect | `DiscountPolicy` の割引種別を sum type で表現する設計判断を明記 | 変更影響範囲の明確化 |
| M-07 | Tester | 境界値一覧表を test_strategy.md に追加 (割引率 0-30%、UN/LOCODE 5 文字、重量・寸法等) | 境界値テストの体系化 |
| M-08 | Tester | hedgehog Generator 設計指針 (`Gen.filter` 多用回避、合成型直接生成推奨) を 1 節追加 | プロパティテストの信頼性 |
| M-09 | Tester | WireMock シナリオに Circuit Breaker 復旧 (Half-Open → Close) とリトライ上限を追加 | 障害復旧パスの検証 |
| M-10 | Tester | アーキテクチャ規約に「同 BC 内での集約境界をまたぐ参照禁止」追加 | 内部凝集性の維持 |
| M-11 | UR | 荷役オフライン対応 (現状「将来」扱い) を Phase 4 に明示 | 現場運用想定 |
| M-12 | UR | US18 の ETA 算出根拠を受入基準に明記 | 荷受人の信頼確保 |
| M-13 | UR | US23 精算の通貨・為替の扱い (USD/JPY、レート確定日) を US21 受入基準に追加 | 国際取引の前提 |
| M-14 | UX | 追跡詳細を地図 + タイムラインのメタファーで強化 | 「現在地を一目で」の UX |
| M-15 | UX | ステータスバッジの色トークン (IN_PORT / IN_TRANSIT / CLAIMED) の WCAG AA コントラスト比検証 | A11y 担保 |
| M-16 | UX | htmx 部分更新時の `aria-live` 領域指定を画面仕様に明記 | スクリーンリーダー対応 |

### 低

| # | 指摘元 | 提案 |
| :---: | :---: | :--- |
| L-01 | PM | user_story.md の優先度欄に「低」も含めるか、release_plan.md の優先度を逆反映 |
| L-02 | PM | US24/US25 のアクターを「マスタ管理者」ロールに再検討 |
| L-03 | Architect | `mtl` の使用範囲 ("ReaderT 基盤のみ、MonadReader/MonadError は使わない") を tech_stack に注記 |
| L-04 | Architect | `-Wincomplete-patterns` 等のコンパイラフラグを `package.yaml` で強制する規約を tech_stack に追加 |
| L-05 | Tester | HPC のブランチカバレッジ計測精度に関する注記 |
| L-06 | Tester | E2E htmx ポーリング系のテストヘルパー API (間隔短縮ノブ) 整備 |
| L-07 | Tester | Pre-commit テストは `--match Domain` 等のフィルタ運用明記 |
| L-08 | UR | US22 割引率上限 30% の業務根拠と変更可否を運用要件にメモ |
| L-09 | UR | キャンセル料の扱い (確定後・出港後) を US13 / US23 に追加 |
| L-10 | UX | 共通パンくずコンポーネントの一貫性確保 |

---

## 矛盾事項

エージェント間の指摘相反は検出されなかったが、エージェントの認識と実装のずれが 1 件あり。

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
| :---: | :--- | :--- | :--- | :--- |
| X-01 | UX エージェントの記述「7 ロール (営業 / オペレーター / 荷役作業員 / 顧客 / 管理者 / 監査者 / 経営層)」 | 実装 (Shipper / Sales / RouteDesigner / Handler / Tracker / Accountant / Admin) | UX エージェントが文書から誤読 | 実装側が正。UX エージェントの指摘自体は有効 (高優先度の UX 改善は実装側にも適用可能)。X-01 は記録のみ |

---

## 懸念事項 (横断)

5 エージェントが共通して指摘した懸念:

1. **IT5-IT6 のベロシティ 7-6 SP の低さ** (PM + UR): MVP 直前なのに余裕。IT1 (12 SP) は逆に重い。IT バランス再調整を推奨
2. **`arch-check` 自作の保守コスト** (Architect + Tester): Haskell には ArchUnit 相当の成熟ツールなし。代替 (`stan`、grep ベース CI スクリプト) のハイブリッド検討
3. **testcontainers-hs の並列実行コスト** (Tester): CI 5-10 分目標を超過する可能性。`aroundAll` 粒度設計の明文化必要
4. **同期イベントディスパッチのレイテンシ** (Architect): Tracking → Booking 双方向参照を含み、subscriber 実行時間が予約 API のレスポンスタイムに乗る。非機能要件と突合検証必要

## スコープ外の発見

- **デプロイ後評価のテレメトリ未定義** (UX): 利用状況分析・KPI 定義がリリース計画に未掲載
- **カオステスト / 障害注入計画なし** (Tester): 可用性 99.9% を主張するなら RDS フェイルオーバーリハーサル必要
- **システム全体のメタファー言語化なし** (UX): 「貨物の旅券」等のメタファーが未定義
- **インセプションデッキとの整合** (UR): ご近所さん (税関・船社・港湾管理者) の関与レベル定義との突合が必要

---

## 改善アクション提案

### 優先実施 (実装着手前)

**Sprint 0 として 1-2 日で対応:**

1. リリース計画の数値整合修正 (H-01, H-02)
2. 集約数表記揺れ統一 (H-05) — architecture_backend.md のコンテキストマップを 7 集約に更新
3. US10/US12 のストレッチ明示 (H-03)
4. arch-check 仕様の ADR 起票 (H-06)
5. トランザクション境界規約の追記 (H-07)
6. 荷受人通知ストーリー追加 (H-11) — user_story.md / release_plan.md
7. 通関最小要件追加 (H-12) — Phase 2 に新規 US

### IT1 内で対応

8. US08 の物理分割 (H-04)
9. P95 検証 CI 組み込み (H-08)
10. 受入基準の BDD 化 (H-10)
11. 荷役オフライン設計検討 (H-14)
12. エラー UI 統一ハンドリング (H-15)

### IT 計画と並行で対応

- 中優先度 16 件は各 IT の関連実装時に併せて対応
- 低優先度 10 件は IT9 (予備) で処理

---

## 各エージェント別フィードバック詳細

### xp-product-manager (高: 6 / 中: 3 / 低: 2)

#### 評価
US01-US25 と UC01-UC19 のトレーサビリティは明確で、4 段階リリース戦略と Haskell 学習コスト係数 1.20 の織り込みは概ね妥当。ただしリリース計画内に US 件数・SP 集計の不整合と、Phase 内優先順位の論理的矛盾があり、IT 計画の修正が必要。

#### 良い点
- US01-US25 が UC と 1:1 対応、トレーサビリティマトリックスで BUC・ビジネス目標まで追跡可能
- 受け入れ基準が検証可能形式 (Testable)
- 真の MVP を Release 1.0 と定義、追跡を MVP に含める判断は妥当
- US08 を最大リスクとして 8 SP・2 IT 分割
- 学習コスト係数 1.20 の根拠が技術固有の理由として具体的

### xp-architect (高: 3 / 中: 3 / 低: 2)

#### 評価
Scala/Java 版のアーキテクチャ思想を維持しつつ、Haskell の言語特性に自然に翻訳できている。ADR 0001 で代替案を明示比較しており意思決定の追跡可能性は高い。総じて「変更を楽に安全にできる」基盤として妥当。

#### 良い点
- ドメイン層の純粋性が言語機能で担保
- `newtype` + スマートコンストラクタ + `unsafeXxx` 復元の二系統化
- 状態遷移を `canTransitionTo` 純粋関数に集約
- postgresql-simple + QuasiQuoter で CQRS Read Model 最適化
- ReaderT Env IO で学習コストを抑制
- イベント発行のトランザクション後配信 + subscriber 例外隔離
- 楽観ロック (version カラム) の統一適用

### xp-interaction-designer (高: 3 / 中: 4 / 低: 1)

#### 評価
OOUX の体系的構成と Lucid SSR + htmx 選定はドメインと整合。代表 5 画面のワイヤーフレームは構造化されているが、エラー状態・空状態・荷役作業員のモバイル UX に具体性不足。

#### 良い点
- オブジェクト中心の URL 設計でブックマーク・共有可能性が確保
- PRG パターンの徹底
- ステータスポーリング 30s の妥当性 (港湾オペレーション単位と整合)
- ロール別ダッシュボードの分離 (認知負荷低減)
- 荷役登録画面の最小入力構成 (フィールド特性反映)

### xp-tester (高: 3 / 中: 4 / 低: 3)

#### 評価
ドメインの純粋性を活かした堅実な戦略。ピラミッド比率、ツール選定、規約検査、契約テストまで一貫。一方、非機能要件の検証手段、状態遷移網羅の証明手段、受入基準のテスト可能性に改善余地。総合 B+。

#### 良い点
- ドメインの純粋性を戦略に直結 (モックなし優先)
- 状態遷移の全ペア網羅 (9×9=81) を例ベースで列挙
- `TransportStatus ↔ TrackingStatus` 双方向プロパティ
- 本番同一 DB の徹底 (testcontainers-hs)
- トレーサビリティ表 (§5) で US ↔ テストレベル明示

### xp-user-representative (高: 3 / 中: 3 / 低: 2)

#### 評価
7 アクター視点・RDRA 2.0 構造は整い、ストーリー粒度も TDD 検証可能。UN/LOCODE・貨物種別・状態モデル・例外処理は最低限カバー。一方、通関・船荷証券・インコタームズ・荷受人主体フローが未着地で、「実運用で困る」シナリオが残る。

#### 良い点
- UN/LOCODE 明示で国際輸送の語彙押さえ
- 状態モデルが業務フローと一貫
- US16 で引取証跡 (荷受人確認) 要件化
- リリース順序が荷主の最大痛点 (追跡できない) を MVP で解消
- US25 で運航変更が頻発する実態に即した差分確認 UI

---

## 参照

- [整合性セルフレビュー](analysis_consistency_review_20260626.md)
- [リリース計画](../development/release_plan.md)
- [分析フェーズ完了報告書](../development/analysis_completion_report.md)
- [ユーザーストーリー](../requirements/user_story.md)
- [ドメインモデル設計](../design/domain-model.md)
