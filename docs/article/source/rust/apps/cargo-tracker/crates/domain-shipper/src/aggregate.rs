//! Shipper 集約。

use crate::value_objects::{
    Address, ContractNumber, DiscountRate, Email, Phone, ShipperCode, ShipperName,
};
use serde::{Deserialize, Serialize};
use shared_kernel::ShipperId;

/// 法人荷主の契約情報。`ShipperKind::Corporate` が内包する。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct CorporateProfile {
    contract_number: ContractNumber,
    discount_rate: DiscountRate,
}

impl CorporateProfile {
    /// 法人契約情報を生成する。
    #[must_use]
    pub fn new(contract_number: ContractNumber, discount_rate: DiscountRate) -> Self {
        Self {
            contract_number,
            discount_rate,
        }
    }

    /// 契約番号を返す。
    #[must_use]
    pub fn contract_number(&self) -> &ContractNumber {
        &self.contract_number
    }

    /// 割引率を返す。
    #[must_use]
    pub fn discount_rate(&self) -> DiscountRate {
        self.discount_rate
    }
}

/// 荷主種別。法人は契約情報を型として内包し、「法人なら契約情報必須」を型で強制する。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum ShipperKind {
    /// 個人荷主。
    Individual,
    /// 法人荷主（契約情報を内包）。
    Corporate(CorporateProfile),
}

/// 荷主集約ルート。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Shipper {
    id: ShipperId,
    code: ShipperCode,
    name: ShipperName,
    email: Email,
    phone: Option<Phone>,
    address: Option<Address>,
    kind: ShipperKind,
}

impl Shipper {
    /// 新規荷主を登録する。`ShipperId` と `ShipperCode` を自動採番する。
    #[must_use]
    pub fn register(
        name: ShipperName,
        email: Email,
        phone: Option<Phone>,
        address: Option<Address>,
        kind: ShipperKind,
    ) -> Self {
        Self {
            id: ShipperId::generate(),
            code: ShipperCode::generate(),
            name,
            email,
            phone,
            address,
            kind,
        }
    }

    /// 永続化レコードから荷主集約を復元する。
    #[must_use]
    #[allow(clippy::too_many_arguments)]
    pub fn reconstitute(
        id: ShipperId,
        code: ShipperCode,
        name: ShipperName,
        email: Email,
        phone: Option<Phone>,
        address: Option<Address>,
        kind: ShipperKind,
    ) -> Self {
        Self {
            id,
            code,
            name,
            email,
            phone,
            address,
            kind,
        }
    }

    /// 荷主 ID を返す。
    #[must_use]
    pub fn id(&self) -> ShipperId {
        self.id
    }

    /// 荷主コードを返す。
    #[must_use]
    pub fn code(&self) -> &ShipperCode {
        &self.code
    }

    /// 荷主名を返す。
    #[must_use]
    pub fn name(&self) -> &ShipperName {
        &self.name
    }

    /// メールアドレスを返す。
    #[must_use]
    pub fn email(&self) -> &Email {
        &self.email
    }

    /// 電話番号を返す。
    #[must_use]
    pub fn phone(&self) -> Option<&Phone> {
        self.phone.as_ref()
    }

    /// 住所を返す。
    #[must_use]
    pub fn address(&self) -> Option<&Address> {
        self.address.as_ref()
    }

    /// 荷主種別を返す。
    #[must_use]
    pub fn kind(&self) -> &ShipperKind {
        &self.kind
    }

    /// 法人荷主かどうかを返す。
    #[must_use]
    pub fn is_corporate(&self) -> bool {
        matches!(self.kind, ShipperKind::Corporate(_))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::value_objects::ContractNumber;
    use rust_decimal::Decimal;

    fn name() -> ShipperName {
        ShipperName::new("山田商事").unwrap()
    }

    fn email() -> Email {
        Email::new("yamada@example.com").unwrap()
    }

    #[test]
    fn 個人荷主を登録すると_id_とコードが採番される() {
        let shipper = Shipper::register(name(), email(), None, None, ShipperKind::Individual);
        assert!(shipper.code().as_str().starts_with("SHP-"));
        assert!(!shipper.is_corporate());
    }

    #[test]
    fn 法人荷主は契約情報を保持する() {
        let profile = CorporateProfile::new(
            ContractNumber::new("C-001").unwrap(),
            DiscountRate::new(Decimal::new(1500, 4)).unwrap(),
        );
        let shipper =
            Shipper::register(name(), email(), None, None, ShipperKind::Corporate(profile));
        assert!(shipper.is_corporate());
        match shipper.kind() {
            ShipperKind::Corporate(p) => {
                assert_eq!(p.discount_rate().value(), Decimal::new(1500, 4));
            }
            ShipperKind::Individual => panic!("法人のはず"),
        }
    }

    #[test]
    fn 登録ごとに異なる_id_が採番される() {
        let a = Shipper::register(name(), email(), None, None, ShipperKind::Individual);
        let b = Shipper::register(name(), email(), None, None, ShipperKind::Individual);
        assert_ne!(a.id(), b.id());
    }
}
