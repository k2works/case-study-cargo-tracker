# ADR-0005: ルーティング表の単一化と効果の持ち上げ

## ステータス

承認済み（2026-09-16・IT4）

## コンテキスト

IT3 でルーティング表を**効果集合ごとに 4 つへ分割**した。Flix の効果は不変であり、
宣言効果と実効果が完全に一致しなければならない（使わない効果を宣言すると
`Unused effect` でコンパイルエラーになる）。そのため、要求する効果の異なるハンドラを
1 つのリストへ入れられなかった。

```flix
// IT3 の形。4 つの表を順に試す
def dispatchAll(req, g) =
    match tryDispatch(loginRoutes(), req, g) { ... dispatchAfterLogin(req, g) }
def dispatchAfterLogin(req, g) =
    match tryDispatch(logoutRoutes(), req, g) { ... sessionRoutes ... dispatchTracking ... }
```

IT3 のレビュー（H16）とふりかえり（P5・T1）で、この形が **BC が増えると持たない**と判断した。

| 問題 | 内容 |
| :--- | :--- |
| 認可の正典が壊れる | 同一パスが 2 つの表にあると**先勝ち**で通り、後ろの表の認可要件が静かに消える |
| 405 を返せない | どの表にも当たらなければ 404。パス一致・メソッド不一致が「存在しない」と応答する |
| ネストが線形に深くなる | 全リクエストが通る唯一の経路が、BC の数だけ深い入れ子になる |
| トランザクションの粒度が持てない | 全リクエストを書き込みトランザクションで包んでいた（IT2 レビュー M15） |

## 決定

### 1. ルーティング表を 1 つに戻す。効果集合の相違は「持ち上げ」で吸収する

Flix の効果は**構文上使われていれば宣言できる**。実行される必要はない。
この性質を使い、実行されない枝で全効果に触れる関数を作る。

```flix
/// 合成ルート（src/composition/Composition.flix）
def touchAll(shouldRun: Bool): Unit \ Session + UserRepo + Password + ReadDb + IO =
    if (shouldRun) {
        discard Session.current();
        discard UserRepo.findByUsername("");
        discard Password.verify("", "");
        discard ReadDb.findByTrackingNumber("");
        discard System.currentTimeMillis()
    } else ()

def lift(h: Request -> Response \ ef):
        Request -> Response \ ef + Session + UserRepo + Password + ReadDb + IO =
    req -> { touchAll(false); h(req) }
```

呼び出し側は常に `false` を渡すため、**中の操作は 1 度も実行されない**。
`ef` が全体集合の部分集合であれば `ef + All` は `All` へ簡約され、
効果集合の異なるハンドラを 1 つのリストへ載せられる。

各 BC のハンドラは**自分が使う効果だけを宣言してよい**。表へ載せる時点の
`lift(...)` 1 つで吸収するため、ハンドラ側の記述は増えない。

### 2. no-op 操作をドメインのポートへ足さない

当初検討した案は、各ポートに `touch(): Unit` という no-op 操作を足して効果を持ち上げるものだった。
実験では成立したが**採らない**。

| 観点 | no-op をポートへ足す案 | 実行されない枝を合成ルートに置く案（採用） |
| :--- | :--- | :--- |
| ドメイン層への影響 | 業務的な意味を持たない操作がポートに現れる | **なし** |
| ハンドラの実装義務 | 本番・インメモリ・テストの全ハンドラが `touch` を実装する | なし |
| 税の所在 | 8 つの BC に分散する | **合成ルート 1 関数に閉じる** |
| 読みやすさ | 操作の意図が読み取れない | `if (shouldRun)` の意図をコメントで説明する必要がある |

言語の制約による税は避けられない。**避けられるのは税の所在**であり、
配線専門の層（合成ルート）に閉じ込めるのが筋である。

### 3. ルート定義に `TxMode` を持たせる

トランザクションを開くかどうかは**ハンドラを呼ぶ前**に決めなければならない。
ハンドラの中で判断する形にすると、判断に至る前にコネクションを取ることになる。

| モード | 用途 | 実装 |
| :--- | :--- | :--- |
| `NoTx` | DB を使わない | トランザクションを開かない。`/health/live` |
| `ReadOnly` | 読み取りのみ | `SharedDbTx.readOnly`（自動コミットを切り、最後にロールバックする） |
| `Write` | 状態を変更する | `SharedDbTx.transactional` |

