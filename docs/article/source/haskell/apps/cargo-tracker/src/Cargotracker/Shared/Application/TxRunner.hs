{-# LANGUAGE RankNTypes #-}

{- | トランザクション境界抽象 (T5-03, ADR-0012, IT6)

Application 層コマンド (VerifyClaimAndRegister、料金算出、通知発火) が複数の
Repository 呼び出しに跨る整合性を要求する場合に、その全体を単一のデータベース
トランザクションで囲むためのポート。

Application 層に配置している理由 (arch-check T-01 準拠):

- withTransaction / withDbTransaction の呼出は Application 層のみに集約する
- Repository (Infrastructure) は自ら Tx を開始しない (T-02 準拠)
- Interfaces (Servant Handler) は Application 経由でしか Tx 境界を管理しない
  (Rule 6 準拠、Interfaces → Infrastructure 直接依存禁止)

設計方針 (ADR-0012):

- 本番 (Warp + Postgres) は `newPostgresTxRunner conn` を注入
- 単体テストは `noTxRunner` (id ラッパ) を注入
- 外部副作用 (メール送信、ログ配信) は `runInTx` の外で実行
  (Tx ロールバック時に副作用が発火しないよう分離)

例外セマンティクス:

- ブロック内で例外が投げられると `withTransaction` が ROLLBACK
- 通常成功パスは COMMIT
- `Either DomainError ()` の `Left` はビジネスエラーであり例外ではない。
  Left でも Tx はコミットされる (仕様として `updateAfterVerify` の attempt_count
  更新は失敗時にもコミットしたいため。Left で ROLLBACK が必要ならブロック内で
  `throwIO` する明示手法を採用する)
-}
module Cargotracker.Shared.Application.TxRunner
  ( TxRunner (..),
    newPostgresTxRunner,
    noTxRunner,
  ) where

import Database.PostgreSQL.Simple (Connection, withTransaction)

{- | ブロックをトランザクション境界で囲む runner。

`forall a` により任意の戻り値型に適用可能。RankNTypes 必須。
-}
newtype TxRunner = TxRunner
  { runInTx :: forall a. IO a -> IO a
  }

-- | Postgres の `withTransaction conn` を包む本番用 runner。
newPostgresTxRunner :: Connection -> TxRunner
newPostgresTxRunner conn = TxRunner (withTransaction conn)

{- | テスト用: トランザクション境界を作らずブロックを素通しする runner。

Repository をインメモリスタブに差し替えた単体テストで使用する。
-}
noTxRunner :: TxRunner
noTxRunner = TxRunner id
