{- | 通関申告集約 (US27, IT3)

予約 (BookingId) に紐付く通関情報。HS コード・通関業者名・申告ステータスの
3 フィールドを持ち、`mkCustomsDeclaration` スマートコンストラクタで一括検証する。

data-model.md では既存 `customs_declaration` テーブルを拡張する方針 (新規
`customs_info` テーブルは作らない)。永続化マッピングは Infrastructure 層で行う。
-}
module Cargotracker.Booking.Domain.Model.CustomsDeclaration
  ( CustomsDeclaration (..),
    mkCustomsDeclaration,
  ) where

import Data.Text (Text)
import qualified Data.Text as T

import Cargotracker.Booking.Domain.Model.State.DeclarationStatus
  ( DeclarationStatus,
  )
import Cargotracker.Booking.Domain.Model.Value.BookingId (BookingId)
import Cargotracker.Booking.Domain.Model.Value.HsCode (HsCode, mkHsCode)
import Cargotracker.Shared.Domain.DomainError (DomainError (..))

data CustomsDeclaration = CustomsDeclaration
  { cdBookingId :: !BookingId
  , cdHsCode :: !HsCode
  , cdBrokerName :: !Text
  , cdStatus :: !DeclarationStatus
  }
  deriving stock (Eq, Show)

{- | 入力 (HS コード文字列・通関業者名・申告ステータス) から集約を構築する。

* HS コード: `mkHsCode` で 6-10 桁の数字を検証
* 通関業者名: 1 文字以上、最大 100 文字
* 申告ステータス: 呼び出し側で `declarationStatusFromText` を経由する
-}
mkCustomsDeclaration ::
  BookingId ->
  Text ->
  Text ->
  DeclarationStatus ->
  Either DomainError CustomsDeclaration
mkCustomsDeclaration bid hsTxt brokerName status = do
  hsCode <- mkHsCode hsTxt
  brokerValidated <- validateBrokerName brokerName
  Right
    CustomsDeclaration
      { cdBookingId = bid
      , cdHsCode = hsCode
      , cdBrokerName = brokerValidated
      , cdStatus = status
      }

validateBrokerName :: Text -> Either DomainError Text
validateBrokerName t
  | T.null trimmed = Left (InvalidBrokerName "通関業者名は必須です")
  | T.length trimmed > 100 = Left (InvalidBrokerName "通関業者名は 100 文字以内で入力してください")
  | otherwise = Right trimmed
  where
    trimmed = T.strip t
