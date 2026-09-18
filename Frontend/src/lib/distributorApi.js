import { ApiError } from './api'

const JSON_HEADERS = { 'Content-Type': 'application/json' }

async function request(path, options) {
  let res
  try {
    res = await fetch(path, options)
  } catch {
    throw new ApiError('Cannot reach the Distributor service.', { status: 0 })
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

export const getDistributionStatus = () => request('/distributor-api/distribution/status')

export const listZoneCapacities = () => request('/distributor-api/zones')
export const createZoneCapacity = (body) =>
  request('/distributor-api/zones', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const updateZoneCapacity = (zoneId, body) =>
  request(`/distributor-api/zones/${zoneId}`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const deleteZoneCapacity = (zoneId) =>
  request(`/distributor-api/zones/${zoneId}`, { method: 'DELETE' })
