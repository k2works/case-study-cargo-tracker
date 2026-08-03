---
title: arch-lint 規約仕様
description: アーキテクチャ規約の機械検査（arch-lint）の検出方法・既知の例外・メタテストの仕様。実装前に確定させる。
published: true
date: 2026-08-17T00:00:00.000Z
tags: design, architecture, arch-lint, flix
---

# arch-lint 規約仕様

## 目的

ArchUnit が使えない Flix において、アーキテクチャ規約を機械的に検査する（[ADR-0002](../adr/ADR-0002-self-built-web-and-security.md)）。

本ドキュメントは**実装前に検出方法と既知の例外を確定させる**ためのものである。
IT1 のレビューで「規約 5 の文言が実装・設計自身のコード例と矛盾する」ことが判明したため、
実装者が字義どおりに実装して大量の偽陽性を出す事態を防ぐ（IT1 ふりかえり Try T4）。

## 基本方針

| 方針 | 内容 |
| :--- | :--- |
| **レイヤ判定はディレクトリパスで行う** | モジュール名（`SharedDbPool` 等）ではなくファイルパスで判定する。Flix の制約でモジュール名がフラットなため |
| **AST 解析は行わない** | 正規表現による行単位の走査に留める。Flix のパーサを持たないため。誤検出を避けるため、規約は「検出できる形」に定義する |
| **例外は許可リストで管理する** | コード内コメントによる抑制（`// arch-lint-ignore`）は設けない。例外は本ドキュメントと設定ファイルに集約し、レビュー可能にする |
| **偽陰性より偽陽性を許容する** | 検出漏れ（規約違反の見逃し）の方が有害。迷ったら検出する側に倒す |

## レイヤの定義（ディレクトリパス）

```text
apps/cargo-tracker/src/<context>/domain/**          → domain
apps/cargo-tracker/src/<context>/application/**     → application
apps/cargo-tracker/src/<context>/infrastructure/**  → infrastructure
apps/cargo-tracker/src/<context>/interfaces/**      → interfaces
apps/cargo-tracker/src/shared/**                    → shared（レイヤはさらに配下で判定）
apps/cargo-tracker/src/Main.flix                    → composition-root
apps/cargo-tracker/test/**                          → test（規約の対象外）
```

`<context>` は `booking`・`shipper`・`estimation`・`routing`・`tracking`・`handling`・`billing`・`shared` のいずれか。

## モジュール名 → レイヤの対応表

検査対象ファイルが宣言するモジュール名を収集し、参照側でどのレイヤを参照しているか判定するために使う。

```text
1. 全 .flix ファイルを走査し「モジュール名 → ファイルパス → レイヤ」の索引を作る
2. 各ファイルの use 宣言・修飾参照から参照先モジュール名を抽出する
3. 索引でレイヤへ解決し、規約に照らす
```

**解決できないモジュール名**（標準ライブラリ・Java パッケージ）は規約 2 を除き無視する。

---

## 規約一覧

### 規約 1: `domain/**` は `infrastructure/**`・`interfaces/**` を参照しない

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `**/domain/**/*.flix` |
| 検出方法 | `use <Module>` および `<Module>.<name>` 形式の修飾参照を抽出し、参照先レイヤが `infrastructure` / `interfaces` なら違反 |
| 正規表現 | `^\s*use\s+([A-Z][A-Za-z0-9]*)` と `\b([A-Z][A-Za-z0-9]*)\.[a-z]` |
| 既知の例外 | なし |
| 違反時のメッセージ | `domain 層のファイルが <レイヤ> 層のモジュール <名前> を参照しています` |

### 規約 2: `domain/**` は `java.**` を参照しない

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `**/domain/**/*.flix` |
| 検出方法 | `import java.` または `import javax.` で始まる行があれば違反 |
| 正規表現 | `^\s*import\s+(java\|javax)\.` |
| 既知の例外 | なし。Java 相互運用はインフラ層に閉じる |
| 補足 | Flix 標準ライブラリの型（`Option`・`Result`・`List`）は import 不要のため対象外 |

### 規約 3: `application/**` は `infrastructure/**` を参照しない

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `**/application/**/*.flix` |
| 検出方法 | 規約 1 と同様。参照先レイヤが `infrastructure` なら違反 |
| 既知の例外 | なし。アプリケーション層は効果宣言（`domain/port`）経由でのみ結合する |

### 規約 4: 異なる Bounded Context 間で直接参照しない

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `src/<context>/**/*.flix`（`shared` を除く） |
| 検出方法 | 参照先モジュールのコンテキストが自コンテキストと異なり、かつ `shared` でもない場合は違反 |
| 既知の例外 | `shared`（共有カーネル）への参照は許可。ACL・ドメインイベント経由の連携は、それ自体が自コンテキスト内のモジュールを経由するため検出されない |
| 補足 | IT2 時点では `tracking` と `shared` のみ存在するため実質的に発火しない。IT4 以降で効く |

