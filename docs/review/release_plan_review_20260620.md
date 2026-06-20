# リリース計画レビュー結果（2026-06-20）

## レビュー対象

- `docs/development/release_plan.md`（Scala 版 take-1 リリース計画書、25 ストーリー / 81 SP / 8 イテレーション / 4 リリース）

## 総合評価

Java 版 take-2 実績（98 SP / 10 IT、平均 9.8 SP/IT）をベースラインに、4 軸優先評価・バッファ戦略・段階リリースを備えた構造は健全で、XP プラクティスを忠実に踏襲できている。一方で、**(1) Release 0.1 を「MVP」と呼ぶ妥当性**、**(2) Scala / 関数型移行リスクの SP 反映不足**、**(3) IT 間 SP の偏り**、**(4) ペルソナ別体験の断絶**、**(5) リリース条件の不揃い** という 5 つの構造的課題が複数エージェントから共通指摘された。修正により計画の予測可能性と現場価値の両方を高められる。

## 改善提案（重要度順）

### 高

| # | 提案 | 指摘元 | 理由 |
|---|------|--------|------|
| H1 | Release 0.1 の「MVP」呼称を見直し「Internal Alpha / 予約受付基盤」と再ラベル。真の MVP は Release 1.0（追跡まで） | PM, User Rep | 荷主体験が断絶し、ステークホルダーの期待値ズレを生む |
| H2 | Scala 移行リスク係数 1.15-1.20 を全 SP に乗じ、81 SP → 93-100 SP に見直し（IT 数 8 → 9-10 へ拡張または US22 を Release 2.1 へ） | Architect | リスク表は学習負荷を明記しているのに SP に反映されていない |
| H3 | IT 間 SP を平準化（IT4=14・IT8=13 → 11 程度、IT6=6 → 8-9）。Release 含む IT は他より -2〜3 SP のポリシー明文化 | PM, Architect, Interaction | 「持続可能なペース」違反。Release 直前 IT が過負荷 |
| H4 | US18（追跡照会）を IT5 に前倒し、US17 と入れ替え | Interaction, User Rep | 追跡番号発行後に照会画面がない期間 2 週間を解消、荷主中核体験の前倒し |
| H5 | リリース条件を統一（共通最低ゲート + リリース毎の増分検証）。Release 0.2 にカバレッジ 80%、Release 1.0・2.0 にパフォーマンス定量基準を追加 | Tester | 条件が場当たり的で test_strategy.md と不整合 |
| H6 | E2E シナリオを US 番号で明示し、Release 累積実行をリリース条件に追加 | Tester | test_strategy.md の E2E（US13/US15/US18）と Release 0.1 「予約フロー E2E」の対応が曖昧 |
| H7 | Java 版等価性検証の手段を ADR で具体化（共通 E2E 本数・判定基準・自動化方式） | PM, Architect, Tester | 「機能等価性」が概念的で検証不能 |
| H8 | US08（経路候補算出）を 5 → 8 SP に上方修正、または IT2 末に US08-spike 2 SP を挿入 | Architect | 関数型再実装の最大リスク要素。学習コストが見積もりに反映されていない |
| H9 | US24・US25 を IT3 冒頭に移動、または Release 0.1 の意味づけを「マスタ整備フェーズ」と明記 | PM | YAGNI 違反リスク。Phase 1 で作っても利用機能がない |

### 中

