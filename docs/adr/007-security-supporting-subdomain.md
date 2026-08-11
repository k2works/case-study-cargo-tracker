# ADR-007: 認証・認可を支援サブドメイン `security` に置く

## ステータス

承認（2026-08-06 / IT1）

## コンテキスト

IT1 で認証・認可（US26 / US27 / US31）を実装するにあたり、`UserAccount` 集約と `Role` 列挙型の
置き場所を決める必要があった。当初は共有カーネル（`shared`）配下に置いた。

**「全 BC から使うから `shared` へ」は常に正しく聞こえる。** しかし ADR-005 は共有カーネルの
構成要素を `Location` と `ShipperId` の 2 つのみと定めており、その理由は「共有カーネルは
最も変更コストが高く、1 クラス増えるたびに全 BC の再ビルドとレビューを強制する」ことにある。

`UserAccount` を共有カーネルに置くと、ロールを 1 つ増やすだけでこのコストが全 BC に発生する。
RBAC のロールは業務の担当が変わるたびに増減するため、**共有カーネルの中で最も変更頻度が
高い要素になる**。これは共有カーネルを 2 要素に絞った判断と正面から矛盾する。

一方で、認証・認可は貨物輸送という業務そのものを表さない。予約・経路・追跡・精算のような
業務の境界付けられたコンテキストとして扱うのも実態に合わない。

## 決定

**認証・認可を支援サブドメイン `security` として、独立したトップレベルパッケージに置く。**

```text
com.example.cargotracker.security/
├── domain/model/         UserAccount, Role
├── domain/repository/    UserAccountRepository
└── infrastructure/
    ├── config/           SecurityConfig, CargoTrackerUserDetailsService,
    │                     AuthenticationAuditListener
    └── repositories/     MyBatisUserAccountRepository
```

判断の要点は 3 つある。

1. **共有カーネルには置かない。** 全 BC から使うことは、共有カーネルに置く理由にならない。
   共有カーネルの範囲は ADR-005 のとおり `Location` と `ShipperId` に限る。
2. **業務の BC としては扱わない。** 貨物輸送の業務を表さないため、コンテキストマップ上でも
   支援サブドメインとして区別する。
3. **`application` 層を持たない。** ユースケースは Spring Security のフィルタチェーンが担い、
   本サブドメインが提供するのは「誰がログインできるか」の判断のみである。
   正典のフル構成（`interfaces` / `application` / `domain` / `infrastructure`）から
   意図的に外れる。

`UserDetailsService` と認証イベントの購読は `infrastructure/config/` に置く。これらは
Spring Security への適合（アダプタ）であり、業務のユースケースではない。

## 何がどこで守るか

| 守るもの | 守り手 |
| :--- | :--- |
| `UserAccount` を共有カーネルに置かない | **`PackageStructureTest.共有カーネルはLocationとShipperIdのみ`** |
| `security` がトップレベルパッケージであること | **`PackageStructureTest.すべてのクラスはBC集合のいずれかに属する`** |
| `security` は `application` 層を持たない | **守らない。** 正典のフル構成から意図的に外れる決定だが、`security/application/` を作っても検査は落ちない。**意図的な逸脱ほど、次の人には「書き忘れ」に見える** |

## 影響

### ArchUnit

- ルール 5（トップレベルパッケージ = BC 集合）の許可リストに `security` を追加する
- ルール 4（BC 間の直接参照禁止）を有効化する際は、`security` を `shared` と同様に
  除外する。**認可は全 BC の入口に横断的に効くため、`security` への参照は BC 間の結合ではない**
- ルール 6（共有カーネルの範囲）の検査対象は `shared.domain.model` である。
  `shared.infrastructure` 配下は横断的な技術基盤であり共有カーネルではない

### ポジティブ

- 共有カーネルが 2 要素に保たれる。ロールの増減が全 BC の再ビルドを強制しない
- 認証・認可の変更が 1 つのパッケージに閉じる
- 次に支援サブドメイン（通知など）を作るときの先例になる

### ネガティブ

- トップレベルパッケージが 1 つ増え、「BC の数」と「トップレベルパッケージの数」が一致しなくなる。
  ルール 5 の許可リストで明示することで、増設が無意識に起きないようにする
- 正典のパッケージ構成から外れた形（2 層のみ）を許すため、「どこまで外れてよいか」の判断が
  今後必要になる。**支援サブドメインであることを ADR で明示した場合に限る**とする

## コンプライアンス

- `shared.domain.model` に `Location` / `ShipperId` 以外が現れないこと（ArchUnit ルール 6。IT1 で有効化済み）
- `security` パッケージが業務の集約（`Cargo` / `Shipper` 等）を参照しないこと
- 支援サブドメインを追加する場合は、本 ADR と同様に ADR を起こすこと

## 備考

- 著者: IT1 クローズ時のマルチパースペクティブレビュー（xp-architect の指摘）
- 関連 ADR: ADR-005（共有カーネルの範囲）、ADR-002（Handling Context の統合）
- 実装: IT1 で `shared` から `security` へ移動済み