### 規約 5: 効果ハンドラの**合成**は合成ルートとテストにのみ現れる

**IT1 のレビューで再定義した規約**（[IT1 実装レビュー](../review/IT1実装_review_20260814.md) H2）。

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `src/**/*.flix`（`shared/infrastructure/runtime/**` を除く） |
| 検出方法 | **ハンドラ適用関数の呼び出しが 2 段以上入れ子になっている**箇所を検出する |
| 正規表現 | `with<PascalCase>\s*\([^)]*\(\)\s*->\s*with<PascalCase>` および `readOnly\s*\([^,]+,\s*\(\)\s*->\s*with` |
| 既知の例外 | `shared/infrastructure/runtime/**`・`test/**` |
| **検出しないもの** | 単一のハンドラを定義・適用するラップ関数（`withJdbcReadDb`・`readOnly` の定義そのもの）。これらは対応するアダプタのディレクトリに置いてよい |

> **なぜこの定義か**: 「`run ... with handler` の出現箇所」で定義すると、アダプタ側のラップ関数
> （`infrastructure/repositories/JdbcReadDb.flix` の `withJdbcReadDb`）まで違反になる。
> 設計の意図は「実装の差し替え可能性を保つ」ことであり、それを壊すのは**合成**の位置である。

### 規約 6: `domain/**`・`application/**`・`interfaces/**` に `run ... with handler` が出現しない

規約 5 を補完し、レイヤ違反としてのハンドラ適用を検出する。

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `**/domain/**`・`**/application/**`・`**/interfaces/**` |
| 検出方法 | `with handler` を含む行があれば違反 |
| 正規表現 | `\bwith\s+handler\b` |
| 既知の例外 | なし |
| 補足 | コメント行（`///` `//` で始まる行）は除外する。IT1 で合成ルートのコメントに `run ... with handler` の記述があり、誤検出の原因になった |

### 規約 7: `Html.RawUnsafe` の使用箇所が許可リストに含まれる

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `src/**/*.flix`（`shared/infrastructure/html/Html.flix` を除く） |
| 検出方法 | `RawUnsafe` を含む行を抽出し、許可リストにないファイルなら違反 |
| 正規表現 | `\bRawUnsafe\b` |
| 既知の例外 | `shared/infrastructure/html/Html.flix`（ADT の定義と `render` の実装） |
| 現状 | **使用箇所 0 件**。「0 件であること」を検査する |

### 規約 8: 状態を変える `<form>` を `Element("form", ...)` で直接構築しない

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `src/**/*.flix`（`shared/infrastructure/html/Components.flix` を除く） |
| 検出方法 | `"form"` を第 1 引数とする要素構築のうち、`attr("method", "get")` を**明示していない**ものを検出する |
| 正規表現 | `element\s*\(\s*"form"` および `Html\.Element\s*\(\s*"form"`（免除: `attr\s*\(\s*"method"\s*,\s*"get"\s*\)`） |
| 既知の例外 | `shared/infrastructure/html/Components.flix`（`Components.form` の実装） |
| 有効化時期 | TS04（IT2）で `Components.form` を実装済み。**有効** |

**GET フォームを免除する理由**（IT5 で追加）:

この規約の目的は CSRF トークンの付け忘れを防ぐことである。CSRF は
「他サイトから利用者の権限で**状態を変えさせられる**」攻撃であり、
状態を変えない GET フォーム（絞り込み・検索）には当てはまらない。

GET フォームに `Components.form` を使うと、意味のない `_csrf` 隠しフィールドが
URL のクエリへ載る。**要らない防御を足すと、要る防御との区別が付かなくなる。**

免除するのは `method="get"` を**明示**したものだけとする。HTML の `form` は
`method` を省くと GET になるが、書き手がそれを意図したかは読み取れず、
POST のつもりで書き忘れた可能性と区別できない。

### 規約 9: SQL 文字列の連結を行わない

[テスト戦略](test_strategy.md) 3.3 の規約 6 に相当（番号は本ドキュメントで振り直している）。

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `src/**/*.flix` |
| 検出方法 | SQL キーワードを含む文字列リテラルに `${...}` 補間が含まれる場合は違反 |
| 正規表現 | `"[^"]*\b(SELECT\|INSERT\|UPDATE\|DELETE\|MERGE)\b[^"]*\$\{` |
| 既知の例外 | なし（IT1 で `tableExists` を `DatabaseMetaData` ベースへ書き換え、例外が不要になった） |
| **検出しないもの** | 文字列の `+` 連結による SQL の組み立て。定数同士の連結（`"SELECT ... " + "FROM ..."`）は可読性のために許容する。**変数の埋め込みのみを禁止**する |

> **規約番号について**: 規約 5 の再定義に伴い規約 6（レイヤ違反としてのハンドラ適用）を新設したため、
> 旧「SQL 文字列連結」は規約 9 へ移した。[テスト戦略](test_strategy.md) 3.3 の一覧も
> 同じ番号へ揃えている（IT2）。本ドキュメントを実装の正典とする。

