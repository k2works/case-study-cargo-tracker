# cargo-tracker

国際貨物輸送管理システムの Flix 実装です。

## 前提

- JDK 25（LTS）
- Flix 0.75.1（`ops/tools/flix/flix.jar`）

## コマンド

```bash
# ビルド
java -jar ../../ops/tools/flix/flix.jar build

# テスト
java -jar ../../ops/tools/flix/flix.jar test

# 実行可能 JAR の生成
java -jar ../../ops/tools/flix/flix.jar build-jar

# 実行（build-jar は Maven 依存を同梱しないため lib/ をクラスパスに追加する）
java -cp "artifact/cargo-tracker.jar:$(find lib -name '*.jar' | tr '\n' ':')" Main
```

## ドキュメント

- [アプリケーション開発環境セットアップ手順書](../../docs/operation/アプリケーション開発環境セットアップ手順書.md)
- [設計ドキュメント](../../docs/design/index.md)
