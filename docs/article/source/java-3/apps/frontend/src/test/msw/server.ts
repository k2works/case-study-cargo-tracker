import { setupServer } from 'msw/node'

/**
 * テスト用の API モックサーバー。
 *
 * アウトサイドインで進めるため、UI のニーズから導出した API 契約をここで先に固定する。
 * バックエンド実装後は同じ契約が守られていることを契約テストで裏取りする。
 */
export const server = setupServer()
