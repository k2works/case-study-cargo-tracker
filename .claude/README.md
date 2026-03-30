# Claude Code Booster - Basic Template

Claude Code をより効率的に使うための基本設定テンプレートです。

このテンプレートは最小限の構成で、プロジェクトに合わせてカスタマイズできる基盤を提供します。

## 主要機能

3 つの機能で Claude Code の動作をカスタマイズできます。

- **Skills**: タスクに応じて自動発動する専門スキル（Progressive Disclosure）
- **Agents**: XP チームロールに基づく専門エージェント
- **Hooks**: 特定のタイミングでスクリプトを自動実行

---

## 機能一覧

### Skills（スキル）

`skills/` ディレクトリ内の `SKILL.md` ファイルとして保存されています。タスク内容に応じて自動的に発動し、必要な指示をコンテキストに読み込みます。

#### オーケストレーション

| スキル | 説明 |
| :--- | :--- |
| `orchestrating-analysis` | 分析フェーズ全体のワークフローをオーケストレーション。各 analyzing-* スキルの実行順序を案内。 |
| `orchestrating-development` | 開発フェーズ全体の TDD ワークフローをオーケストレーション。Codex 分業体制を案内。 |
| `orchestrating-operation` | 運用フェーズ全体のワークフローをオーケストレーション。各 operating-* スキルの実行順序を案内。 |
| `orchestrating-project` | 計画・進捗管理フェーズ全体のワークフローをオーケストレーション。リリース計画、GitHub Project 同期、進捗追跡の実行順序を案内。 |

#### 分析系

| スキル | 説明 |
| :--- | :--- |
| `analyzing-business` | ビジネスアーキテクチャ分析を支援。ビジネスモデルキャンバス、バリューストリーム、ケイパビリティマップ等の作成。 |
| `analyzing-inception-deck` | インセプションデッキ作成。プロジェクトの「なぜ」「何を」「どうやって」を 10 の問いで整理。 |
| `analyzing-requirements` | RDRA 2.0 に基づいた体系的な要件定義を作成。 |
| `analyzing-usecases` | ユースケース・ユーザーストーリー作成を支援。 |
| `analyzing-architecture` | アーキテクチャパターンの選択と設計ドキュメント作成。 |
| `analyzing-data-model` | ER 図作成、テーブル定義、リレーション設計。 |
| `analyzing-domain-model` | エンティティ、値オブジェクト、集約の設計。 |
| `analyzing-ui-design` | 画面遷移図と画面イメージを設計。 |
| `analyzing-tech-stack` | フレームワーク、ライブラリ、インフラの選定と評価。 |
| `analyzing-test-strategy` | テストピラミッド設計、テスト種別の定義、カバレッジ目標の設定。 |
| `analyzing-non-functional` | 性能、セキュリティ、可用性、保守性の要件策定。 |
| `analyzing-operation` | 運用フロー、監視設計、障害対応手順の策定。 |

#### 開発系

| スキル | 説明 |
| :--- | :--- |
| `developing-backend` | バックエンド開発の TDD ワークフロー。インサイドアウトアプローチ。 |
| `developing-frontend` | フロントエンド開発の TDD ワークフロー。アウトサイドインアプローチ。 |
| `developing-release` | リリースワークフロー。品質ゲート、バージョンバンプ、CHANGELOG 生成、git commit + tag。 |

#### レビュー系

| スキル | 説明 |
| :--- | :--- |
| `analyzing-review` | 分析成果物のマルチパースペクティブレビュー。XP エージェント 5 名（PM、アーキテクト、ID、テスター、ユーザー代表）を並列起動。 |
| `developing-review` | 開発成果物のマルチパースペクティブレビュー。XP エージェント 5 名（プログラマー、テスター、アーキテクト、TW、ユーザー代表）を並列起動。 |
| `operating-review` | 運用成果物のマルチパースペクティブレビュー。XP エージェント 3 名（アーキテクト、テスター、PM）を並列起動。 |

#### 計画・進捗系

| スキル | 説明 |
| :--- | :--- |
| `planning-releases` | アジャイルなリリース計画とイテレーション計画を作成・管理。 |
| `syncing-github-project` | リリース計画を GitHub Project・Issue・Milestone に同期。 |
| `tracking-progress` | プロジェクトの開発進捗を包括的に分析しレポート生成。 |

