import { useMutation } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import { apiClient } from '../../../lib/api-client'
import { useAuthStore } from '../../../stores/authStore'
import type { LoginRequest, TokenResponse } from '../types/auth'

export function useLogin() {
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)

  return useMutation({
    mutationFn: (data: LoginRequest) =>
      apiClient.post<TokenResponse>('/api/v1/auth/login', data),
    onSuccess: (response) => {
      setAuth(response.token, {
        username: response.username,
        roles: response.roles,
      })
      navigate('/dashboard')
    },
  })
}

export function useLogout() {
  const navigate = useNavigate()
  const logout = useAuthStore((s) => s.logout)

  return () => {
    logout()
    navigate('/login')
  }
}
