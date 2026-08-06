# 第 6 章：IT5 追跡と荷役

## このイテレーションのゴール

> 予約確定から追跡番号発行・荷役記録・引取・追跡照会までを一気通貫させ、Release 1.0 MVP の業務フローを完成させる（Tracking・Handling コンテキストをドメイン層から堅牢に立ち上げる）

コンテキストが 2 つ同時に増えます。目標 SP 17 はこのプロジェクト最大で、超過時の調整候補（US17）まで計画に書かれています。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 17（US14 / US15 / US16 / US17 / US18） |
| ユニットテスト | 160 件緑（+32） |
| 統合テスト | 123 件緑（+22） |
| アーキテクチャテスト | 24 件緑（+11） |
| カバレッジ（全体 / ドメイン層） | 91.3% / 89.1% |
| ADR | 0011 追跡照会の所有者制御は capability トークンとロール / 0012 荷役→追跡の連携はベストエフォートと冪等 |

アーキテクチャテストが 13 → 24 件へほぼ倍増しています。コンテキストが 2 つ増えたぶん、`[<Theory>]` のケースが自動的に増えたためです。

## 扱うユーザーストーリー

| ID | ストーリー |
| :--- | :--- |
| US14 | 追跡番号を発行する |
| US15 | 荷役作業を記録する |
| US16 | 引取作業を記録する |
| US17 | 貨物状態を手動更新する |
| US18 | 追跡情報を照会する |

## モデリング：種別にデータを埋め込む（再訪）

荷役種別のうち、積込と荷降しだけは航海番号を必要とします。受領・通関・引取には航海番号がありません。

```fsharp
// src/CargoTracker.Handling/Domain.fs
/// 荷役種別。VoyageNumber 必須の種別にのみ航海番号を型で埋め込む（不正状態の排除）。
type HandlingType =
    | Receive
    | Load of VoyageNumber
    | Unload of VoyageNumber
    | Customs
    | Claim
```

IT2 の `CargoType` と同じ技法です。「積込なのに航海番号がない」も「受領なのに航海番号がある」も、型として存在しません。

姉妹シリーズの Java 実装は、`TrackingActivityEvent` が全種別共通で `voyageNumber` フィールドを持ち、受領イベントには `null` を入れています。

```java
// Java: 種別によらず voyageNumber フィールドを持つ
TrackingActivityEvent event = new TrackingActivityEvent(
        TrackingEventType.MANUAL_UPDATE, locationUnlocode, dateTime, null);
```

末尾の `null` が「この種別には航海番号がない」を表しています。読み手はフィールドの意味を型から知ることができず、コメントか実装を追うしかありません。

## モデリング：デシジョンテーブルを純粋関数にする

US15 の核心は、記録された荷役が旅程と整合しているかの判定です。要件はデシジョンテーブルの形をしています。

| 荷役種別 | 判定条件 | 不一致のとき |
| :--- | :--- | :--- |
| 受領 | 場所 = 出発港 | 警告 |
| 積込 | 旅程に「その場所・その航海」の区間がある | 誤送 |
| 荷降し | 旅程に「その場所・その航海」の区間がある | 誤送 |
| 通関 | 常に妥当 | — |
| 引取 | 場所 = 目的港 | 警告 |

結果は 3 値なので、DU にします。

```fsharp
/// 荷役妥当性検証の結果（デシジョンテーブル）。
type ValidationOutcome =
    | Valid
    | Warning of message: string
    | Misrouted
```

`bool` にしなかったのが要点です。「妥当ではない」には**警告どまり**と**誤送（業務上の重大事象）**の 2 種類があり、後続の扱いが違います。`bool` にすると、この差が呼び出し側の暗黙知になります。

判定本体はテーブルをそのまま写した純粋関数です。

