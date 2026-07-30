import { describe, expect, it } from 'vitest';
import { CustomsDeclaration } from './customs-declaration.js';
import { HandlingValidationError } from './handling-validation-error.js';

function pending(): CustomsDeclaration {
  return CustomsDeclaration.register({
    declarationNumber: 'DECL-TEST01',
    handlingActivityId: 1,
    declaredAt: new Date('2026-09-10T10:00:00Z'),
  });
}

describe('CustomsDeclaration（集約ルート・通関状態遷移）', () => {
  it('register は PENDING・clearedAt=null で生成する', () => {
    const declaration = pending();
    expect(declaration.status).toBe('PENDING');
    expect(declaration.clearedAt).toBeNull();
    expect(declaration.declarationNumber).toBe('DECL-TEST01');
  });

  it('申告番号が空なら登録できない', () => {
    expect(() =>
      CustomsDeclaration.register({ declarationNumber: '  ', handlingActivityId: 1, declaredAt: new Date() }),
    ).toThrow(HandlingValidationError);
  });

  it('申告日時が不正なら登録できない', () => {
    expect(() =>
      CustomsDeclaration.register({
        declarationNumber: 'DECL-X',
        handlingActivityId: 1,
        declaredAt: new Date('invalid'),
      }),
    ).toThrow(HandlingValidationError);
  });

  it('PENDING → CLEARED で clearedAt が設定される', () => {
    const declaration = pending();
    const clearedAt = new Date('2026-09-12T00:00:00Z');
    declaration.clear(clearedAt);
    expect(declaration.status).toBe('CLEARED');
    expect(declaration.clearedAt).toEqual(clearedAt);
  });

  it('PENDING → HELD で clearedAt は設定されない', () => {
    const declaration = pending();
    declaration.hold();
    expect(declaration.status).toBe('HELD');
    expect(declaration.clearedAt).toBeNull();
  });

  it('PENDING → REJECTED で clearedAt は設定されない', () => {
    const declaration = pending();
    declaration.reject();
    expect(declaration.status).toBe('REJECTED');
    expect(declaration.clearedAt).toBeNull();
  });

  it('HELD → CLEARED は許可され、clearedAt が設定される', () => {
    const declaration = pending();
    declaration.hold();
    const clearedAt = new Date('2026-09-13T00:00:00Z');
    declaration.clear(clearedAt);
    expect(declaration.status).toBe('CLEARED');
    expect(declaration.clearedAt).toEqual(clearedAt);
  });

  it('HELD → REJECTED は許可される', () => {
    const declaration = pending();
    declaration.hold();
    declaration.reject();
    expect(declaration.status).toBe('REJECTED');
  });

  it('CLEARED は終端（再遷移は不可）', () => {
    const declaration = pending();
    declaration.clear();
    expect(() => declaration.hold()).toThrow(HandlingValidationError);
    expect(() => declaration.reject()).toThrow(HandlingValidationError);
    expect(() => declaration.clear()).toThrow(HandlingValidationError);
  });

  it('REJECTED は終端（再遷移は不可）', () => {
    const declaration = pending();
    declaration.reject();
    expect(() => declaration.clear()).toThrow(HandlingValidationError);
    expect(() => declaration.hold()).toThrow(HandlingValidationError);
  });

  it('CLEARED 済みの clearedAt は再 clear でも上書きされない', () => {
    const declaration = CustomsDeclaration.reconstruct({
      declarationNumber: 'DECL-R',
      handlingActivityId: 1,
      status: 'CLEARED',
      declaredAt: new Date('2026-09-10T10:00:00Z'),
      clearedAt: new Date('2026-09-12T00:00:00Z'),
      remarks: null,
    });
    expect(() => declaration.clear(new Date('2026-09-20T00:00:00Z'))).toThrow(HandlingValidationError);
    expect(declaration.clearedAt).toEqual(new Date('2026-09-12T00:00:00Z'));
  });

  it('reconstruct は永続化状態から復元する', () => {
    const declaration = CustomsDeclaration.reconstruct({
      declarationNumber: 'DECL-R',
      handlingActivityId: 7,
      status: 'HELD',
      declaredAt: new Date('2026-09-10T10:00:00Z'),
      clearedAt: null,
      remarks: '検査中',
    });
    expect(declaration.status).toBe('HELD');
    expect(declaration.handlingActivityId).toBe(7);
    expect(declaration.remarks).toBe('検査中');
  });
});
