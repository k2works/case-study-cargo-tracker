# ADR-024: 貨物状態の手動更新・例外の起票・公開追跡照会

追跡管理者が状態を手で反映し（US17）、遅延・破損・紛失を例外として起票し（US19・US20）、荷主がログインなしで追跡を照会する（US18）ときに、何を許し、何を返し、何を発行しないかを決める。

日付: 2026-08-23

## ステータス

承認済み

## コンテキスト

IT8 で **trackingms が読み書きの両方を持つ**。IT7 までの trackingms は荷役のイベントを受けて状態を進めるだけで、外から触れる入口を 1 つも持っていなかった。

決めることが 11 ある。3 つの理由でまとめて決める。

1. **認証の外に業務の入口ができる。** 公開追跡照会（US18-5）は、このシステムで唯一ログインを要さない業務経路である。返す項目も、総当たりへの対策も、あとから絞ると「一度出したものを引っ込める」ことになる。
2. **IT7 で塞いだ巻き戻りが、手動経路から再発しうる。** [ADR-022] の非同期経路には `canAdvanceTo` の守りを入れたが、手動更新は別の入口である。**同じ規則を通らせるかどうかは設計の判断**であり、実装の詳細ではない。
3. **trackingms は旅程を持っていない。** US18-2 の「推定到着日」を出すには、どこかから旅程を取る必要がある。[ADR-022] 決定 1 は `CargoRoutedEvent` を「IT6 では発行しない」と決めたままである。**ここが本 IT で最も「新しい結合方式の発明」が起きやすい**。

加えて、**設計に足りていないもの・食い違っているものが 13 件ある**（[イテレーション 8 計画](../development/iteration_plan-8.md) の注）。この ADR と同じ変更で設計へ反映する。

## 決定

### 1. 手動更新も、進む向きにしか動かせない

追跡管理者の手動更新（US17-2）は、荷役のイベントと**同じ `TrackingStatus#canAdvanceTo` を通す**。戻る向きの更新は 409 で断る。

手動だから自由に動かせる、とはしない。荷主が見ているのは 1 本の状態であり、**どの入口から動いたかは荷主に見えない**。手動経路にだけ抜け道を作ると、IT7 で塞いだ「引取済だったはずの貨物が受領待ちに戻っている」が、送り直しではなく人の操作で起きる。

**誤りを直す手段は「戻す」ではなく「例外を起票する」である**（決定 2）。実際に何が起きたかを消さずに残す点で、[ADR-023] 決定 3（予定外の作業は拒まず記録する）と同じ立場をとる。

> **`ONBOARD_CARRIER` と `EXCEPTION` / `UNKNOWN` の扱いは IT8 の着手前に閉じた**（返済枠 0.3）。進行の道の外にある 2 値は、どちらの向きにも `canAdvanceTo` が偽を返す。例外への出入りは決定 2 の専用の操作だけが行う。

### 2. 例外の発生前状態は、列に持つ

例外を起票すると状態は `EXCEPTION` になり、解決すると**発生前の状態に戻る**（US19-4）。この「発生前の状態」は `tracking_activity.status_before` に**永続化する**。履歴から再導出しない。

再導出は、ユニットテストが緑のまま**クロスリクエストで誤復帰する**形の欠陥を生む。1 リクエストの中では履歴が手元にあるので正しく見え、行に残っていないことに気づけない。

- 起票は `TrackingActivity#raiseException`、解決は `#resolveException` とする。設計（`addException` / `resolveException`）の名前に寄せ、**同じ変更で `domain-model.md` を直す**（計画の注 5）。
- **多重起票を許さない。** すでに `EXCEPTION` の貨物にもう一度起票すると、`status_before` が `EXCEPTION` で上書きされ、解決しても戻れない。2 件目は 409 で断る。

### 3. 紛失だけが緊急である

`escalationFlag` は `ExceptionType` そのものが答える（[ADR-023] 決定 1 と同じ形）。`LOST` だけが真で、他は偽である。

呼び出し側に `if (type == LOST)` を書かせると、種別が増えたときに書き換える場所が散らばる。

### 4. 推定到着日は、追跡番号の発行イベントで受け取る

trackingms は旅程を持たないが、**到着期限（`arrival_deadline`）はすでに持っている**（`TrackingNumberIssued` が運んでいる）。US18-2 が求めるのは**推定到着日**であり、期限とは別物である。

- **`TrackingNumberIssued` に推定到着日を載せる。** bookingms が旅程の最後の荷降し時刻を、業務の暦で日付に切って運ぶ。
- **ACL（trackingms → bookingms の REST）は引かない。** 公開照会は認証の外にあり、1 件の照会が bookingms への同期呼び出しになると、**総当たりがそのまま bookingms への負荷になる**。
- **旅程そのものは運ばない。** trackingms が要るのは日付 1 つで、旅程を写すと [ADR-019] の ACL と二重の写しになる。

