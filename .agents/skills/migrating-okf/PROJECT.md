# migrating-okf — 本プロジェクトでの設定

SKILL.md の汎用手順に対する、本プロジェクト固有の値です。食い違う場合は本ファイルを正とします。

## バンドル

| 項目 | 値 |
| :--- | :--- |
| バンドルルート | `docs/` |
| 仕様バージョン | 0.2（`docs/index.md` の `okf_version`） |
| 仕様リファレンス | `docs/reference/OKF導入ガイド_V0.2.md` |
| 配置規約 | `docs/reference/ドキュメント構成ガイド.md` |

## 検証コマンド

```bash
gulp okf:check              # 推奨。docs/ を検査する
gulp okf:help               # okf:upgrade・okf:viz などの一覧
python3 .claude/skills/migrating-okf/scripts/okf_check.py --check docs
```

`.claude/skills/` と `.agents/skills/` に同じスクリプトの複製があります。スクリプトを直したら**両方に反映**してください（Gulp タスクが参照するのは `.claude/` 側です）。

## 除外パス

除外は `docs/.okfignore` で宣言します（1 行 1 パターン、末尾 `/` でディレクトリ配下すべて、グロブ可）。`mkdocs.yml` の `exclude_docs` と対応させてください。

現在の除外：

- `article/source/` — 記事のサンプル実装ソースツリー。入れ子の `docs/` やサードパーティ由来の README を含むため、知識バンドルの対象にしません

## type の割り当て

ディレクトリから素直に導きます。文書ごとに新しい型を作りません。

| ディレクトリ | `type` |
| :--- | :--- |
| `docs/adr/` | `ADR` |
| `docs/strategy/` | `Strategy` |
| `docs/requirements/` | `Requirements` |
| `docs/design/` | `Design` |
| `docs/development/` | `Development` |
| `docs/operation/` | `Playbook` |
| `docs/review/` | `Review` |
| `docs/article/` | `Article` |
| `docs/reference/` | `Reference` |
| `docs/template/` | `Template` |

`tags` は `[<ディレクトリ名>]`、記事は `[article, <シリーズ名>]` を既定とします。

## generated

- 本文を変更していない移行では、`by` は git log の著者から `human:kakimomokuri`、`at` は本文を最後に変えたコミットの日時（UTC）
- エージェントが本文を書いた場合のみ `claude-code/<model>` を使います
- `verified` は、人またはプロセスが実際に内容を確認したときだけ付けます。移行作業でエージェントが読んだだけでは付けません

## 意図的に残している WARN

| 対象 | 内容 | 理由 |
| :--- | :--- | :--- |
| `template/まずこれを読もうリスト.md` | リンク切れ 10 件 | コピー先のプロジェクトを基準にしたパスのため。バンドル内には対応先が存在しません |
