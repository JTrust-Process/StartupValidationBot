import { getDealById } from '../services/dealService';

export function renderDealWorkspacePage(path: string): string {
  const id = path.split('/').pop() ?? '';
  const deal = getDealById(id);

  if (!deal) {
    return `
      <div class="page">
        <div class="card">
          <h2>Deal not found</h2>
          <p>No deal exists for ID: ${id}</p>
        </div>
      </div>
    `;
  }

  return `
    <div class="page">
      <div class="page-header page-header--row">
        <div>
          <h2>${deal.companyName}</h2>
          <p>${deal.sector} • ${deal.platform} • ${deal.roundType}</p>
        </div>

        <span class="status-chip status-chip--${deal.status}">${deal.status}</span>
      </div>

      <div class="card">
        <h3>Description</h3>
        <p>${deal.shortDescription}</p>
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Quick Score</h3>
          <p class="metric">${deal.quickScore}</p>
        </div>

        <div class="card">
          <h3>Deep Score</h3>
          <p class="metric">${deal.deepScore ?? '-'}</p>
        </div>

        <div class="card">
          <h3>Valuation</h3>
          <p class="metric">${deal.valuation ? `$${deal.valuation.toLocaleString()}` : '-'}</p>
        </div>

        <div class="card">
          <h3>Min Investment</h3>
          <p class="metric">${deal.minimumInvestment ? `$${deal.minimumInvestment}` : '-'}</p>
        </div>
      </div>

      <div class="card">
        <h3>Workspace Tabs</h3>
        <p>Next step: add Overview, Quick Screen, Deep Diligence, Decision, and Reviews sections.</p>
      </div>
    </div>
  `;
}