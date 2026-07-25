package domain

// Shipper は荷主集約のルート。個人・法人の 2 種別を持つ。
// 法人の場合は contract に契約情報（契約番号・割引率）を保持する。
type Shipper struct {
	id          ShipperId
	code        ShipperCode
	name        ShipperName
	email       Email
	phone       *Phone
	address     *Address
	shipperType ShipperType
	contract    *CorporateContract
}

// RegisterIndividualShipper は個人荷主を新規登録する。
func RegisterIndividualShipper(
	id ShipperId,
	code ShipperCode,
	name ShipperName,
	email Email,
) (*Shipper, error) {
	return &Shipper{
		id:          id,
		code:        code,
		name:        name,
		email:       email,
		shipperType: ShipperTypeIndividual,
	}, nil
}

// RegisterCorporateShipper は法人荷主を契約番号・割引率付きで新規登録する。
func RegisterCorporateShipper(
	id ShipperId,
	code ShipperCode,
	name ShipperName,
	email Email,
	contractNumber ContractNumber,
	discountRate DiscountRate,
) (*Shipper, error) {
	contract := NewCorporateContract(contractNumber, discountRate)
	return &Shipper{
		id:          id,
		code:        code,
		name:        name,
		email:       email,
		shipperType: ShipperTypeCorporate,
		contract:    &contract,
	}, nil
}

// ID は荷主識別子を返す。
func (s *Shipper) ID() ShipperId { return s.id }

// Code は荷主コードを返す。
func (s *Shipper) Code() ShipperCode { return s.code }

// Name は荷主名を返す。
func (s *Shipper) Name() ShipperName { return s.name }

// Email はメールアドレスを返す。
func (s *Shipper) Email() Email { return s.email }

// Phone は電話番号を返す（未設定なら nil）。
func (s *Shipper) Phone() *Phone { return s.phone }

// Address は住所を返す（未設定なら nil）。
func (s *Shipper) Address() *Address { return s.address }

// ShipperType は荷主種別を返す。
func (s *Shipper) ShipperType() ShipperType { return s.shipperType }

// IsCorporate は法人荷主かどうかを返す。
func (s *Shipper) IsCorporate() bool { return s.shipperType == ShipperTypeCorporate }

// Contract は法人契約情報を返す（個人荷主なら nil）。
func (s *Shipper) Contract() *CorporateContract { return s.contract }

// SetPhone は電話番号を設定する（オプション項目）。
func (s *Shipper) SetPhone(p Phone) { s.phone = &p }

// SetAddress は住所を設定する（オプション項目）。
func (s *Shipper) SetAddress(a Address) { s.address = &a }
