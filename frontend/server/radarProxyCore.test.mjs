import assert from 'node:assert/strict';
import test from 'node:test';

import { buildForwardedHeaders, buildRadarTarget, proxyRadarRequest } from './radarProxyCore.mjs';

test('builds only HTTPS Radar backend targets', () => {
  assert.equal(
    buildRadarTarget('/api/radar/admin/status?fresh=true', 'https://radar-backend.example'),
    'https://radar-backend.example/api/radar/admin/status?fresh=true'
  );
  assert.throws(() => buildRadarTarget('/api/deals', 'https://radar-backend.example'), /Only Radar API/);
  assert.throws(() => buildRadarTarget('/api/radar/status', 'http://radar-backend.example'), /must use HTTPS/);
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