### 規約 10: `shared` は Bounded Context を参照しない

**IT2 のレビューで追加した規約**（[IT2 実装レビュー](../review/IT2実装_review_20260828.md) H9）。

| 項目 | 内容 |
| :--- | :--- |
| 対象 | `src/shared/**`（合成ルートを除く） |
| 検出方法 | 参照先モジュールのコンテキストが `shared` 以外なら違反 |
| 既知の例外 | `src/composition/**`・`Main.flix`（BC を配線するのが役目のため） |
| 違反時のメッセージ | `共有カーネルが Bounded Context (<名前>) のモジュール <名前> を参照しています` |

> **なぜ必要か**: 規約 4（BC 間の直接参照）は `shared` を一律に対象外としているため、
> 共有カーネルが特定の BC に依存し始めても検出できなかった。IT2 時点では合成ルートが
> `shared/infrastructure/runtime/` にあり、この免除なしでは合成ルート自体が違反になる状態だった。
> **合成ルートを `src/composition/` へ移し、`shared` の免除を外した**。

---

## レイヤの定義の補足

```text
apps/cargo-tracker/src/composition/**                → composition-root
```

合成ルートは BC・共有カーネルのいずれにも属さない独立したディレクトリに置く。

---

## メタテスト（`arch-lint` 自身の検証）

検査器にバグがあれば「検査をパスしているのに違反している」状態がサイレントに発生する。
そのため `arch-lint` 自身に回帰テストを設ける（[ADR-0002](../adr/ADR-0002-self-built-web-and-security.md) の補償策）。

### フィクスチャの構成

```text
ops/scripts/arch-lint/fixtures/
├── violations/          # 負例: 各規約に違反するファイル。すべて検出されなければならない
│   ├── rule01-domain-references-infrastructure.flix
│   ├── rule02-domain-imports-java.flix
│   ├── rule03-application-references-infrastructure.flix
│   ├── rule04-cross-context-reference.flix
│   ├── rule05-handler-composition-outside-runtime.flix
│   ├── rule06-with-handler-in-application.flix
│   ├── rule07-rawunsafe-outside-allowlist.flix
│   ├── rule08-form-element-directly.flix
│   ├── rule08-form-element-multiline.flix          # 複数行に分けた違反
│   ├── rule09-sql-string-interpolation.flix
│   └── rule09-sql-interpolation-multiline.flix     # 複数行に分けた違反
└── conformant/          # 正例: 規約に適合するファイル。1 件も検出されてはならない
    ├── rule01-domain-references-shared.flix
    ├── rule02-infrastructure-imports-java.flix     # インフラ層の java import は適法
    ├── rule03-application-references-domain-port.flix
    ├── rule04-same-context-reference.flix          # 同一 BC 内の参照は適法
    ├── rule05-handler-wrapper-in-adapter.flix      # アダプタ配下の単一ハンドラ定義は適法
    ├── rule06-with-handler-in-infrastructure.flix
    ├── rule07-rawunsafe-in-html-module.flix
    ├── rule08-form-in-components.flix
    ├── rule09-sql-constant-concatenation.flix      # 定数同士の連結は適法
    └── rule09-sql-multiline-constants.flix         # 複数行の定数連結も適法
```

> **複数行の違反について**: 検査は継続行を「論理行」へ畳んでから照合する。
> 行単位の照合では、SQL を `"..." +` で改行して連結する本プロジェクトの書き方が
> そのまま検出漏れになるため（IT2 レビューで実測）。

### 判定基準

| 条件 | 期待結果 |
| :--- | :--- |
| `violations/` の各ファイル | 対応する規約で 1 件以上検出され、**それ以外の規約は検出されない** |
| `conformant/` の各ファイル | **1 件も検出されない** |

正例の誤検出（偽陽性）は、開発者が `arch-lint` を信用しなくなる直接の原因になるため、
負例の検出と同等に重視する。

## 実行方法

```bash
npm run arch:lint          # 検査を実行（違反があれば終了コード 1）
npm run arch:lint:test     # メタテスト（フィクスチャによる自己検証）
npm run arch:check         # メタテスト → 検査（**CI が実行するのはこれ**）
```

CI では両方を実行する。メタテストが失敗した場合、検査結果そのものが信用できないため
`arch-lint` の結果に関わらず PR を赤にする。

## 更新履歴

| 日付 | 更新内容 |
| :--- | :--- |
| 2026-08-17 | 初版作成（IT2 タスク 0.2。ふりかえり Try T4） |
| 2026-08-28 | テスト戦略 3.3 の規約番号を本ドキュメントへ揃えた。規約 8 は `Components.form` 実装により有効化済み |
| 2026-08-31 | 規約 10（`shared` は BC を参照しない）を追加。合成ルートを `src/composition/` へ移設（IT3） |
| 2026-08-03 | 規約 8 を「状態を変える form」に限定。`method="get"` を明示した検索フォームを免除（IT5 TS09） |
