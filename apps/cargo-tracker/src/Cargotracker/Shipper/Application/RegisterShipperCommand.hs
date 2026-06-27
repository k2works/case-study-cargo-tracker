{- | 荷主登録コマンド (IT1 US02/US03 2.2)

入力 (Text/Text/Text + ShipperKindInput) を受け取り、
1. 各値オブジェクトをスマートコンストラクタで構築・検証
2. ContactEmail で重複検出 (既存ありなら ConcurrentModification を返す)
3. Shipper 集約を生成
4. Repository.save で永続化

ShipperKindInput により US02 個人 / US03 法人を同じコマンドで扱う。
本実装にはトランザクション境界 (T-01) が伴うが IT1 段階では Repository
側に委ねる (PostgresShipperRepository が `withTransaction` でラップする想定)。
-}
module Cargotracker.Shipper.Application.RegisterShipperCommand
  ( RegisterShipperInput (..),
    ShipperKindInput (..),
    execute,
  ) where

import Data.Text (Text)

import Cargotracker.Shared.Domain.DomainError (DomainError (..))
import Cargotracker.Shipper.Application.Ports
  ( ShipperRepository (..),
  )
import Cargotracker.Shipper.Domain.Model.Shipper
  ( ContractRank,
    Shipper,
    mkCorporateNumber,
    mkCorporateShipper,
    mkIndividualShipper,
  )
import Cargotracker.Shipper.Domain.Model.Value.Address (mkAddress)
import Cargotracker.Shipper.Domain.Model.Value.ContactEmail (mkContactEmail, unContactEmail)
import Cargotracker.Shipper.Domain.Model.Value.ShipperId (mkShipperId)
import Cargotracker.Shipper.Domain.Model.Value.ShipperName (mkShipperName)

data ShipperKindInput
  = InputIndividual
  | InputCorporate !Text !ContractRank
  deriving stock (Eq, Show)

data RegisterShipperInput = RegisterShipperInput
  { inputId :: !Text
  , inputName :: !Text
  , inputEmail :: !Text
  , inputAddress :: !Text
  , inputKind :: !ShipperKindInput
  }
  deriving stock (Eq, Show)

execute ::
  Monad m =>
  ShipperRepository m ->
  RegisterShipperInput ->
  m (Either DomainError Shipper)
execute repo input = case buildShipper input of
  Left e -> pure (Left e)
  Right shipper -> case mkContactEmail (inputEmail input) of
    Left e -> pure (Left e)
    Right email -> do
      mExisting <- findByContactEmail repo email
      case mExisting of
        Just _ ->
          pure
            ( Left
                ( ConcurrentModification
                    ("duplicate shipper email: " <> unContactEmail email)
                )
            )
        Nothing -> do
          save repo shipper
          pure (Right shipper)
  where
    buildShipper :: RegisterShipperInput -> Either DomainError Shipper
    buildShipper i = do
      sid <- mkShipperId (inputId i)
      name <- mkShipperName (inputName i)
      email <- mkContactEmail (inputEmail i)
      addr <- mkAddress (inputAddress i)
      case inputKind i of
        InputIndividual -> Right (mkIndividualShipper sid name email addr)
        InputCorporate cnText rank -> do
          cn <- mkCorporateNumber cnText
          Right (mkCorporateShipper sid name email addr cn rank)
