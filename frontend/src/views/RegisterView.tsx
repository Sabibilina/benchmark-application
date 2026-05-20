import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useNavigate, Link } from 'react-router-dom'
import { authApi } from '../api/auth'
import { useAuthStore } from '../stores/authStore'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { useState } from 'react'
import type { ApiError } from '../types'

const schema = z.object({
  username: z.string().min(2, 'Username must be at least 2 characters'),
  email: z.string().email('Enter a valid email'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  confirmPassword: z.string(),
}).refine((d) => d.password === d.confirmPassword, {
  message: 'Passwords do not match',
  path: ['confirmPassword'],
})

type FormData = z.infer<typeof schema>

export default function RegisterView() {
  const navigate = useNavigate()
  const loginWithToken = useAuthStore((s) => s.loginWithToken)
  const [apiError, setApiError] = useState<string | null>(null)

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<FormData>({
    resolver: zodResolver(schema),
  })

  async function onSubmit(data: FormData) {
    setApiError(null)
    try {
      const res = await authApi.register(data.username, data.email, data.password)
      loginWithToken(res.data.token, { username: data.username, email: data.email })
      navigate('/', { replace: true })
    } catch (err) {
      setApiError((err as ApiError).message ?? 'Registration failed')
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-zinc-950">
      <div className="w-full max-w-sm bg-zinc-900 rounded-2xl border border-zinc-800 p-8 shadow-2xl">
        <div className="mb-8 text-center">
          <span className="text-brand-400 font-bold text-2xl">MusicStream</span>
          <p className="text-zinc-500 text-sm mt-1">Create your account</p>
        </div>

        {apiError && (
          <div className="mb-4 p-3 rounded-lg bg-red-900/40 border border-red-700 text-sm text-red-300">
            {apiError}
          </div>
        )}

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
          <Input
            id="username"
            label="Username"
            placeholder="yourusername"
            error={errors.username?.message}
            {...register('username')}
          />
          <Input
            id="email"
            label="Email"
            type="email"
            placeholder="you@example.com"
            error={errors.email?.message}
            {...register('email')}
          />
          <Input
            id="password"
            label="Password"
            type="password"
            placeholder="••••••••"
            error={errors.password?.message}
            {...register('password')}
          />
          <Input
            id="confirmPassword"
            label="Confirm Password"
            type="password"
            placeholder="••••••••"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword')}
          />
          <Button type="submit" disabled={isSubmitting} className="mt-2 w-full">
            {isSubmitting ? 'Creating account…' : 'Create account'}
          </Button>
        </form>

        <p className="text-center text-sm text-zinc-500 mt-6">
          Already have an account?{' '}
          <Link to="/login" className="text-brand-400 hover:text-brand-300 transition-colors">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
