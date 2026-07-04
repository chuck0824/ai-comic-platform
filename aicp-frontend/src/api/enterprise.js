import request from '@/api/request'

const BASE = '/api/v1/enterprise'

export const enterpriseApi = {
  /** Get the current enterprise context (menus, allowedActions). */
  getContext() {
    return request.get(`${BASE}/context`)
  },

  // ─── Departments ────────────────────────────────────────────────────────
  listDepartments() {
    return request.get(`${BASE}/departments`)
  },
  createDepartment(body) {
    return request.post(`${BASE}/departments`, body)
  },
  updateDepartment(id, body) {
    return request.patch(`${BASE}/departments/${encodeURIComponent(id)}`, body)
  },
  deleteDepartment(id) {
    return request.delete(`${BASE}/departments/${encodeURIComponent(id)}`)
  },

  // ─── Members ────────────────────────────────────────────────────────────
  listMembers(params = {}) {
    return request.get(`${BASE}/members`, { params })
  },
  updateMember(id, body) {
    return request.patch(`${BASE}/members/${id}`, body)
  },

  // ─── Invitations ────────────────────────────────────────────────────────
  createInvitation(body) {
    return request.post(`${BASE}/invitations`, body)
  },

  // ─── Roles ──────────────────────────────────────────────────────────────
  listRoles() {
    return request.get(`${BASE}/roles`)
  },
  createRole(body) {
    return request.post(`${BASE}/roles`, body)
  },
  updateRole(id, body) {
    return request.patch(`${BASE}/roles/${encodeURIComponent(id)}`, body)
  },

  // ─── Billing ────────────────────────────────────────────────────────────
  getBillingSummary() {
    return request.get(`${BASE}/billing-summary`)
  }
}
