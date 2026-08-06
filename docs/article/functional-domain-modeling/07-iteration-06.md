# 第 7 章：IT6 輸送例外の登録と解決

## このイテレーションのゴール

> 輸送中の例外（遅延・破損・紛失）を追跡管理者が登録し、貨物状態を「例外発生」（InException）へ自動遷移させ、荷主通知・エスカレーション・対応報告までを一気通貫させる。Release 1.1 の例外対応フローを完成させ、IT5 の技術的負債（荷役→追跡の一貫性・追跡照会の所有者制御）を解消する。

ゴール文の後半が**技術的負債の解消**になっています。IT5 のふりかえりで挙がった項目を、機能ストーリーと同格に置いた形です。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 6（US19 / US20）+ Release 1.0 フィードバック対応・retro-5 Try 消化 |
| ユニットテスト | 188 件緑（+28） |
| 統合テスト | 128 件緑（+5） |
| カバレッジ（全体 / ドメイン層） | 91.6% / 89.7% |

新規ストーリーは 6 SP だけで、残りは負債返済とフィードバック対応です。**新機能を積まないイテレーションを計画に組み込んだ**のがこの回の性格です。

## 扱うユーザーストーリー

| ID | ストーリー |
| :--- | :--- |
| US19 | 遅延例外を処理する |
| US20 | 破損・紛失例外を処理する |

## モデリング：解決状態を DU で表す

例外には「未解決」と「解決済み」があり、解決済みには解決時刻が必ず伴います。

```fsharp
// src/CargoTracker.Tracking/Domain.fs
/// 例外の解決状態。「未解決（エスカレーション有無）」と「解決済み（解決時刻必須）」を
/// DU で表現し「解決済みなのに時刻が null」という不正状態を型で排除する（domain-model ビジネスルール 5・6）。
type ExceptionResolution =
    | Unresolved of escalated: bool
    | Resolved of resolvedAt: DateTimeOffset
```

コメントが設計意図を明示しています。素朴に書けば `IsResolved: bool` と `ResolvedAt: DateTimeOffset option` の 2 フィールドになりますが、その形は 4 通りの組み合わせを許します。

| `IsResolved` | `ResolvedAt` | 意味 |
| :--- | :--- | :--- |
| false | None | 未解決（正しい） |
| true | Some | 解決済み（正しい） |
| **true** | **None** | 解決済みなのに時刻がない（不正） |
| **false** | **Some** | 未解決なのに解決時刻がある（不正） |

DU にすれば 2 通りしか存在しません。不正な 2 通りは表現不可能です。

さらに、エスカレーションの有無は**未解決のときだけ意味を持つ**という関係も表現されています。`Resolved` はエスカレーションフラグを持ちません。解決済みの例外に「エスカレーション中」という属性が残ることがありません。

例外そのものは、この解決状態を含むレコードです。

```fsharp
/// 追跡例外イベント（遅延・破損・紛失・通関保留の記録）。
/// ResolutionNote は解決時に入力する対応内容（US19「対応報告」）。未解決時は None。
type TrackingException =
    { ExceptionType: ExceptionType
      Location: Location
      OccurredAt: DateTimeOffset
      Description: string
      Resolution: ExceptionResolution
      ResolutionNote: string option }
```

`ResolutionNote` が `Resolution` の中ではなく外にある点は、設計の緩みです。厳密にやるなら `Resolved of resolvedAt: DateTimeOffset * note: string option` として解決状態に含めるべきで、そうすれば「未解決なのに対応内容がある」を排除できます。コメントで「未解決時は None」と補っているのは、型で表しきれていないためです。

**どこまで型に持ち上げるかは都度の判断**であり、この実装は解決時刻までを型で守り、対応内容は規約に委ねました。

## モデリング：業務ルールを構築時に確定する

紛失は必ずエスカレーションする、というルールがあります。

