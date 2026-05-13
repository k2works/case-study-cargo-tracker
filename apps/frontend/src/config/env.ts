export const env = {
  authApiBaseUrl: import.meta.env.VITE_AUTH_API_BASE_URL ?? 'http://localhost:8081',
  bookingApiBaseUrl: import.meta.env.VITE_BOOKING_API_BASE_URL ?? 'http://localhost:8082',
  isDev: import.meta.env.DEV,
  isProd: import.meta.env.PROD,
}