| # | 提案 | 指摘元 | 理由 |
|---|------|--------|------|
| M1 | US21（料金算出）を Release 1.0 に前倒し、Release 1.0 を「Beta（社内運用）」、2.0 を「GA」に再定義 | PM, User Rep | 「課金して運べる」状態が本番投入可能ライン |
| M2 | US01（見積）の料金ロジックを US21 と共通化する前提を明記 | User Rep | 二重実装による「見積金額と請求金額が違う」クレームの予防 |
| M3 | バッファ戦略をベロシティ係数とフィーチャバッファのいずれかに統一（推奨：フィーチャ 30% のみ） | PM | 二重バッファでベロシティ実測精度が低下 |
| M4 | エフェクトシステムのリスク記述を tech_stack.md と整合（Future + Either 合成パターン） | Architect | リスク表とスタックが食い違い、対策が曖昧 |
| M5 | E2E 共通フィクスチャ整備 3 SP を IT2 または IT4 に独立計上 | Architect | Java 版等価性検証コストが SP に未計上 |
| M6 | 予備イテレーション（IT9）を Gantt に明示 | Architect | バッファが実日程に確保されていない |
| M7 | パフォーマンステストの機能別 SLA を全リリースで定義（追跡 P95、精算 P95 等） | Tester | Release 0.2 のみ定量基準 |
| M8 | TDD によるテストコード作成が SP に含まれることを明記 | Tester | 「テストを後から書く」誘惑を排除 |
| M9 | 横断要件（状態遷移網羅、楽観ロック、セッション境界）と IT のマッピング表を追加 | Tester | 非機能テスト着手タイミングが不明 |
| M10 | Phase 4（IT7-8）の SP 配分を逆転（IT7=13, IT8=10）または IT8 にバッファ確保 | Tester, Interaction | 精算ロジックの法的リスクを考慮 |
| M11 | Phase 1 の「経路設計者は引き渡し済み予約を一覧で確認できる（操作は Release 0.2 から）」をリリース条件に明記 | Interaction | US06 引き渡し後のユーザー体験断絶を明示 |

### 低

| # | 提案 | 指摘元 | 理由 |
|---|------|--------|------|
| L1 | US08・US19・US20 の分割可能性を IT 計画作成時に検討 | PM | 5 SP は 1 名稼働 20h/週の上限ぎりぎり |
| L2 | ADR 0001 への参照リンクを概要に追加 | Architect | 見積もり前提のトレーサビリティ |
| L3 | バグ密度 0.5 件/SP 以下の計測手段（バグ定義、起票方法）を定義 | Tester | 計測できない目標は形骸化 |
| L4 | ベロシティ目標（10-13 SP）と実効ベロシティ（8-11 SP）を一本化 | Architect | 同一文書内に 2 レンジで達成率評価が曖昧 |
| L5 | バーンダウンチャートに実績ライン用プレースホルダを追加 | Interaction | tracking-progress スキルとの連携 |
| L6 | ペルソナ別の体験完成タイミング表を追加 | Interaction | ペルソナ別進捗が俯瞰できない |
| L7 | US05（危険物・冷凍貨物）の Phase 2 移動余地を残す | User Rep | 一般貨物（US04）の完成度低下を予防 |
| L8 | US22 後回し時の運用回避策（手動値引き等）を明記 | User Rep | 法人割引マスタ未整備時の現場対応 |
| L9 | 低優先度後回し可能ストーリーの依存関係（US17 = 楽観ロック代表）を明記 | Tester | US17 後回しで横断要件検証ができない |

## 矛盾事項

| # | 視点 A | 視点 B | 論点 | 推奨判断 |
|---|--------|--------|------|----------|
| C1 | PM: US24/US25 を IT3 冒頭に移動を提案 | 計画書: 経路設計の前提マスタとして Phase 1 に配置 | 航海スケジュールマスタの配置 | Interaction の指摘も踏まえ、**Release 0.1 を「予約受付 + マスタ整備」と再ラベル**して現状維持し、Release 1.0/2.0 で初めて活用される設計を明文化 |
| C2 | Tester: IT7=13, IT8=10 の SP 配分逆転を提案 | Interaction: US22 を IT7 末尾、US21 を IT7、UI と精算は IT8 を提案 | Phase 4 の負荷分散 | **IT7 で US19・US20・US21（13 SP）、IT8 で US22・US23（8 SP）+ Release 2.0 リリース作業**に再配分 |

## エージェント別フィードバック詳細

### xp-product-manager（高: 3 / 中: 2 / 低: 2）

- 評価: B+（合格・改善余地あり）
- 主要指摘: MVP 呼称、ベロシティとイテレーション計画の内部矛盾、US24/25 YAGNI、Release 2.0 境界の弱さ、バッファ二重計上
- スコープ外発見: user_story.md と release_plan.md は ID/タイトル/件数すべて整合、インセプションデッキ優先順位と Phase 順序が一致

