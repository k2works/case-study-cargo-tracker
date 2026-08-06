# 第 10 章：IT9 遅延・破損・紛失の例外処理

## このイテレーションのゴール

> IT8 申し送り事項（受入条件未達成の H-8・H-9 対応と SonarQube 確認）を解消し、遅延・破損・紛失の例外処理を実装して Phase 3 例外処理機能を完成させる

例外処理は、業務システムで最も設計が難しい領域です。正常系は 1 本ですが、例外は種類ごとに扱いが違い、それぞれに業務対応が紐づきます。

| 項目 | 内容 |
| :--- | :--- |
| 目標 SP | 12（このプロジェクト最大） |
| 実績 SP | 12 |
| Java テスト | 315 件（+14） |
| Playwright E2E | 93 件（+6） |
| 命令カバレッジ | 80% |
| ブランチカバレッジ | 74% |

## 扱うユーザーストーリー

| ID | ストーリー | SP |
| :--- | :--- | :--- |
| US19 | 遅延例外を処理する | 5 |
| US20 | 破損・紛失例外を処理する | 5 |
| IT8-改善 | H-8・H-9 の受入条件充足 | 2 |

## Java 実装

### 例外種別に業務属性を持たせる

3 種類の例外のうち、紛失だけがエスカレーション（緊急対応）を要します。この違いを列挙型のフィールドで表現しました。

```java
// tracking/domain/model/valueobjects/ExceptionType.java
public enum ExceptionType {
    DELAY("遅延", false),
    DAMAGE("破損", false),
    LOST("紛失", true);

    private final String displayName;
    private final boolean escalationRequired;

    public boolean isEscalationRequired() {
        return escalationRequired;
    }
}
```

「紛失ならエスカレーション」というルールが `if (type == LOST)` として散らばらず、列挙の定義 1 箇所に集まっています。新しい例外種別を追加するときも、エスカレーションの要否を必ず指定することになります。

Java の列挙型がフィールドとメソッドを持てる性質を活かした、素直で良い設計です。同じことを C の enum のような単純な定数でやると、種別ごとの属性が別のテーブルや `switch` に散ります。

### コマンドサービスの肥大化

例外登録のコマンドサービスを見ると、このプロジェクトで最も込み入った処理になっています。

```java
// tracking/application/internal/commandservices/TrackingCommandService.java
public void registerException(RegisterExceptionCommand command) {
    TrackingNumber trackingNumber = TrackingNumber.of(command.trackingNumber());
    TrackingRecord trackingRecord = trackingRepository.findByTrackingNumber(trackingNumber)
            .orElseThrow(() -> new IllegalArgumentException(
                    TRACKING_RECORD_NOT_FOUND + command.trackingNumber()));
    trackingRecord.addException(command.exceptionType(), command.locationUnlocode(),
            command.occurrenceTime());
    trackingRepository.updateStatus(trackingRecord);
    TrackingActivityEvent lastEvent = trackingRecord.getHandlingEvents().getLast();
    trackingRepository.saveHandlingEvent(command.trackingNumber(), lastEvent);
    trackingRepository.saveExceptionEvent(
            command.trackingNumber(),
            command.exceptionType(),
            command.locationUnlocode(),
            command.occurrenceTime(),
            command.reason(),
            command.responseNote(),
            command.exceptionType().isEscalationRequired()
    );
}
```

リポジトリを 3 回呼んでいます。状態の更新、荷役イベントの保存、例外イベントの保存。しかも 2 回目は、集約に追加したイベントを `getLast()` で取り出して渡すという回りくどい形です。

```java
TrackingActivityEvent lastEvent = trackingRecord.getHandlingEvents().getLast();
trackingRepository.saveHandlingEvent(command.trackingNumber(), lastEvent);
```

これは **集約の永続化がリポジトリに閉じていない**ことを意味します。本来 `trackingRepository.save(trackingRecord)` の一発で、集約の変更（状態 + 追加されたイベント）が保存されるべきです。アプリケーション層が「集約のどの部分をどう保存するか」を知ってしまっており、永続化の詳細が漏れています。

原因は、`TrackingRecord` が「新規追加されたイベント」を区別して持っていないことです。C# 実装の `AggregateRoot` がドメインイベントを溜めるのと同じ仕組みで、未保存のイベントを集約が保持していれば、リポジトリ側で「未保存分だけ INSERT する」処理を書けます。

