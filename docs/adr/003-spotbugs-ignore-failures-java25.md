# ADR-003: SpotBugs を ignoreFailures=true で運用する（Java 25 対��）

SpotBugs が Java 25 ���クラスファイルを未サポートのため、ignoreFailures=true でビルドを継続する。

日付: 2026-04-04

## ステータス

承認済み

## コンテキスト

SpotBugs 4.9.3 は Java 25 のクラスファイル（major version 69）をサポートしていない。`Unsupported class file major version 69` エラーが発生し、exit code 4 でビルドが失敗する。

- SpotBugs を完全に無効化すると、将来 Java 25 対応時に再有効化を忘れるリスクがある
- 参考プロジ���クトでは `ignoreFailures = true` を採用し、レポート出力のみ行う方針としている

## 決定

**`spotbugs { ignoreFailures = true }` を設定し、ビルド失敗させずレポートのみ出力する。**

### 変���箇所

- `apps/cargo-tracker/build.gradle` の `spotbugs` ブロック

### 代替案

| 代替案 | 却下理由 |
|--------|---------|
| SpotBugs を完全無効化 (`enabled = false`) | 将来の再有効化忘れリスク、レポートが生成されない |
| SpotBugs を除外して Java 25 対応を待つ | 対応時期が不明、CI で差分を検知できない |

## 影響

### ポジティブ

- ビルドが失敗しないためデベロッパーの生産性を維持できる
- SpotBugs レポートは HTML で生成されるため、手動確認は可能

### ネガティブ

- SpotBugs の指摘がビルドで自動ブロックされないため、バグパターンが見逃される可能性がある

## コンプライ��ンス

- SpotBugs が Java 25 に対応した時点で `ignoreFailures = false` に戻すこと
- SonarQube の Quality Gate で代替的な品質チェックを行う��と

## 備考

- 関連コミット: `0eec9a8`, `cab0cb8`
- Checkstyle も同様に `ignoreFailures = true` を設定している