```fsharp
module TrackingException =

    /// 例外を登録する。Lost の場合は必ずエスカレーションする（ビジネスルール 3）。
    let register
        (exceptionType: ExceptionType)
        (location: Location)
        (occurredAt: DateTimeOffset)
        (description: string)
        : TrackingException =
        let escalated = (exceptionType = Lost)

        { ExceptionType = exceptionType
          Location = location
          OccurredAt = occurredAt
          Description = description
          Resolution = Unresolved escalated
          ResolutionNote = None }
```

`let escalated = (exceptionType = Lost)` の 1 行がルールの全体です。呼び出し側はエスカレーションの要否を指定できません——**指定させない**ことでルールが破られなくなります。

姉妹シリーズの Java 実装は、同じルールを列挙型のフィールドに持たせました。

```java
public enum ExceptionType {
    DELAY("遅延", false),
    DAMAGE("破損", false),
    LOST("紛失", true);
```

こちらも良い設計ですが、値を使う側で `isEscalationRequired()` を呼び忘れる余地があります。実際 Java 実装ではこのフラグをリポジトリに渡して保存しており、呼び出し箇所が 1 つでも漏れれば整合が崩れます。F# 版は構築時に確定するので、後から参照する場所での漏れが起きません。

## モデリング：状態は導出されるので遷移を書かない

例外が発生したら貨物状態が「例外発生」になる、という要件があります。しかし `TrackingActivity` には状態遷移のコードがありません。

```fsharp
    /// 現在の追跡状態：アクティブな例外があれば InException、なければ最新イベントから導出する純粋関数。
    /// currentStatus は保持値でなく導出関数のため、例外解決後は自動的に元の状態へ復帰する（ビジネスルール 5）。
    let currentStatus (activity: TrackingActivity) : TrackingStatus =
        if activity.Exceptions |> List.exists TrackingException.isActive then
            InException
        else
            match activity.Events with
            | [] -> NotReceived
            | latest :: _ -> TrackingEventType.toStatus latest.EventType
```

例外を登録すると `Exceptions` リストに積まれ、`currentStatus` が `InException` を返すようになります。**遷移を書く代わりに、状態の定義を書いた**という構図です。

解決後の復帰も自動です。`TrackingException.isActive` が `false` になれば、条件式が偽になり、イベント列からの導出に戻ります。

```fsharp
    /// 未解決かどうか。
    let isActive (ex: TrackingException) : bool =
        match ex.Resolution with
        | Unresolved _ -> true
        | Resolved _ -> false
```

姉妹シリーズの Java 実装では、この復帰が実装されていません。`addException` が状態を `EXCEPTION` に設定するだけで、そこから戻る遷移が定義されていないためです。復帰は手動状態更新（US17）という別機能に頼ることになります。

「状態を保持して遷移で更新する」と「状態を定義して導出する」の差が、要件の充足度にそのまま出ています。

## モデリング：解決のコマンド

例外の解決は、集約のコマンドとして表されます。

```fsharp
/// 集約への操作コマンド。
type TrackingCommand =
    | RecordEvent of TrackingActivityEvent
    | RegisterException of ExceptionType * Location * DateTimeOffset * string
    | ResolveException of index: int * resolvedAt: DateTimeOffset * note: string
```

処理は `execute` の 1 つの腕です。

```fsharp
        | ResolveException(index, resolvedAt, note) ->
            match List.tryItem index activity.Exceptions with
            | None -> Error(NotFound("TrackingException", string index))
            | Some { Resolution = Resolved _ } -> Error(BusinessRuleViolation("AlreadyResolved", "この例外はすでに解決済みです。"))
            | Some ex ->
                let resolved =
                    { ex with
                        Resolution = Resolved resolvedAt
                        ResolutionNote =
                            (if System.String.IsNullOrWhiteSpace note then
                                 None
                             else
                                 Some note) }

                let exceptions =
                    activity.Exceptions |> List.mapi (fun i e -> if i = index then resolved else e)

                Ok(
                    { activity with
                        Exceptions = exceptions },
                    [ TrackingExceptionResolved(activity.TrackingNumber, ex.ExceptionType) ]
                )
```

