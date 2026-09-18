import { ApiError } from './api'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

// Storage lives on Producer, same as /api/plants -- the default '/api' proxy already routes
// there, so this needs no new vite.config.js entry the way Customer/Grid/Distributor/Billing did.
async function request(path, options) {
  let res
  try {
    res = await fetch(path, options)
  } catch {
    throw new ApiError('Cannot reach the Producer service.', { status: 0 })
  }

  if (!res.ok) {
    const body = await res.text()
    let parsed
    try {
      parsed = JSON.parse(body)
    } catch {
      parsed = null
    }
    throw new ApiError(parsed?.message ?? `Request failed (${res.status})`, {
      status: res.status,
      details: parsed?.details ?? [],
    })
  }

  return res.status === 204 ? null : res.json()
}

export const listStorage = () => request('/api/storage')
export const createStorage = (body) =>
  request('/api/storage', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const upgradeStorage = (id, body) =>
  request(`/api/storage/${id}`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const deleteStorage = (id) => request(`/api/storage/${id}`, { method: 'DELETE' })
