/*
Copyright (C) 2023-2026 QuantumNous

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.

For commercial licensing, please contact support@quantumnous.com
*/
import { useEffect, useState } from 'react'
import { createFileRoute, useNavigate, useSearch } from '@tanstack/react-router'
import i18next from 'i18next'
import { toast } from 'sonner'
import { useAuthStore, type AuthUser } from '@/stores/auth-store'
import { api } from '@/lib/api'
import { saveUserId } from '@/features/auth/lib/storage'

type SsoSearch = {
  ticket?: string
  redirect?: string
}

function AicpSsoPage() {
  const navigate = useNavigate()
  const search = useSearch({ from: '/sso/aicp' }) as SsoSearch
  const [message, setMessage] = useState(i18next.t('Signing you in…'))

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      const ticket = search?.ticket?.trim()
      const redirect = sanitizeRedirect(search?.redirect) || '/dashboard'
      if (!ticket) {
        toast.error(i18next.t('Missing SSO ticket'))
        navigate({ to: '/sign-in', replace: true })
        return
      }
      try {
        const res = await api.post('/api/user/login/aicp-sso', { ticket })
        if (!res?.data?.success || !res.data.data) {
          throw new Error(res?.data?.message || 'SSO failed')
        }
        const user = res.data.data as AuthUser
        if (cancelled) return
        useAuthStore.getState().auth.setUser(user)
        if (user.id != null) saveUserId(user.id)
        setMessage(i18next.t('Signed in. Redirecting…'))
        navigate({ to: redirect as never, replace: true })
      } catch (err) {
        if (cancelled) return
        const text =
          err instanceof Error && err.message
            ? err.message
            : i18next.t('SSO login failed')
        toast.error(text)
        navigate({ to: '/sign-in', replace: true })
      }
    })()
    return () => {
      cancelled = true
    }
  }, [navigate, search?.redirect, search?.ticket])

  return (
    <div className='flex min-h-svh items-center justify-center px-6'>
      <p className='text-muted-foreground text-sm'>{message}</p>
    </div>
  )
}

function sanitizeRedirect(raw?: string): string | null {
  if (!raw) return null
  if (!raw.startsWith('/') || raw.startsWith('//')) return null
  return raw
}

export const Route = createFileRoute('/sso/aicp')({
  validateSearch: (search: Record<string, unknown>): SsoSearch => ({
    ticket: typeof search.ticket === 'string' ? search.ticket : undefined,
    redirect: typeof search.redirect === 'string' ? search.redirect : undefined,
  }),
  component: AicpSsoPage,
})
