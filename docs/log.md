# Docs Update Log

## 2026-09-02
* **Migration**: 前回移行以降に追加された 104 件を OKF v0.2 に適合させた。記事 97 件（practical-ddd-spring-boot 23・ai-driven-development 15・xp-domain-driven-design 14・monolith-architecture 12・enterprise-architecture 12・practical-ddd-in-enterprise-java 11・functional-domain-modeling 10）に `type: Article` を、[ビジネスアーキテクチャ](/strategy/business_architecture.md)・[インセプションデッキ](/strategy/inception-deck.md) に `type: Strategy` を付与。requirements 4 件と review 1 件は Wiki.js 由来のフロントマター（`published`・`editor`・`date`）を OKF 形式に併合し、`type: Requirements` / `type: Review` を与えた。本文は変更していない。
* **Update**: 検査・移行の対象外パスを宣言する `docs/.okfignore` を追加し、`article/source/` を除外した。mkdocs.yml の `exclude_docs` と対応する。配下は記事のサンプル実装ソースツリーで、入れ子の docs やサードパーティ由来の README を含むため知識バンドルの対象にしない。`okf_check.py` に `.okfignore` 対応を追加した。

## 2026-08-26
* **Verification**: [ドキュメント構成ガイド](/reference/ドキュメント構成ガイド.md) を human:kakimomokuri が検証
* **Update**: ドキュメント構成ガイドを更新。docs/review を共通からプロジェクト別カテゴリに変更（プロジェクト別は 7 カテゴリに）。
* **Creation**: ドキュメント構成ガイドを新規作成。単一企業・統合戦略・複数プロジェクトのコンセプトと apps/ との対応規約を定義。

## 2026-08-25
* **Update**: リンク切れ 53 件を修正。`grokking-concurrency` のサンプルコード参照をインラインコード表記に統一、`functional-desgin-ppp/elixir` の目次 6〜10 章を実際の章構成に合わせて書き直し、[Codex CLI MCP アプリケーション開発フロー](/reference/CodexCLIMCPアプリケーション開発フロー.md) の関連ドキュメントを実在ガイドに付け替え、未執筆の付録は「未作成」と明記。`template/まずこれを読もうリスト.md` の 10 件はコピー先基準のパスのため据え置き。
* **Migration**: `docs/` を OKF v0.2 の知識バンドルに移行。601 件のコンセプト（Article 552 件・Reference 31 件・Template 18 件）に `type`・`title`・`description`・`tags`・`generated` を付与し、ルート `index.md` に `okf_version: "0.2"` を宣言。本文は変更していない。Wiki.js 由来のフロントマターは OKF 形式に併合した。
