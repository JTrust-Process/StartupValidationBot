import type { RadarCompany } from '../models/radar';
import { getRadarAdminSession, loginRadarAdmin, RadarApiError } from '../services/radarService';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { safeExternalUrl } from '../utils/urls';

export function formatRadarDate(value: string | null): string {
  if (!value) return 'Never';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString();
}

export function scoreTone(score: number): string {
  if (score >= 75) return 'high';
  if (score >= 50) return 'medium';
  return 'low';
}

export function renderScore(label: string, score: number): string {
  return `
    <div class="radar-score radar-score--${scoreTone(score)}">
      <span>${escapeHtml(label)}</span>
      <strong>${score}</strong>
    </div>
  `;
}

export function renderCompanyRows(companies: RadarCompany[]): string {
  if (!companies.length) {
    return '<div class="radar-empty">No companies match this view yet.</div>';
  }
  return companies.map((company) => {
    const websiteUrl = safeExternalUrl(company.websiteUrl);
    return `
    <article class="radar-company-row" data-company-id="${company.id}">
      <div class="radar-company-row__identity">
        <div class="radar-company-row__title">
          <a href="#/radar/company/${company.id}">${escapeHtml(company.name)}</a>
        </div>
        <p>${escapeHtml(company.description || 'No source summary captured yet.')}</p>
        <div class="radar-tags">
          <span class="radar-tag">${escapeHtml(company.sector || 'Unknown')}</span>
          ${company.categories.slice(0, 3).map((category) => `<span class="radar-tag">${escapeHtml(category)}</span>`).join('')}
          <span class="radar-meta">${company.sourceCount} source${company.sourceCount === 1 ? '' : 's'}</span>
          <span class="radar-meta">Updated ${escapeHtml(formatRadarDate(company.lastSeenAt))}</span>
        </div>
      </div>
      <div class="radar-company-row__scores">
        ${renderScore('Radar', company.radarScore)}
      </div>
      <div class="radar-company-row__actions">
        <a class="button button--secondary button--compact" href="#/radar/company/${company.id}">Deep dive</a>
        ${websiteUrl ? `<a class="button button--secondary button--compact" href="${escapeAttribute(websiteUrl)}" target="_blank" rel="noreferrer">Visit</a>` : ''}
      </div>
    </article>
    `;
  }).join('');
}

export function renderRadarError(error: unknown): string {
  const message = error instanceof Error ? error.message : 'Unknown Radar error';
  return `<div class="notice notice--danger"><strong>Radar request failed</strong><p>${escapeHtml(message)}</p></div>`;
}

/**
 * Shared sign-in gate for the Radar views.
 *
 * Every Radar read now requires the browser session, so an anonymous visitor must get a login form
 * rather than a wall of failed requests. The session cookie is HttpOnly: nothing here reads, stores
 * or forwards a token, and no worker credential is ever involved.
 */
export function renderRadarAuthGate(configured: boolean, message = ''): string {
  if (!configured) {
    return `
      <div class="radar-panel radar-auth">
        <h3>Radar is not configured for sign-in</h3>
        <p class="notice notice--warning">
          The server has no <code>RADAR_ADMIN_PASSWORD_HASH</code>, so it cannot authenticate anyone and
          Radar data stays locked. Generate a hash with <code>RadarPasswordHashTool</code> and set it on
          the backend, then reload this page.
        </p>
      </div>
    `;
  }

  return `
    <div class="radar-panel radar-auth">
      <h3>Sign in to Startup Radar</h3>
      <p>This is a private research workspace. Sign in to view companies, sources and trends.</p>
      ${message ? `<div class="alert alert--danger">${escapeHtml(message)}</div>` : ''}
      <form id="radar-auth-gate-form" class="radar-auth-form">
        <div class="form-field">
          <label for="radar-gate-password">Admin password</label>
          <input id="radar-gate-password" name="password" type="password" maxlength="256"
                 autocomplete="current-password" required />
        </div>
        <button class="button button--primary" type="submit">Sign in</button>
      </form>
    </div>
  `;
}

/**
 * Runs a Radar view loader, replacing the container with a sign-in gate when the server reports the
 * caller is unauthenticated. Re-runs the loader after a successful sign-in.
 */
export async function guardRadarView(container: HTMLElement, load: () => Promise<void>): Promise<void> {
  try {
    await load();
  } catch (error) {
    if (!(error instanceof RadarApiError) || error.status !== 401) {
      container.innerHTML = renderRadarError(error);
      return;
    }
    await showAuthGate(container, load, '');
  }
}

async function showAuthGate(container: HTMLElement, load: () => Promise<void>, message: string): Promise<void> {
  let configured = true;
  try {
    configured = (await getRadarAdminSession()).configured;
  } catch {
    // Treat an unreachable session endpoint as "configured" so the user still sees a login form.
  }

  container.innerHTML = renderRadarAuthGate(configured, message);
  const form = container.querySelector<HTMLFormElement>('#radar-auth-gate-form');
  form?.addEventListener('submit', async (event) => {
    event.preventDefault();
    const button = form.querySelector<HTMLButtonElement>('button[type="submit"]');
    if (button) button.disabled = true;
    try {
      await loginRadarAdmin(String(new FormData(form).get('password') || ''));
      await guardRadarView(container, load);
    } catch (loginError) {
      const text = loginError instanceof Error ? loginError.message : 'Sign in failed.';
      await showAuthGate(container, load, text);
    } finally {
      if (button) button.disabled = false;
    }
  });
}
