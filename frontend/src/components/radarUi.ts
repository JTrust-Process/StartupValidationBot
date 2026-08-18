import type { RadarCompany } from '../models/radar';
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
