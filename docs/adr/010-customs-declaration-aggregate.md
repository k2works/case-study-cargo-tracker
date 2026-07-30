# ADR-010: 通関申告を独立集約に昇格し、読みモデルの DB 直依存を意図的例外とする

`CustomsDeclaration` を HandlingActivity 集約内エンティティから独立集約ルート + Repository ポートへ昇格し、状態遷移規則をドメインに封じる。CQRS 読み取りサービスの DB 直依存は意図的例外として明文化する。

日付: 2026-07-30

## ステータス

承認済み

## コンテキスト

domain-model は当初 `CustomsDeclaration` を「CLEARED まで CLAIM 不可」の不変条件を根拠に HandlingActivity 集約内エンティティと定義していた。しかし IT5 の実装では、通関申告の登録・状態更新（`CustomsDeclarationService`）が AppDatabase 直依存の生 insert/update となり、(a) ドメイン集約が存在せず状態遷移規則（PENDING → CLEARED / HELD / REJECTED）が検証されない、(b) CLEARED 解除時に `cleared_at` が消失し監査・課金起点の時刻が失われる、(c) 同一 BC 内でヘキサゴナル規律が非対称になる、という問題がレビューで指摘された（IT5 architect H1 / programmer M5・Try T3）。

実態として、通関申告のライフサイクル（申告 → 審査 → 通関/留置/却下）は個々の荷役作業と独立に進行し、荷役作業との強整合を要するのは「CLEARED まで CLAIM 不可」の判定だけである。この判定は登録時に Read Model（`HandlingActivityHistory.isCustomsCleared`）で行われており、集約内包による強整合を必要としていない。

## 決定

1. **`CustomsDeclaration` を独立集約ルートに昇格する**。状態遷移規則（PENDING からのみ CLEARED / HELD / REJECTED へ。HELD → CLEARED / REJECTED は許可。CLEARED / REJECTED は終端）と「`cleared_at` は CLEARED 到達時に確定し以後上書き・消去しない」を集約メソッド（`clear()` / `hold()` / `reject()`）に封じる。
2. **`CustomsDeclarationRepository` ポートを導入し**、コマンド側の DB 直依存を排除する。DI は既存規約（interface + Symbol トークン + useFactory）に従う。
3. **「CLEARED まで CLAIM 不可」の担保は従来どおり** `RegisterHandlingActivityService` が Read Model（`isCustomsCleared`）で判定する。集約境界の変更によって不変条件の担保箇所は変わらない。
4. **HELD 遷移は `customs.held` イベントで Tracking へ伝播し**、CUSTOMS_HOLD 例外を冪等リスナーが自動登録する（domain-model Tracking ビジネスルール 4・ADR-005/009 準拠）。
5. **CQRS 読み取りサービス（QueryService / Read Model）の AppDatabase 直依存は意図的例外**とする。読みモデルはドメインを経由しない画面最適化クエリであり（architecture_backend の CQRS 方針）、ポート抽象を挟まない。コマンド側は必ずポート経由とする（この非対称が規律）。

### 代替案

- **HandlingActivity 集約内エンティティのまま実装を是正**: 通関申告は荷役作業と独立に更新され、荷役集約経由の更新は不自然なトラバーサルとロック範囲の拡大を招く。強整合を要する不変条件も存在しない。却下
- **db 直依存のサービスを維持し遷移検証だけ足す**: 遷移規則が手続きコードに散り、ヘキサゴナル規律の非対称（同一 BC でポート経由と直依存が混在）が残る。却下
- **読みモデルにもポート抽象を導入**: 抽象の利得がなく、CQRS の「読みは最適化クエリを直接書く」意図に反する。却下（例外として明文化する方を選ぶ）

## 影響

### ポジティブ

- 通関の状態遷移が集約でテスト可能になり、`cleared_at` 消失のような監査リスクが構造的に防がれる
- Handling BC 内のコマンド側規律が対称になり、dependency-cruiser の検証対象に乗る

### ネガティブ

- domain-model の集約境界定義の改訂が必要（本 ADR とともに同期）
- CLEARED → HELD の再留置は現行規則では表現できない（業務上必要になった場合は遷移規則の改訂を別途判断する）

## コンプライアンス

- 集約の遷移規則・`cleared_at` 保持を単体テストで網羅する
- Port 追加時はフルの `npm run verify`（dependency-cruiser 含む）で構造検証する

## 備考

- 著者: k2works
- 関連 ADR: ADR-005（イベント連携）、ADR-008（共有 DB 直読の範囲）、ADR-009（コミット後副作用）
- 関連: IT5 実装レビュー（architect H1 / programmer M5）、IT6 計画 注 10
