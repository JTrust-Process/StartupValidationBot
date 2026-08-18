const FORWARDED_REQUEST_HEADERS = ['accept', 'content-type', 'cookie', 'origin', 'user-agent'];
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

export function buildForwardedHeaders(headers = {}) {
  const forwarded = new Headers();
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = typeof headers.get === 'function' ? headers.get(name) : headers[name];
    if (value) forwarded.set(name, Array.isArray(value) ? value.join(', ') : String(value));
  }
  forwarded.set('accept-encoding', 'identity');
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
    const target = buildRadarTarget(request.url, options.backendOrigin ?? process.env.RADAR_BACKEND_ORIGIN);
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
    response.statusCode = 502;
    response.setHeader('Content-Type', 'application/json');
    response.setHeader('Cache-Control', 'no-store');
    response.end(JSON.stringify({ ok: false, error: error instanceof Error ? error.message : 'Radar proxy failed' }));
  }
}
