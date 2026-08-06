{- | Routing Application 層のポート (IT1 US24)

VoyageRepository: 航海集約の永続化と航海番号での検索 (重複検出)。
-}
module Cargotracker.Routing.Application.Ports
  ( VoyageRepository (..),
  ) where

import Cargotracker.Routing.Domain.Model.Value.VoyageNumber (VoyageNumber)
import Cargotracker.Routing.Domain.Model.Voyage (Voyage)
import Cargotracker.Shared.Domain.DomainError (DomainError)

data VoyageRepository m = VoyageRepository
  { findByVoyageNumber :: VoyageNumber -> m (Maybe Voyage)
  , saveVoyage :: Voyage -> m ()
  , updateVoyage :: Voyage -> m (Either DomainError ())
  {- ^ US25 (IT2): 既存 Voyage の区間集合を全置換し version を更新する。
  楽観ロック衝突や対象不在を DomainError で表現できるよう Either を返す。
  -}
  , findAllVoyages :: m [Voyage]
  -- ^ IT2 一覧画面用 (暫定ページング無し、最大 100 件) — IT4 で findVoyagesPaged へ移行 (ADR-0006)
  }

{-# DEPRECATED
  findAllVoyages
  "ADR-0006 PG-03: IT4 で findVoyagesPaged :: PageReq -> m (Page Voyage) へ移行する。\
  \新規 callsite の追加は避け、既存 callsite は段階的に移行すること。"
  #-}