```fsharp
    /// 荷役妥当性検証（デシジョンテーブルの純粋関数化）。
    let validateFor (snapshot: CargoSnapshot) (handlingType: HandlingType) (location: Location) : ValidationOutcome =
        let code = Location.value location

        match handlingType with
        | Receive ->
            if code = snapshot.Origin then
                Valid
            else
                Warning "受領場所が出発港と一致しません。"
        | Load voyage ->
            let matches =
                snapshot.ItineraryLegs
                |> List.exists (fun leg -> leg.LoadLocation = code && leg.VoyageNumber = VoyageNumber.value voyage)

            if matches then Valid else Misrouted
        | Unload voyage ->
            let matches =
                snapshot.ItineraryLegs
                |> List.exists (fun leg -> leg.UnloadLocation = code && leg.VoyageNumber = VoyageNumber.value voyage)

            if matches then Valid else Misrouted
        | Customs -> Valid
        | Claim ->
            if code = snapshot.Destination then
                Valid
            else
                Warning "引取場所が目的港と一致しません。"
```

`Load voyage` のパターンで航海番号が取り出せているのは、種別に埋め込んであるからです。`Receive` の腕では航海番号が見えないので、誤って参照するコードが書けません。

**要件のテーブルと実装が 1 対 1 で対応している**ため、要件が変わったときに直す場所が自明です。

### ACL としてのスナップショット

判定に必要な貨物情報は、Booking Context から取ります。ただし `Cargo` 集約そのものではありません。

```fsharp
/// ACL 経由で取得した貨物情報のスナップショット（妥当性検証に使用）。
type LegSnapshot =
    { LoadLocation: string
      UnloadLocation: string
      VoyageNumber: string }

type CargoSnapshot =
    { BookingId: string
      Origin: string
      Destination: string
      ItineraryLegs: LegSnapshot list }
```

フィールドがすべて `string` である点に注目してください。Handling Context の値オブジェクト（`Location`・`VoyageNumber`）ですらありません。

これは意図的な「薄さ」です。スナップショットは**外部から来た未検証のデータ**であり、Handling の型体系に持ち込む前の生の形で受け取ります。境界を越えた値をそのまま自コンテキストの型にすると、他コンテキストの検証ルールを暗黙に信頼することになります。

## モデリング：状態を保持せず導出する

追跡状態は `TrackingActivity` のフィールドではありません。イベント列と例外から**計算します**。

```fsharp
// src/CargoTracker.Tracking/Domain.fs
type TrackingActivity =
    { TrackingNumber: TrackingNumber
      BookingId: TrackingBookingId
      Events: TrackingActivityEvent list // 時系列（新しい順）
      Exceptions: TrackingException list } // 新しい順（index 0 が最新）

module TrackingActivity =

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

コメントが効用を説明しています。**例外が解決されたら自動的に元の状態に戻る**。状態をフィールドで持っていたら、例外解決時に「どの状態に戻すか」を計算して代入する必要があり、そこにバグが入ります。

導出にしたことで、「状態」と「イベント履歴」が食い違うことがありません。片方だけ更新して不整合になる、という事故が起きようがない設計です。

集約への操作も、状態を更新しません。

```fsharp
    /// 追跡イベント・例外を集約へ適用する。状態は導出のため自動更新される。
    let execute
        (activity: TrackingActivity)
        (command: TrackingCommand)
        : Result<TrackingActivity * TrackingEvent list, DomainError> =
        match command with
        | RecordEvent event ->
            let updated =
                { activity with
                    Events = event :: activity.Events }

            Ok(updated, [ TrackingEventRecorded(activity.TrackingNumber, event.EventType) ])
        // ...
