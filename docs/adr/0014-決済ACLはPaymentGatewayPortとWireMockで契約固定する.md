# ADR-0014: 決済 ACL は PaymentGatewayPort で抽象化し WireMock.Net で契約固定する

入金確認（US23）の外部決済機関連携を、関数レコードの ACL ポート（`PaymentGatewayPort`）で抽象化し、契約を WireMock.Net で固定する決定。

日付: 2026-07-17

## ステータス

- 2026-07-17 提案（IT7・US23 精算処理。IT7 は合成層スタブで実装し、外部連携の実装は後続 IT）
- 2026-07-17 承認（IT8 task3.1。実 HTTP アダプタ `PaymentGateway.createHttp` を合成層に実装し、契約（成功・決済拒否・障害）をインプロセス HTTP スタブで固定する受け入れテストを整備）

> **契約スタブの実装変更（IT8）**: 当初 WireMock.Net を想定したが、WireMock.Net が高深刻度の脆弱性を持つ推移的依存（`System.Linq.Dynamic.Core` 1.3.12・`Scriban.Signed` 5.5.0 等・NU1903）を多数引き込むため、セキュリティを優先し .NET 標準の `HttpListener` によるインプロセス契約スタブへ切り替えた。契約固定（リクエスト/レスポンス形状・エラーコード写像）という本 ADR の意図は同等に達成される。外部テスト依存を増やさない利点もある。

## コンテキスト

US23（精算を処理する）は「決済機関との連携により入金確認ができる」ことを求める。これは Cargo Tracker で初めての外部システム（決済ゲートウェイ）との連携である。外部連携をドメイン・アプリ層に直接持ち込むと、決済機関の API 仕様変更がドメインへ波及し、テストも外部依存でflaky になる。

一方、開発戦略（終盤・手順 3）は「決済 ACL（`PaymentGatewayPort`）を関数レコードで結線し、WireMock.Net で契約を固定する」と定めている。

## 決定

**入金確認の外部決済連携を、アプリ層のポート `PaymentGatewayPort`（関数レコード）で抽象化し、実装アダプタの契約を WireMock.Net で固定する。**

- **ポート定義**（`CargoTracker.Billing.Application`）:

  ```fsharp
  type PaymentGatewayPort =
      { ConfirmPayment: InvoiceId -> Money -> Async<Result<DateTimeOffset, DomainError>> }
  ```

  アプリ層の `Billing.confirmPayment` はこのポートを受け取り、入金確認の成否と支払時刻のみに依存する。決済機関の HTTP 詳細は知らない。

- **IT7 の実装（スタブ）**: 合成層（`CargoTracker.Web`）で即時成功を返すスタブ（`stubPaymentGateway`）を結線し、精算フローを全層で成立させる。外部 HTTP 実装は含めない。

- **外部連携の実装（後続 IT）**: 決済機関の実 API を呼ぶアダプタを合成層に実装し、その契約（リクエスト/レスポンス形状・エラーコード）を WireMock.Net でスタブしたテストで固定する。ドメイン・アプリ層のテストは `PaymentGatewayPort` のインメモリ実装で純粋に保つ。

### 代替案

- **案 B: 決済 API を直接アプリ層/ドメインから呼ぶ**（却下）: 外部依存がドメインへ波及し、BC 分離・純粋性・テスト容易性を損なう。
- **案 C: IT7 で実 API 連携まで実装**（却下・後続へ）: 決済機関の選定・認証情報・サンドボックス整備を要し、Release 1.1 の精算フロー完成をブロックする。ポート抽象で先に業務フローを完成させ、実連携は独立 IT で行う。

## 影響

### ポジティブ

- 決済連携がポートに閉じ、ドメイン・アプリ層は外部詳細から独立する（ADR-0001 の BC 分離・ヘキサゴナルと一貫）。
- IT7 はスタブで精算フローを完成でき、Release 1.1 出荷をブロックしない。
- 実連携の契約を WireMock.Net で固定でき、外部依存の flaky テストを避けられる。

### ネガティブ

- IT7 時点では実際の入金確認は行われず、スタブが常に成功を返す（業務的には未検証）。
- 実 API アダプタ・エラーマッピング・リトライ/冪等性は後続 IT の未実装作業として残る。

## コンプライアンス

- `Billing.confirmPayment` が `PaymentGatewayPort` にのみ依存し、Billing ドメインが外部 HTTP を参照しないことを ArchUnitNET で確認する。
- 実連携実装時、決済機関の契約（成功・決済拒否・障害）を固定した受け入れテストを整備する（IT8 で `PaymentGatewayContractTests` に実装済み。契約スタブは HttpListener・脆弱性回避のため WireMock.Net は不採用）。

## 備考

著者: アーキテクト（Claude Code 支援）。関連: ADR-0001（BC 分離）、ADR-0006（時刻・GUID 注入ポート）、ADR-0013（Billing↔Booking 連携）、`docs/development/development_strategy.md`（終盤・決済 ACL）、`docs/development/iteration_plan-7.md`（タスク 3.3）、`docs/development/retrospective-7.md`（Try#2）。
