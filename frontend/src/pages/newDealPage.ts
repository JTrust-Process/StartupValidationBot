import { createDeal } from '../services/dealService';
import { navigateTo } from '../utils/router';

export function renderNewDealPage(): string {
  return `
    <div class="page">
      <div class="page-header">
        <h2>New Deal</h2>
        <p>Add a startup deal to your diligence pipeline.</p>
      </div>

      <div class="card">
        <form class="form-grid" id="new-deal-form">
          <div class="form-field">
            <label for="companyName">Company Name</label>
            <input id="companyName" name="companyName" type="text" placeholder="Enter company name" required />
          </div>

          <div class="form-field">
            <label for="sector">Sector</label>
            <input id="sector" name="sector" type="text" placeholder="Defense Tech, Energy, AI..." required />
          </div>

          <div class="form-field">
            <label for="platform">Platform</label>
            <input id="platform" name="platform" type="text" placeholder="Wefunder, Republic..." required />
          </div>

          <div class="form-field">
            <label for="roundType">Round Type</label>
            <input id="roundType" name="roundType" type="text" placeholder="SAFE, Equity..." required />
          </div>

          <div class="form-field">
            <label for="valuation">Valuation</label>
            <input id="valuation" name="valuation" type="number" min="0" step="1" placeholder="12000000" />
          </div>

          <div class="form-field">
            <label for="minimumInvestment">Minimum Investment</label>
            <input id="minimumInvestment" name="minimumInvestment" type="number" min="0" step="1" placeholder="100" />
          </div>

          <div class="form-field form-field--full">
            <label for="shortDescription">Short Description</label>
            <textarea
              id="shortDescription"
              name="shortDescription"
              rows="4"
              placeholder="What does the company do?"
              required
            ></textarea>
          </div>

          <div class="form-actions">
            <button type="submit" class="button button--primary">Create Deal</button>
          </div>
        </form>
      </div>
    </div>
  `;
}

export function bindNewDealPageEvents(root: HTMLElement): void {
  const form = root.querySelector<HTMLFormElement>('#new-deal-form');

  if (!form) return;

  form.addEventListener('submit', async (event) => {
    event.preventDefault();

    const formData = new FormData(form);

    const companyName = String(formData.get('companyName') ?? '').trim();
    const sector = String(formData.get('sector') ?? '').trim();
    const platform = String(formData.get('platform') ?? '').trim();
    const roundType = String(formData.get('roundType') ?? '').trim();
    const shortDescription = String(formData.get('shortDescription') ?? '').trim();

    const valuationRaw = String(formData.get('valuation') ?? '').trim();
    const minimumInvestmentRaw = String(formData.get('minimumInvestment') ?? '').trim();

    if (!companyName || !sector || !platform || !roundType || !shortDescription) {
      window.alert('Please complete all required fields.');
      return;
    }

    try {
      const newDeal = await createDeal({
        companyName,
        sector,
        platform,
        roundType,
        shortDescription,
        valuation: valuationRaw ? Number(valuationRaw) : undefined,
        minimumInvestment: minimumInvestmentRaw ? Number(minimumInvestmentRaw) : undefined
      });

      navigateTo(`/deals/${newDeal.id}`);
    } catch (error) {
      console.error('Failed to create deal:', error);
      window.alert('Failed to create deal. Make sure the backend is running.');
    }
  });
}