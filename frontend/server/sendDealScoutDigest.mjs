#!/usr/bin/env node
import { readFile } from 'node:fs/promises';
import { stdin } from 'node:process';
import { digestTextToHtml, DISCLAIMER } from './email/digestHtml.mjs';
import { sendDealScoutDigestEmail } from './email/resendClient.mjs';

async function readStdin() {
  let body = '';

  for await (const chunk of stdin) {
    body += chunk;
  }

  return body;
}

function getArgValue(name) {
  const prefix = `${name}=`;
  const match = process.argv.find((arg) => arg.startsWith(prefix));
  return match ? match.slice(prefix.length) : '';
}

async function readPayload() {
  const file = getArgValue('--file');
  const raw = file ? await readFile(file, 'utf8') : await readStdin();

  if (!raw.trim()) {
    return {
      subject: 'Weekly Startup Deal Scout - deals to review',
      text: DISCLAIMER,
      html: digestTextToHtml(DISCLAIMER)
    };
  }

  return JSON.parse(raw);
}

const payload = await readPayload();
const subject = payload.subject || 'Weekly Startup Deal Scout - deals to review';
const text = payload.text || payload.body || DISCLAIMER;
const html = payload.html || digestTextToHtml(text);
const result = await sendDealScoutDigestEmail({
  to: payload.to,
  subject,
  text,
  html
});

console.log(JSON.stringify(result, null, 2));
process.exitCode = result.ok ? 0 : 1;