**分からないときは「未定」と出す**（US18-2）。経路が決まる前の貨物には推定到着日が無く、イベントは空を運ぶ。0 や現在時刻で埋めると、荷主は「今日着く」と読む。

> **実装時に判明（2026-08-23）——別イベントにすると順序が保証されない。**
>
> 当初は [ADR-022] 決定 1 が保留した `CargoRoutedEvent` を独立したイベントとして発行し、trackingms が別のキューで受ける形にした。旅程が決まるのは経路の割り当て（US09）だが**そのとき追跡はまだ存在しない**ため、追跡番号の発行と同じユースケースから続けて発行した。
>
> **kind の統合環境で通したところ、推定到着日が届かなかった。** 2 つのイベントは同じ交換機から**別々のキュー**へ入り、それぞれ別の消費者スレッドが読む。順序は保証されない——`CargoRoutedEvent` が追跡の作成より先に処理されると、引く相手が無く**黙って捨てられる**（「知らない追跡番号では止まらない」という、こちらが意図した振る舞いによって）。
>
> **単体テストも契約テストも往復テストも、すべて緑だった。** 往復テストは追跡を作ってから経路のイベントを送っており、順序が入れ替わる形を一度も通していなかった。
>
> **1 つのイベントに畳んだ。** 作成時の値は作成と一緒に運ぶ。経路を組み直したとき（US28・IT10）に「変わったこと」を送るのは、そのときに設計する——**購読者が確実に扱えないイベントを先に出さない**（決定 8 と同じ立場）。

### 5. 公開照会は、荷主が自分の貨物と分かるだけを返す

返すのは **追跡番号・現在の状態・現在地の港湾名・推定到着日・履歴（日時・状態・港湾名）** だけである。

**返さないもの**を明記する。予約番号・荷主名・荷受人名・作業者・航海番号・例外の詳細（`description` / `resolutionNotes`）・`offRoute`。認証が無い以上、**追跡番号を手に入れた誰もが見る**。荷役の作業者名や予定外だった事実は、荷主に伝えるものではなく社内の手がかりである。

### 6. 総当たりは、遅らせて気づく

追跡番号は `TRK-yyyyMMdd-nnnn` で、**日付が既知なら 4 桁しかない**。認証が無いので、総当たりを止める仕組みが他に無い。

- **応答時間の平準化は、本 IT では行わない。** 「すぐ 404 が返る番号」と「時間のかかる番号」の差から実在を絞る攻撃はありうるが、実装すると全照会に固定の待ちが入り、荷主の体感を確実に悪くする。**攻撃はまだ観測していない。** 照会ログ（決定 7）で兆候を見てから決める——**行わないことをここに書く**（書かないと「対策済み」と読まれる）。
- **同一 IP からの照会に上限を置く**（1 分あたり 30 件）。超えたら 429 で断る。上限は荷主 1 人の使い方（1 日数回）から十分に離す。
- **`X-Forwarded-For` の先頭を信じない。** このヘッダは<strong>各ホップが末尾に追記する</strong>ため、先頭はクライアントが送った文字列そのものである。先頭を採ると、値を毎回変えるだけで上限をいくらでも回避できる。採るのは**末尾**——こちらに最も近いホップが書いた値で、そこは詐称できない。
- **ヘルスチェックは対象外にする。** 横断的な防御を一律に掛けると、過負荷のときに liveness が 429 を返し、Kubernetes が再起動ループに入る。**上限を入れた変更の中で、除外も検査に落とす。**

### 7. 照会は記録する（UC15 の最低保証）

誰が・いつ・どの追跡番号を照会したかを `tracking_lookup_log` に残す。認証が無い経路なので「誰が」は IP と `User-Agent` である。

**記録は照会の成否に関わらず残す。** 見つからなかった照会こそ、総当たりを見つける材料である。

**記録に失敗しても照会は返す。** 記録のために荷主の照会を止めない——ただし [IT7 の学び](../development/retrospective-7.md)のとおり、**失敗を黙って捨てない**。書けなかったことは警告として残す。

### 8. `TrackingExceptionDetectedEvent` は発行しない

**発行しないことをここに明記する**——書かないと実装漏れと読まれる（[ADR-022] 決定 1 の `CargoRoutedEvent`、[ADR-023] 決定 5 の `CargoDeliveredEvent` と同じ形）。

