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

/**
 * 荷主登録ユースケース（US02/US03）。
 * Email 重複チェックと ShipperCode 自動生成（集約側）を行う。
 */
export class RegisterShipperService {
  constructor(private readonly shippers: ShipperRepository) {}

  async register(command: RegisterShipperCommand): Promise<number> {
    const existing = await this.shippers.findByEmail(command.email.trim().toLowerCase());
    if (existing !== null) {
      throw new EmailAlreadyRegisteredError(command.email);
    }

    const shipper = this.buildShipper(command);
    return this.shippers.save(shipper);
  }

  private buildShipper(command: RegisterShipperCommand): Shipper {
    if (command.shipperType === ShipperType.CORPORATE) {
      if (!command.contractNumber) {
        throw new Error('法人荷主には契約番号が必要です');
      }
      return Shipper.registerCorporate({
        name: command.name,
        email: command.email,
        phone: command.phone,
        address: command.address,
        contractNumber: command.contractNumber,
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
