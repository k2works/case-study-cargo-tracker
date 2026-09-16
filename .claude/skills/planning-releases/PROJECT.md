# planning-releases — 本プロジェクトでの設定

SKILL.md の汎用手順に対する、国際貨物輸送管理システム固有の値です。本プロジェクトは AI-DLC を採用しているため、SKILL.md の XP 版の用語は `docs/reference/リリース・イテレーション計画ガイド_AI-DLC版.md` 第 8 章の読み替えに従います。食い違う場合は本ファイルを正とします。

## ファイルの置き場所

| 成果物 | パス |
| :--- | :--- |
| Unit 定義・依存 DAG・ストーリーマップ | `docs/requirements/units.md` |
| リスク台帳 | `docs/requirements/risk_register.md` |
| リリース計画 | `docs/development/release_plan.md` |
| Bolt 計画（イテレーション計画の読み替え） | `docs/development/bolt_plan-N.md`（N は `release_plan.md` の Bolt 番号） |
| AI-DLC 移行計画 | `docs/development/aidlc_migration_plan.md` |

`iteration_plan-N.md` は新しく作らない。

## 見積もりと計画

- ストーリーポイントは付けていない。Unit ごとのエントロピー評価（意図・構造・検証・リスク・仮定を LOW／MED／HIGH）で見積もる。評価は `units.md` と `release_plan.md` の両方にあり、`release_plan.md` の表を正とする
- 見直しは Bolt の承認ゲート通過時に行う。進捗の遅れだけでは再評価しない（遅れの原因が人の立ち会い時間なら、集中時間の確保を見直す）
- 本番機能のテスト戦略は Standard 未満にしない
- Bolt の全体の番号と並びは `release_plan.md`、Unit 内の順序は `units.md` の「推奨 Bolt」。両者を変えたら同時に直す

## Bolt 計画の書き方

- Bolt ゴールには「確認したい仮説」を入れる（`release_plan.md` の Bolt の並びの列をそのまま使う）
- ゴールとステップを一致させる。「UI から DB まで通す」ならその層のステップをすべて含める
- `release_plan.md` の「着手前に解消する要確認」に載っている Bolt は、要確認が解消するまで計画を承認に出さない
- ストーリーの「AI の仮定」のうち、その Bolt で確かめるものを Bolt 計画の「AI の仮定」に写す

## ゲート密度

- 既定は「各ステップでゲート」
- Bolt 1（ウォーキングスケルトン）の結果で見直し、エントロピー評価に HIGH の無い Unit（U-02・U-04）から下げる
- リスクが HIGH の Unit（U-01・U-03・U-05）は、確認必須の操作を含むステップで必ず停止する

## 進捗の測り方

ベロシティの代わりに、完了 Unit 数（Deployment Unit として検証済みのみ）・承認ゲート通過数・手戻り率（変更依頼数 ÷ 通過数）・リードタイムを記録する。
