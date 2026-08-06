{- | 引取確認 + 荷役登録コマンド (US16, IT5)

業務フロー:
1. 荷役作業員が確認コード + 場所を入力
2. Tracking BC の ConfirmationCodeRepository.findByBookingId で登録済コードを取得
3. Domain 層 verify で 3 段階検証 (試行超過 / 使用済 / 不一致)
4. 成功時: markUsed + updateAfterVerify (used_at = now) + Handling BC で Claim イベント登録
5. 失敗時: attempt_count +1 のみ更新して DomainError 返却

Cross-BC 通信:
- Handling BC が Tracking BC の Application 層 (ConfirmationCodeRepository) を経由
- Rule 4 遵守 (Application 層 port のみ import、Domain 型は境界内)
- ConfirmationCode 型は同 BC (Tracking) 内なので Handling は Text 経由で扱う
- 但し verify / markUsed の純粋関数呼出のため Domain import が必要 → Tracking の
  Domain を「Cross-BC 共有カーネル的」に import することを ADR-0012 (IT6 検討) で合意
  IT5 段階では arch-check の H-01 ルールで警告のみ

T-01 準拠: Tx 境界は本 Command 内で withDbTransaction (IT6 で追加)。
T-03 準拠: verify / markUsed / mkHandlingActivity は純粋関数。
-}
module Cargotracker.Handling.Application.VerifyClaimAndRegisterCommand
  ( VerifyClaimInput (..),
    execute,
  ) where

import Data.Text (Text)
import Data.Time (UTCTime)

import Cargotracker.Handling.Application.Ports
  ( HandlingActivityRepository (..),
  )
import Cargotracker.Handling.Domain.Model.HandlingActivity (mkHandlingActivity)
import Cargotracker.Handling.Domain.Model.HandlingType (HandlingType (..))
import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shared.Security.ConstantTime (Verifier)
import Cargotracker.Tracking.Application.ConfirmationCodePorts
  ( ConfirmationCodeRepository,
    verifyAndConsumeWith,
  )
import Cargotracker.Tracking.Application.Ports
  ( TrackingRepository,
    markClaimedByBookingId,
  )

data VerifyClaimInput = VerifyClaimInput
  { inputBookingId :: !Text
  , inputConfirmationCode :: !Text
  , inputLocationUnlocode :: !Text
  , inputOperatorName :: !Text
  , inputCompletionTime :: !UTCTime
  , inputNow :: !UTCTime
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  Verifier ->
  ConfirmationCodeRepository m ->
  HandlingActivityRepository m ->
  TrackingRepository m ->
  VerifyClaimInput ->
  m (Either DomainError ())
execute checker codeRepo handlingRepo trackingRepo input = do
  verifyResult <-
    verifyAndConsumeWith
      checker
      codeRepo
      (inputBookingId input)
      (inputConfirmationCode input)
      (inputNow input)
  case verifyResult of
    Left err -> pure (Left err)
    Right () ->
      -- 検証成功 → Claim イベント登録 → Tracking 状態を「引取済」に反映
      -- (H-01 SSoT: 具体的な TransportStatus 値は Tracking Application の
      --  markClaimedByBookingId 内でのみ参照する。本 Handling BC からは
      --  Text ベース Cross-BC helper 呼出のみを行う)
      case mkHandlingActivity
        (inputNow input)
        (inputBookingId input)
        Claim
        (inputCompletionTime input)
        (inputLocationUnlocode input)
        Nothing -- Claim は voyage_number 不要
        (inputOperatorName input) of
        Left err -> pure (Left err)
        Right activity -> do
          saveResult <- saveHandlingActivity handlingRepo activity
          case saveResult of
            Left err -> pure (Left err)
            Right () ->
              -- T5-04 (ADR-0012 決定 4): Tracking.transport_status を「引取済」状態に遷移。
              -- Tx 境界 (HandlingPageApi.handlerClaimPost の runInTx) 内で実行される
              -- ため、失敗時は verifyAndConsume の状態更新もロールバックされる。
              -- 具体的な TransportStatus 値は markClaimedByBookingId が内部で決定する。
              markClaimedByBookingId trackingRepo (inputBookingId input)
