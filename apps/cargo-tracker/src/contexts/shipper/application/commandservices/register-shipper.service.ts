import { Shipper } from '../../domain/model/shipper.js';
import { ShipperType } from '../../domain/model/value-objects.js';
import {
  EmailAlreadyRegisteredError,
  type ShipperRepository,
} from '../../domain/repository/shipper-repository.js';

export interface RegisterShipperCommand {
  shipperType: ShipperType;
  name: string;
  email: string;
  phone?: string;
  address?: string;
  contractNumber?: string;
  discountRate?: number;
}

/** 荷主登録結果（US02 受入基準: 発行された荷主 ID を利用者に提示する） */
export interface RegisterShipperResult {
  id: number;
  shipperCode: string;
}

/**
 * 荷主登録ユースケース（US02/US03）。
 * Email 重複チェックと ShipperCode 自動生成（集約側）を行う。
 */
export class RegisterShipperService {
  constructor(private readonly shippers: ShipperRepository) {}

  async register(command: RegisterShipperCommand): Promise<RegisterShipperResult> {
    // Email の正規化規則は Email 値オブジェクトに一元化する
    const shipper = this.buildShipper(command);
    const existing = await this.shippers.findByEmail(shipper.email.value);
    if (existing !== null) {
      throw new EmailAlreadyRegisteredError(command.email, existing.code.value);
    }

    const id = await this.shippers.save(shipper);
    return { id, shipperCode: shipper.code.value };
  }

  private buildShipper(command: RegisterShipperCommand): Shipper {
    if (command.shipperType === ShipperType.CORPORATE) {
      // 契約番号必須の検証は ContractNumber 値オブジェクト（ドメイン）に一元化する
      return Shipper.registerCorporate({
        name: command.name,
        email: command.email,
        phone: command.phone,
        address: command.address,
        contractNumber: command.contractNumber ?? '',
        discountRate: command.discountRate ?? 0,
      });
    }
    return Shipper.registerIndividual({
      name: command.name,
      email: command.email,
      phone: command.phone,
      address: command.address,
    });
  }
}
