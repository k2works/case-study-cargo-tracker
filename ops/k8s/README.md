# Kubernetes マニフェスト（Kustomize 版）

`apps/docker-compose.yml`（local-docker プロファイル）を Kubernetes 化したものです。
同じ構成を **Helm 版**（`ops/helm/cargo-tracker/`）でも提供しており、両者を比較できます。

## 構成

インフラ（クラスタ内）とアプリ 7 ms をすべて `cargo-tracker` namespace にデプロイします。

| 種別 | リソース | 補足 |
| :--- | :--- | :--- |
| インフラ | zookeeper / kafka / postgresql | StatefulSet + PVC（headless Service） |
| アプリ | authms / bookingms / routingms / trackingms / handlingms / billingms / gatewayms | Deployment + ClusterIP Service |
| 設定 | cargo-config（ConfigMap）/ postgres-init（ConfigMap） | 非機密設定・DB 初期化スクリプト |
| 機密 | cargo-secret（Secret） | 開発用デフォルト値。本番は要上書き |
| 公開 | cargo-tracker（Ingress） | gatewayms のみ外部公開 |

```
ops/k8s/
├── base/                 # 共通マニフェスト（kubectl kustomize base で展開）
│   ├── kustomization.yaml
│   ├── namespace.yaml
│   ├── configmap.yaml    # cargo-config + postgres-init
│   ├── secret.yaml       # ※開発用デフォルト値
│   ├── zookeeper.yaml / kafka.yaml / postgresql.yaml
│   ├── authms.yaml ... gatewayms.yaml
│   └── ingress.yaml
└── overlays/
    ├── local/            # minikube/kind/Docker Desktop 用（gateway を NodePort 30080 に）
    └── prod/             # ステートレス ms を replicas:2、イメージタグ stable
```

## 前提

- docker-compose と異なり Kubernetes は事前ビルド済みイメージを参照します。
  各 ms の Dockerfile（`apps/backend/<ms>/Dockerfile`）からイメージをビルドし、
  クラスタが参照できるレジストリ（または minikube/kind のローカルイメージ）に配置してください。
  - 既定イメージ名: `cargo-tracker/<ms>:latest`（`kustomization.yaml` の `images:` で差し替え可能）
  - minikube: `minikube image load cargo-tracker/authms:latest ...`
  - kind: `kind load docker-image cargo-tracker/authms:latest ...`

## デプロイ

```bash
# ローカル（minikube 例）
kubectl apply -k ops/k8s/overlays/local
kubectl -n cargo-tracker get pods -w

# 本番想定
kubectl apply -k ops/k8s/overlays/prod
```

レンダリング結果の確認（クラスタ不要）:

```bash
kubectl kustomize ops/k8s/overlays/local
```

## アクセス

- Ingress 経由: `http://cargo-tracker.local/`（hosts に Ingress IP を登録）
- NodePort（local overlay）: `http://<node-ip>:30080/`
- ポートフォワード: `kubectl -n cargo-tracker port-forward svc/gatewayms 8080:8080`

## 機密の取り扱い

`base/secret.yaml` の値は**開発・ローカル検証専用**です。本番では以下のいずれかで
`cargo-secret` を上書きしてください（ハードコード禁止・ADR-0021 と整合）。

- `kubectl create secret generic cargo-secret --from-env-file=.env -n cargo-tracker`
- Sealed Secrets / External Secrets Operator（AWS Secrets Manager 連携）

## Kustomize 版と Helm 版の比較

| 観点 | Kustomize 版（`ops/k8s/`） | Helm 版（`ops/helm/cargo-tracker/`） |
| :--- | :--- | :--- |
| ツール依存 | kubectl 同梱（追加導入不要） | helm の別途導入が必要 |
| パラメータ化 | overlay の patch / replacements | `values.yaml` + `--set` で柔軟 |
| 7 ms の重複 | 各 ms を個別 YAML で記述（明示的・冗長） | `range .Values.microservices` で 1 テンプレート化（DRY） |
| 環境差分 | overlays/local・prod を物理分離 | `-f values-<env>.yaml` で切替 |
| リリース管理 | なし（kubectl apply のみ） | `helm install/upgrade/rollback` で版管理 |
| 学習コスト | 低（素の YAML に近い） | 中（テンプレート言語） |
| 向くケース | 構成が固定的・GitOps で差分を見たい | 多環境・再利用・配布したい |

どちらも同一のクラスタ内構成（インフラ込み 7 ms + Ingress）を生成します。
プロジェクトの運用方針に応じて一方に寄せることを推奨します。
