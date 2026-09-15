const JSON_HEADERS = { 'Content-Type': 'application/json' }

/** Carries the backend's own wording so the UI never invents an error message. */
export class ApiError extends Error {
  constructor(message, { status = 0, details = [] } = {}) {
    super(message)
    this.status = status
    this.details = details
  }
}

async function request(path, options) {
  let res
  try {
    res = await fetch(path, options)
  } catch {
    throw new ApiError('Cannot reach the Producer service.', { status: 0 })
  }

  if (!res.ok) {
    // The service replies with an ApiError body; the dev proxy replies with plain
    // text when the backend is down. Parse defensively rather than assuming JSON.
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

export const getStatus = () => request('/api/simulation/status')

export const listPlants = () => request('/api/plants')
export const createPlant = (body) =>
  request('/api/plants', { method: 'POST', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const upgradePlant = (id, body) =>
  request(`/api/plants/${id}`, { method: 'PUT', headers: JSON_HEADERS, body: JSON.stringify(body) })
export const deletePlant = (id) => request(`/api/plants/${id}`, { method: 'DELETE' })
export const getPlantHistory = (id, { limit } = {}) =>
  request(`/api/plants/${id}/history${limit ? `?limit=${limit}` : ''}`)
