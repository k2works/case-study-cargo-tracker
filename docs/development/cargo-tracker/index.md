# cargo-tracker — 開発

cargo-tracker プロジェクトの開発ドキュメントです。

## ドキュメント一覧

### 計画・戦略

| ドキュメント | 概要 |
| :--- | :--- |
| [リリース計画](release_plan.md) | US01〜US31 を 15 イテレーション・5 リリースに配分。SP・ベロシティ・バッファ・リスク |
| [開発戦略](development_strategy.md) | 序盤（IT1-3 アウトサイドイン）・中盤（IT4-10 インサイドアウト）・終盤（IT11-15 アウトサイドイン）の局面とアプローチ |
| [リリース完了報告書 0.1](release_report-0_1_0.md) | Release 0.1 予約基盤（IT1〜IT3・27 SP・達成率 100%） |

### イテレーション

| イテレーション | 計画 | ふりかえり | 完了報告書 | 状態 |
| :--- | :--- | :--- | :--- | :--- |
| IT1 基盤・認証・荷主登録 | [計画](iteration_plan-1.md) | [ふりかえり](retrospective-1.md) | [完了報告書](iteration_report-1.md) | **完了**（実績 9 SP・達成率 100%。持ち越し 5 件） |
| IT2 貨物予約・法人荷主・アカウント保護 | [計画](iteration_plan-2.md) | [ふりかえり](retrospective-2.md) | [完了報告書](iteration_report-2.md) | **完了**（実績 9 SP・達成率 100%。欠陥 12 件を発見・修正） |
| IT3 危険物・引き渡し・航海登録 | [計画](iteration_plan-3.md) | [ふりかえり](retrospective-3.md) | [完了報告書](iteration_report-3.md) | **完了**（実績 9 SP・達成率 100%。欠陥 34 件を発見・修正。routingms を立ち上げ） |
| IT4 航海の更新と検索・予約の修正 | [計画](iteration_plan-4.md) | [ふりかえり](retrospective-4.md) | [完了報告書](iteration_report-4.md) | **完了**（実績 8 SP・達成率 100%。返済枠 6 件を繰越ゼロで返済。中盤の最初） |
| IT5 経路候補の算出と確定 | [計画](iteration_plan-5.md) | [ふりかえり](retrospective-5.md) | [完了報告書](iteration_report-5.md) | **完了**（実績 10 SP・達成率 100%。引き継ぎ枠 6 件を繰越ゼロで返済。サービス越しの同期問い合わせが 1 本通った。受入基準 3 件は未達） |
| IT6 条件調整と荷主への通知 | [計画](iteration_plan-6.md) | [ふりかえり](retrospective-6.md) | [完了報告書](iteration_report-6.md) | **完了**（実績 8 SP・達成率 100%。引き継ぎ枠 3 件を繰越ゼロで返済。**実装して初めて分かった欠陥が 6 件**、うち 4 件は画面から踏むテストでしか出なかった。受入基準 3 点は未達。**並列レビューが返らないままクローズ**） |
| IT7 予約確定と追跡番号発行 | [計画](iteration_plan-7.md) | [ふりかえり](retrospective-7.md) | [完了報告書](iteration_report-7.md) | **完了**（実績 9 SP・達成率 100%。引き継ぎ枠 3 件を繰越ゼロで返済。**サービスをまたぐ連鎖が初めて通った**——契約コマンド 1 本目・trackingms の最初の集約・Reaction Handler の 1 本目。受入基準は未達 2・一部未達 2。**着手前の検証が「そもそも動かない計画」を 2 件止めた**） |
| IT8 追跡照会と手動更新 | [計画](iteration_plan-8.md) | [ふりかえり](retrospective-8.md) | [完了報告書](iteration_report-8.md) | **完了**（8/8 SP・100%）。**認証を通らない経路が初めて通った**。追跡番号を推測しにくい形式に（[ADR-0011](../../adr/cargo-tracker/0011-tracking-number-is-hard-to-guess.md)）、総当たりを回数で止め、荷主は自社の貨物だけが見える。レビュー高 14 件をクローズ前に修正 |
| IT9 荷役作業の記録 | [計画](iteration_plan-9.md) | [ふりかえり](retrospective-9.md) | [完了報告書](iteration_report-9.md) | **完了**（7/7 SP・100%。9 回連続）。**handlingms が動き出した**（新設 BC・契約 2 本・投影 3 つ・画面 2 つ）。予定外の荷役も拒まず記録し、追跡と予約へ連鎖する。**このプロジェクトで最も多くの実欠陥が見つかった IT**——着手前の検証で 3 件、レビューで高 10 件（5 件は複数視点が独立に指摘）、フルビルドと実クラスタで 4 件。**正典が実装不能だったものが 2 件**（[ADR-0012](../../adr/cargo-tracker/0012-cargo-snapshot-from-tracking-initialized.md)） |
| IT10 引取と遅延例外 | [計画](iteration_plan-10.md) | [ふりかえり](retrospective-10.md) | [完了報告書](iteration_report-10.md) | **完了**（7/7 SP・100%。10 回連続）。引き渡しが精算と予約へ伝わり、遅延例外の起票から解決まで通った。引き継ぎ枠 3 件 + 負債枠を**繰越ゼロで返済**（6 回連続）。**Try T3 を 3 回目でようやく守り**、US ごとのクラスタ E2E が 2 件の欠陥を出した。**クラスタ E2E は通しで 18 件すべて緑**（IT9 は 15/16）。クローズのレビューで**高 12 件**——5 件は複数視点が独立に指摘。**フルビルドが本物の欠陥を 2 件**出した |
| IT11 例外・誤配 | [計画](iteration_plan-11.md) | — | — | **開発完了**（10/10 SP）。US20 破損・紛失 / US28 誤配検知・再設計。**終盤の最初**——荷役 → 誤配検知 → 追跡の例外 → 予約の経路状態 → 現在地起点の再設計を 1 本で通した。引き継ぎ枠 A・B と負債枠 6 件を消化。**クラスタ E2E が実欠陥 2 件**を出した |
| IT12〜IT15 | 未作成 | — | — | 未着手 |

## 補足

- 実ドキュメントを追加したら、この一覧を更新します。
* [イテレーション 5 完了報告書](./iteration_report-5.md)
* [イテレーション 5 ふりかえり](./retrospective-5.md)
* [イテレーション 6 ふりかえり](./retrospective-6.md)
* [イテレーション 6 完了報告書](./iteration_report-6.md)
* [イテレーション 7 計画 - 予約確定と追跡番号発行](./iteration_plan-7.md)
* [イテレーション 7 完了報告書](./iteration_report-7.md)
* [イテレーション 7 ふりかえり](./retrospective-7.md)
* [イテレーション 8 ふりかえり](./retrospective-8.md)
* [イテレーション 9 完了報告書](./iteration_report-9.md)
* [イテレーション 9 ふりかえり](./retrospective-9.md)
* [イテレーション 10 計画](./iteration_plan-10.md)
* [イテレーション 11 計画](./iteration_plan-11.md)
* [イテレーション 10 完了報告書](./iteration_report-10.md)
* [イテレーション 10 ふりかえり](./retrospective-10.md)