3 つのケースを順に処理しています。

1. **`None`** — その index の例外がない → `NotFound`
2. **`Some { Resolution = Resolved _ }`** — すでに解決済み → 業務ルール違反
3. **`Some ex`** — 未解決 → 解決する

2 番目のパターンに注目してください。`Some { Resolution = Resolved _ }` は**レコードのフィールドに対する入れ子のパターンマッチ**です。「例外は見つかったが、その解決状態が `Resolved` である」という条件が、`if` 文なしで書けています。

二重解決の防止がこの 1 行だけで済むのは、解決状態を DU にしたおかげです。`bool` フラグなら `if ex.IsResolved then ...` と書くことになり、判定と分岐が別々になります。

### index でアクセスすることの危うさ

`ResolveException of index: int` は、リストの位置で例外を指定します。ここは設計上の弱点です。

姉妹シリーズの Ruby 実装で、まさにこの形のバグが検出されています。

> tester が検出した `remove_adjustment` の seq_number 負値インデックスによる末尾誤削除（実バグ）

連番からインデックスを計算する処理で負値が入り、Ruby の配列が末尾から数える挙動で別の要素が消えていました。

F# の `List.tryItem` は範囲外で `None` を返すため、負値でも例外になりません。ただし **index が意味を持つのはリストの並び順に依存する**という本質的な脆さは残ります。`Exceptions` は「新しい順」と決められていますが、それはコメントに書かれた規約であり、型は保証しません。例外に ID を振って指定するほうが堅牢です。

## このイテレーションの学び

### 負債返済をゴールに書く効果

このイテレーションは新規ストーリーが 6 SP だけで、残りは IT5 の負債とフィードバック対応でした。

姉妹シリーズの Java 実装では、IT5 のレビュー指摘 H-1〜H-9 のうち多くが IT10 まで持ち越されました。IT10 になってようやく「IT9 申し送り事項（技術的負債 H-1〜H-3・H-5・H-6）を解消し」がゴール文に入り、リリース直前の負債返済イテレーションとして処理されています。

F# 実装は同じことを IT6 の時点で、しかも計画的に行いました。**新機能を積まない回を最初から計画に入れる**ほうが、リリース直前に押し込むより安全です。

### 型で消えたテスト（再び）

解決状態を DU にしたことで、次のテストが不要になりました。

- 「解決済みフラグが true なのに解決時刻が null のとき例外」— 作れない
- 「未解決なのに解決時刻が入っているデータの扱い」— 作れない
- 「解決後にエスカレーションフラグが残らない」— `Resolved` が持たない

代わりに書くのは「二重解決が `BusinessRuleViolation` を返す」という 1 件です。

ユニットテストが 28 件増えている一方で統合テストは 5 件しか増えていないのも、この回の作業がドメイン層に集中していたことを示しています。

### 型で表しきらなかった箇所

正直に見れば、このイテレーションには型で守れたはずの緩みが 2 つあります。

| 箇所 | 現状 | 型で守るなら |
| :--- | :--- | :--- |
| `ResolutionNote` | `Resolution` の外にある | `Resolved of DateTimeOffset * string option` |
| 例外の指定 | リストの index | 例外 ID の単一ケース DU |

どちらも「そうすべきだができていない」箇所です。関数型ドメインモデリングを採用したからといって、自動的にすべてが型で守られるわけではありません。**型に持ち上げる判断を都度下し続ける**必要があり、その判断は疲れます。

このプロジェクトが 8 イテレーション通して高いカバレッジを維持できたのは、型の力だけでなく、ふりかえりで緩みを言語化し続けたためです。

---

- 前の章：[第 6 章：IT5 追跡と荷役](06-iteration-05.md)
- 次の章：[第 8 章：IT7 料金算出と精算](08-iteration-07.md)
