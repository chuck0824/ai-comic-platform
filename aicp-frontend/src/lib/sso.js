/**
 * Cross-product SSO helpers for opening new-api (3001) from the AICP workbench.
 */
import { authApi } from '@/api/auth'

function joinBasePath(base, path) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const trimmed = String(base || '').replace(/\/$/, '')
  if (trimmed) return `${trimmed}${normalizedPath}`
  return normalizedPath
}

export function getNewApiPublicBase() {
  const configured = import.meta.env.VITE_NEW_API_PUBLIC_URL
  if (configured && String(configured).trim()) {
    return String(configured).trim().replace(/\/$/, '')
  }
  if (import.meta.env.DEV) return 'http://localhost:3001'
  return ''
}

export function getNewApiPublicUrl(path = '/dashboard') {
  return joinBasePath(getNewApiPublicBase(), path)
}

/**
 * Open the model console with a one-shot SSO ticket when the user is logged in.
 * Falls back to a plain navigation if ticket issuance fails.
 */
export async function openNewApiWithSso(path = '/dashboard') {
  const redirect = path.startsWith('/') ? path : `/${path}`
  try {
    const res = await authApi.createSsoTicket()
    const ticket = res?.data?.ticket
    if (!ticket) throw new Error('missing ticket')
    const target = getNewApiPublicUrl(
      `/sso/aicp?ticket=${encodeURIComponent(ticket)}&redirect=${encodeURIComponent(redirect)}`
    )
    window.location.assign(target)
  } catch (e) {
    console.warn('SSO ticket failed, falling back to plain new-api URL', e)
    window.location.assign(getNewApiPublicUrl(redirect))
  }
}
