import assert from 'node:assert/strict';
import test from 'node:test';

import {
  buildForwardedHeaders,
  buildRadarTarget,
  proxyRadarRequest,
  resolveRadarProxyRequestUrl
} from './radarProxyCore.mjs';

test('builds only HTTPS Radar backend targets', () => {
  assert.equal(
    buildRadarTarget('/api/radar/admin/status?fresh=true', 'https://radar-backend.example'),
    'https://radar-backend.example/api/radar/admin/status?fresh=true'
  );
  assert.equal(
    buildRadarTarget('/api/deal-workspaces/12', 'https://radar-backend.example'),
    'https://radar-backend.example/api/deal-workspaces/12'
  );
  assert.equal(
    buildRadarTarget('/api/deals/12', 'https://radar-backend.example'),
    'https://radar-backend.example/api/deals/12'
  );
  assert.throws(() => buildRadarTarget('/api/private', 'https://radar-backend.example'), /explicitly allowed/);
  assert.throws(() => buildRadarTarget('/api/radar/status', 'http://radar-backend.example'), /must use HTTPS/);
});

test('restores nested Radar paths from Vercel rewrites', () => {
  assert.equal(
    resolveRadarProxyRequestUrl('/api/radar/proxy?__radar_path=auth%2Fsession&fresh=true'),
    '/api/radar/auth/session?fresh=true'
  );
  assert.equal(resolveRadarProxyRequestUrl('/api/radar/health'), '/api/radar/health');
  assert.throws(
    () => resolveRadarProxyRequestUrl('/api/radar/proxy?__radar_path=..%2F..%2Fdeals'),
    /Invalid API proxy path/
  );
  assert.equal(
    resolveRadarProxyRequestUrl('/api/deal-workspaces/proxy?__deal_workspace_path=12'),
    '/api/deal-workspaces/12'
  );
  assert.equal(resolveRadarProxyRequestUrl('/api/deals/proxy?__deals_path='), '/api/deals/');
  assert.throws(
    () => resolveRadarProxyRequestUrl('/api/deals/proxy?__deals_path=..%252F..%252Fradar'),
    /Invalid API proxy path/
  );
});

test('never forwards browser-supplied worker credentials', () => {
  const headers = buildForwardedHeaders({
    accept: 'application/json',
    origin: 'https://frontend.example',
    cookie: 'radar_admin_session=session',
    authorization: 'Bearer browser-controlled',
    'x-radar-run-token': 'browser-controlled'
  });
  assert.equal(headers.get('cookie'), 'radar_admin_session=session');
  assert.equal(headers.get('origin'), 'https://frontend.example');
  assert.equal(headers.has('authorization'), false);
  assert.equal(headers.has('x-radar-run-token'), false);
});

test('relays session cookies through the same-origin proxy', async () => {
  let upstreamRequest;
  const responseHeaders = new Map();
  const response = {
    statusCode: 0,
    setHeader(name, value) { responseHeaders.set(name.toLowerCase(), value); },
    end(body) { this.body = body; }
  };
  await proxyRadarRequest({
    url: '/api/radar/auth/login',
    method: 'POST',
    headers: { origin: 'https://frontend.example', authorization: 'Bearer do-not-forward' },
    body: { password: 'not-logged-by-proxy' }
  }, response, {
    backendOrigin: 'https://backend.example',
    fetchImpl: async (url, options) => {
      upstreamRequest = { url, options };
      return new Response('{"authenticated":true}', {
        status: 200,
        headers: { 'content-type': 'application/json', 'set-cookie': 'radar_admin_session=value; HttpOnly' }
      });
    }
  });
  assert.equal(upstreamRequest.url, 'https://backend.example/api/radar/auth/login');
  assert.equal(upstreamRequest.options.headers.has('authorization'), false);
  assert.deepEqual(responseHeaders.get('set-cookie'), ['radar_admin_session=value; HttpOnly']);
  assert.equal(response.statusCode, 200);
});

test('proxies private deal cookies and preserves upstream Set-Cookie', async () => {
  let upstreamRequest;
  const responseHeaders = new Map();
  const response = {
    statusCode: 0,
    setHeader(name, value) { responseHeaders.set(name.toLowerCase(), value); },
    end(body) { this.body = body; }
  };
  await proxyRadarRequest({
    url: '/api/deal-workspaces/proxy?__deal_workspace_path=42',
    method: 'PUT',
    headers: {
      cookie: 'radar_admin_session=session',
      origin: 'https://frontend.example',
      authorization: 'Bearer browser-controlled',
      'x-radar-run-token': 'browser-controlled'
    },
    body: { companyName: 'Example', platform: 'Republic' }
  }, response, {
    backendOrigin: 'https://backend.example',
    fetchImpl: async (url, options) => {
      upstreamRequest = { url, options };
      return new Response('{}', { status: 200, headers: { 'set-cookie': 'session=next; HttpOnly' } });
    }
  });
  assert.equal(upstreamRequest.url, 'https://backend.example/api/deal-workspaces/42');
  assert.equal(upstreamRequest.options.headers.get('cookie'), 'radar_admin_session=session');
  assert.equal(upstreamRequest.options.headers.has('authorization'), false);
  assert.equal(upstreamRequest.options.headers.has('x-radar-run-token'), false);
  assert.deepEqual(responseHeaders.get('set-cookie'), ['session=next; HttpOnly']);
});

test('returns a safe 502 when the backend is unavailable', async () => {
  const response = {
    statusCode: 0,
    headers: new Map(),
    setHeader(name, value) { this.headers.set(name.toLowerCase(), value); },
    end(body) { this.body = body; }
  };
  await proxyRadarRequest({ url: '/api/deals', method: 'GET', headers: {} }, response, {
    backendOrigin: 'https://backend.example',
    fetchImpl: async () => { throw new Error('secret backend detail'); }
  });
  assert.equal(response.statusCode, 502);
  assert.deepEqual(JSON.parse(String(response.body)), {
    ok: false,
    error: 'Application backend is unavailable.'
  });
  assert.equal(String(response.body).includes('secret backend detail'), false);
});

test('derives a client address for durable login throttling', () => {
  const withRealIp = buildForwardedHeaders({
    'x-real-ip': '203.0.113.44',
    'x-forwarded-for': '198.51.100.9, 203.0.113.44'
  });
  assert.equal(withRealIp.get('x-radar-client-ip'), '203.0.113.44');

  // Right-most forwarded entry wins: it is appended by the closest proxy.
  const forwardedOnly = buildForwardedHeaders({ 'x-forwarded-for': 'attacker-value, 203.0.113.7' });
  assert.equal(forwardedOnly.get('x-radar-client-ip'), '203.0.113.7');

  // A client cannot inject the header the backend trusts.
  const spoofed = buildForwardedHeaders({ 'x-radar-client-ip': '10.0.0.1' });
  assert.equal(spoofed.get('x-radar-client-ip'), null);

  // Junk is dropped rather than forwarded.
  const junk = buildForwardedHeaders({ 'x-real-ip': 'not an address; DROP TABLE' });
  assert.equal(junk.get('x-radar-client-ip'), null);
});
