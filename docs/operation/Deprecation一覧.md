# Deprecation 一覧

廃止予定の API エンドポイント・機能の一覧。Sunset 日付を過ぎたものは次のイテレーションで削除対象とする。

## 廃止予定エンドポイント

| # | エンドポイント | サービス | 廃止理由 | Deprecated | Sunset | 代替 |
|---|--------------|---------|---------|-----------|--------|------|
| 1 | `POST /api/v1/tracking/_internal/initialize` | trackingms | IT7 TI07 で Event 駆動化（`CargoTrackedEvent`）に移行するため REST 暫定実装を廃止 | 2026-05-19 | 2026-08-30 | Axon `@EventHandler` が `CargoTrackedEvent` を購読して自動初期化 |

## 廃止済みエンドポイント

現時点なし。

## 廃止手順

1. Deprecated 日付に `@Deprecated` アノテーションとレスポンスヘッダ `Deprecation: <date>` / `Sunset: <date>` を付与する
2. Sunset 日付の到来をイテレーション計画レビュー時に確認する
3. Sunset 日付到達後のイテレーションで Controller メソッドを削除し、ADR にその旨を記録する

## 関連

- [ADR-0014 shared モジュールへの Event クラス昇格](../adr/0014-shared-module-event-classes.md)
- [ADR-0012 handlingms と trackingms の責務分離](../adr/0012-handlingms-trackingms-responsibility-separation.md)
