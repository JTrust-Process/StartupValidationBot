import type { Deal } from '../models/deal';
import {
  exportDealsAsJson,
  getDeals,
  getFinalRecommendation,
  getFinalScore,
  getRedFlagCount,
  getRiskStatus,
  importDealsFromJson
} from '../services/dealService';
import {
  formatDealStatus,
  formatInvestorEligibility,
  formatOfferingExemption,
  formatSecurityType
} from '../utils/formatters';
import { escapeAttribute, escapeHtml } from '../utils/html';

interface DealFilters {
  companyName: string;
  platform: string;
  sector: string;
  decision: string;
  investorEligibility: string;
  offeringExemption: string;
}

function uniqueValues(deals: Deal[], key: 'platform' | 'sector'): string[] {
  return Array.from(new Set(deals.map((deal) => deal[key]).filter(Boolean))).sort((a, b) =>
    a.localeCompare(b)
  );
}

function getOptionHtml(values: string[], selectedValue = ''): string {
  return values
    .map(
      (value) => `
        <option value="${escapeAttribute(value)}" ${value === selectedValue ? 'selected' : ''}>
          ${escapeHtml(value)}
        </option>
      `
    )
    .join('');
}

function matchesFilters(deal: Deal, filters: DealFilters): boolean {
  const companyMatches =
    !filters.companyName ||
    deal.companyName.toLowerCase().includes(filters.companyName.toLowerCase());

  return (
    companyMatches &&
    (!filters.platform || deal.platform === filters.platform) &&
    (!filters.sector || deal.sector === filters.sector) &&
    (!filters.decision || deal.decision === filters.decision) &&
    (!filters.investorEligibility || deal.investorEligibility === filters.investorEligibility) &&
    (!filters.offeringExemption || deal.offeringExemption === filters.offeringExemption)
  );
}

function getRowsHtml(deals: Deal[]): string {
  if (!deals.length) {
    return `
      <tr>
        <td colspan="8">No matching deals.</td>
      </tr>
    `;
  }

  return deals
    .map((deal) => {
      const finalScore = getFinalScore(deal);
      const recommendation = getFinalRecommendation(finalScore);
      const riskStatus = getRiskStatus(deal);

      return `
        <tr>
          <td>
            <a class="table-link" href="#/deals/${deal.id}">${escapeHtml(deal.companyName)}</a>
            <div class="table-subtext">${escapeHtml(deal.shortDescription || 'No summary captured.')}</div>
          </td>
          <td>${escapeHtml(deal.platform || '-')}</td>
          <td>${escapeHtml(deal.sector || '-')}</td>
          <td>${formatSecurityType(deal.securityType)}</td>
          <td>
            <span class="status-chip status-chip--${deal.decision.toLowerCase().replace('_', '-')}">
              ${formatDealStatus(deal.decision)}
            </span>
          </td>
          <td>
            <div>${formatInvestorEligibility(deal.investorEligibility)}</div>
            <div class="table-subtext">${formatOfferingExemption(deal.offeringExemption)}</div>
          </td>
          <td>
            <div>${finalScore}</div>
            <div class="table-subtext">${escapeHtml(recommendation.label)}</div>
          </td>
          <td>
            <span class="risk-chip risk-chip--${riskStatus.tone}">
              ${riskStatus.label}
            </span>
            <div class="table-subtext">${getRedFlagCount(deal)} flags</div>
          </td>
        </tr>
      `;
    })
    .join('');
}

function readFilters(root: HTMLElement): DealFilters {
  return {
    companyName: root.querySelector<HTMLInputElement>('#company-filter')?.value.trim() ?? '',
    platform: root.querySelector<HTMLSelectElement>('#platform-filter')?.value ?? '',
    sector: root.querySelector<HTMLSelectElement>('#sector-filter')?.value ?? '',
    decision: root.querySelector<HTMLSelectElement>('#decision-filter')?.value ?? '',
    investorEligibility:
      root.querySelector<HTMLSelectElement>('#eligibility-filter')?.value ?? '',
    offeringExemption:
      root.querySelector<HTMLSelectElement>('#exemption-filter')?.value ?? ''
  };
}

function applyFilters(root: HTMLElement): void {
  const tableBody = root.querySelector<HTMLTableSectionElement>('#deals-table-body');
  if (!tableBody) return;

  const filters = readFilters(root);
  const filteredDeals = getDeals().filter((deal) => matchesFilters(deal, filters));
  tableBody.innerHTML = getRowsHtml(filteredDeals);
}

