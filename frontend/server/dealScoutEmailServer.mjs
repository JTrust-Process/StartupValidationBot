#!/usr/bin/env node
import './env/loadServerEnv.mjs';
import http from 'node:http';
import { runAutomatedDealScoutDigest } from './dealScoutAutomation.mjs';
import { digestTextToHtml } from './email/digestHtml.mjs';
import { sendDealScoutDigestEmail } from './email/resendClient.mjs';

const port = Number(process.env.PORT || process.env.DEAL_SCOUT_EMAIL_SERVER_PORT || 8787);
const host = process.env.DEAL_SCOUT_EMAIL_SERVER_HOST || (process.env.PORT ? '0.0.0.0' : '127.0.0.1');
const sendEndpoint = '/api/deal-scout/digest/send';
const runEndpoint = '/api/deal-scout/digest/run';
const allowedOrigins = String(
  process.env.DEAL_SCOUT_ALLOWED_ORIGIN || 'http://127.0.0.1:5173,http://localhost:5173'
)
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);
const allowClientRecipient = process.env.DEAL_SCOUT_ALLOW_CLIENT_RECIPIENT === 'true';
const runToken = process.env.DEAL_SCOUT_RUN_TOKEN || '';

function getCorsOrigin(requestOrigin) {
  if (requestOrigin && allowedOrigins.includes(requestOrigin)) return requestOrigin;
  return allowedOrigins[0] || 'http://127.0.0.1:5173';
}

function sendJson(response, statusCode, payload, requestOrigin = '') {
  response.writeHead(statusCode, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': getCorsOrigin(requestOrigin),
    'Access-Control-Allow-Headers': 'authorization, content-type, x-deal-scout-run-token',
    'Access-Control-Allow-Methods': 'POST, OPTIONS'
  });
  response.end(JSON.stringify(payload));
}

function createRequestError(message, statusCode) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}

async function readJson(request) {
  let body = '';

  for await (const chunk of request) {
    body += chunk;
    if (body.length > 128_000) {
      throw createRequestError('request body too large', 413);
    }
  }

  if (!body.trim()) return {};

  try {
    return JSON.parse(body);
  } catch {
    throw createRequestError('invalid JSON request body', 400);
  }
}

function getBearerToken(request) {
  const authorization = request.headers.authorization || '';
  if (authorization.startsWith('Bearer ')) return authorization.slice('Bearer '.length).trim();
  return request.headers['x-deal-scout-run-token'] || '';
}

function requireRunToken(request, response, origin) {
  if (!runToken) {
    sendJson(response, 503, {
      ok: false,
      error: 'missing DEAL_SCOUT_RUN_TOKEN; automated digest endpoint is not enabled'
    }, origin);
    return false;
  }

  if (getBearerToken(request) !== runToken) {
    sendJson(response, 401, {
      ok: false,
      error: 'invalid or missing Deal Scout run token'
    }, origin);
    return false;
  }

  return true;
}

const server = http.createServer(async (request, response) => {
  const origin = request.headers.origin;
  if (origin && !allowedOrigins.includes(origin)) {
    sendJson(response, 403, {
      ok: false,
      error: 'origin not allowed for Deal Scout email sending'
    }, origin);
    return;
  }

  if (request.method === 'OPTIONS') {
    sendJson(response, 204, {}, origin);
    return;
  }

  const url = new URL(request.url || '/', `http://${request.headers.host || '127.0.0.1'}`);

  if (url.pathname !== sendEndpoint && url.pathname !== runEndpoint) {
    sendJson(response, 404, {
      ok: false,
      error: `Use POST ${sendEndpoint} or POST ${runEndpoint}`
    }, origin);
    return;
  }

  if (request.method !== 'POST') {
    sendJson(response, 405, {
      ok: false,
      error: `Use POST ${url.pathname}`
    }, origin);
    return;
  }

  if (url.pathname === runEndpoint) {
    try {
      if (!requireRunToken(request, response, origin)) return;

      const result = await runAutomatedDealScoutDigest({ send: true });
      sendJson(response, result.ok ? 200 : 400, result, origin);
    } catch (error) {
      sendJson(response, 500, {
        ok: false,
        error: error instanceof Error ? error.message : 'unknown automated digest error'
      }, origin);
    }
    return;
  }

  try {
    const payload = await readJson(request);
    const to = process.env.DEAL_SCOUT_EMAIL_RECIPIENT || (allowClientRecipient ? payload.to : '');
    const subject = String(payload.subject || '').trim();
    const text = String(payload.text || '').trim();
    const html = String(payload.html || digestTextToHtml(text)).trim();

    if (!subject) {
      sendJson(response, 400, { ok: false, error: 'missing subject' }, origin);
      return;
    }

    if (!text && !html) {
      sendJson(response, 400, { ok: false, error: 'missing text or html body' }, origin);
      return;
    }

    if (!to) {
      sendJson(response, 400, { ok: false, error: 'missing recipient' }, origin);
      return;
    }

    if (/[;,]/.test(String(to))) {
      sendJson(response, 400, { ok: false, error: 'only one email recipient is allowed for now' }, origin);
      return;
    }

    const result = await sendDealScoutDigestEmail({ to, subject, text, html });
    sendJson(response, result.ok ? 200 : 400, result, origin);
  } catch (error) {
    const statusCode = Number.isInteger(error?.statusCode) ? error.statusCode : 500;
    sendJson(response, statusCode, {
      ok: false,
      error: error instanceof Error ? error.message : 'unknown email server error'
    }, origin);
  }
});

server.listen(port, host, () => {
  console.log(`Deal Scout email server listening on http://${host}:${port}${sendEndpoint}`);
});