設計のイベント一覧は「例外検知後、通知を配信」と書いているが、**本 IT の通知は代替**（決定 9）であり、配信する相手がいない。購読者のいないイベントを先に出すと、契約だけが増えて誰も守らない。

**`CargoDeliveredEvent`（billingms へ）も置き場を作らない。** 精算は US23（IT12）であり、[リリース計画](../development/release_plan.md)は発行の実装を US21 に置いている。引取済の一覧が要る時期は US23 の計画で決める。

### 9. 通知は「送った事実」を記録し、荷主の画面に出す

US17-4・US19-3・US20-4 の通知は、**メールを送らない**。通知したという事実を `tracking_notification` に残し、**公開追跡画面に「お知らせ」として出す**。

**代替であることを画面・マニュアル・完了報告書に明記する**（US12・US15-5 と同じ形）。書かないと、荷主は「メールが来ないのは不具合」と受け取る。

**[リリース計画](../development/release_plan.md) の Release 0.2 の制約「荷主向けの追跡番号メール通知は US18 が出るまで無効化する」は、US18 の完了では解けない。** 制約の文言を「メール送信の実装（Release 1.1 以降）まで」に直す。**US18 で解ける前提のまま置くと、IT8 のクローズで解いたことにされる。**

### 10. `ROLE_SHIPPER` への予約参照は、まだ開かない

[ADR-008](008-no-user-shipper-link-in-it2.md) は「紐付けを設計する US18（IT8）まで開かない。US18 で紐付けと同時に広げ直す」と約束したが、**US18 の受入基準では果たせない**。US18 は「追跡番号だけで、ログインなしに照会する」ことであり、**利用者と荷主を結ぶキーを一切必要としない**。

- **US18 で開かない。** 開くには「この利用者はどの荷主か」を決める必要があり、それは US18 の受入基準に無い。基準に無いものを実装すると、検査の根拠がどこにもない機能が入る。
- **[ADR-008] の記述を直す。** 「US18 まで」ではなく「利用者と荷主の紐付けを扱うストーリーまで」とし、**そのストーリーが未計画であることを明記する**。
- **荷主が自分の貨物を見る手段は、公開追跡照会である**（本 IT で入る）。予約の中身は見られない。

これは US19 を「通知基盤」と呼んでいた誤りと同じ形である——**約束したストーリーと、約束の中身が噛み合っていない**。噛み合っていないことを書き残す。

### 11. 起票できるのは 3 種別だけ

`ExceptionType` は設計どおり 5 値（`DELAY` / `DAMAGE` / `LOST` / `MISROUTE` / `CUSTOMS_HOLD`）を持つが、**画面から起票できるのは `DELAY` / `DAMAGE` / `LOST` の 3 つ**である。

- `MISROUTE` は US28（IT10）が荷役の記録から**自動で**検知する。手で起票できると、自動検知と人の起票が混ざる。
- `CUSTOMS_HOLD` は US29（IT9）の通関申告が起票する。

**選択肢に出さない。**「一覧に行だけ出て押せない」形を作らない（[IT7 の学び](../development/retrospective-7.md)——メニューに出すのは、そのロールで何かできる画面に限る）。起票できる種別は列挙が答え（`ExceptionType#raisableByOperator`）、画面は分岐を持たない。

## 結果

### よくなること

- 手動経路と非同期経路が**同じ規則**を通るため、入口が増えても巻き戻りが再発しない
- 公開画面が返す項目が**列挙されている**ため、属性が増えたときに「うっかり出る」が起きない
- 発行しないイベント 2 件が**検査に落ちる**ため、あとから静かに増えない

### 悪くなること・引き受けるリスク

- 既存の契約に項目が 1 つ増える。**契約は 2 本のままである**——3 本目を増やすより、作成時の値を作成と一緒に運ぶほうが順序の問題を持ち込まない
- 総当たり対策が**当て推量の閾値**（1 分 30 件）に依存する。実運用の数字が無いため、[運用手順書](../operation/) に閾値の変え方を書く
- **上限はプロセス内で数える。** trackingms を N 台にすると、実効の上限は N×30 になる。**台数を増やすときは閾値を割る**——1 台構成でのみ成り立つ数字である
- **`tracking_notice` は送信の記録ではなく、送信の代替である。** メール送信を実装する日に、この表が「送った履歴」と誤読されうる
- **予定外のまま放置された貨物の状態を、荷主が見る**ようになる。待ち行列は US28（IT10）であり、それまでは運用ルールで拾う

## コンプライアンス

**決定の数だけ検査を用意する。** 「〜を確かめる」ではなく、**検査の場所を書く**（[IT7 Try 3](../development/retrospective-7.md)）。

