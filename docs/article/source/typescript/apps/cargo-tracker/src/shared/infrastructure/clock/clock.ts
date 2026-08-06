/**
 * 現在時刻プロバイダ（IT7 1.4）。
 * 未来日ガードの「現在時刻」を DI で差し替え可能にし、Date.now() 直呼びによるテスト不安定を避ける。
 */
export type Clock = () => Date;

/** Clock の DI トークン */
export const CLOCK = Symbol('CLOCK');

/** 既定の Clock（実時刻） */
export const systemClock: Clock = () => new Date();
