# IT8 ふりかえり (KPT)

## 概要

IT8 (Release 2.0 GA: 精算処理 + 保証系完済) のふりかえり。Ralph Loop 開発 17 反復 (loop カウンタ 26 で cancel) で計画 22 SP のうち 21+ SP を消化し、`v1.0.0-mvp` / `v2.0.0` の 2 タグを付与。US23 精算を Billing Context で全レイヤ一巡完成し、IT7 繰越の保証系 (RoleGate 配線 / katip / E2E 統合ハッピーパス完成) を消化した。クロージングで XP 5 エージェント並列レビューを実施し高優先 5 件を即対応、3 件を IT9 backlog に整理。

- 計画: [iteration_plan-8.md](./iteration_plan-8.md)
- 報告書: [iteration_report-8.md](./iteration_report-8.md)
- レビュー: [it8_review_20260707.md](../review/it8_review_20260707.md)

---

## Keep (継続すべき良かったこと)

### 技術面

- **validating-iteration-plan が計画の BC 設計欠陥を開発着手前に検知**: 初版の「Settlement BC 新設」案が既存 Billing Context (Invoice 集約) と衝突していたのを、4 エージェント並列検証で発見・修正。実装後なら migration やり直し + 全レイヤ改名だった
- **既存パターンの踏襲でブレを抑制**: Cross-BC ポートは Text-DTO (Rule 4)、リポジトリはレコード式ポート、通知は既存 Cross-BC ヘルパへ委譲。新規抽象を作らず既存 BC の慣行に合わせたことで arch-check 違反を最小化
- **arch-check が Ralph Loop の暴走を止める安全網として機能**: UI 文言の「TsClaimed」(H-01)、Handling→Tracking Domain import (Rule 4) を自動検知。どちらも Text ヘルパ (`executeText`) への再設計で解消
- **TDD の一貫性**: 全コマンドに対応する Spec、spy による副作用検証 (成功時のみ 1 回発火/失敗時は非発火) がレビューでも評価された

### プロセス面

- **前提の実測でブロッカーを解消**: 「Docker 待ち」と 3 反復放置した T7-G/E2E が、`docker ps` 一発で Postgres 稼働中と判明。思い込みを実測で覆し、実 DB 統合テスト + E2E 完成 + 2 タグまで到達
- **業務価値のある実装がテスタビリティも解いた**: T7-E (UNLOAD 通知に平文コード) が、IT7 未解決だった「E2E のテスト用コード取得手段」の解になった
- **クロージングでの正式レビュー → 即対応の 2 段運用**: self-review (Ralph Loop 中) と developing-review (クロージング) の使い分けで、軽微な高優先を GA 品質に間に合わせつつ、設計判断を伴うものは IT9 に整理

### ドキュメント面

- **実装差分の注記による乖離管理**: domain-model / data-model の状態機械案とコード実態 (ADR-0009 で SSoT) の乖離を注記で明示。レビューで「乖離管理は妥当」と評価
- **計画への設計判断の記録**: resolveConfirmedCost の暫定実装方式、US10/US12 の UI 移送を計画に都度注記し、報告書・レビューと一貫

---

## Problem (改善すべき問題点)

### 技術面

- **P-1 (H-01): 「単一 Tx」計画が未配線**: ConfirmPayment の invoice 更新 + Cargo.Settled 連動は計画・docstring で「単一 Tx」とされたが、`billingPageApp` に TxRunner が注入されず独立コミット。docstring が実装を先行して書かれ、実装が追いつかなかった
- **P-2 (H-02): OverdueCheckCommand の起動主体不在**: 実装・テスト済だが Main 未配線で US23 受入基準 5 が機能的に不成立。「コマンドを作れば完了」と錯覚し、起動導線 (エンドポイント/バッチ) の配線を忘れた
- **P-3: fake テスト中心で実 DB 制約の乖離が不可視だった**: cargo CHECK の欠落値 (RouteAssigned/Cancelled) は Settled 追加時に実 DB を見て初めて発覚。InMemory/fake では DB 制約と実装の乖離が見えない
- **P-4: 割引計算式の二重定義**: Domain と View に同じ丸め規則が重複 (レビュー H-03 で解消)

### プロセス面

- **P-5: stale バイナリで 30 分溶かした**: cabal ファイルが package.yaml より新しくテストバイナリが再生成されず、「303 成功なのに spy が空」を実装バグと誤認。不可解な挙動をまずビルド鮮度で疑うべきだった
- **P-6: 「環境待ち」の早すぎる判断**: T7-G/E2E を環境依存として 3 反復放置。前提 (Docker 稼働) を確認せずブロッカー扱いした

### ドキュメント面

- **P-7: 用語の二分 (精算書/請求書)**: 同一 Invoice 集約が文書と実装で別名 (レビュー M-01 で注記追加)
- **P-8: ADR Status の更新漏れ**: ADR-0016 が実装済・GA 済でも「提案」のまま (レビュー H-05 で採用に更新)

---

