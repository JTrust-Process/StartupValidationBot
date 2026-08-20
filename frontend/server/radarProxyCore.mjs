// Strict allowlist. `authorization` and `x-radar-run-token` are deliberately absent so a browser can
// never present the server-to-server worker credential, and `x-radar-client-ip` is absent so a client
// cannot forge the value we derive below.
const FORWARDED_REQUEST_HEADERS = ['accept', 'content-type', 'cookie', 'origin', 'user-agent'];
const CLIENT_IP_HEADER = 'x-radar-client-ip';
const FORWARDED_RESPONSE_HEADERS = ['content-type', 'content-disposition'];

export function buildRadarTarget(requestUrl, backendOrigin) {
  if (!backendOrigin) throw new Error('RADAR_BACKEND_ORIGIN is not configured');
  const backend = new URL(backendOrigin);
  const localHttp = backend.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(backend.hostname);
  if (backend.protocol !== 'https:' && !localHttp) {
    throw new Error('RADAR_BACKEND_ORIGIN must use HTTPS');
  }
  if (backend.pathname !== '/' || backend.search || backend.hash || backend.username || backend.password) {
    throw new Error('RADAR_BACKEND_ORIGIN must be an origin without a path or credentials');
  }
  const incoming = new URL(requestUrl, 'https://radar-proxy.invalid');
  if (incoming.pathname !== '/api/radar' && !incoming.pathname.startsWith('/api/radar/')) {
    throw new Error('Only Radar API paths may be proxied');
  }
  return new URL(`${incoming.pathname}${incoming.search}`, backend).toString();
}

export function resolveRadarProxyRequestUrl(requestUrl) {
  const incoming = new URL(requestUrl, 'https://radar-proxy.invalid');
  const rewrittenPath = incoming.searchParams.get('__radar_path');
  if (rewrittenPath === null) return requestUrl;

  incoming.searchParams.delete('__radar_path');
  const resolved = new URL(`/api/radar/${rewrittenPath}`, incoming.origin);
  if (!resolved.pathname.startsWith('/api/radar/')) {
    throw new Error('Invalid Radar proxy path');
  }
  return `${resolved.pathname}${incoming.search}`;
}

function headerValue(headers, name) {
  const value = typeof headers.get === 'function' ? headers.get(name) : headers[name];
  if (!value) return '';
  return Array.isArray(value) ? value.join(', ') : String(value);
}

/**
 * Resolves the calling client's address from what the hosting platform supplies, so the backend can
 * throttle logins per client instead of per proxy. Only well-formed addresses are forwarded; anything
 * else is dropped and the backend falls back to its own socket address.
 */
export function resolveClientIp(headers = {}) {
  const realIp = headerValue(headers, 'x-real-ip').trim();
  if (isPlausibleAddress(realIp)) return realIp.toLowerCase();

  const forwardedFor = headerValue(headers, 'x-forwarded-for');
  if (!forwardedFor) return '';
  // Right-most entry is appended by the closest proxy and is hardest for a client to control.
  const parts = forwardedFor.split(',');
  const nearest = parts[parts.length - 1].trim();
  return isPlausibleAddress(nearest) ? nearest.toLowerCase() : '';
}

function isPlausibleAddress(value) {
  return Boolean(value) && value.length <= 64 && /^[0-9a-fA-F.:[\]%]+$/.test(value);
}

export function buildForwardedHeaders(headers = {}) {
  const forwarded = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = headerValue(headers, name);
    if (value) forwarded.set(name, value);
  }
  forwarded.set('accept-encoding', 'identity');

  const clientIp = resolveClientIp(headers);
  if (clientIp) forwarded.set(CLIENT_IP_HEADER, clientIp);

  return forwarded;
}

async function requestBody(request) {
  if (['GET', 'HEAD'].includes(String(request.method).toUpperCase())) return undefined;
  if (request.body !== undefined && request.body !== null) {
    if (Buffer.isBuffer(request.body) || typeof request.body === 'string') return request.body;
    return JSON.stringify(request.body);
  }
  const chunks = [];
  for await (const chunk of request) chunks.push(Buffer.from(chunk));
  return chunks.length ? Buffer.concat(chunks) : undefined;
}

export async function proxyRadarRequest(request, response, options = {}) {
  try {
    const target = buildRadarTarget(
      resolveRadarProxyRequestUrl(request.url),
      options.backendOrigin ?? process.env.RADAR_BACKEND_ORIGIN
    );
    const upstream = await (options.fetchImpl ?? fetch)(target, {
      method: request.method,
      headers: buildForwardedHeaders(request.headers),
      body: await requestBody(request),
      redirect: 'manual'
    });
    response.statusCode = upstream.status;
    response.setHeader('Cache-Control', 'no-store');
    for (const name of FORWARDED_RESPONSE_HEADERS) {
      const value = upstream.headers.get(name);
      if (value) response.setHeader(name, value);
    }
    const cookies = typeof upstream.headers.getSetCookie === 'function'
      ? upstream.headers.getSetCookie()
      : [upstream.headers.get('set-cookie')].filter(Boolean);
    if (cookies.length) response.setHeader('Set-Cookie', cookies);
    response.end(Buffer.from(await upstream.arrayBuffer()));
  } catch (error) {
    // Internal configuration detail stays in the platform log; the browser gets a generic message so
    // deployment state is not disclosed to anonymous callers.
    console.error('radar_proxy_error', error instanceof Error ? error.message : 'unknown proxy failure');
    response.statusCode = 502;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('Cache-Control', 'no-store');
    response.end(JSON.stringify({ ok: false, error: 'Radar backend is unavailable.' }));
  }
}