| 決定 | 検査 |
| :--- | :--- |
| 1. 手動更新も進む向きだけ | `TrackingExceptionFlowTest#doesNotRegressOnManualUpdate` と `TrackingManagementControllerTest#rejectsBackwardUpdate`。**進める先だけを返している**ことは `#returnsOnlyAdvanceableStatuses` が見る——押せるのに断られる操作を出さない |
| 2. 発生前状態は列に持つ | `TrackingExceptionPersistenceIntegrationTest#restoresTheStatusBeforeTheExceptionAcrossRequests`——**保存して読み直してから**解決する。集約を持ち回さない。多重起票は `TrackingExceptionFlowTest#rejectsASecondException` と `TrackingExceptionPersistenceIntegrationTest#rejectsASecondExceptionAcrossRequests` |
| 3. 紛失だけが緊急 | `ExceptionTypeTest#onlyLostIsUrgent`（`@EnumSource` で全種別を回し、`LOST` 以外が偽）。**値が層をまたいで生き延びる**ことは `PublicTrackingControllerTest#neverExposesInternalFields`（緊急のフラグが荷主に届くことも同じ検査で見る） が見る（[Try 2](../development/retrospective-7.md)） |
| 4. 推定到着日は発行イベントで | `TrackingNumberIssuedContract.FIELDS` に項目を持ち、両側の契約テストが名簿を DTO から導いて突き合わせる。実 RabbitMQ の往復は `TrackingNumberIssuedRoundTripTest#startsTrackingWhenTheEventArrives`——**同じイベントで届くこと**をここで見る。`NotifyConfirmAndIssueUseCaseTest#publishesTheEstimatedArrival` が旅程からの導出を、`#sendsAnEmptyEstimatedArrivalWithoutAnItinerary` が空の扱いを確かめる。**ACL を引いていない**ことは `ArchitectureTest` の `serviceIsolationRule` が見る。**未定を 0 で埋めない**ことは `PublicTrackingControllerTest#saysUndecidedWhenNoRouteIsAssigned` |
| 5. 返す項目 | `PublicTrackingControllerTest#returnsOnlyTheAgreedFields`——**名簿を DTO の要素から導く**。返さない項目は `PublicTrackingControllerTest#neverExposesInternalFields` が、応答の JSON 全文に予約番号・作業者・航海番号・例外の詳細が現れないことで見る |
| 6. 総当たり対策 | `PublicLookupThrottleFilterTest#rejectsBeyondTheLimit`（429）・`#exemptsEverythingOutsideThePublicPrefix`（ヘルスチェックと業務 API は対象外）。**転送元を信じない**ことは `PublicTrackingControllerTest#takesOnlyTheFirstForwardedAddress` が見る |
| 7. 照会ログ | `PublicTrackingControllerTest#recordsBothFoundAndNotFound`。**書けなかったことが警告として残る**ことは `MyBatisTrackingLookupLoggerTest#warnsWhenTheLogFails` が、ログ実装から拾って見る |
| 8. 発行しないイベント | `TrackingPublishesNothingTest#hasNoPublishingCallSite`——**発行の呼び出し箇所を数える**（返済枠 0.10 と同じ形）。trackingms は 1 本も発行しない |
| 9. 通知の代替 | `TrackingExceptionPersistenceIntegrationTest#recordsNoticesWithoutSending`（送信の実装が存在しない）。画面の明記は `tracking-lookup-page.test.tsx#お知らせは画面に出し、メールを送っていないことを書く`。**制約の文言を直したこと**は `manual-coverage.test.ts` と `release_plan.md` の突き合わせ |
| 10. `ROLE_SHIPPER` を開かない | `CargoBookingControllerTest` の荷主ロールの検査——**開いていないことを対で確かめる**。開いた日に赤になる |
| 11. 起票できるのは 3 種別 | `ExceptionTypeTest#onlyThreeAreRaisableByOperator`（`@EnumSource` で 5 値すべてを回す）・`TrackingManagementControllerTest#rejectsAutoDetectedTypes`（400）・画面は `tracking-manage-page.test.tsx#誤配・税関保留は、起票の選択肢に出ない` |

## 備考

- 関連 ADR: [ADR-008](008-no-user-shipper-link-in-it2.md), [ADR-009](009-cargo-status-columns-from-the-start.md), [ADR-019](019-route-assignment-api.md), [ADR-022](022-domain-event-contract.md), [ADR-023](023-handling-activity-validation.md)
- 関連ストーリー: US17（貨物状態の手動更新）, US18（追跡情報の照会）, US19（遅延例外）, US20（破損・紛失例外）, US28（誤配・IT10）, US29（通関申告・IT9）, US21・US23（精算・IT11/IT12）
