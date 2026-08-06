{- | 追跡番号 VO (US14, IT5)

追跡活動を一意に識別する業務キー。8 文字の英数大文字で表現する
(domain-model.md §4 Tracking Context の TrackingNumber 定義に準拠)。

生成規約:
- 予約確定 (BookingConfirmed) 時に IssueTrackingNumberCommand が採番
- 形式: 上 2 文字 = "TR"、続く 6 文字は英数大文字 (推測困難化)
- 業務公開 (追跡ページ URL に含まれる) ため、シーケンシャル採番は避ける

T-03 準拠: スマートコンストラクタは純粋関数、IO の randomness は
Application 層で生成してから mkTrackingNumber に渡す。
-}
module Cargotracker.Tracking.Domain.Model.Value.TrackingNumber
  ( TrackingNumber (..),
    mkTrackingNumber,
    unsafeTrackingNumber,
  ) where

import Data.Char (isAsciiUpper, isDigit)
import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Shared.Domain.DomainError (DomainError (..))

newtype TrackingNumber = TrackingNumber {unTrackingNumber :: Text}
  deriving stock (Eq, Ord, Show)

-- | スマートコンストラクタ。8 文字英数大文字のみを受理する。
mkTrackingNumber :: Text -> Either DomainError TrackingNumber
mkTrackingNumber t
  | T.length t == 8 && T.all validChar t = Right (TrackingNumber t)
  | otherwise = Left (InvalidTrackingNumberFormat t)
  where
    validChar c = isAsciiUpper c || isDigit c

{- | DB 復元時などスマートコンストラクタを通さない再構築用。
検証済みデータのみに使うこと。
-}
unsafeTrackingNumber :: Text -> TrackingNumber
unsafeTrackingNumber = TrackingNumber
