export function renderDashboardPage(): string {
  return `
    <div class="page">
      <div class="page-header">
        <h2>Dashboard</h2>
        <p>Overview of your diligence pipeline.</p>
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Total Deals</h3>
          <p class="metric">3</p>
        </div>

        <div class="card">
          <h3>Watchlist</h3>
          <p class="metric">1</p>
        </div>

        <div class="card">
          <h3>Invested Small</h3>
          <p class="metric">1</p>
        </div>

        <div class="card">
          <h3>Passes</h3>
          <p class="metric">1</p>
        </div>
      </div>

      <div class="card">
        <h3>Next Goal</h3>
        <p>Build the full deals table, then wire the new-deal form.</p>
      </div>
    </div>
  `;
}