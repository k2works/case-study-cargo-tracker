# 第 9 章：ランタイムスタックと環境戦略

| 項目 | 内容 |
| :--- | :--- |
| 観点 | テクノロジーアーキテクチャ |
| 一次資料 | `docs/design/tech_stack.md`・`architecture_infrastructure.md`・`architecture_frontend.md`・ADR-001 / 003 / 006 / 011 |
| 主題 | 何の上で動かし、環境差をどう扱うのか |

## スタック

| 領域 | 技術 | バージョン |
| :--- | :--- | :--- |
| 言語 | Java | 25（LTS） |
| フレームワーク | Spring Boot | 4.1.0 |
| 永続化 | MyBatis（`mybatis-spring-boot-starter`） | 4.1.0 |
| マイグレーション | Flyway（`flyway-core` ＋ `flyway-database-postgresql`） | — |
| DB | PostgreSQL | 16.x |
| 画面 | Thymeleaf（SSR）＋ htmx 2.0.4 ＋ Bootstrap 5.3.8 | — |
| API ドキュメント | springdoc-openapi | 3.1.0 |
| ビルド | Gradle（Groovy DSL） | 9.7.0 |
| コンテナ | Docker（`eclipse-temurin:25-jdk` → `25-jre`） | — |

**Java 25 と Spring Boot 4.1 は、いずれも執筆時点の最新 LTS 系です。** ADR-001 はこう記録しています。

> グリーンフィールドであるため、Java 21 + Spring Boot 3.4 で開始して後から移行する案は採らない。

**移行コストを先に払わない**判断です。既存資産がなく、ライブラリの対応状況を確認できるなら、最新の LTS から始めるほうが総コストは小さくなります。

ただしこの判断は、**エコシステムが追いついていることの確認を前提**にしています。`tech_stack.md` は主要ライブラリごとに「Spring Boot 4 対応済み」を明記しており（MyBatis 4.1.0・springdoc 3.1.0・Testcontainers 1.21.4）、確認したうえでの判断であることが読み取れます。

## 画面 — SSR ＋ htmx

**SPA を採用していません。** Thymeleaf によるサーバーサイドレンダリングに、htmx で部分更新を足す構成です。

| 判断 | 内容 |
| :--- | :--- |
| Thymeleaf（SSR） | Spring Boot との統合、シンプルな構成 |
| htmx 2.0.4 | 追跡ステータスの自動更新・フォーム検証などを **JS 最小で**実現する |
| Bootstrap 5.3.8 | 業務系コンポーネントが揃っており、学習コストが低い |
| WebJars | フロントのビルドパイプラインを持たない |

**この選択は、アプリケーションアーキテクチャに影響を与えています。**

SPA なら、画面が必要とするデータは REST API の JSON になります。API の設計が画面と独立し、DTO 変換の層が要ります。

SSR なら、Controller が直接 `BookingView` をテンプレートに渡します。**第 8 章で見た「読み取り側のビューが表示のための述語を持つ」という設計は、SSR だからこそ自然に成立しています。** JSON にシリアライズするなら、`isRouted()` のような述語メソッドは値としてしか渡せません。

**フロントエンドのビルドを持たないことも効いています。** WebJars で CSS / JS を配信するため、Node.js のビルドパイプラインがありません。**デプロイの成果物は JAR 1 つ**です。

## DB を用途別に 3 つ使い分ける（ADR-003）

**この題材でいちばん実務的な判断です。**

| 用途 | DB |
| :--- | :--- |
| ローカルでのアプリ起動・画面確認 | **H2**（PostgreSQL 互換モード、インメモリ） |
| **Repository / MyBatis Mapper のテスト** | **Testcontainers（実 PostgreSQL 16）** |
| Controller 統合テスト・E2E | PostgreSQL |
| 本番・ステージング | RDS PostgreSQL 16 |

> 転記元：`docs/design/tech_stack.md`「DB の使い分け（ADR-003）」

そして 1 行の規律が付きます。

> **SQL の正しさを H2 で判断しない。** 方言差（`TIMESTAMPTZ`・部分インデックス・`NUMERIC` の丸め）が
> 本番障害として現れるため、SQL を検証する場所は実 PostgreSQL に固定する。
> H2 は `developmentOnly` 依存とし、本番の成果物に含めない。

**H2 が担うのは「起動の速さ」だけです。** 画面を触るサイクルを短くするために使い、SQL の正しさは判断しません。

### 方言差は両方向に出る

第 7 章で見た部分インデックスは「PostgreSQL にあって H2 に無い」機能でした。**逆方向も起きます。**

一次資料が記録している実例です。

> 実装時に `CLOB` が PostgreSQL に存在せず失敗したため `TEXT` に変更した経緯がある
> （`TEXT` は H2 でも受け付けられる）。**片方でしか動かない型は、もう片方で起動して初めて分かる。**

**「H2 で書いたら PostgreSQL で落ちた」**という向きです。ローカルが緑でも本番が赤になります。