function downloadJsonBackup(): void {
  const blob = new Blob([exportDealsAsJson()], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  const date = new Date().toISOString().slice(0, 10);

  anchor.href = url;
  anchor.download = `startup-deal-os-backup-${date}.json`;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export function renderDealsPage(): string {
  const deals = getDeals();
  const platformOptions = getOptionHtml(uniqueValues(deals, 'platform'));
  const sectorOptions = getOptionHtml(uniqueValues(deals, 'sector'));

  return `
    <div class="page">
      <div class="page-header page-header--row">
        <div>
          <h2>Deals</h2>
          <p>Search, filter, back up, and open your startup diligence records.</p>
        </div>

        <div class="workspace-actions">
          <button class="button button--secondary" id="export-deals-button" type="button">Export JSON</button>
          <button class="button button--secondary" id="import-deals-button" type="button">Import JSON</button>
          <input id="import-deals-input" class="visually-hidden" type="file" accept="application/json,.json" />
          <a class="button button--primary" href="#/deals/new">Add Deal</a>
        </div>
      </div>

      <div class="notice notice--neutral">
        Local-first workspace. Back up your research with JSON export before clearing browser data.
      </div>

      <div class="card">
        <form class="filter-grid" id="deal-filters">
          <div class="form-field">
            <label for="company-filter">Company Name</label>
            <input id="company-filter" type="search" placeholder="Search company..." />
          </div>

          <div class="form-field">
            <label for="platform-filter">Platform</label>
            <select id="platform-filter">
              <option value="">All platforms</option>
              ${platformOptions}
            </select>
          </div>

          <div class="form-field">
            <label for="sector-filter">Sector</label>
            <select id="sector-filter">
              <option value="">All sectors</option>
              ${sectorOptions}
            </select>
          </div>

          <div class="form-field">
            <label for="decision-filter">Decision</label>
            <select id="decision-filter">
              <option value="">All decisions</option>
              <option value="PASS">Pass</option>
              <option value="WATCH">Watch</option>
              <option value="INVEST_SMALL">Invest Small</option>
            </select>
          </div>

          <div class="form-field">
            <label for="eligibility-filter">Eligibility</label>
            <select id="eligibility-filter">
              <option value="">All eligibility</option>
              <option value="NON_ACCREDITED">Non-accredited</option>
              <option value="ACCREDITED_ONLY">Accredited only</option>
              <option value="UNCLEAR">Unclear</option>
            </select>
          </div>

          <div class="form-field">
            <label for="exemption-filter">Exemption</label>
            <select id="exemption-filter">
              <option value="">All exemptions</option>
              <option value="REG_CF">Reg CF</option>
              <option value="REG_A">Reg A</option>
              <option value="REG_D">Reg D</option>
              <option value="UNKNOWN">Unknown</option>
              <option value="OTHER">Other</option>
            </select>
          </div>
        </form>
      </div>

      <div class="card table-card">
        <table class="data-table">
          <thead>
            <tr>
              <th>Company</th>
              <th>Platform</th>
              <th>Sector</th>
              <th>Security</th>
              <th>Decision</th>
              <th>Eligibility</th>
              <th>Final</th>
              <th>Risk</th>
            </tr>
          </thead>
          <tbody id="deals-table-body">
            ${getRowsHtml(deals)}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

export function bindDealsPageEvents(root: HTMLElement): void {
  const filterForm = root.querySelector<HTMLFormElement>('#deal-filters');
  const exportButton = root.querySelector<HTMLButtonElement>('#export-deals-button');
  const importButton = root.querySelector<HTMLButtonElement>('#import-deals-button');
  const importInput = root.querySelector<HTMLInputElement>('#import-deals-input');

  filterForm?.addEventListener('input', () => {
    applyFilters(root);
  });

  filterForm?.addEventListener('change', () => {
    applyFilters(root);
  });

  exportButton?.addEventListener('click', () => {
    downloadJsonBackup();
  });

  importButton?.addEventListener('click', () => {
    importInput?.click();
  });

  importInput?.addEventListener('change', async () => {
    const file = importInput.files?.[0];
    if (!file) return;

    const confirmed = window.confirm(
      'Importing JSON will replace the deals currently stored in this browser. Continue?'
    );

    if (!confirmed) {
      importInput.value = '';
      return;
    }

    try {
      await importDealsFromJson(await file.text());
      importInput.value = '';
      window.dispatchEvent(new HashChangeEvent('hashchange'));
    } catch (error) {
      console.error('Failed to import deals:', error);
      window.alert('Import failed. Please choose a valid Startup Deal OS JSON backup.');
      importInput.value = '';
    }
  });
}
