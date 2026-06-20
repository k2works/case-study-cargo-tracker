# 0004 US26（認証・認可）を UC 横断ストーリーとして扱う

US26（ログイン・ログアウト）が特定の UC に属さない横断的ストーリーであることを宣言し、トレーサビリティを明文化する

日付: 2026-06-20

## ステータス

2026-06-20 提案されました

## コンテキスト

`system_usecase.md` に定義された UC は UC01〜UC19 で、いずれも業務シナリオに紐付く。US26（システムにログイン・ログアウトする）は当初 `UC20` として導入を検討したが、以下の理由で UC として位置付けるのが不適切：

- UC01〜UC19 のすべてが認証済みアクターを前提とする（UC20 を 1 つだけ追加しても他 UC の前提を変更する必要がある）
- UC は「ビジネス目的を達成する操作の集合」だが、認証は前提条件であって目的そのものではない
- Java 版 take-2・take-5 でも認証は横断要件（非機能・セキュリティ）として扱われた

`validating-iteration-plan` の検証で「UC20 が未定義」と検出されたため、対応方針を ADR で確定する必要が生じた。

## 決定

**US26 を「UC 横断ストーリー」として扱う**。具体的には：

- `user_story.md` の US26 の「対応 UC」欄を `横断（認証・認可、全 UC の前提）` と記載する
- 他のストーリー（US01〜US25）の受入基準には認証を明示しない（暗黙の前提）
- 認証・認可の実装は `app/cargotracker/shared/interfaces/web/AuthenticatedAction` および `SessionTimeoutFilter` に集約し、各コンテキストの Controller は `AuthenticatedAction` で包んで利用する
- E2E テストでは、各シナリオの先頭でログインステップを共通化する（`E2ESupport.withAuthenticatedSession`）

将来 UC20（認証・認可）を `system_usecase.md` に追加する場合は本 ADR を廃止する。当面は本 ADR を US26 の唯一の根拠とする。

## 影響

**ポジティブ**:

- UC ↔ US のトレーサビリティ表で `横断` ラベルが明示され、認証が非機能要件であることが読み取れる
- 各 UC に「認証済みである」という前提を繰り返し記載する必要がなくなる
- 認証の実装変更が `Shared/Interfaces/Web` に閉じ、他コンテキストへの影響が局所化される

**ネガティブ・トレードオフ**:

- レポート系ツール（GitHub Project の UC 別集計など）で US26 が「横断」グループに分類される
- 新規参加者が「なぜ US26 だけ UC が空欄か」と疑問を持つ可能性 → 本 ADR が回答となる

## コンプライアンス

- `user_story.md` の US26 「対応 UC」欄が `横断（認証・認可、全 UC の前提）` であること
- `AuthenticatedAction` の適用範囲を ArchUnit で検査：`*.interfaces.web` 配下のすべての Controller アクションが `AuthenticatedAction` で包まれているか、または公開 URL リストに登録されていること
- 公開 URL リスト: `/login`、`/public/tracking/:trackingNumber`

## 備考

- 著者: AI Agent
- 関連: US26、ADR 0002（bcrypt とセッション管理）
- 参考: validating-iteration-plan 検証結果（2026-06-20）、Java 版 take-5 の認証実装