この非対称性への対策として、`H2DialectSmokeTest` / `H2DialectCoverageTest` というテストが用意されています。**全クエリを H2 で実行して「解釈できるか」だけを見る**スモークテストです。結果の正しさは検証しません——それは実 PostgreSQL の仕事だからです。

**環境を分けたら、分けた両方で「解釈できること」を検査する。** 環境差を許容する代わりに払うコストです。

### `developmentOnly` と `verifyProductionDependencies`

H2 を本番成果物から外す仕組みは 2 段です。

1. Gradle の `developmentOnly` 設定に置く（`bootJar` に含まれない）
2. **`verifyProductionDependencies` というカスタムタスク**が、本番クラスパスから H2 / WireMock / spring-cloud-contract / hibernate-orm / jakarta.persistence を排除していることを検査する

**2 段目があるのは、1 段目が設定ミスで崩れうるからです。** `implementation` に書き換えれば H2 は本番に入ります。それを検査が防ぎます。

**排除対象に `hibernate-orm` と `jakarta.persistence` が入っている**のに注目してください。ADR-004（MyBatis を採用し JPA を採用しない）を、**依存の存在そのもので検査しています**。誰かが JPA を使い始めるには、まずこのタスクを赤にする必要があります。

## コンテナ — マルチステージビルド

```dockerfile
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

# 依存解決の結果をレイヤーキャッシュに載せるため、
# ビルド定義だけを先にコピーする。ソース変更のたびに依存を取り直さない。
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY config ./config
COPY src ./src

# テストは Docker build では実行しない。
# Repository のテストは Testcontainers を必要とし、
# Docker-in-Docker が前提になるためイメージビルドには載せない。

ARG INCLUDE_H2=false
RUN if [ "$INCLUDE_H2" = "true" ]; then \
        ./gradlew --no-daemon bootJar -x test -PincludeH2=true; \
    else \
        ./gradlew --no-daemon bootJar -x test; \
    fi

FROM eclipse-temurin:25-jre

# 非 root ユーザーで実行する
RUN groupadd --system --gid 1001 cargotracker \
    && useradd --system --uid 1001 --gid cargotracker --create-home cargotracker

WORKDIR /app
COPY --from=builder --chown=cargotracker:cargotracker /build/build/libs/*.jar app.jar
USER cargotracker

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENV SPRING_PROFILES_ACTIVE=dev

CMD ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar app.jar"]
```

> 転記元：`apps/cargo-tracker/Dockerfile`

読みどころが 4 つあります。

**（1）テストをイメージビルドで実行しない。** 理由が書かれています——Repository のテストは Testcontainers を必要とし、Docker-in-Docker が前提になるからです。**テスト戦略が DB の実物を要求した結果、ビルドの構造が決まっています。**

**（2）`ARG INCLUDE_H2=false` — 既定は安全側。**

> 開発環境（Heroku）のイメージは bootJar を H2 で起動するため、H2 を実行時依存に含める。
> ADR-003 により H2 は既定で `developmentOnly` としているため、**開発環境イメージだけが明示的に opt-in する**。
> **既定は false（安全側）。本番向けイメージは既定のままにすること。**

**「入れる」を明示的な操作にし、「入れない」を既定にしています。** 忘れたときに安全側に倒れる設計です。

**（3）ヒープをコンテナのメモリに追従させる。** `-XX:MaxRAMPercentage=75.0` は絶対値でヒープを指定しません。dyno やタスクのメモリが変わっても追従します。

**（4）非 root ユーザーで実行する。** uid 1001 の専用ユーザーを作り、JAR の所有者もそのユーザーにしています。

## 設計はAWS、実配備はHeroku

**ここに、一次資料と実装のいちばん大きな差があります。**

`architecture_infrastructure.md` は AWS を前提に設計されています。

| 設定項目 | ローカル | ステージング | 本番 |
| :--- | :--- | :--- | :--- |
| DB | Docker PostgreSQL | RDS（Single-AZ） | RDS（Multi-AZ） |
| ECS タスク数 | - | 1 | 2〜6（Auto Scaling） |
| Spring Profile | `local` | `staging` | `production` |
| Flyway Clean | 許可 | 禁止 | 禁止 |
| シークレット管理 | `.env` ファイル | AWS Secrets Manager | AWS Secrets Manager |

> 転記元：`docs/design/architecture_infrastructure.md`「環境別設定一覧」

**Dockerfile は Heroku Container Runtime 向けです。**

```dockerfile
# 制約:
#   - Heroku Container Runtime は x86_64 イメージのみをサポートする。
#     Apple Silicon では --platform linux/amd64 を指定してビルドすること。
#   - web プロセスは Heroku が注入する $PORT で listen しなければならない。
#   - HEALTHCHECK は Heroku Container Runtime では使用されない。
```

`ENV SPRING_PROFILES_ACTIVE=dev` であり、設計上の `staging` / `production` プロファイルではありません。

**これは設計の失敗ではなく、到達点の違いです。** 設計文書は「本番運用するならこうする」を書いており、実配備は開発環境の公開にとどまっています。ECS Fargate・RDS Multi-AZ・Secrets Manager・Auto Scaling は、**いずれも実際には動いていません**。

