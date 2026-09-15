import { ApiError } from './api'

async function request(path) {
  let res
  try {
    res = await fetch(path)
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

  return res.json()
}

export const getDistributionStatus = () => request('/distributor-api/distribution/status')