**リポジトリのインターフェースが集約単位でない設計は、アプリケーション層に永続化の知識を漏らす**という典型例です。

### エスカレーションフラグの扱い

`exceptionType().isEscalationRequired()` の結果を、リポジトリに渡して DB に保存しています。列挙から導出できる値を、なぜ永続化するのか。

理由は監査です。将来「紛失はエスカレーション不要」に業務ルールが変わったとき、過去のレコードは当時のルールで判断されたことを保持すべきです。導出値を保存するのは、多くの場合は冗長ですが、**判断時点のルールを凍結する**目的では正当な設計です。

ただし、この意図はコードにもドキュメントにも記録されていません。次に読む人には「無駄な冗長性」に見えます。

## 他言語ではどう書いたか

### 例外処理と Billing の連携

例外が発生したら料金を調整する、という要求が US21（料金算出）と結びつきます。この連携をどう扱ったかで実装が分かれます。

| 言語 | 例外 → 料金調整の連携 |
| :--- | :--- |
| Java | IT10 で US21 受入基準 6 として実装 |
| Go | IT8 で対応。「例外発生時の料金調整」を明示 |
| Ruby | IT7 で実装し、IT9 で運用可能な形に作り直し |

Ruby 実装の経緯が最も詳細に記録されています。IT7 で料金調整を実装したものの、IT8 のレビューで「経理業務として回せる形になっていない」ことが判明し、IT9 で作り直しています。

> 特に料金調整（US21-6）を経理業務として回せる形（補償費用の方向確定・取消・監査証跡・例外からの導線）で解消する。

4 つの不足が挙がっています。

1. **補償費用の方向確定** — 増額なのか減額なのか。当社負担なら請求減算、という業務判断を確定させた
2. **取消** — 誤った調整を取り消せる
3. **監査証跡** — 担当者・理由・日時
4. **例外からの導線** — 例外管理画面から該当請求書へ行ける

1 が最も重い問題です。「補償費用」という項目名だけでは、それが請求額を増やすのか減らすのか決まりません。実装は片方を選んでいたはずですが、それが業務判断として確定していなかった。IT9 で ADR-0005 として記録されました。

**符号の意味が曖昧な金額項目は、必ずどこかで逆に解釈される**というのがこの事例の教訓です。

さらに Ruby 実装では、この作り直しの過程で実バグが検出されています。

> tester が検出した `remove_adjustment` の seq_number 負値インデックスによる末尾誤削除（実バグ）

調整の取消処理で、連番から配列インデックスを計算する際に負値が入り、Ruby の配列が末尾から数える挙動によって別の要素が削除されていました。ユニットテストは通っていました。

### 例外の状態遷移

例外発生時、貨物状態は `EXCEPTION`（例外発生）になります。問題は **その後どこに戻るか**です。

Java の `TrackingRecord.addException` は状態を `EXCEPTION` にするだけで、復帰の遷移を定義していません。復帰は手動状態更新（US17）で行うことになります。

Haskell・Scala の遷移表方式では、`EXCEPTION` からの遷移先を表に書く必要があります。書かなければコンパイルは通っても遷移が拒否され、テストで気づきます。**遷移表を持つ設計は、状態を追加したときに「そこから出る道」を考えることを強制します**。

Java 実装のように個別ガード方式だと、`EXCEPTION` に入る道だけ書いて出る道を書き忘れても、何も起きません。手動更新という抜け道があるため、実際に業務が詰まることもありません。ただし「例外から復帰する」という業務が、正規の遷移としてモデル化されていない状態です。

### 緊急フラグとロール別通知

US20 の受入基準に「紛失の場合は緊急フラグが設定される」があります。フラグを立てた後、誰に伝えるかが実装ごとに違います。

Rust 実装では、通知の宛先をロールで指定しています。

> 期限超過→経理へ未払い通知: `CheckOverdueService`＋`Invoice::mark_overdue`。手動駆動エンドポイント＋一覧ボタンを提供し、HTTP テストで OVERDUE 遷移＋PAYMENT_OVERDUE（**経理・ROLE_BILLING 宛**）を 1:1 実証。

通知先が「ROLE_BILLING を持つユーザー」と明示され、それがテストでアサートされています。Java 実装では通知そのものが未実装（メールインフラ未整備）で、UI 上の記録に留まっています。

