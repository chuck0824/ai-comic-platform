/**
 * JWT helpers that preserve snowflake user IDs.
 * JSON.parse coerces large numbers and loses precision above 2^53.
 */

/** Decode JWT payload JSON text (UTF-8), without parsing numbers. */
export function decodeJwtPayloadText(token) {
  if (!token || typeof token !== 'string') return null
  try {
    const base64Url = token.split('.')[1]
    if (!base64Url) return null
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
    return decodeURIComponent(
      atob(padded)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
  } catch {
    return null
  }
}

/**
 * Extract uid claim as a decimal string (never as Number).
 * Supports both `"uid":"123"` and `"uid":123`.
 */
export function extractJwtUid(token) {
  const text = decodeJwtPayloadText(token)
  if (!text) return null
  const m = text.match(/"uid"\s*:\s*"?(\d+)"?/)
  if (m) return m[1]
  const userId = text.match(/"userId"\s*:\s*"?(\d+)"?/)
  if (userId) return userId[1]
  const user_id = text.match(/"user_id"\s*:\s*"?(\d+)"?/)
  if (user_id) return user_id[1]
  return null
}

/** Parse payload object for non-numeric claims (nickname, uuid, etc.). */
export function decodeJwtPayload(token) {
  const text = decodeJwtPayloadText(token)
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}