#### 運用系

| スキル | 説明 |
| :--- | :--- |
| `operating-setup` | 環境構築を段階的に実行。テンプレートから手順書を生成し環境を構築。 |
| `operating-provision` | IaC（Terraform）によるインフラプロビジョニングを実行。 |
| `operating-cicd` | CI/CD パイプラインを構築。CI と CD の設計・実装。 |
| `operating-deploy` | 各環境へのデプロイを実行。ローリング、Blue/Green、ロールバック。 |
| `operating-backup` | データベースのバックアップ・リストアを実行。 |
| `operating-docs` | 設計ドキュメントの一覧表示、進捗確認、インデックス更新、Markdown Lint。 |
| `operating-qt` | SonarQube によるコード品質管理。スキャン実行、Quality Gate 確認、イシュー分析。 |
| `operating-script` | 運用スクリプト（Gulp タスク）の作成・更新。 |
| `killing-processes` | 開発サーバーや Node.js プロセスを強制終了。ポート競合の解決。 |

#### ドキュメント・Git 系

| スキル | 説明 |
| :--- | :--- |
| `generating-slides` | インセプションデッキから PowerPoint スライドを生成。 |
| `git-commit` | 意味のある変更単位ごとにコミットを作成。Conventional Commits 準拠。 |
| `creating-adr` | Architecture Decision Record の作成を支援。 |
| `creating-release-report` | リリース完了報告書を作成。release_plan・iteration_report・git log・CHANGELOG からデータ収集。 |
| `creating-iteration-report` | イテレーション完了報告書を作成。iteration_plan・release_plan・テスト結果からデータ収集。 |

#### 共通

| スキル | 説明 |
| :--- | :--- |
| `ai-agent-guidelines` | AI Agent の実行ガイドライン。TDD サイクル、品質保証、完了報告のルール。 |

### Agents（エージェント）

`agents/` ディレクトリ内の Markdown ファイルで定義されます。XP（エクストリームプログラミング）のチームロールに基づく専門エージェントが利用できます。

| エージェント | 役割 |
| :--- | :--- |
| `xp-programmer` | TDD でのコード実装、タスク分解、見積もり、リファクタリング |
| `xp-tester` | テスト戦略設計、テスタビリティレビュー、テスト技法コーチング |
| `xp-architect` | アーキテクチャ設計、大規模リファクタリング、ADR 作成 |
| `xp-product-manager` | ストーリー作成、優先順位付け、リリース計画 |
| `xp-project-manager` | 進捗管理、ボトルネック特定、ステークホルダー調整 |
| `xp-interaction-designer` | UI/UX 設計、画面遷移設計、システムメタファー選定 |
| `xp-technical-writer` | ユーザー向けドキュメント、API ドキュメント作成 |
| `xp-user-representative` | ユーザー視点でのストーリーレビュー、機能評価 |
| `xp-executive` | 戦略的判断、リソース配分、チーム支援 |

### Hooks（自動化スクリプト）

`settings.json` で設定して、開発作業を自動化できます。

| 実行スクリプト | イベント | 説明 |
| :--- | :--- | :--- |
| `deny-check.sh` | `PreToolUse` | `rm -rf /` のような危険なコマンドの実行を未然に防ぐ。 |
| `check-ai-commit.sh` | `PreToolUse` | `git commit` でコミットメッセージに AI の署名が含まれている場合にエラーを出す。 |
| `preserve-file-permissions.sh` | `PreToolUse` / `PostToolUse` | ファイル編集前に元の権限を保存し、編集後に復元する。 |
| `ja-space-format.sh` | `PostToolUse` | ファイル保存時に、日本語と英数字の間のスペースを自動で整形する。 |
| `auto-comment.sh` | `PostToolUse` | 新規ファイル作成時や大幅な編集時に、docstring の追加を促す。 |
| `(osascript)` | `Notification` | Claude がユーザーの確認を待っている時に、macOS の通知センターでお知らせする。 |
| `check-continue.sh` | `Stop` | タスク完了時に、継続可能なタスクがないか確認する。 |
| `(osascript)` | `Stop` | 全タスク完了時に、macOS の通知センターで完了を知らせる。 |

