const DISCLAIMER =
  'This is a research shortlist, not financial advice. Review offering documents and risks before investing.';

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function digestTextToHtml(text) {
  const body = String(text ?? '').trim();
  const paragraphs = body
    .split(/\n{2,}/)
    .map((block) => `<p>${escapeHtml(block).replace(/\n/g, '<br>')}</p>`)
    .join('\n');

  return `<!doctype html>
<html>
  <body style="font-family: Arial, sans-serif; line-height: 1.5; color: #111827;">
    <h1 style="font-size: 20px;">Startup Deal Scout</h1>
    <p style="padding: 12px; background: #fff7ed; border: 1px solid #fed7aa;">
      ${escapeHtml(DISCLAIMER)}
    </p>
    ${paragraphs}
  </body>
</html>`;
}

export { DISCLAIMER };
