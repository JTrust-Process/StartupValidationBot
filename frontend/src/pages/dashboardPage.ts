import { getDeals } from '../services/dealService';
import { formatDealStatus } from '../utils/formatters';

function getAverage(values: number[]): string {
  if (values.length === 0) return '-';

  const total = values.reduce((sum, value) => sum + value, 0);
  return (total / values.length).toFixed(1);
}

export function renderDashboardPage(): string {
  const deals = getDeals();

  const totalDeals = deals.length;
  const watchCount = deals.filter((deal) => deal.status === 'WATCH').length;
  const passCount = deals.filter((deal) => deal.status === 'PASS').length;
  const investSmallCount = deals.filter((deal) => deal.status === 'INVEST_SMALL').length;

  const quickScores = deals.map((deal) => deal.quickScore);
  const deepScores = deals
    .filter((deal) => typeof deal.deepScore === 'number')
    .map((deal) => deal.deepScore as number);

  const averageQuickScore = getAverage(quickScores);
  const averageDeepScore = getAverage(deepScores);

  const recentDeals = deals.slice(0, 5);
  const reviewCount = deals.filter((deal) => deal.review?.nextReviewDate).length;

  const recentDealsHtml = recentDeals.length
    ? recentDeals
        .map(
          (deal) => `
            <tr>
              <td><a class="table-link" href="#/deals/${deal.id}">${deal.companyName}</a></td>
              <td>${formatDealStatus(deal.status)}</td>
              <td>${deal.quickScore}</td>
              <td>${deal.deepScore ?? '-'}</td>
            </tr>
          `
        )
        .join('')
    : `
      <tr>
        <td colspan="4">No deals yet.</td>
      </tr>
    `;

  return `
    <div class="page">
      <div class="page-header">
        <h2>Dashboard</h2>
        <p>Overview of your diligence pipeline.</p>
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Total Deals</h3>
          <p class="metric">${totalDeals}</p>
        </div>

        <div class="card">
          <h3>Watch</h3>
          <p class="metric">${watchCount}</p>
        </div>

        <div class="card">
          <h3>Pass</h3>
          <p class="metric">${passCount}</p>
        </div>

        <div class="card">
          <h3>Invest Small</h3>
          <p class="metric">${investSmallCount}</p>
        </div>
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Average Quick Score</h3>
          <p class="metric">${averageQuickScore}</p>
          <p class="metric-subtext">Across all tracked deals</p>
        </div>

        <div class="card">
          <h3>Average Deep Score</h3>
          <p class="metric">${averageDeepScore}</p>
          <p class="metric-subtext">Only scored deals</p>
        </div>

        <div class="card">
          <h3>Deals with Deep Diligence</h3>
          <p class="metric">${deepScores.length}</p>
          <p class="metric-subtext">Deals that have been fully reviewed</p>
        </div>

        <div class="card">
          <h3>Scheduled Reviews</h3>
          <p class="metric">${reviewCount}</p>
          <p class="metric-subtext">Deals with a next review date</p>
        </div>
      </div>

      <div class="card">
        <div class="page-header">
          <h3>Recent Deals</h3>
          <p>Your most recently added or updated pipeline entries.</p>
        </div>

        <table class="data-table">
          <thead>
            <tr>
              <th>Company</th>
              <th>Status</th>
              <th>Quick</th>
              <th>Deep</th>
            </tr>
          </thead>
          <tbody>
            ${recentDealsHtml}
          </tbody>
        </table>
      </div>
    </div>
  `;
}

export function bindDashboardPageEvents(): void {
  // no-op for now
}