> **`ReadOnly` の実装（IT4 の統合テストで是正）**: 当初 `readOnly` は
> `conn.setReadOnly(true)` を呼ぶだけだった。しかし H2 は**この設定だけでは書き込みを
> 拒否しない**。さらに自動コミットが有効なままだったため、`TxMode.ReadOnly` と
> 宣言したルートが誤って保存すると、**その 1 文が即座にコミットされ、
> トランザクション制御の外側で永続化される**状態になっていた。
>
> 自動コミットを切り、最後に必ずロールバックする形へ改めた。読み取りしかしない
> 正常な経路ではロールバックは何も変えず、誤った書き込みだけが破棄される。
> `JdbcShipperRepoTest.testReadOnlyTransactionDiscardsWrites` が「例外が飛ぶこと」ではなく
> 「**残らないこと**」を検証する。拒否の仕方は DB 実装で変わるが、守りたい性質は変わらない。

`NoTx` のルートでも効果の型は表全体で揃っているため、使わないハンドラを供給する必要がある。
**供給するのは「呼ばれたら設計の誤り」を表明するハンドラ**とし、既定値は返さない。
既定値（`None` 等）を返すと、`NoTx` と宣言したルートが実際には DB を要求していたときに
**未認証として静かに通ってしまう**。

### 4. 「見つからない」を 405 と 404 に分ける

パスは一致するがメソッドが違う場合は `MethodMismatch`（405）とし、
パスが 1 つも一致しなければ `NotFound`（404）とする。

### 5. 二重定義を機械で検出する

表が 1 つになっても、同じ表の中に同じメソッド・同じパターンを 2 度書けば
先勝ちで後の認可要件が消える。`duplicateKeys` で検出し、**起動時に落とす**とともに
テスト（`AppRoutesTest.testNoDuplicateRoutes`）で開発者がその場で受け取れるようにする。

## 影響

### 正の影響

- **「ルート表が認可の唯一の正典」が回復した**。ルート定義と設計の可否表を突き合わせる
  テスト（`AppRoutesTest.testRouteRolesMatchDesignTable`）が書けるようになった（IT3 レビュー M17）
- ディスパッチの入れ子が消え、BC を追加してもネストが深くならない
- `/health/ready` を表へ載せられた（IT3 レビュー M9）。ルート表の外でパス文字列を比較する例外が消えた
- 読み取り専用ルートでコミットが走らなくなった（IT2 レビュー M15）
- 405 を返せるようになった

### 負の影響

- `touchAll` / `lift` は**言語の制約に対する回避策**であり、それ自体に業務上の意味がない。
  Flix を知らない読み手には意図が読み取れない（コメントで補う）
- 表の効果集合は「全 BC の効果の和」になる。BC が増えるたびに `touchAll` へ 1 行足す必要がある。
  足し忘れは新しい BC のルートを載せた時点でコンパイルエラーになるため、静かには壊れない
- `NoTx` ルートのハンドラが誤って DB を要求すると**実行時に落ちる**。
  型では防げない（`AppRoutesTest` と `RoutingHttpTest` で補う）

### この判断が正当化される条件

- `lift` の実行時コストが無視できること。**実行されない枝であることを
  `RouterTest` 相当の呼び出し回数テストで確認済み**（IT4 の実験で 0 回を実測）
- Flix が効果の上位変換（subeffecting）を将来サポートした場合、`touchAll` / `lift` は削除できる。
  その時点で本 ADR を「置き換え済み」とする

## コンプライアンス

| 検査 | 内容 |
| :--- | :--- |
| `AppRoutesTest.testNoDuplicateRoutes` | 実際の表に二重定義がない |
| `AppRoutesTest.testNoTxRoutesAreAnonymous` | `NoTx` のルートは未認証で通れるものに限る |
| `AppRoutesTest.testGetRoutesDoNotWrite` | `GET` に書き込みトランザクションを与えていない |
| `AppRoutesTest.testRouteRolesMatchDesignTable` | ルート定義の認可要件が設計の可否表と一致する |
| `RouterTest`（12 件） | 解決・405/404 の区別・二重定義検出 |
| `RoutingHttpTest`（6 件） | HTTP 経路で 405・liveness・readiness が期待どおり |
| 起動時検査 | `startServer` が二重定義を見つけたら起動を止める |

## 備考

- 関連 ADR: [ADR-0002](ADR-0002-self-built-web-and-security.md)（Web・セキュリティ基盤の自作）
- 関連ドキュメント: [バックエンドアーキテクチャ](../design/architecture_backend.md)、[イテレーション 4 計画](../development/iteration_plan-4.md)
- 未解決: IT4 の実装中に、公開追跡の `GET` が 1 度だけ 403 を返す事象を観測した。
  以後 4 回のフル実行では再現していない。`Anonymous` のルートで `guard` が 403 を返す経路は
  論理上存在しないため、原因は特定できていない。再発時に原因へ辿り着けるよう、
  ステータス検証のヘルパー（`SharedHttpTestClient.assertStatus`）が本文を出力するようにした
