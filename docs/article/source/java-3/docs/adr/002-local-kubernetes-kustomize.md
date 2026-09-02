# ADR-002: ローカル開発環境に kind + Kustomize を採用

ローカル開発環境は Docker Compose ではなく、kind クラスタ上に Kustomize（base / overlay 構成）で全サービスをデプロイする。

日付: 2026-08-19

## ステータス

承認済み

## コンテキスト

- 7 つのマイクロサービス + PostgreSQL + RabbitMQ + フロントエンドをローカルで一括起動する必要がある（ADR-001）
- take-3 は Docker Compose を採用していたが、本プロジェクトでは記事「Docker/Kubernetes 入門」のケーススタディ 2（イベント駆動マイクロサービス）で kind + Kustomize による同一構成のデプロイ実績がある
- ステージング・本番のコンテナオーケストレーション運用（宣言的マニフェスト・Probe・Service ディスカバリ）にローカルから慣れておきたい

## 決定

- ローカルは **kind** クラスタに **Kustomize** でデプロイする（`kubectl apply -k`）
- マニフェストは `ops/k8s/kustomize/base/`（共通の土台）と `overlays/local/`（環境差分）に分ける
- 6 DB の初期化 SQL は `configMapGenerator` で ConfigMap 化、イメージタグは `images` で集中管理する
- 起動順序制御は作り込まず、`readinessProbe` の範囲で DB 起動待ちの再起動から復帰させる

### 代替案

- **Docker Compose**（take-3 方式）: 起動は最も簡単だが、Kubernetes の宣言的構成・Probe・Ingress をローカルで検証できない。記事のケーススタディ 13 章で比較済み
- **Helm**: パラメータ化は強力だが、テンプレート言語の学習コストがかかる。環境差分が少ないローカル用途では Kustomize の overlay で十分（記事 14 章の比較結論に従う）
- **minikube / Docker Desktop 内蔵 K8s**: kind はクラスタの作成・破棄が速く、CI でも同一手段を使える

## 影響

### ポジティブ

- ローカルと CI（統合テスト）で同じ base マニフェストを共有でき、環境差分は overlay に隔離される
- `kubectl kustomize` で合成結果を適用前に確認できる（テンプレート言語不要、kubectl 同梱）
- Kubernetes の運用スキルが開発初日から蓄積される

### ネガティブ

- Docker Compose よりも初期セットアップの手数が多い（kind クラスタ作成、`kind load docker-image` によるイメージロード）
- 開発者に kubectl / Kustomize の基礎知識が必要

## コンプライアンス

- `kubectl apply -k ops/k8s/kustomize/overlays/local` の適用後、10 Pod がすべて `1/1 Running` になることをセットアップ手順書の確認項目とする
- 環境固有の値が base に混入していないことをレビューで確認する（差分は overlay のみ）

## 備考

- 著者: k2works
- 参考: [ケーススタディ 2: イベント駆動 - Kustomize vs Helm](../article/getting-start-docker-kubernetes/14-case-event-driven-kustomize-vs-helm.md)
- 関連ドキュメント: [インフラストラクチャアーキテクチャ設計](../design/architecture_infrastructure.md)
- 関連 ADR: ADR-001、ADR-003
