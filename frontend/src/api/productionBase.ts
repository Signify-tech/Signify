/** Fail the deployment build before producing a frontend with an unusable API origin. */
export function validateProductionApiBase(value: string | undefined): string {
  const base = (value ?? '').trim().replace(/\/+$/, '')
  let url: URL
  try {
    url = new URL(base)
  } catch {
    throw new Error('Set VITE_API_BASE_URL to the HTTPS Render origin before building.')
  }
  if (url.protocol !== 'https:' || url.hostname === 'localhost' || url.hostname.endsWith('.localhost')
    || url.hostname === '[::1]' || /^127\./.test(url.hostname)
    || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('VITE_API_BASE_URL must be a remote HTTPS origin without credentials, /api, query, or fragment.')
  }
  return base
}