### xp-architect（高: 3 / 中: 3 / 低: 2）

- 主要指摘: Scala 学習コストの SP 不足（81 SP → 93-100 SP）、IT 間 SP 偏り、US08 過小見積もり、エフェクトシステム記述の不整合、E2E 共通化 SP 未計上、予備 IT が Gantt にない
- スコープ外発見: tech_stack.md（Pekko + Future）とリスク記述（Cats Effect / ZIO）の不整合

### xp-tester（高: 3 / 中: 4 / 低: 2）

- 主要指摘: リリース条件の不揃い、E2E シナリオと Release のタイミング不整合、Java 版等価性検証の抽象性、パフォーマンス SLA 不足、テストコード SP の明示不足、横断要件マッピング不在
- 懸念: IT6=6 SP の妥当性、IT8=13 SP の精算ロジック法的リスク

### xp-user-representative（高: 2 / 中: 2 / 低: 2）

- 主要指摘: Release 0.1 「MVP」呼称、US18 が Release 1.0 まで出ない、US01/US21 二重実装リスク、US06 後の経路設計者の手元滞留、危険物 US05 の野心性
- スコープ外発見: マスタ管理者向け機能未定義、荷主向け照会・通知 UI 不足、キャンセル・予約変更ストーリー欠落

### xp-interaction-designer（高: 3 / 中: 2 / 低: 2）

- 主要指摘: US18 前倒し、US06 引き渡し後の体験断絶、IT6 SP 過少、Phase 1 のペルソナ分断（営業 + 経路設計者）、公開追跡 URL の位置づけ不在、IT8 の請求書発行集中
- スコープ外発見: マスタ管理者・荷受人ペルソナの不在、ふりかえりタスクの計画組み込みなし

## 改善アクション

### 優先度 1（即着手）

1. **リリース呼称・境界の再定義**（H1, M1）: Release 0.1 = Internal Alpha、Release 1.0 = MVP（追跡照会まで）、Release 2.0 = GA（精算込み）。US21 を Release 1.0 へ前倒し
2. **SP 見直し**（H2, H8）: Scala 移行係数 1.15 適用 + US08 を 5 → 8 SP に上方修正 → 総計を再計算
3. **IT 平準化と US18 前倒し**（H3, H4）: IT 配分を 10/10/10/12/11/9/12/9 程度に再調整
4. **リリース条件統一**（H5, H6）: 共通最低ゲート + 増分検証セクションに分割、E2E シナリオを US 番号で明記

### 優先度 2（IT1 開始前）

5. **Java 版等価性検証の ADR 化**（H7）
6. **エフェクトシステム記述の修正**（M4）
7. **横断要件・テストコード SP の明示**（M8, M9）
8. **予備 IT9 を Gantt に追加**（M6）

### 優先度 3（IT2 までに）

9. **ペルソナ別体験完成タイミング表の追加**（L6）
10. **マスタ管理者・荷受人ペルソナの扱いを明確化**（スコープ外発見対応）
11. **キャンセル・予約変更ストーリーの取り扱い判断**（スコープ外発見対応）

## レビュー結果に対する対応方針

各指摘に対する「修正する / 許容する / 保留する」の判断は次のステップで実施する。

## 関連ファイル

- `/Users/k2works/IdeaProjects/case-study-cargo-tracker-scala-take-1/docs/development/release_plan.md`
- `/Users/k2works/IdeaProjects/case-study-cargo-tracker-scala-take-1/docs/requirements/user_story.md`
- `/Users/k2works/IdeaProjects/case-study-cargo-tracker-scala-take-1/docs/design/tech_stack.md`
- `/Users/k2works/IdeaProjects/case-study-cargo-tracker-scala-take-1/docs/design/test_strategy.md`
- `/Users/k2works/IdeaProjects/case-study-cargo-tracker-scala-take-1/docs/design/ui_design.md`
- `/Users/k2works/IdeaProjects/case-study-cargo-tracker-scala-take-1/docs/strategy/inception-deck.md`