## Try (次に試すこと)

### 高優先 (IT9 冒頭で必達)

| ID | 内容 | 期待効果 |
| :--- | :--- | :--- |
| T8-A | `billingPageApp` に TxRunner を注入し ConfirmPayment を単一 Tx 化 (H-01) | 入金確認と Cargo.Settled 連動の部分失敗による不整合を排除 |
| T8-B | `OverdueCheckCommand` の起動主体を配線 (経理担当者向け手動トリガ or 定期バッチ) (H-02) | US23 受入基準 5 を機能的に成立させる |
| T8-C | 予約単位の距離/重量を永続化し resolveConfirmedCost を実値化 (Main.hs 暫定 distance/weight=0 の返済) | 請求金額が実費を反映し US23 の業務価値を回復 |

### 中優先 (IT9 内で対応)

| ID | 内容 | 期待効果 |
| :--- | :--- | :--- |
| T8-D | T7-G 残 4 Repository (PricingRule/CurrencyRate/Notification/Exception) の実 DB 統合テスト | fake では見えない SQL マッピング/CHECK 乖離のリグレッション検知 |
| T8-E | US10 UI (EstimatePageApi 調整フォーム) / US12 UI (予約詳細の経路通知ボタン) | ストレッチの UI 完成、#242/#244 Close |
| T8-F | E2E 追跡番号検証を data-testid + アンカー付き正規表現に厳格化 (H-06) | 常緑テスト化の解消、フォーマット変更バグの検知 |
| T8-G | `errorFlash` の DomainError 網羅化 + フラッシュキー集約 (M-03) | 業務エラーで汎用文言に落ちる問題の解消 |
| T8-H | `transitDaysOf` を Shared に抽出 (M-06) / Refunded 状態のエラー名整合 (M-05) | コード重複と状態×エラーの不一致の解消 |

### 低優先 (Release 2.0+ 準備)

| ID | 内容 |
| :--- | :--- |
| T8-I | 通知チャネルの実配信 (メール送信) 実装 (M-04、現状 Log スタブ) |
| T8-J | 画面文言の業務用語化 (reference_code 等の開発用語の露出解消、M-07) |
| T8-K | Invoice 状態遷移の不変条件 (version 単調増加・冪等性) の hedgehog プロパティ化 (M-09) |
| T8-L | 消費税 (tax) 計算の実装 (invoice テーブルには列があるが集約未使用) |
| T8-M | Main.rootApp の port 合成を各 BC の wiring モジュールに切り出し |

---

## メタ知見 (Ralph Loop 開発フェーズの学び)

- **「コマンド実装 = 機能完成」ではない**: OverdueCheck (P-2) のように、Domain/App/テストが揃っても起動導線がなければ業務基準は未達。受入基準を「誰がいつ起動するか」まで含めて確認する
- **docstring は実装より先に書かない**: 「単一 Tx で囲む」と docstring に書いたが配線が追いつかず、レビューで乖離を指摘された (P-1)。実装済の事実だけを書く
- **fake の限界を実 DB 統合テストで補う**: DB 制約 (CHECK/UK/楽観ロック) の乖離は fake では原理的に見えない。GA レベルでは主要 Repository を実 DB で検証すべき (P-3 → T8-D)
- **end-of-life 判定は正しく機能した**: AI 単独完結タスク消化後に `/ralph-loop:cancel-ralph` で停止し、クロージング (レビュー→報告書→KPT→同期) をユーザー主導サイクルで実行。feedback memory の運用に沿った

---

## ベロシティ実績と次期 IT の計画への反映

### 実績推移

| IT | 計画 SP | 実績 SP | 達成率 |
| :---: | :---: | :---: | :---: |
| IT1 | 13 | 20 | 154% |
| IT2 | 10 | 18 | 180% |
| IT3 | 29 | 22 | 76% |
| IT4 | 20 | 19 | 95% |
| IT5 | 22 | 40+ | 182% |
| IT6 | 18 | 30+ | 167% |
| IT7 | 10 | 30+ | 300%+ |
| **IT8** | **22** | **21+** | **約 98%** |

平均ベロシティ (IT1-IT8 単純平均): **約 25.0 SP**

### IT9 計画への示唆

- IT9 は Release 2.0 GA 後の**残務回収 + 品質強化イテレーション**。新規ストーリーより、レビュー H-01/H-02 の機能的ギャップ (T8-A/B) と US10/US12 UI (T8-E) を優先
- IT8 の達成率 98% は「計画精度が上がった」証左。ストレッチを Domain/App 層に限定し UI を移送する判断が、無理のない見積りにつながった
- 実 DB 統合テストの範囲拡大 (T8-D) を IT9 の Try に据え、GA 後の信頼性を担保する

---

## 関連ドキュメント

- [IT8 計画](./iteration_plan-8.md)
- [IT8 完了報告書](./iteration_report-8.md)
- [IT8 マルチパースペクティブレビュー](../review/it8_review_20260707.md)
- [リリース計画](./release_plan.md)
