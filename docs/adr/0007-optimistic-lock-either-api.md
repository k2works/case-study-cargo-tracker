# 0007 楽観ロックを Either API として表現する

リポジトリの `save` が楽観ロック競合時に `OptimisticLockException` を throw する現状を、`Either[DomainError.ConcurrentModification, A]` で表現する API に統一する。

日付: 2026-06-21

## ステータス

2026-06-21 提案されました（IT4 タスク 0.5）

実装は IT5 以降に先送り。本 ADR は方針合意のみを固める。

## コンテキスト

IT2 で `version: Int` カラムと `OptimisticLockException` を導入し、IT3 で全集約（`Cargo` / `Estimate` / `Shipper` / `Voyage`）に展開した。現状の API 形は：

```scala
trait CargoRepository:
  def save(cargo: Cargo): Unit  // 競合時は OptimisticLockException を throw
```

これに対し、IT3 マルチパースペクティブレビュー（高優先度 #7）で次の課題が指摘された。

1. **例外による副作用伝播**: `Either[DomainError, A]` で統一しているドメイン層に、競合時だけ例外が混ざる。アプリケーションサービスは `try/catch` で個別に変換する必要があり、表現の一貫性が崩れる。
2. **forwarding が冗長**: アプリケーションサービスの多くが `Either[String, A]` で結果を返すため、`save` の戻りも結果型に揃えた方がドメイン層から UI までの合成（`for` 式）が素直になる。
3. **テスタビリティ**: 例外を ScalaTest で `intercept[OptimisticLockException]` するより、`Left(ConcurrentModification)` を `shouldBe` で比較するほうが意図が明示的になる。

参考: Cats / ZIO 系ライブラリでもエラー値化の方向に倒すのが定石。Scala 3 の `Either` は `for` 内包表記で第一級扱い。

## 決定

### (a) 共有エラー型に `ConcurrentModification` を追加

`cargotracker.shared.domain.DomainError` を新設し、横断的なドメインエラーを束ねる（既存の `OptimisticLockException` は段階移行のため一時併存）。

```scala
package cargotracker.shared.domain

sealed trait DomainError
object DomainError:
  /** 楽観ロック競合（version が永続化済みと一致しない）。 */
  case object ConcurrentModification extends DomainError
```

### (b) リポジトリ API の戻り型変更

```scala
trait CargoRepository:
  def save(cargo: Cargo): Either[DomainError.ConcurrentModification.type, Cargo]
```

- 既存の `Unit` を返す API は段階廃止する。新規リポジトリは最初から Either 版で実装する。
- 戻り値の `Cargo` は version インクリメント後のインスタンス（呼び出し側で再利用できるよう保存後の集約を返す）。

### (c) アプリケーションサービスへの伝播

```scala
def confirmRoute(cmd: SelectRouteCommand): Either[String, Cargo] =
  for
    cargo <- repository.findByBookingId(cmd.bookingId).toRight("予約が見つかりません")
    updated <- cargo.assignItinerary(itinerary).left.map(_.toString)
    saved <- repository.save(updated).left.map { case ConcurrentModification =>
      "他の処理により予約が更新されています。最新状態を再読み込みしてください"
    }
  yield saved
```

`for` 式 1 本に集約し、`try/catch` を排除する。

### (d) Controller での文言

PRG（Post-Redirect-Get）で flash["error"] に上記文言を載せ、再読み込みリンクを表示する。HTTP ステータスは 409 ではなく 303 + flash で従来 UX と整合させる。

## 結果

### 利点

- ドメイン層から UI 層までエラー表現が `Either` に統一され、`for` 内包の表現力が活きる
- 楽観ロックのテストが値比較で済む
- 将来的に `DomainError` を sealed で拡張し、エラー種別を網羅的に処理できる

### 欠点・コスト

- 既存集約 4 件（`Cargo` / `Estimate` / `Shipper` / `Voyage`）の save 呼び出し全箇所を Either 化する必要がある（影響: アプリケーションサービス 6 件、Controller 6 件、テスト 12 件以上）
- 段階移行中は `Unit` 版と `Either` 版が併存し、誤用リスクがある

### マイグレーション戦略（IT5 で着手）

1. `DomainError` 追加 + 既存 `OptimisticLockException` を deprecate コメント
2. `CargoRepository.save` の戻り型を変更し、呼び出し側を 1 アプリケーションサービスずつ移行
3. 他集約（`Estimate` / `Shipper` / `Voyage`）に展開
4. 全箇所移行後、`OptimisticLockException` を削除

## 代替案

### 代替案 1: 現状維持（例外）

例外フローも Play / Java 慣行的には十分機能する。しかし上記コンテキスト 1-3 の改善は見送られ、`Either` を活かしたドメインエラー表現の整合性は得られない。

### 代替案 2: Either[String, A]

専用の sealed trait を作らず、エラーメッセージ文字列で表現する。実装は最も軽量だが、Controller 側で文言を分岐する手段が文字列マッチになり脆い。

### 代替案 3: Cats / ZIO 導入

`EitherT` などで合成を強化する案。ライブラリ依存を増やすため、まずは `Either` 単体での運用で十分か検証する（本 ADR の範囲外）。

## 関連

- ADR 0006 航海データモデル追補
- IT3 マルチパースペクティブレビュー 高優先度 #7
- 実装タスク（IT5 以降）: `CargoRepository.save` Either 化を皮切りに段階移行
