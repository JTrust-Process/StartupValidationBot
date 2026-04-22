import { getDealById, getQuickScreenOutcome, saveQuickScreen } from '../services/dealService';
import { navigateTo } from '../utils/router';

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

  const quickScreen = deal.quickScreen;
  const quickOutcome = getQuickScreenOutcome(deal.quickScore);

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
          <p class="metric-subtext">${quickOutcome}</p>
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
        <div class="page-header">
          <h3>Quick Screen</h3>
          <p>Score this deal from 0 to 2 across the five first-pass categories.</p>
        </div>

        <form class="form-grid" id="quick-screen-form" data-deal-id="${deal.id}">
          <div class="form-field">
            <label for="businessClarity">Business Clarity</label>
            <input
              id="businessClarity"
              name="businessClarity"
              type="number"
              min="0"
              max="2"
              step="1"
              value="${quickScreen?.businessClarity ?? 0}"
              required
            />
          </div>

          <div class="form-field">
            <label for="tractionEvidence">Traction Evidence</label>
            <input
              id="tractionEvidence"
              name="tractionEvidence"
              type="number"
              min="0"
              max="2"
              step="1"
              value="${quickScreen?.tractionEvidence ?? 0}"
              required
            />
          </div>

          <div class="form-field">
            <label for="edge">Edge</label>
            <input
              id="edge"
              name="edge"
              type="number"
              min="0"
              max="2"
              step="1"
              value="${quickScreen?.edge ?? 0}"
              required
            />
          </div>

          <div class="form-field">
            <label for="priceSanity">Price / Valuation Sanity</label>
            <input
              id="priceSanity"
              name="priceSanity"
              type="number"
              min="0"
              max="2"
              step="1"
              value="${quickScreen?.priceSanity ?? 0}"
              required
            />
          </div>

          <div class="form-field">
            <label for="trustTransparency">Trust / Transparency</label>
            <input
              id="trustTransparency"
              name="trustTransparency"
              type="number"
              min="0"
              max="2"
              step="1"
              value="${quickScreen?.trustTransparency ?? 0}"
              required
            />
          </div>

          <div class="form-field form-field--full">
            <label for="whatIsIt">What is it?</label>
            <textarea
              id="whatIsIt"
              name="whatIsIt"
              rows="3"
              required
            >${quickScreen?.whatIsIt ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="whyMightItWin">Why might it win?</label>
            <textarea
              id="whyMightItWin"
              name="whyMightItWin"
              rows="3"
              required
            >${quickScreen?.whyMightItWin ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="bestProofPoint">Best proof point</label>
            <textarea
              id="bestProofPoint"
              name="bestProofPoint"
              rows="3"
              required
            >${quickScreen?.bestProofPoint ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="biggestDoubt">Biggest doubt</label>
            <textarea
              id="biggestDoubt"
              name="biggestDoubt"
              rows="3"
              required
            >${quickScreen?.biggestDoubt ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="whySpendingTime">Why spend time on this?</label>
            <textarea
              id="whySpendingTime"
              name="whySpendingTime"
              rows="3"
              required
            >${quickScreen?.whySpendingTime ?? ''}</textarea>
          </div>

          <div class="form-actions">
            <button type="submit" class="button button--primary">Save Quick Screen</button>
          </div>
        </form>
      </div>
    </div>
  `;
}

export function bindDealWorkspacePageEvents(root: HTMLElement, path: string): void {
  const dealId = path.split('/').pop() ?? '';
  const form = root.querySelector<HTMLFormElement>('#quick-screen-form');

  if (!form || !dealId) return;

  form.addEventListener('submit', (event) => {
    event.preventDefault();

    const formData = new FormData(form);

    const businessClarity = Number(formData.get('businessClarity'));
    const tractionEvidence = Number(formData.get('tractionEvidence'));
    const edge = Number(formData.get('edge'));
    const priceSanity = Number(formData.get('priceSanity'));
    const trustTransparency = Number(formData.get('trustTransparency'));

    const whatIsIt = String(formData.get('whatIsIt') ?? '').trim();
    const whyMightItWin = String(formData.get('whyMightItWin') ?? '').trim();
    const bestProofPoint = String(formData.get('bestProofPoint') ?? '').trim();
    const biggestDoubt = String(formData.get('biggestDoubt') ?? '').trim();
    const whySpendingTime = String(formData.get('whySpendingTime') ?? '').trim();

    const numericScores = [
      businessClarity,
      tractionEvidence,
      edge,
      priceSanity,
      trustTransparency
    ];

    const scoresAreValid = numericScores.every(
      (score) => Number.isInteger(score) && score >= 0 && score <= 2
    );

    if (!scoresAreValid) {
      window.alert('Each quick-screen score must be a whole number from 0 to 2.');
      return;
    }

    if (
      !whatIsIt ||
      !whyMightItWin ||
      !bestProofPoint ||
      !biggestDoubt ||
      !whySpendingTime
    ) {
      window.alert('Please complete all quick-screen note fields.');
      return;
    }

    saveQuickScreen({
      dealId,
      businessClarity,
      tractionEvidence,
      edge,
      priceSanity,
      trustTransparency,
      whatIsIt,
      whyMightItWin,
      bestProofPoint,
      biggestDoubt,
      whySpendingTime
    });

    navigateTo(`/deals/${dealId}`);
  });
}