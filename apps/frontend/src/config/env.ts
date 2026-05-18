// 本番（Heroku）では nginx が /api/ を gateway にプロキシするため空文字（相対 URL）を使用。
// ローカル開発では VITE_API_BASE_URL を設定するか、デフォルトの gateway ポートを使用する。
export const env = {
  authApiBaseUrl: import.meta.env.VITE_AUTH_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? '',
  bookingApiBaseUrl: import.meta.env.VITE_BOOKING_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? '',
  routingApiBaseUrl: import.meta.env.VITE_ROUTING_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? '',
  handlingApiBaseUrl: import.meta.env.VITE_HANDLING_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? '',
  trackingApiBaseUrl: import.meta.env.VITE_TRACKING_API_BASE_URL ?? import.meta.env.VITE_API_BASE_URL ?? '',
  isDev: import.meta.env.DEV,
  isProd: import.meta.env.PROD,
}
