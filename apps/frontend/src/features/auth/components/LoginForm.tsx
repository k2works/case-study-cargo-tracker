import { useForm } from 'react-hook-form'
import { useLogin } from '../hooks/useAuth'
import type { LoginRequest } from '../types/auth'

export function LoginForm() {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginRequest>({
    defaultValues: { username: 'admin', password: 'password' },
  })
  const loginMutation = useLogin()

  const onSubmit = (data: LoginRequest) => {
    loginMutation.mutate(data)
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      <div>
        <label htmlFor="username" className="block text-sm font-medium text-gray-700">
          ユーザー名
        </label>
        <input
          id="username"
          type="text"
          autoComplete="username"
          {...register('username', { required: 'ユーザー名を入力してください' })}
          className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        {errors.username && (
          <p className="mt-1 text-sm text-red-600">{errors.username.message}</p>
        )}
      </div>

      <div>
        <label htmlFor="password" className="block text-sm font-medium text-gray-700">
          パスワード
        </label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          {...register('password', { required: 'パスワードを入力してください' })}
          className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 shadow-sm focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500"
        />
        {errors.password && (
          <p className="mt-1 text-sm text-red-600">{errors.password.message}</p>
        )}
      </div>

      {loginMutation.isError && (
        <div className="rounded-md bg-red-50 p-3">
          <p className="text-sm text-red-600">
            ユーザー名またはパスワードが正しくありません
          </p>
        </div>
      )}

      <button
        type="submit"
        disabled={loginMutation.isPending}
        className="w-full rounded-md bg-blue-600 px-4 py-2 text-white font-medium hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {loginMutation.isPending ? 'ログイン中...' : 'ログイン'}
      </button>
    </form>
  )
}
