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

function joinBasePath(base: string | undefined, path: string): string {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const trimmed = base?.trim().replace(/\/$/, '')
  if (trimmed) return `${trimmed}${normalizedPath}`
  return normalizedPath
}

function readPublicEnv(): ImportMetaEnv {
  return (import.meta.env ?? {}) as ImportMetaEnv
}

/**
 * AICP workbench (Vue app, typically :8080).
 * Set `VITE_AICP_WORKBENCH_URL` in deploy (e.g. https://comic.example.com).
 * In local DEV only, falls back to http://localhost:8080 when unset.
 */
export function getAicpWorkbenchUrl(path = '/home'): string {
  const env = readPublicEnv()
  const configured = env.VITE_AICP_WORKBENCH_URL
  if (configured?.trim()) return joinBasePath(configured, path)
  if (env.DEV) return joinBasePath('http://localhost:8080', path)
  return joinBasePath(undefined, path)
}

/**
 * new-api public site paths (dashboard, pricing, …).
 * Leave `VITE_NEW_API_PUBLIC_URL` empty for same-origin relative links.
 */
export function getNewApiPublicUrl(path: string): string {
  const configured = readPublicEnv().VITE_NEW_API_PUBLIC_URL
  if (configured?.trim()) return joinBasePath(configured, path)
  return joinBasePath(undefined, path)
}

export function getComicWorkbenchUrl(): string {
  return getAicpWorkbenchUrl('/home')
}

/** SSO consume URL on the AICP workbench. */
export function getAicpSsoConsumeUrl(ticket: string, redirect = '/home'): string {
  const q = new URLSearchParams({
    ticket,
    redirect: redirect.startsWith('/') ? redirect : `/${redirect}`,
  })
  return getAicpWorkbenchUrl(`/sso?${q.toString()}`)
}

/** SSO consume URL on new-api for tickets issued by 8080. */
export function getNewApiSsoConsumeUrl(ticket: string, redirect = '/dashboard'): string {
  const q = new URLSearchParams({
    ticket,
    redirect: redirect.startsWith('/') ? redirect : `/${redirect}`,
  })
  return getNewApiPublicUrl(`/sso/aicp?${q.toString()}`)
}
