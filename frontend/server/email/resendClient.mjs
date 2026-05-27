import { Resend } from 'resend';

function readEnv(name) {
  return typeof process !== 'undefined' ? process.env[name] ?? '' : '';
}

function validateSingleRecipient(to) {
  const trimmed = String(to ?? '').trim();

  if (!trimmed) return { ok: false, error: 'missing recipient' };
  if (/[;,]/.test(trimmed)) {
    return { ok: false, error: 'only one email recipient is allowed for now' };
  }

  return { ok: true, to: trimmed };
}

export async function sendDealScoutDigestEmail({ to, subject, text, html }) {
  const provider = readEnv('EMAIL_PROVIDER').toLowerCase();
  const apiKey = readEnv('RESEND_API_KEY');
  const from = readEnv('RESEND_FROM');
  const recipient = validateSingleRecipient(to || readEnv('DEAL_SCOUT_EMAIL_RECIPIENT'));

  if (provider !== 'resend') {
    return {
      ok: false,
      error: 'EMAIL_PROVIDER is not "resend"; email sending is preview-only or not configured.'
    };
  }

  if (!apiKey) {
    return {
      ok: false,
      error: 'missing RESEND_API_KEY'
    };
  }

  if (!from) {
    return {
      ok: false,
      error: 'missing RESEND_FROM'
    };
  }

  if (!recipient.ok) {
    return {
      ok: false,
      error: recipient.error
    };
  }

  if (!subject || !String(subject).trim()) {
    return {
      ok: false,
      error: 'missing subject'
    };
  }

  if ((!text || !String(text).trim()) && (!html || !String(html).trim())) {
    return {
      ok: false,
      error: 'missing text or html body'
    };
  }

  try {
    const resend = new Resend(apiKey);
    const response = await resend.emails.send({
      from,
      to: [recipient.to],
      subject: String(subject).trim(),
      text: text ? String(text) : undefined,
      html: html ? String(html) : undefined
    });

    if (response.error) {
      return {
        ok: false,
        error: response.error.message ?? 'Resend returned an email send error.'
      };
    }

    return {
      ok: true,
      id: response.data?.id ?? ''
    };
  } catch (error) {
    return {
      ok: false,
      error: error instanceof Error ? error.message : 'Unknown Resend send error.'
    };
  }
}