**読む側が区別すべきなのは次の点です。**

| 項目 | 状態 |
| :--- | :--- |
| コンテナ化・非 root 実行・ヒープ追従 | **実装済み・稼働中** |
| Flyway の環境別 locations（seed / demo の分離） | **実装済み・稼働中** |
| ECS Fargate / RDS Multi-AZ / ALB / Secrets Manager | **設計のみ**（Terraform 構成も含め、java-2 配下に実体なし） |
| GitHub Actions の CI/CD パイプライン | **設計のみ**（java-2 配下にワークフロー定義なし） |

**設計文書に書かれた構成を「動いている」と読まないこと**が、この題材を参照する際の最大の注意点です。第 11 章で総括します。

## 外部連携を持たない構成の帰結（ADR-006）

第 1 章・第 3 章で触れた ADR-006 は、テクノロジースタックにも表れています。

> **本システムは外部システムと HTTP 連携しない**（ADR-006）。経路算出・通関・決済・港湾・通知はいずれも内部シミュレーションで代替する。
>
> そのため、外部 ACL ポート、Spring WebClient による外部 API クライアント、
> WireMock による契約テストはいずれも採用しない。
>
> 転記元：`docs/design/tech_stack.md`「外部システム連携技術」

**採用しない技術を明記している**のが特徴です。通常、技術スタック表には採用したものだけが並びます。ここでは「なぜ WebClient が無いのか」が読めます。

さらに **`verifyProductionDependencies` が WireMock と spring-cloud-contract を本番クラスパスから排除**しています。採用しない判断が、依存の検査にまで落ちています。

**復帰の手順も残されています。**

> 将来、実際の連携先が定まった時点で ADR-006 を改訂し、`test_strategy.md` に記載の復帰手順に従って導入する。

**「やらない」判断に、やることになったときの手順を添える**という形です。

## 公開エンドポイントの防御（ADR-011）

荷受人はアカウントを持たず、公開の追跡画面を使います（第 2 章）。**認証で守れない入口**です。

ADR-011 の決定は「公開エンドポイントの防御を単一プロセス内のレートリミットで行う」であり、実装は `shared/infrastructure/web/PublicRateLimitFilter` です。

**「単一プロセス内の」という限定が正直です。** 複数インスタンスに水平展開すると、レートリミットはインスタンスごとに独立します。分散環境では Redis 等の共有ストアが要ります。

**いま単一プロセスで動いている以上、いま有効な防御を入れ、限界を ADR に書く**——という判断です。「分散対応していないから入れない」でも「分散でも効くふりをする」でもありません。

## 依存をロックする

```groovy
dependencyLocking { lockAllConfigurations() }
```

`gradle.lockfile` が全依存のバージョンを固定します。理由が `tech_stack.md` にあります。

> **依存はロックしている。** 脆弱性スキャン（Trivy）が Gradle の依存を見るために必要であり、
> 無いとスキャンは 0 件で緑になる。**「緑だが何も検査していない」はスキャンが無いより危険である。**

**「緑だが何も検査していない」——本シリーズを通じて何度も現れる形です。**

- ArchUnit の `allowEmptyShould(true)` を使わない（第 10 章）
- 名簿に無いテーブルを通さず赤にする（第 7 章）
- 依存をロックしないとスキャンが 0 件で緑になる（本章）

**検査が「何も見つからなかった」と「何も見ていない」を区別できないとき、後者が前者を装います。** どの仕組みも、この 1 点への対処です。

BOM の扱いも書かれています。

> Boot の BOM が管理する版に脆弱性が残る場合は、`dependencyManagement` で修正版に上書きする。
> **Boot が追いついたら上書きを削除する**（残すと古い方に固定してしまう）。

**上書きは一時的な措置であり、消す条件を書いておく**という規律です。

## この章の要点

| 観察 | 内容 |
| :--- | :--- |
| Java 25 / Boot 4.1 | グリーンフィールドなら移行コストを先に払わない。**エコシステムの対応確認が前提** |
| SSR ＋ htmx | ビューが述語を持てる。**フロントのビルドを持たず成果物は JAR 1 つ** |
| DB を 3 つ使い分け | H2 は起動の速さのみ。**SQL の正しさは実 PostgreSQL でしか判断しない** |
| 方言差は両方向 | 「H2 で書いたら PostgreSQL で落ちた」も起きる。**両方で解釈できるかを検査する** |
| `INCLUDE_H2=false` | **既定を安全側に置き、危険側を明示的な操作にする** |
| 採用しない技術を書く | WebClient も WireMock も無い理由が読める。**復帰手順まで残す** |
| ADR-011 の限定 | 「単一プロセス内の」と書く。**限界を隠さず、いま効く防御を入れる** |
| 依存ロック | 無いとスキャンが 0 件で緑。**「緑だが何も検査していない」への対処** |
| 設計と実配備の差 | AWS 構成は**設計のみ**。動いているのは Heroku 上の開発環境 |

次章では、この技術基盤がアプリケーションアーキテクチャを縛り返す仕組み——検査の全体像を見ます。
