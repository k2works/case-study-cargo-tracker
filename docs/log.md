# Docs Update Log

## 2026-09-02
* **Creation**: Creation: cargo-tracker の設計レビュー（2026-09-02）。XP エージェント 5 名の並列レビューを統合。高 20 件のうち 17 件を修正、通知の送信基盤はスコープ外と明記、段階導入計画は次工程へ引き渡す。
* **Creation**: Creation: cargo-tracker の技術スタックを新規作成（stale_after 90 日）。Axon 系のバージョン同期と Axon Server 2026.0.4 を正典とした。
* **Creation**: Creation: cargo-tracker の運用要件を新規作成。リプレイを障害対応でなく日常操作に置き直し、四半期の復元演習と crypto-shredding の手順を加えた。
* **Creation**: Creation: cargo-tracker の非機能要件を新規作成。反映の遅れを SLO に置き、Axon Server SE の停止を可用性の前提として明記し、イベントが削除できない前提で個人情報の削除要件を定めた。
* **Creation**: Creation: cargo-tracker のテスト戦略を新規作成。投影をプロファイルで除外せず Testcontainers の Axon Server で確かめ、契約は両側のゴールデン JSON と往復で守り、名簿は載っていないものを通さない形にした。
* **Creation**: Creation: cargo-tracker の UI 設計を新規作成。反映中・楽観的更新・409 の扱いを画面共通の規約にし、要確認一覧（反映の拒否・失敗した Saga）を画面として置いた。
* **Creation**: Creation: cargo-tracker のインフラストラクチャアーキテクチャを新規作成。Axon Server を無効化する開発環境（Heroku）は採らず、結合テストは Testcontainers とステージングで行う。
* **Creation**: Creation: cargo-tracker のフロントエンドアーキテクチャを新規作成。結果整合性を隠さず 202 + 後追い確認を既定にした。
* **Creation**: Creation: cargo-tracker のデータモデル設計を新規作成。投影テーブルは派生データとして業務 CHECK を置かず、一意制約は投影を最後の砦とし、履歴テーブルはイベント列に置き換える方針で 6 DB を定義した。
* **Creation**: Creation: cargo-tracker のドメインモデル設計を新規作成。take-4 の Axon 5 前提モデルに java-3 の UC21 通関・UC22 キャンセル承認・US28 誤配・US31 アカウント保護を取り込み、内部イベントと契約イベントを分けて定義した。
* **Creation**: ADR-0002 Event Store は Axon Server SE、Read Model は PostgreSQL + MyBatis にする。
* **Update**: ユーザーの指示により配置をモジュラーモノリスからマイクロサービス（7 サービス + Gateway + shared）に変更。バックエンドアーキテクチャ・ADR-0001（改名）・ADR-0002 を改訂。サービス間の同期問い合わせは REST でなく Axon Query Bus を通す。
* **Creation**: ADR-0001 CQRS / Event Sourcing を Axon Framework 5 でモジュラーモノリスとして実装する（同日マイクロサービスに改訂）。
* **Creation**: cargo-tracker のバックエンドアーキテクチャ（Axon Framework 5 による CQRS / Event Sourcing 版）を新規作成。tmp/take-4 と source/java-3 の設計を参照元とし、プロセス境界は第 3 章のモジュラーモノリスに戻して永続化と読み書きの分離だけを変える。
* **Creation**: [エンタープライズ Java における実践的 DDD（draft-2）](/article/practical-ddd-in-enterprise-java/draft-2/index.md) の第 4 章「プロセスを越えるイベント — マイクロサービス版の Cargo Tracker」を執筆。参照元は新たに収録した `source/java-3`（`java/take-7`。8 サービス + 共有ライブラリ・RabbitMQ）。索引・アウトライン（§4〜§7）・第 3 章末尾の誘導もあわせて更新した。
* **Creation**: 第 4 章の参照元として `docs/article/source/java-3/` を収録（実装 1,186 ファイル・一次資料 199 ファイル）。アウトライン §5 の着手条件「メッセージングを使う実装が `docs/article/source/` に収録されること」を満たすため。`source/README.md` に節を追加した。
* **Creation**: [Docker/Kubernetes 実践コンテナ解説](/article/getting-start-docker-kubernetes/index.md) と [実践データベース設計](/article/practical-database-design/index.md) のシリーズ索引を新規作成。各章から張られていたリンク切れ 13 件を解消した。
* **Migration**: 前回移行以降に追加された 104 件を OKF v0.2 に適合させた。記事 97 件（practical-ddd-spring-boot 23・ai-driven-development 15・xp-domain-driven-design 14・monolith-architecture 12・enterprise-architecture 12・practical-ddd-in-enterprise-java 11・functional-domain-modeling 10）に `type: Article` を、[ビジネスアーキテクチャ](/strategy/business_architecture.md)・[インセプションデッキ](/strategy/inception-deck.md) に `type: Strategy` を付与。requirements 4 件と review 1 件は Wiki.js 由来のフロントマター（`published`・`editor`・`date`）を OKF 形式に併合し、`type: Requirements` / `type: Review` を与えた。本文は変更していない。
* **Update**: 検査・移行の対象外パスを宣言する `docs/.okfignore` を追加し、`article/source/` を除外した。mkdocs.yml の `exclude_docs` と対応する。配下は記事のサンプル実装ソースツリーで、入れ子の docs やサードパーティ由来の README を含むため知識バンドルの対象にしない。`okf_check.py` に `.okfignore` 対応を追加した。

## 2026-08-26
* **Verification**: [ドキュメント構成ガイド](/reference/ドキュメント構成ガイド.md) を human:kakimomokuri が検証
* **Update**: ドキュメント構成ガイドを更新。docs/review を共通からプロジェクト別カテゴリに変更（プロジェクト別は 7 カテゴリに）。
* **Creation**: ドキュメント構成ガイドを新規作成。単一企業・統合戦略・複数プロジェクトのコンセプトと apps/ との対応規約を定義。

## 2026-08-25
* **Update**: リンク切れ 53 件を修正。`grokking-concurrency` のサンプルコード参照をインラインコード表記に統一、`functional-desgin-ppp/elixir` の目次 6〜10 章を実際の章構成に合わせて書き直し、[Codex CLI MCP アプリケーション開発フロー](/reference/CodexCLIMCPアプリケーション開発フロー.md) の関連ドキュメントを実在ガイドに付け替え、未執筆の付録は「未作成」と明記。`template/まずこれを読もうリスト.md` の 10 件はコピー先基準のパスのため据え置き。
* **Migration**: `docs/` を OKF v0.2 の知識バンドルに移行。601 件のコンセプト（Article 552 件・Reference 31 件・Template 18 件）に `type`・`title`・`description`・`tags`・`generated` を付与し、ルート `index.md` に `okf_version: "0.2"` を宣言。本文は変更していない。Wiki.js 由来のフロントマターは OKF 形式に併合した。
