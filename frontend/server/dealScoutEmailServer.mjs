#!/usr/bin/env node
import './env/loadServerEnv.mjs';
import http from 'node:http';
import { digestTextToHtml } from './email/digestHtml.mjs';
import { sendDealScoutDigestEmail } from './email/resendClient.mjs';

const port = Number(process.env.DEAL_SCOUT_EMAIL_SERVER_PORT || 8787);
const endpoint = '/api/deal-scout/digest/send';
const allowedOrigin = process.env.DEAL_SCOUT_ALLOWED_ORIGIN || 'http://127.0.0.1:5173';

function sendJson(response, statusCode, payload) {
  response.writeHead(statusCode, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': allowedOrigin,
    'Access-Control-Allow-Headers': 'content-type',
    'Access-Control-Allow-Methods': 'POST, OPTIONS'
  });
  response.end(JSON.stringify(payload));
}

async function readJson(request) {
  let body = '';

  for await (const chunk of request) {
    body += chunk;
    if (body.length > 128_000) {
      throw new Error('request body too large');
    }
  }

  return body.trim() ? JSON.parse(body) : {};
}

const server = http.createServer(async (request, response) => {
  const origin = request.headers.origin;
  if (origin && origin !== allowedOrigin) {
    sendJson(response, 403, {
      ok: false,
      error: 'origin not allowed for Deal Scout email sending'
    });
    return;
  }

  if (request.method === 'OPTIONS') {
    sendJson(response, 204, {});
    return;
  }

  if (request.url !== endpoint || request.method !== 'POST') {
    sendJson(response, 404, {
      ok: false,
      error: `Use POST ${endpoint}`
    });
    return;
  }

  try {
    const payload = await readJson(request);
    const to = payload.to || process.env.DEAL_SCOUT_EMAIL_RECIPIENT || '';
    const subject = String(payload.subject || '').trim();
    const text = String(payload.text || '').trim();
    const html = String(payload.html || digestTextToHtml(text)).trim();

    if (!subject) {
      sendJson(response, 400, { ok: false, error: 'missing subject' });
      return;
    }

    if (!text && !html) {
      sendJson(response, 400, { ok: false, error: 'missing text or html body' });
      return;
    }

    if (!to) {
      sendJson(response, 400, { ok: false, error: 'missing recipient' });
      return;
    }

    if (/[;,]/.test(String(to))) {
      sendJson(response, 400, { ok: false, error: 'only one email recipient is allowed for now' });
      return;
    }

    const result = await sendDealScoutDigestEmail({ to, subject, text, html });
    sendJson(response, result.ok ? 200 : 400, result);
  } catch (error) {
    sendJson(response, 500, {
      ok: false,
      error: error instanceof Error ? error.message : 'unknown email server error'
    });
  }
});

server.listen(port, '127.0.0.1', () => {
  console.log(`Deal Scout email server listening on http://127.0.0.1:${port}${endpoint}`);
});
