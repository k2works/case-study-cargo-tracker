import {
  Address,
  ContractNumber,
  DiscountRate,
  Email,
  Phone,
  ShipperCode,
  ShipperName,
  ShipperType,
} from './value-objects.js';

interface RegisterIndividualParams {
  name: string;
  email: string;
  phone?: string;
  address?: string;
}

interface RegisterCorporateParams extends RegisterIndividualParams {
  contractNumber: string;
  discountRate: number;
}

interface ReconstructParams {
  id: number;
  code: string;
  shipperType: ShipperType;
  name: string;
  email: string;
  phone: string | null;
  address: string | null;
  contractNumber: string | null;
  discountRate: number;
}

/**
 * 荷主集約ルート。個人・法人の 2 種別を扱う。
 * 法人は契約番号と割引率を必須で持つ（domain-model.md ビジネスルール）。
 */
export class Shipper {
  private constructor(
    readonly id: number | undefined,
    readonly code: ShipperCode,
    readonly shipperType: ShipperType,
    readonly name: ShipperName,
    readonly email: Email,
    readonly phone: Phone | undefined,
    readonly address: Address | undefined,
    readonly contractNumber: ContractNumber | undefined,
    readonly discountRate: DiscountRate,
  ) {}

  static registerIndividual(params: RegisterIndividualParams): Shipper {
    return new Shipper(
      undefined,
      ShipperCode.generate(),
      ShipperType.INDIVIDUAL,
      ShipperName.of(params.name),
      Email.of(params.email),
      params.phone ? Phone.of(params.phone) : undefined,
      params.address ? Address.of(params.address) : undefined,
      undefined,
      DiscountRate.zero(),
    );
  }

  static registerCorporate(params: RegisterCorporateParams): Shipper {
    return new Shipper(
      undefined,
      ShipperCode.generate(),
      ShipperType.CORPORATE,
      ShipperName.of(params.name),
      Email.of(params.email),
      params.phone ? Phone.of(params.phone) : undefined,
      params.address ? Address.of(params.address) : undefined,
      ContractNumber.of(params.contractNumber),
      DiscountRate.of(params.discountRate),
    );
  }

  static reconstruct(params: ReconstructParams): Shipper {
    return new Shipper(
      params.id,
      ShipperCode.of(params.code),
      params.shipperType,
      ShipperName.of(params.name),
      Email.of(params.email),
      params.phone ? Phone.of(params.phone) : undefined,
      params.address ? Address.of(params.address) : undefined,
      params.contractNumber ? ContractNumber.of(params.contractNumber) : undefined,
      DiscountRate.of(params.discountRate),
    );
  }
}
