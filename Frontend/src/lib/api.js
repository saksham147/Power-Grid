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

const post = (path, body) =>
  request(path, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

export const getStatus = () => request('/api/simulation/status')
export const setFrequencyDeviation = (frequencyDeviation) =>
  request('/api/simulation/frequency-deviation', {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify({ frequencyDeviation }),
  })

export const listPlants = (activeOnly) =>
  request(`/api/plants${activeOnly ? '?activeOnly=true' : ''}`)
export const createPlant = (body) => post('/api/plants', body)
export const upgradePlant = (id, body) =>
  request(`/api/plants/${id}`, {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify(body),
  })
export const deletePlant = (id) => request(`/api/plants/${id}`, { method: 'DELETE' })
export const setPlantActive = (id, active) =>
  request(`/api/plants/${id}/active`, {
    method: 'PATCH',
    headers: JSON_HEADERS,
    body: JSON.stringify({ active }),
  })

export const getPlantHistory = (id, { limit } = {}) =>
  request(`/api/plants/${id}/history${limit ? `?limit=${limit}` : ''}`)


/** No response at all (0), or the dev proxy reporting a refused upstream (502). */
export const isUnreachable = (error) => error?.status === 0 || error?.status === 502
