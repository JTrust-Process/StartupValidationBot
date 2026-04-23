import {
  getDealById,
  getDeepDiligenceOutcome,
  getQuickScreenOutcome,
  saveDecision,
  saveDeepDiligence,
  saveQuickScreen,
  saveReview
} from '../services/dealService';
import { navigateTo } from '../utils/router';

function renderOverviewSection(path: string): string {
  const id = path.split('/').pop() ?? '';
  const deal = getDealById(id);

  if (!deal) return '';

  const quickOutcome = getQuickScreenOutcome(deal.quickScore);
  const deepOutcome = deal.deepScore ? getDeepDiligenceOutcome(deal.deepScore) : 'Not scored';
  const decision = deal.decision;

  return `
    <div class="card">
      <div class="page-header">
        <h3>Overview</h3>
        <p>Current thesis snapshot for this deal.</p>
      </div>

      <div class="overview-grid">
        <div class="overview-item">
          <div class="overview-label">Quick Screen</div>
          <div class="overview-value">${deal.quickScore} / 10</div>
          <div class="overview-subtext">${quickOutcome}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Deep Diligence</div>
          <div class="overview-value">${deal.deepScore ?? '-'}</div>
          <div class="overview-subtext">${deepOutcome}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Decision</div>
          <div class="overview-value">${decision?.status ?? deal.status}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Minimum Investment</div>
          <div class="overview-value">${deal.minimumInvestment ? `$${deal.minimumInvestment}` : '-'}</div>
        </div>
      </div>

      <div class="summary-grid">
        <div class="summary-block">
          <h4>Rationale</h4>
          <p>${decision?.rationale ?? 'No decision rationale saved yet.'}</p>
        </div>

        <div class="summary-block">
          <h4>Next Milestone Needed</h4>
          <p>${decision?.nextMilestoneNeeded ?? 'No next milestone defined yet.'}</p>
        </div>

        <div class="summary-block">
          <h4>What Would Change My Mind</h4>
          <p>${decision?.whatWouldChangeMyMind ?? 'No change-of-mind note saved yet.'}</p>
        </div>

        <div class="summary-block">
          <h4>Best Proof Point</h4>
          <p>${deal.quickScreen?.bestProofPoint ?? 'No proof point captured yet.'}</p>
        </div>

        <div class="summary-block">
          <h4>Review Status</h4>
          <p>${
            deal.review
              ? `Next review: ${deal.review.nextReviewDate}<br />Thesis: ${deal.review.thesisDirection}<br /><br />${deal.review.reviewNote}`
              : 'No review scheduled yet.'
          }</p>
        </div>
      </div>
    </div>
  `;
}

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
  const decision = deal.decision;
  const deepDiligence = deal.deepDiligence;
  const quickOutcome = getQuickScreenOutcome(deal.quickScore);
  const deepOutcome = deal.deepScore ? getDeepDiligenceOutcome(deal.deepScore) : 'Not scored';

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

      ${renderOverviewSection(path)}

      <div class="card-grid">
        <div class="card">
          <h3>Quick Score</h3>
          <p class="metric">${deal.quickScore}</p>
          <p class="metric-subtext">${quickOutcome}</p>
        </div>

        <div class="card">
          <h3>Deep Score</h3>
          <p class="metric">${deal.deepScore ?? '-'}</p>
          <p class="metric-subtext">${deepOutcome}</p>
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
            <input id="businessClarity" name="businessClarity" type="number" min="0" max="2" step="1" value="${quickScreen?.businessClarity ?? 0}" required />
          </div>

          <div class="form-field">
            <label for="tractionEvidence">Traction Evidence</label>
            <input id="tractionEvidence" name="tractionEvidence" type="number" min="0" max="2" step="1" value="${quickScreen?.tractionEvidence ?? 0}" required />
          </div>

          <div class="form-field">
            <label for="edge">Edge</label>
            <input id="edge" name="edge" type="number" min="0" max="2" step="1" value="${quickScreen?.edge ?? 0}" required />
          </div>

          <div class="form-field">
            <label for="priceSanity">Price / Valuation Sanity</label>
            <input id="priceSanity" name="priceSanity" type="number" min="0" max="2" step="1" value="${quickScreen?.priceSanity ?? 0}" required />
          </div>

          <div class="form-field">
            <label for="trustTransparency">Trust / Transparency</label>
            <input id="trustTransparency" name="trustTransparency" type="number" min="0" max="2" step="1" value="${quickScreen?.trustTransparency ?? 0}" required />
          </div>

          <div class="form-field form-field--full">
            <label for="whatIsIt">What is it?</label>
            <textarea id="whatIsIt" name="whatIsIt" rows="3" required>${quickScreen?.whatIsIt ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="whyMightItWin">Why might it win?</label>
            <textarea id="whyMightItWin" name="whyMightItWin" rows="3" required>${quickScreen?.whyMightItWin ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="bestProofPoint">Best proof point</label>
            <textarea id="bestProofPoint" name="bestProofPoint" rows="3" required>${quickScreen?.bestProofPoint ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="biggestDoubt">Biggest doubt</label>
            <textarea id="biggestDoubt" name="biggestDoubt" rows="3" required>${quickScreen?.biggestDoubt ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="whySpendingTime">Why spend time on this?</label>
            <textarea id="whySpendingTime" name="whySpendingTime" rows="3" required>${quickScreen?.whySpendingTime ?? ''}</textarea>
          </div>

          <div class="form-actions">
            <button type="submit" class="button button--primary">Save Quick Screen</button>
          </div>
        </form>
      </div>

      <div class="card">
        <div class="page-header">
          <h3>Decision</h3>
          <p>Force a deliberate pass, watch, or invest-small decision.</p>
        </div>

        <form class="form-grid" id="decision-form" data-deal-id="${deal.id}">
          <div class="form-field">
            <label for="decisionStatus">Decision</label>
            <select id="decisionStatus" name="decisionStatus" required>
              <option value="watch" ${decision?.status === 'watch' ? 'selected' : ''}>Watch</option>
              <option value="pass" ${decision?.status === 'pass' ? 'selected' : ''}>Pass</option>
              <option value="invest-small" ${decision?.status === 'invest-small' ? 'selected' : ''}>Invest Small</option>
            </select>
          </div>

          <div class="form-field form-field--full">
            <label for="rationale">Rationale</label>
            <textarea id="rationale" name="rationale" rows="4" required>${decision?.rationale ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="whatWouldChangeMyMind">What would change your mind?</label>
            <textarea id="whatWouldChangeMyMind" name="whatWouldChangeMyMind" rows="4" required>${decision?.whatWouldChangeMyMind ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="nextMilestoneNeeded">Next milestone needed</label>
            <textarea id="nextMilestoneNeeded" name="nextMilestoneNeeded" rows="4" required>${decision?.nextMilestoneNeeded ?? ''}</textarea>
          </div>

          <div class="form-actions">
            <button type="submit" class="button button--primary">Save Decision</button>
          </div>
        </form>
      </div>

      <div class="card">
        <div class="page-header">
          <h3>Deep Diligence</h3>
          <p>Score the deal from 1 to 5 across the core diligence categories.</p>
        </div>

        <form class="form-grid" id="deep-diligence-form" data-deal-id="${deal.id}">
          <div class="form-field">
            <label for="businessModelScore">Business Model</label>
            <input id="businessModelScore" name="businessModelScore" type="number" min="1" max="5" step="1" value="${deepDiligence?.businessModelScore ?? 3}" required />
          </div>

          <div class="form-field">
            <label for="marketCustomerScore">Market / Customer</label>
            <input id="marketCustomerScore" name="marketCustomerScore" type="number" min="1" max="5" step="1" value="${deepDiligence?.marketCustomerScore ?? 3}" required />
          </div>

          <div class="form-field">
            <label for="tractionQualityScore">Traction Quality</label>
            <input id="tractionQualityScore" name="tractionQualityScore" type="number" min="1" max="5" step="1" value="${deepDiligence?.tractionQualityScore ?? 3}" required />
          </div>

          <div class="form-field">
            <label for="competitiveEdgeScore">Competitive Edge</label>
            <input id="competitiveEdgeScore" name="competitiveEdgeScore" type="number" min="1" max="5" step="1" value="${deepDiligence?.competitiveEdgeScore ?? 3}" required />
          </div>

          <div class="form-field">
            <label for="riskScore">Risk</label>
            <input id="riskScore" name="riskScore" type="number" min="1" max="5" step="1" value="${deepDiligence?.riskScore ?? 3}" required />
          </div>

          <div class="form-field form-field--full">
            <label for="businessModelNote">Business Model Note</label>
            <textarea id="businessModelNote" name="businessModelNote" rows="3" required>${deepDiligence?.businessModelNote ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="marketCustomerNote">Market / Customer Note</label>
            <textarea id="marketCustomerNote" name="marketCustomerNote" rows="3" required>${deepDiligence?.marketCustomerNote ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="tractionQualityNote">Traction Quality Note</label>
            <textarea id="tractionQualityNote" name="tractionQualityNote" rows="3" required>${deepDiligence?.tractionQualityNote ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="competitiveEdgeNote">Competitive Edge Note</label>
            <textarea id="competitiveEdgeNote" name="competitiveEdgeNote" rows="3" required>${deepDiligence?.competitiveEdgeNote ?? ''}</textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="riskNote">Risk Note</label>
            <textarea id="riskNote" name="riskNote" rows="3" required>${deepDiligence?.riskNote ?? ''}</textarea>
          </div>

          <div class="form-actions">
            <button type="submit" class="button button--primary">Save Deep Diligence</button>
          </div>
        </form>
      </div>

      <div class="card">
        <div class="page-header">
          <h3>Review Tracking</h3>
          <p>Track when to revisit the deal and whether the thesis is improving or weakening.</p>
        </div>

        <form class="form-grid" id="review-form" data-deal-id="${deal.id}">
          <div class="form-field">
            <label for="nextReviewDate">Next Review Date</label>
            <input
              id="nextReviewDate"
              name="nextReviewDate"
              type="date"
              value="${deal.review?.nextReviewDate ?? ''}"
              required
            />
          </div>

          <div class="form-field">
            <label for="thesisDirection">Thesis Direction</label>
            <select id="thesisDirection" name="thesisDirection" required>
              <option value="stronger" ${deal.review?.thesisDirection === 'stronger' ? 'selected' : ''}>Stronger</option>
              <option value="unchanged" ${deal.review?.thesisDirection === 'unchanged' ? 'selected' : ''}>Unchanged</option>
              <option value="weaker" ${deal.review?.thesisDirection === 'weaker' ? 'selected' : ''}>Weaker</option>
            </select>
          </div>

          <div class="form-field form-field--full">
            <label for="reviewNote">Review Note</label>
            <textarea
              id="reviewNote"
              name="reviewNote"
              rows="4"
              required
            >${deal.review?.reviewNote ?? ''}</textarea>
          </div>

          <div class="form-actions">
            <button type="submit" class="button button--primary">Save Review</button>
          </div>
        </form>
      </div>
    </div>
  `;
}

export function bindDealWorkspacePageEvents(root: HTMLElement, path: string): void {
  const dealId = path.split('/').pop() ?? '';
  const quickScreenForm = root.querySelector<HTMLFormElement>('#quick-screen-form');
  const decisionForm = root.querySelector<HTMLFormElement>('#decision-form');
  const deepDiligenceForm = root.querySelector<HTMLFormElement>('#deep-diligence-form');
  const reviewForm = root.querySelector<HTMLFormElement>('#review-form');

  if (quickScreenForm && dealId) {
    quickScreenForm.addEventListener('submit', (event) => {
      event.preventDefault();

      const formData = new FormData(quickScreenForm);

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

      if (!whatIsIt || !whyMightItWin || !bestProofPoint || !biggestDoubt || !whySpendingTime) {
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

  if (decisionForm && dealId) {
    decisionForm.addEventListener('submit', (event) => {
      event.preventDefault();

      const formData = new FormData(decisionForm);

      const status = String(formData.get('decisionStatus') ?? '').trim() as 'watch' | 'pass' | 'invest-small';
      const rationale = String(formData.get('rationale') ?? '').trim();
      const whatWouldChangeMyMind = String(formData.get('whatWouldChangeMyMind') ?? '').trim();
      const nextMilestoneNeeded = String(formData.get('nextMilestoneNeeded') ?? '').trim();

      if (!status || !rationale || !whatWouldChangeMyMind || !nextMilestoneNeeded) {
        window.alert('Please complete all decision fields.');
        return;
      }

      saveDecision({
        dealId,
        status,
        rationale,
        whatWouldChangeMyMind,
        nextMilestoneNeeded
      });

      navigateTo(`/deals/${dealId}`);
    });
  }

  if (deepDiligenceForm && dealId) {
    deepDiligenceForm.addEventListener('submit', (event) => {
      event.preventDefault();

      const formData = new FormData(deepDiligenceForm);

      const businessModelScore = Number(formData.get('businessModelScore'));
      const marketCustomerScore = Number(formData.get('marketCustomerScore'));
      const tractionQualityScore = Number(formData.get('tractionQualityScore'));
      const competitiveEdgeScore = Number(formData.get('competitiveEdgeScore'));
      const riskScore = Number(formData.get('riskScore'));

      const businessModelNote = String(formData.get('businessModelNote') ?? '').trim();
      const marketCustomerNote = String(formData.get('marketCustomerNote') ?? '').trim();
      const tractionQualityNote = String(formData.get('tractionQualityNote') ?? '').trim();
      const competitiveEdgeNote = String(formData.get('competitiveEdgeNote') ?? '').trim();
      const riskNote = String(formData.get('riskNote') ?? '').trim();

      const numericScores = [
        businessModelScore,
        marketCustomerScore,
        tractionQualityScore,
        competitiveEdgeScore,
        riskScore
      ];

      const scoresAreValid = numericScores.every(
        (score) => Number.isInteger(score) && score >= 1 && score <= 5
      );

      if (!scoresAreValid) {
        window.alert('Each deep-diligence score must be a whole number from 1 to 5.');
        return;
      }

      if (
        !businessModelNote ||
        !marketCustomerNote ||
        !tractionQualityNote ||
        !competitiveEdgeNote ||
        !riskNote
      ) {
        window.alert('Please complete all deep-diligence note fields.');
        return;
      }

      saveDeepDiligence({
        dealId,
        businessModelScore,
        businessModelNote,
        marketCustomerScore,
        marketCustomerNote,
        tractionQualityScore,
        tractionQualityNote,
        competitiveEdgeScore,
        competitiveEdgeNote,
        riskScore,
        riskNote
      });

      navigateTo(`/deals/${dealId}`);
    });
  }

  if (reviewForm && dealId) {
    reviewForm.addEventListener('submit', (event) => {
      event.preventDefault();

      const formData = new FormData(reviewForm);

      const nextReviewDate = String(formData.get('nextReviewDate') ?? '').trim();
      const thesisDirection = String(formData.get('thesisDirection') ?? '').trim() as
        | 'stronger'
        | 'weaker'
        | 'unchanged';
      const reviewNote = String(formData.get('reviewNote') ?? '').trim();

      if (!nextReviewDate || !thesisDirection || !reviewNote) {
        window.alert('Please complete all review fields.');
        return;
      }

      saveReview({
        dealId,
        nextReviewDate,
        thesisDirection,
        reviewNote
      });

      navigateTo(`/deals/${dealId}`);
    });
  }
}