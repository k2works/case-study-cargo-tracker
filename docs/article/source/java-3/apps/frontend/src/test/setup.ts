import '@testing-library/jest-dom/vitest'
import { configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { installApiAuth } from '../features/auth/install-api-auth'
import { server } from './msw/server'

// 既定の 1 秒は、カバレッジ計測を挟むと足りない。「待つ時間」を測っているテストは
// 1 本も無いので、待ちを伸ばしてもテストが緩むことはない。伸ばさないと、
// 実装は正しいのに走査のときだけ落ちる（IT2 の sonar-local:check で実際に落ちた）
configure({ asyncUtilTimeout: 5000 })
installApiAuth()

// ハンドラをテスト間で持ち越すと、あるテストのモックが別のテストを緑にしてしまう
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
