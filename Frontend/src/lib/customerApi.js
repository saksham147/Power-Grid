import { ApiError } from './api'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

async function request(path, options) {
  let res
  try {
    res = await fetch(path, options)
  } catch {
    throw new ApiError('Cannot reach the Customer service.', { status: 0 })
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

export const getDemand = () => request('/customer-api/demand')

export const listZones = () => request('/customer-api/zones')
export const createZone = (body) =>
  request('/customer-api/zones', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const upgradeZone = (zoneId, body) =>
  request(`/customer-api/zones/${zoneId}`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const deleteZone = (zoneId) => request(`/customer-api/zones/${zoneId}`, { method: 'DELETE' })

export const listUnits = () => request('/customer-api/units')
export const createUnit = (body) =>
  request('/customer-api/units', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const upgradeUnit = (unitId, body) =>
  request(`/customer-api/units/${unitId}`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const deleteUnit = (unitId) => request(`/customer-api/units/${unitId}`, { method: 'DELETE' })
