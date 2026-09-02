import { ApiError } from '../../lib/api-client'

/** サーバが理由を添えて拒否した（400）ときだけ、その理由を返す。 */
export function invalidInputMessage(error: unknown): string | null {
  if (!(error instanceof ApiError) || error.status !== 400) {
    return null
  }
  const body = error.body as { message?: string } | undefined
  return body?.message ?? '入力内容を確認してください。'
}
