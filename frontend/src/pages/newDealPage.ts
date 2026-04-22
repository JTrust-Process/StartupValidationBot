export function renderNewDealPage(): string {
  return `
    <div class="page">
      <div class="page-header">
        <h2>New Deal</h2>
        <p>Deal intake form will go here next.</p>
      </div>

      <div class="card">
        <form class="form-grid">
          <div class="form-field">
            <label for="companyName">Company Name</label>
            <input id="companyName" type="text" placeholder="Enter company name" />
          </div>

          <div class="form-field">
            <label for="sector">Sector</label>
            <input id="sector" type="text" placeholder="Defense Tech, Energy, AI..." />
          </div>

          <div class="form-field">
            <label for="platform">Platform</label>
            <input id="platform" type="text" placeholder="Wefunder, Republic..." />
          </div>

          <div class="form-field">
            <label for="roundType">Round Type</label>
            <input id="roundType" type="text" placeholder="SAFE, Equity..." />
          </div>

          <div class="form-field form-field--full">
            <label for="description">Short Description</label>
            <textarea id="description" rows="4" placeholder="What does the company do?"></textarea>
          </div>

          <div class="form-actions">
            <button type="button" class="button button--primary">Save Later</button>
          </div>
        </form>
      </div>
    </div>
  `;
}