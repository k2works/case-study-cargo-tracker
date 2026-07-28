import { describe, expect, it } from 'vitest';
import { Shipper } from './shipper.js';
import { ShipperType } from './value-objects.js';

describe('Shipper 集約', () => {
  it('個人荷主を登録できる（割引率ゼロ・契約番号なし）', () => {
    const shipper = Shipper.registerIndividual({
      name: '山田太郎',
      email: 'yamada@example.com',
    });
    expect(shipper.shipperType).toBe(ShipperType.INDIVIDUAL);
    expect(shipper.code.value).toMatch(/^SHP-/);
    expect(shipper.discountRate.value).toBe(0);
    expect(shipper.contractNumber).toBeUndefined();
  });

  it('法人荷主を契約番号・割引率つきで登録できる', () => {
    const shipper = Shipper.registerCorporate({
      name: '株式会社サンプル',
      email: 'corp@example.com',
      contractNumber: 'CT-001',
      discountRate: 0.2,
    });
    expect(shipper.shipperType).toBe(ShipperType.CORPORATE);
    expect(shipper.contractNumber?.value).toBe('CT-001');
    expect(shipper.discountRate.value).toBe(0.2);
  });

  it('法人で割引率が範囲外なら登録できない', () => {
    expect(() =>
      Shipper.registerCorporate({
        name: '株式会社サンプル',
        email: 'corp@example.com',
        contractNumber: 'CT-001',
        discountRate: 0.5,
      }),
    ).toThrow();
  });

  it('永続化値から再構築できる', () => {
    const shipper = Shipper.reconstruct({
      id: 10,
      code: 'SHP-abcd1234',
      shipperType: ShipperType.CORPORATE,
      name: '株式会社サンプル',
      email: 'corp@example.com',
      phone: null,
      address: null,
      contractNumber: 'CT-001',
      discountRate: 0.1,
    });
    expect(shipper.id).toBe(10);
    expect(shipper.contractNumber?.value).toBe('CT-001');
  });
});