**注意**: スクリプトファイルは `scripts/` ディレクトリに配置する必要があります。このテンプレートには `.gitkeep` のみが含まれているため、実際のスクリプトは `~/.claude/scripts/` から参照するか、プロジェクトに合わせて作成してください。

---

## ディレクトリ構造

```
.claude/
├── agent-memory/                          # エージェントメモリ
├── agents/                                # XP チームロール定義（.md）
├── assets/                                # 通知音などのアセット
├── scripts/                            # Hooks 用スクリプト
├── skills/                             # スキル定義（SKILL.md + 参照ファイル）
│   ├── ai-agent-guidelines/SKILL.md
│   ├── git-commit/SKILL.md
│   ├── creating-adr/SKILL.md
│   ├── generating-slides/SKILL.md
│   ├── analyzing-business/SKILL.md
│   ├── analyzing-inception-deck/SKILL.md
│   ├── analyzing-requirements/SKILL.md
│   ├── analyzing-usecases/SKILL.md
│   ├── analyzing-architecture/SKILL.md
│   ├── analyzing-data-model/SKILL.md
│   ├── analyzing-domain-model/SKILL.md
│   ├── analyzing-ui-design/SKILL.md
│   ├── analyzing-tech-stack/SKILL.md
│   ├── analyzing-test-strategy/SKILL.md
│   ├── analyzing-non-functional/SKILL.md
│   ├── analyzing-operation/SKILL.md
│   ├── analyzing-review/SKILL.md
│   ├── developing-backend/SKILL.md
│   ├── developing-frontend/SKILL.md
│   ├── developing-release/SKILL.md
│   ├── developing-review/SKILL.md
│   ├── operating-setup/SKILL.md
│   ├── operating-qt/SKILL.md
│   ├── operating-script/SKILL.md
│   ├── operating-review/SKILL.md
│   ├── operating-provision/SKILL.md
│   ├── operating-cicd/SKILL.md
│   ├── operating-deploy/SKILL.md
│   ├── operating-backup/SKILL.md
│   ├── operating-docs/SKILL.md
│   ├── killing-processes/SKILL.md
│   ├── tracking-progress/SKILL.md
│   ├── syncing-github-project/SKILL.md
│   ├── planning-releases/SKILL.md
│   ├── creating-release-report/SKILL.md
│   ├── creating-iteration-report/SKILL.md
│   ├── orchestrating-analysis/SKILL.md
│   ├── orchestrating-development/SKILL.md
│   ├── orchestrating-operation/SKILL.md
│   └── orchestrating-project/SKILL.md
├── README.md
├── settings.json                       # Claude Code 設定
└── settings.local.json                 # ローカル環境用設定
```

---

## カスタマイズ

- **スキルの追加**: `/skill-creator` プラグインを使用してスキルを作成・テスト・最適化します
- **エージェントの追加**: `agents/` に `.md` ファイルを追加するだけです
- **Hooks の編集**: `settings.json` を編集して、自動化処理を変更できます
- **スクリプトの追加**: `scripts/` にシェルスクリプトを追加し、`settings.json` で参照します

### skill-creator プラグインの導入

skill-creator はスキルの作成・テスト・評価・最適化を自動化するプラグインです。

#### インストール

1. Claude Code を起動
2. `/plugin` コマンドを実行
3. 検索ボックスで `skill-creator` を探して選択
4. インストールするスコープを選ぶ（ユーザー / プロジェクト / ローカル）
5. Claude Code を再起動

#### 使い方

```bash
# 新しいスキルを作成
/skill-creator 〇〇を支援するスキルを作成して

# 既存スキルの改善
/skill-creator skills/operating-deploy/SKILL.md を改善して

# スキルの説明文を最適化（トリガー精度の向上）
/skill-creator operating-deploy のdescriptionを最適化して
```

#### ワークフロー

skill-creator は以下のサイクルでスキルを作成・改善します。

1. **要件整理** — スキルの目的・トリガー条件・出力形式を整理
2. **ドラフト作成** — SKILL.md を作成
3. **テスト実行** — テストケースを作成し、with-skill / baseline の並行テストを実行
4. **評価** — アサーション（判定基準）によるグレーディングとベンチマーク比較
5. **改善** — フィードバックに基づきスキルを改善し、再テスト
6. **説明文最適化** — トリガー精度を向上させる description の自動最適化
