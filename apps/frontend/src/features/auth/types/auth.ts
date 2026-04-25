export interface LoginRequest {
  username: string
  password: string
}

export interface TokenResponse {
  token: string
  username: string
  roles: string[]
}

export interface UserInfo {
  username: string
  roles: string[]
}