Java のふりかえりには、この判断が記録されています。

> US19・US20 の受入条件「荷主への通知が送信される」はメールインフラ未整備のため UI 上の通知記録のみで対応。本格的な通知機能は IT10 以降の判断事項として持ち越し。

未実装を未実装と記録する姿勢は正しいものです。ただし F# 実装は IT8 で「MailSender 送信抽象・連絡先解決・通知失敗経路テスト」を実装しており、**送信そのものを抽象化してテストで検証する**ところまで到達しています。メールインフラがなくても、ポートとスタブがあれば「通知が発火すること」は検証できます。

## このイテレーションの学び

### ブランチカバレッジが上がらない

命令カバレッジ 80% を達成した一方、ブランチカバレッジは 74% に留まりました。

> 例外処理の分岐（escalation_flag true/false 等）は E2E でカバーしているが、単体テストの分岐網羅が不十分な箇所が残存

例外処理は分岐が多い領域です。3 つの例外種別 × エスカレーション有無 × 発生時の貨物状態、という組み合わせが生まれます。E2E テストで代表的な経路を通すことはできますが、全組み合わせを E2E で網羅するのは非現実的です。

ここは単体テストの領分です。`ExceptionType` の各値についてエスカレーション要否を検証し、`TrackingRecord.addException` を各状態から呼んで検証する。テーブル駆動テストが最も効率的な形になります。

Go 実装は言語慣習としてテーブル駆動テストが標準であり、この種の組み合わせ網羅が自然に書かれます。Haskell・Rust ではプロパティベーステスト（hedgehog / proptest）で「どの状態から例外を登録しても状態は `EXCEPTION` になる」といった不変式を検証できます。

**分岐の多い領域では、言語のテスト文化がカバレッジに直接効きます**。

### レビューで検出される欠陥の種類

このプロジェクトでは、イテレーションのクローズ前にマルチパースペクティブレビュー（プログラマー・テスター・アーキテクト・テクニカルライター・ユーザー代表の 5 視点）を実施しています。

各実装のレビューで検出された欠陥を並べると、視点ごとの守備範囲がはっきりします。

| 視点 | 検出した欠陥の例 |
| :--- | :--- |
| tester | `remove_adjustment` の負値インデックスによる末尾誤削除（Ruby） |
| tester | 監査証跡の偽陽性テスト（担当者を固定値で検証していた）（Ruby） |
| architect | 未配線サービス（受入基準が実運用で駆動しない）（Rust） |
| user-representative | 出港済みの便が一覧に混ざり、一覧全体が信用されない（Flix） |
| architect | ACL の方向反転が ADR に記録されていない（Flix） |

user-representative（ユーザー代表）が検出した「出港済みの便が一覧に混ざる」は、受入基準に書かれていない問題です。仕様上は正しいが、業務として使えない。**受入基準を満たすことと、業務が回ることは別**という、このプロジェクトを通じて繰り返し現れるテーマです。

Flix 実装のふりかえりには、この教訓が「一覧は『毎朝どう使うか』から確かめる」という形で記録されています。

### 全緑のまま欠陥が残る

Flix 実装の IT3 ふりかえりに、端的な記録があります。

> 緑のテストと壊れ方の設計は別物。自作セキュリティは攻撃者視点・運用視点をレビューで明示依頼する（IT3 で全緑のまま 5 件の欠陥）。

テストが全件通っている状態で、レビューが 5 件の欠陥を検出しました。テストは「意図した動作をするか」を検証しますが、「意図しない使われ方をしたときにどう壊れるか」は検証しません。

同じ実装の IT6 では、楽観的ロックと診断ヘッダを追加したものの、どちらも実際には機能していなかったことが判明しています。

> 安全装置は破るテストで固定する。楽観的ロック・診断ヘッダは「入れたこと」でなく「働くこと」を検証。

楽観的ロックを入れたなら、**同時更新を起こして片方が失敗することをテストで示す**必要があります。「version カラムを追加した」だけでは、機能しているかどうか分かりません。

---

- 前の章：[第 9 章：IT8 引取記録・追跡照会・状態手動更新](09-iteration-08.md)
- 次の章：[第 11 章：IT10 輸送料金算出とリリース 2.0](11-iteration-10.md)
