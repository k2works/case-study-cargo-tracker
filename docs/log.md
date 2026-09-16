# Docs Update Log

## 2026-09-16
* **Verification**: [Unit 定義](/requirements/units.md)・[リリース計画](/development/release_plan.md)・[ユーザーストーリー](/requirements/user_story.md)・[ドメイン用語集](/design/glossary.md)・[リスク台帳](/requirements/risk_register.md)・[AI-DLC 移行計画](/development/aidlc_migration_plan.md) を human:kakimomokuri が承認ゲートで検証
* **Creation**: [リリース計画](/development/release_plan.md) を新規作成。5 Unit をフェーズ 1〜3 の Bolt 1〜20 に割り当て、Unit ごとのエントロピー評価・スコープ・深さ・テスト戦略、確認したい仮説、着手前に解消する要確認、ゲート密度、バッファを定義。ストーリーポイントの見積もり実績が無いため、ポイントの列は設けていない。
* **Creation**: [Unit 定義](/requirements/units.md) を新規作成。ストーリー 31 本を U-01 認証・U-02 貨物予約・U-03 航海スケジュール・経路設計・U-04 荷役・追跡・U-05 精算にまとめ、各 Unit の目的・NFR・リスク・測定基準・推奨 Bolt・エントロピー評価・AI の仮定、依存 DAG、リリース対応、ストーリーマップを定義。未配置のストーリーは無し。
* **Update**: [ユーザーストーリー](/requirements/user_story.md) の受入条件 31 本を Given/When/Then に書き直し、AC 番号・Unit・参照する成果物・確認ポイント・AI の仮定の欄を追加。見出しを「受け入れ基準」から「受入条件」に統一し、description の「US36」を実体に合わせて「US31」に訂正。
* **Update**: [要件定義書](/requirements/requirements_definition.md)・[システムユースケース](/requirements/system_usecase.md)・[ビジネスユースケース](/requirements/business_usecase.md) に用語集の統一案 8 件を反映（ルート→経路、予約状態名を状態モデルに統一、引き取り→引取、冷凍・冷蔵貨物、経由港、例外種別 5 種など）。要件定義書の状態モデルとユースケース一覧を、システムユースケースの UC 採番（UC01〜UC22）に揃えた。UC21 の存在しない ADR-006 の参照と、状態の英字コードを削除。[docs/index.md](/index.md) の BUC・UC の件数を実体（24 件・22 件）に訂正。
* **Creation**: [ドメイン用語集](/design/glossary.md) を新規作成。アクター・業務の概念・状態（予約・貨物・通関）・区分値を定義し、語のゆれ 8 件の統一案と採番の不一致を記録。
* **Creation**: [リスク台帳](/requirements/risk_register.md) を新規作成。AI に渡せる範囲（In/Out）、確認必須の操作、R-01〜R-10 のリスクと承認ゲートを定義。
* **Creation**: [AI-DLC 移行計画](/development/aidlc_migration_plan.md) を新規作成。移行前の検査結果 ERROR 2 / WARN 7 を記録し、10 ステップと決定事項を定義。あわせて `.claude/skills/` と `.agents/skills/` の developing-backend・developing-frontend・planning-releases・analyzing-usecases・developing-review・ai-agent-guidelines に `PROJECT.md`（AI-DLC のガードレール）を追加。

## 2026-09-12
* **Verification**: [AI-DLC用語集](/reference/AI-DLC用語集.md) を human:kakimomokuri が検証
* **Verification**: [コーディングとテストガイド_AI-DLC版](/reference/コーディングとテストガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [ユースケース作成ガイド_AI-DLC版](/reference/ユースケース作成ガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [リリース・イテレーション計画ガイド_AI-DLC版](/reference/リリース・イテレーション計画ガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [開発ガイド_AI-DLC版](/reference/開発ガイド_AI-DLC版.md) を human:kakimomokuri が検証
* **Verification**: [AI-DLC導入ガイド](/reference/AI-DLC導入ガイド.md) を human:kakimomokuri が検証
* **Creation**: [AI-DLC 用語集](/reference/AI-DLC用語集.md) を新規作成。論文・参照実装・本プロジェクトの AI-DLC 版ガイドの用語を 6 区分で定義し、XP・Scrum との対応表と参照先を追加。`CLAUDE.md` の参照表にも登録。
* **Creation**: [開発ガイド（AI-DLC 版）](/reference/開発ガイド_AI-DLC版.md) を新規作成。XP 版の開発ライフサイクル（分析・開発・運用・構築・配置）を AI-DLC の 3 フェーズで読み替え、各活動で AI が生成し人が検証するものを定義。AI-DLC 版ガイド 4 本と XP 版ガイドの対応表を追加。あわせて `CLAUDE.md` に AI-DLC を採用する旨と守るべき 6 原則を明記し、ペルソナの参照先を開発ガイド（AI-DLC 版）に変更。
* **Creation**: [コーディングとテストガイド（AI-DLC 版）](/reference/コーディングとテストガイド_AI-DLC版.md) を新規作成。XP 版の章構成を保ちながら、TDD の三原則を AI に守らせる Bolt 開発フロー、承認ゲートの密度選択、AI 向けのアプローチ指示、ステップ計画、Red/Green/Refactor 各フェーズの人の検証観点、AI 生成コード固有の品質観点、ソース束縛レビュー、テスト戦略の水準、ガードレール化、quick-cement としての技術的負債、開発スキルの読み替え表を定義。
* **Creation**: [ユースケース作成ガイド（AI-DLC 版）](/reference/ユースケース作成ガイド_AI-DLC版.md) を新規作成。XP 版の章構成を保ちながら、Mob Elaboration によるユースケース作成手順、12 ステップの AI と人の分担、トレーサビリティ項目を加えたテンプレート、セマンティクス密度、AI 生成ユースケースの検証観点、ブラウンフィールドのリバースエンジニアリング、プロンプトパターン、分析スキルの読み替え表を定義。
* **Creation**: [リリース・イテレーション計画ガイド（AI-DLC 版）](/reference/リリース・イテレーション計画ガイド_AI-DLC版.md) を新規作成。XP 版の章構成を保ちながら、Intent → Unit → Bolt の計画階層、承認駆動の Bolt 計画、エントロピー評価による見積もり、完了 Unit 数・ゲート通過数・リードタイムによる進捗管理、リスク台帳、Bolt 終了報告テンプレート、計画スキルの読み替え表を定義。
* **Update**: [AI-DLC 導入ガイド](/reference/AI-DLC導入ガイド.md) を一次情報（Method Definition Paper・aidlc-workflows ユーザーガイド）で精査し拡充。10 の基本原則、成果物定義（Intent・Unit・Bolt・Domain/Logical Design・Deployment Unit）、Inception の 6 成果物、グリーンフィールド／ブラウンフィールド実践例、付録 A のプロンプトパターン、参照実装の 5 フェーズ 33 ステージ・スコープ・エージェント・承認ゲート・監査ログ、33 ステージと Skills の対応表を追加。
* **Creation**: [AI-DLC 導入ガイド](/reference/AI-DLC導入ガイド.md) を新規作成。AI-DLC の概要・コア原則・3 フェーズ・ベストプラクティスを整理し、XP と Skills 体系への対応表と段階的な導入ステップを定義。

## 2026-09-02
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