```

イベントをリストの先頭に積むだけです。状態は次に `currentStatus` を呼んだときに計算されます。

### 導出と永続化のトレードオフ

ここには注意すべき論点があります。姉妹シリーズで見た Ruby 実装のふりかえりには、逆の教訓が記録されていました。

> 発生前状態を永続化せず履歴から再導出すると、ユニット緑でもクロスリクエストで誤復帰する偽の安全網。必ずカラム永続化。

一見矛盾しますが、条件が違います。Ruby 実装の問題は、**集約の全履歴を読まずに部分的な情報から状態を再計算していた**ことでした。F# 実装の `currentStatus` は、`Events` と `Exceptions` を**すべて保持した集約**に対する関数です。集約をリポジトリから復元する時点で全イベントを読み込んでいるため、再導出しても情報が欠けません。

つまり判断基準は「導出か永続化か」ではなく、**導出に必要な情報が集約に揃っているか**です。揃っていないなら永続化するしかありません。

なお、この設計には性能上の代償があります。イベントが増え続ける集約を毎回全件読むことになるため、履歴が長期化する業務では成立しません。この題材では 1 貨物あたりのイベント数が高々数十件なので問題になりませんが、無条件に適用できる手法ではありません。

## モデリング：コンテキスト間の状態写像

Tracking Context の `TrackingStatus` と Shared の `TransportStatus` は、ケースが同じでも別の型です。

```fsharp
    /// TrackingStatus を Shared の TransportStatus へ写像する（アプリ層で Booking.Delivery へ同期）。
    /// ケースの追加・変更はコンパイルエラーとしてここに伝播する（BC 分離・網羅変換）。
    let toTransportStatus (status: TrackingStatus) : TransportStatus =
        match status with
        | NotReceived -> TransportStatus.NotReceived
        | Received -> TransportStatus.Received
        | Loaded -> TransportStatus.Loaded
        | OnboardCarrier -> TransportStatus.OnboardCarrier
        | Unloaded -> TransportStatus.Unloaded
        | AwaitingClaim -> TransportStatus.AwaitingClaim
        | Claimed -> TransportStatus.Claimed
        | InException -> TransportStatus.InException
        | Unknown -> TransportStatus.Unknown
```

9 ケースを機械的に書き写しているだけに見えますが、コメントの通り**ケースを追加するとこの関数がコンパイルエラーになります**。

型を分けずに共有していれば、この関数は不要です。しかし共有すると、Tracking の都合で状態を追加したときに他コンテキストが黙って影響を受けます。あえて別型にして変換を書くことで、**境界を越える影響が変換関数の修正として可視化されます**。

これは冗長さと引き換えに得る安全性であり、コンテキスト数が多いほど効きます。

## このイテレーションの学び

### ベストエフォートと冪等を選ぶ

ADR-0012 に「荷役→追跡の状態連携はベストエフォート＋冪等で行う」が記録されています。

荷役が記録されたら追跡状態も更新したい。しかし荷役の記録は成功して追跡の更新が失敗したとき、荷役を取り消すべきかというと、そうではありません。荷役は現実に起きた事実だからです。

そこで、連携は失敗を許容し（ベストエフォート）、再実行しても壊れないようにする（冪等）方針を採りました。第 5 章で見た post-commit ディスパッチと同じ構造です。

**型で守れるのは 1 プロセス・1 トランザクション内の一貫性まで**であり、その外は運用の設計に委ねられます。関数型ドメインモデリングはこの境界を消してはくれません。

### 技術的負債を次のイテレーションのゴールに書く

IT5 のふりかえりで挙がった負債は、IT6 のゴール文に直接書き込まれました。

> …Release 1.1 の例外対応フローを完成させ、**IT5 の技術的負債（荷役→追跡の一貫性・追跡照会の所有者制御）を解消する**。

姉妹シリーズで見た通り、Java 実装は技術的負債をタスクリストの下位項目に置き続け、IT5 の指摘が IT10 まで持ち越されました。「余力があればやる」と位置づけたものは消化されません。

F# 実装は負債をゴール文に格上げしました。Java 実装が SonarQube スキャンで同じことをして初めて消化できたのと同型の対処です。

### 追跡照会の所有者制御

US18（追跡情報の照会）には、認証なしの公開ページが含まれます。ADR-0011 として「capability トークン（公開）とロール（認証）で行う」が記録されています。

姉妹シリーズの Java 実装は、公開ページと認証済みページで**同じ DTO と同じテンプレート**を使っていました。認証済み画面に項目を足すと公開ページにも出る構造です。

F# 実装が capability トークンという用語を持ち出したのは、「追跡番号を知っていること」自体を権限とみなす設計です。推測不能な番号を知っている＝見る権利がある、と定義することで、認可の根拠がはっきりします。ただしこの設計は**番号が推測不能であること**に全面的に依存するため、番号の生成方式が安全性の要になります。

---

- 前の章：[第 5 章：IT4 経路確定から予約確定まで](05-iteration-04.md)
- 次の章：[第 7 章：IT6 輸送例外の登録と解決](07-iteration-06.md)
