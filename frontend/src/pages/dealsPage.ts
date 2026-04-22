import { getDeals } from '../services/dealService';

export function renderDealsPage(): string {
  const deals = getDeals();

  const rows = deals
    .map(
      (deal) => `
        <tr>
          <td>
            <a class="table-link" href="#/deals/${deal.id}">${deal.companyName}</a>
            <div class="table-subtext">${deal.shortDescription}</div>
          </td>
          <td>${deal.sector}</td>
          <td>${deal.platform}</td>
          <td>${deal.roundType}</td>
          <td>${deal.quickScore}</td>
          <td>${deal.deepScore ?? '-'}</td>
          <td><span class="status-chip status-chip--${deal.status}">${deal.status}</span></td>
        </tr>
      `
    )
    .join('');

  return `
    <div class="page">
      <div class="page-header page-header--row">
        <div>
          <h2>Deals</h2>
          <p>All tracked startup opportunities.</p>
        </div>

        <a class="button button--primary" href="#/deals/new">Add Deal</a>
      </div>

      <div class="card">
        <table class="data-table">
          <thead>
            <tr>
              <th>Company</th>
              <th>Sector</th>
              <th>Platform</th>
              <th>Round</th>
              <th>Quick</th>
              <th>Deep</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            ${rows}
          </tbody>
        </table>
      </div>
    </div>
  `;
}