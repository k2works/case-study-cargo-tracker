# operating-qt（本プロジェクトの実際）

SKILL.md の汎用手順に対する、本プロジェクト固有の設定です。食い違う場合はこちらを正とします。

## ポート

**ホスト側は 9001 です**（コンテナ内は 9000 のまま）。

| 項目 | 値 |
| :--- | :--- |
| Web UI | `http://localhost:9001` |
| `SONAR_HOST_URL`（`.env`） | `http://localhost:9001` |
| `LOCAL_SONAR_PORT` の既定 | `9001` |

9000 は IntelliJ IDEA が LISTEN します。既定のままだと `sonar-local:start` が
`bind: address already in use` で止まり、走査のたびに IDE を落とす必要がありました。

## プロジェクトキー

`.env` の `SONAR_PROJECT_KEY` は `sonarqube.config.json` のキーと一致させます。
食い違うとゲート判定が対象を見つけられず、**一度も走っていないのに緑に見えます**（IT2 で実際に起きました）。

## 実行

```bash
npx gulp sonar-local:check   # scan + gate
```

カバレッジは走査の直前に生成されます（`sonarqube.config.json` の `coverageCommand`）。
別々に実行すると lcov が古いまま走査され、新規カバレッジが 0% になります。
