import { setupServer } from 'msw/node';

/** API のモック。**本物より甘くしない**（F4）：本物が断る入力は、モックも断る。 */
export const server = setupServer();
