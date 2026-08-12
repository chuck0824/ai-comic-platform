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
import { api } from '@/lib/api'
import { getAicpSsoConsumeUrl, getAicpWorkbenchUrl } from '@/lib/product-urls'

/**
 * Open the AICP workbench. When the current new-api user is linked via
 * aicp_user_id, issue a reverse SSO ticket so 8080 logs them in automatically.
 */
export async function openAicpWorkbenchWithSso(path = '/home'): Promise<void> {
  const redirect = path.startsWith('/') ? path : `/${path}`
  try {
    const res = await api.post('/api/user/sso/aicp-ticket')
    const ticket = res?.data?.data?.ticket as string | undefined
    if (res?.data?.success && ticket) {
      window.location.assign(getAicpSsoConsumeUrl(ticket, redirect))
      return
    }
  } catch {
    // Fall through — user may be a native 3001 account without AICP link.
  }
  window.location.assign(getAicpWorkbenchUrl(redirect))
}
