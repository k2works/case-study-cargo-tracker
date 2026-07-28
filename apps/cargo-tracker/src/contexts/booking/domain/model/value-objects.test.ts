import { describe, expect, it } from 'vitest';
import {
  Consignee,
  RouteSpecification,
  HazardousDeclaration,
  TemperatureRequirement,
  Weight,
} from './value-objects.js';

describe('Consignee（荷受人）', () => {
  it('氏名・住所・連絡先メールを保持する', () => {
    const c = Consignee.of({ name: '受取太郎', address: '大阪市', contactEmail: 'uke@example.com' });
    expect(c.name).toBe('受取太郎');
    expect(c.contactEmail).toBe('uke@example.com');
  });
  it('氏名が空なら拒否', () => {
    expect(() => Consignee.of({ name: '', address: '', contactEmail: 'a@example.com' })).toThrow();
  });
  it('不正なメールを拒否', () => {
    expect(() => Consignee.of({ name: '受取', address: '', contactEmail: 'bad' })).toThrow();
  });
});

describe('RouteSpecification（ルート仕様）', () => {
  it('出発地・目的地・到着期限を保持する', () => {
    const spec = RouteSpecification.of({
      origin: 'JPTYO',
      destination: 'USLAX',
      arrivalDeadline: new Date('2026-09-30'),
    });
    expect(spec.origin.unlocode).toBe('JPTYO');
    expect(spec.destination.unlocode).toBe('USLAX');
  });
  it('出発地と目的地が同一なら拒否', () => {
    expect(() =>
      RouteSpecification.of({ origin: 'JPTYO', destination: 'JPTYO', arrivalDeadline: new Date() }),
    ).toThrow();
  });
});

describe('Weight（重量）', () => {
  it.each([0, -1])('0 以下 %d を拒否', (w) => {
    expect(() => Weight.of(w)).toThrow();
  });
  it('正の値を許可', () => {
    expect(Weight.of(1000).value).toBe(1000);
  });
});

describe('HazardousDeclaration（危険物申告）', () => {
  it('クラス・UN 番号・正式輸送品名を必須で保持する', () => {
    const h = HazardousDeclaration.of({ hazardousClass: '3', unNumber: 'UN1203', properShippingName: 'Gasoline' });
    expect(h.hazardousClass).toBe('3');
  });
  it.each([
    { hazardousClass: '', unNumber: 'UN1203', properShippingName: 'X' },
    { hazardousClass: '3', unNumber: '', properShippingName: 'X' },
    { hazardousClass: '3', unNumber: 'UN1203', properShippingName: '' },
  ])('必須項目欠落は拒否', (p) => {
    expect(() => HazardousDeclaration.of(p)).toThrow();
  });
});

describe('TemperatureRequirement（温度管理条件）', () => {
  it('最低・最高温度と単位を保持する', () => {
    const t = TemperatureRequirement.of({ minTemperature: -20, maxTemperature: -10, unit: 'CELSIUS' });
    expect(t.minTemperature).toBe(-20);
  });
  it('最低 > 最高なら拒否', () => {
    expect(() =>
      TemperatureRequirement.of({ minTemperature: 10, maxTemperature: -10, unit: 'CELSIUS' }),
    ).toThrow();
  });
  it('最低 == 最高（単一温度点）は許可する（境界）', () => {
    const t = TemperatureRequirement.of({ minTemperature: 5, maxTemperature: 5, unit: 'CELSIUS' });
    expect(t.minTemperature).toBe(5);
  });
  it('不正な単位はデフォルト CELSIUS に正規化する', () => {
    const t = TemperatureRequirement.of({ minTemperature: -5, maxTemperature: 0, unit: 'KELVIN' });
    expect(t.unit).toBe('CELSIUS');
  });
});
