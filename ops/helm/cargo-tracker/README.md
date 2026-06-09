# Cargo Tracker Helm チャート

`apps/docker-compose.yml` を Kubernetes 化した Helm 版です。
Kustomize 版（`ops/k8s/`）と同一構成を生成します（比較は `ops/k8s/README.md` 参照）。

## 特徴

- `values.yaml` の `microservices` リストをループして 7 ms の Deployment + Service を生成（DRY）。
- インフラ（Zookeeper / Kafka / PostgreSQL）もクラスタ内に StatefulSet で配置。
- 機密は `secret.create=true` 時に `cargo-secret` を生成（本番は外部 Secret 管理に切替推奨）。

## 前提

各 ms のイメージ（既定 `cargo-tracker/<ms>:latest`）を事前にビルドし、
クラスタ参照可能な場所に配置しておくこと（`ops/k8s/README.md` 参照）。

## デプロイ

```bash
# レンダリング確認（クラスタ不要）
helm template cargo-tracker ops/helm/cargo-tracker -n cargo-tracker

# Lint
helm lint ops/helm/cargo-tracker

# インストール
helm install cargo-tracker ops/helm/cargo-tracker \
  -n cargo-tracker --create-namespace

# アップグレード / ロールバック
helm upgrade cargo-tracker ops/helm/cargo-tracker -n cargo-tracker
helm rollback cargo-tracker -n cargo-tracker
```

## 主な values

| キー | 既定 | 説明 |
| :--- | :--- | :--- |
| `image.registry` | `cargo-tracker` | イメージ接頭辞（レジストリ） |
| `image.tag` | `latest` | 全 ms 共通タグ |
| `defaults.replicas` | `1` | ステートレス ms のレプリカ数 |
| `secret.*` | 開発用 | 本番は `-f values-prod.yaml` 等で上書き |
| `ingress.enabled` | `true` | gatewayms の外部公開 |
| `ingress.host` | `cargo-tracker.local` | Ingress ホスト名 |

## 本番デプロイ例

```bash
helm upgrade --install cargo-tracker ops/helm/cargo-tracker \
  -n cargo-tracker --create-namespace \
  --set image.registry=registry.example.com/cargo-tracker \
  --set image.tag=1.2.0 \
  --set defaults.replicas=2 \
  --set secret.create=false   # 機密は External Secrets 等で別途投入
```

`secret.create=false` の場合は、同名の `cargo-secret`（同一キー構成）を
別途用意してください（ADR-0021 AWS Secrets Manager と整合）。
