# ADR-003: 開発は H2、Repository のテストは Testcontainers を使う

ローカル開発の起動高速化には H2 を使い、SQL の正しさを検証する Repository のテストは Testcontainers（実 PostgreSQL）で行う。

日付: 2026-08-06（改訂: 2026-08-06）

## ステータス

承認済み

> **本 ADR は改訂されている。** 初版は「テスト用 DB を Testcontainers に一本化し H2 を採用しない」という決定だった。改訂の理由は[改訂履歴](#改訂履歴)を参照。

## コンテキスト

DB の使い分けが設計ドキュメント間で正反対に分かれていた。

| 立場 | ドキュメント |
| :--- | :--- |
| H2 採用 | `tech_stack.md`（バージョン・接続 URL まで明記）、`architecture_backend.md` |
| H2 不採用 | `test_strategy.md`、`data-model.md` |

さらに `data-model.md` は冒頭で H2 不採用を宣言しながら、判断 4・判断 7・Flyway 方針の 3 つを「H2 互換性」を根拠に下しているという**自己矛盾**を抱えていた。

決定にあたって考慮すべき点は 2 つある。

1. **方言差のリスク**: H2 の PostgreSQL 互換モードは方言を完全には再現しない。特に `TIMESTAMPTZ`・部分インデックス・`ON CONFLICT`・`NUMERIC` の丸め挙動に差がある。**H2 上で緑になったテストが本番で落ちうる**
2. **開発ループの速度**: ローカルでアプリを起動するたびに PostgreSQL コンテナの起動を待つのは、日常的な試行錯誤の回転を遅くする。TDD のサイクルは短いほどよい

初版は 1 を重く見て H2 を全廃したが、その結果**ローカル開発でも Docker 必須**という代償を負っていた。

## 決定

**用途で使い分ける。**

| 用途 | DB | 理由 |
| :--- | :--- | :--- |
| ローカルでのアプリ起動・画面の動作確認 | **H2**（PostgreSQL 互換モード、インメモリ） | 起動が速い。ここで検証したいのは画面遷移と業務の流れであり、SQL 方言ではない |
| ドメイン層・アプリケーション層のユニットテスト | **DB を使わない** | POJO とモックのみ。そもそも DB に依存しない |
| **Repository / MyBatis Mapper のテスト** | **Testcontainers（PostgreSQL 16）** | **SQL の正しさを検証する唯一の場所**。ここを H2 にすると方言差が本番障害として現れる |
| Controller の統合テスト（MockMvc） | Testcontainers（PostgreSQL 16） | Repository を経由するため |
| E2E（Playwright） | PostgreSQL（Docker Compose） | 本番に近い構成で通しの動作を見る |
| 本番・ステージング | PostgreSQL 16（RDS） | — |

**方言差のリスクは「SQL を検証する場所を実 PostgreSQL に固定する」ことで抑える。** H2 を使うのはアプリを起動して画面を触る場面に限り、**SQL の正しさを H2 で判断しない**。

### Flyway マイグレーションの構成

H2 と PostgreSQL の両方でスキーマを構築する必要があるため、Flyway の `{vendor}` プレースホルダで**共通部分とベンダー固有部分を分離する**。

```text
src/main/resources/db/migration/
├── common/          両方で実行される（テーブル定義の大半）
├── postgresql/      PostgreSQL でのみ実行（部分インデックス等）
└── h2/              H2 でのみ実行（互換用の代替定義。原則として空）
```

```yaml
spring:
  flyway:
    locations: classpath:db/migration/common,classpath:db/migration/{vendor}
```

- **テーブル定義は `common/` に置く。** ここが H2 と PostgreSQL で分岐すると、開発中に見ているスキーマと本番のスキーマが別物になる
- **PostgreSQL 固有の機能は `postgresql/` に隔離する。** 部分インデックス（`WHERE resolved_at IS NULL`）が該当する
- `h2/` は原則として空にする。ここにテーブル定義が増え始めたら、それは**共通部分が分岐している兆候**であり、設計を見直す合図とする

### 変更箇所

- `build.gradle`: H2 を `developmentOnly` に追加する（`runtimeOnly` にしない。本番の成果物に H2 を含めない）
- `application-local.yml`: H2（インメモリ、PostgreSQL 互換モード）に切り替える
- `application-local-postgres.yml` を追加する: 本番互換の確認が必要なときに Docker の PostgreSQL を使う
- `data-model.md`: 「PostgreSQL 固有の構文を制約なく使用してよい」を「共通部分は H2 互換、PostgreSQL 固有は `postgresql/` に隔離」に改める
- `test_strategy.md`: Repository テストは Testcontainers、ローカル起動は H2 という使い分けを明記する
- `tech_stack.md`: H2 を再掲する（用途を「ローカル開発のみ」と明記）

### 代替案

| 代替案 | 却下理由 |
| :--- | :--- |
| すべて Testcontainers（初版の決定） | ローカルでアプリを起動するたびにコンテナ起動を待つ。TDD の回転が落ちる代償が大きい |
| すべて H2 | SQL の正しさを検証する場所が無くなる。方言差が本番障害として現れ、**テストは緑なのに壊れる**という最悪の形になる |
| H2 を Repository テストにも使い、本番前に一度だけ PostgreSQL で通す | 「本番前の一度」で見つかった不具合は、直すコストが最も高い時点で見つかることになる |

## 影響

### ポジティブ

- ローカルでアプリを起動して画面を触るサイクルが速くなる。Docker の起動を待たない
- SQL の正しさは実 PostgreSQL で検証されるため、方言差起因の本番障害は防げる
- Docker が使えない環境でも、画面の動作確認だけなら進められる

### ネガティブ

- **マイグレーションが H2 互換の制約を受ける。** `common/` に置くテーブル定義は両方で動く構文に限られる
- **開発中に見ているスキーマと本番のスキーマが完全には一致しない。** `postgresql/` に隔離した定義はローカルでは適用されない
- H2 と PostgreSQL の 2 つの方言を把握し続けるコストがかかる
- **「ローカルでは動いたのに」が起きうる。** これは受け入れるリスクであり、だからこそ Repository テストを実 PostgreSQL に固定している

## コンプライアンス

- **H2 は `developmentOnly` 依存であること。** `bootJar` の中身に H2 が含まれていないことを確認する（含まれていたら本番で H2 に接続しうる）
- **Repository / Mapper のテストは必ず Testcontainers を使うこと。** H2 で Repository テストを書いていないことをレビューで確認する
- `db/migration/h2/` にテーブル定義が増えていないこと。増えていたら共通部分が分岐している
- `application-local.yml` の H2 は必ず PostgreSQL 互換モード（`MODE=PostgreSQL`）で使うこと

## 改訂履歴

| 日付 | 内容 |
| :--- | :--- |
| 2026-08-06 | 初版。「テスト用 DB は Testcontainers に一本化し H2 を採用しない」 |
| 2026-08-06 | 改訂。**開発ループの速度を理由に、ローカル起動用の H2 を復活させた。** ただし SQL の正しさを検証する Repository テストは Testcontainers のまま維持する。初版が守ろうとした「方言差で本番が壊れない」ことは、検証の場所を実 PostgreSQL に固定することで担保する |

初版の決定を覆した理由は、**初版が「方言差のリスク」だけを見て「開発ループの速度」を評価に入れていなかった**ことにある。両者はトレードオフであり、用途で使い分けることで双方を満たせる。

## 備考

- 著者: 設計レビュー（2026-08-06 マルチパースペクティブレビュー H11）、2026-08-06 改訂
- 関連 ADR: ADR-001（Java / Spring バージョン）、ADR-004（MyBatis 採用）
- 出典: `docs/review/設計ドキュメント_review_20260806.md` H11
