import type {
  Deal,
  DealDecision,
  DealInput,
  DeepDiligenceInput,
  EvidenceClaimInput,
  QuickScreenInput,
  RedFlagKey,
  RedFlagMap,
  ReviewData
} from '../models/deal';
import { RED_FLAG_DEFINITIONS } from '../models/deal';
import {
  deleteDeal,
  deleteEvidenceClaim,
  acceptSuggestedRedFlags,
  getDealById,
  getDeepDiligenceOutcome,
  getFinalRecommendation,
  getFinalScore,
  getQuickScreenOutcome,
  getRedFlagCount,
  getRiskStatus,
  loadDealById,
  saveDecision,
  saveDeepDiligence,
  saveEvidenceClaim,
  saveQuickScreen,
  saveRedFlags,
  saveReview,
  ignoreSuggestedRedFlags,
  updateDeal
} from '../services/dealService';
import {
  getDataConfidenceLabel,
  getDataConfidenceScore,
  getDetectedRiskKeywords,
  getSuggestedRedFlags,
  hasUnscoredRiskLanguage
} from '../services/riskAnalysis';
import {
  formatCurrency,
  formatDate,
  formatDealStatus,
  formatEvidenceSourceType,
  formatEvidenceStrength,
  formatInvestorEligibility,
  formatLiquidity,
  formatOfferingExemption,
  formatRevenueStatus,
  formatSecurityType,
  formatThesisDirection
} from '../utils/formatters';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { navigateTo } from '../utils/router';

async function refreshWorkspaceDeal(dealId: number): Promise<void> {
  await loadDealById(dealId);
  window.dispatchEvent(new HashChangeEvent('hashchange'));
}

function selected(currentValue: string, optionValue: string): string {
  return currentValue === optionValue ? 'selected' : '';
}

function checked(value: boolean): string {
  return value ? 'checked' : '';
}

function getString(formData: FormData, key: keyof DealInput): string {
  return String(formData.get(key) ?? '').trim();
}

function getNumber(formData: FormData, key: keyof DealInput): number | undefined {
  const value = String(formData.get(key) ?? '').trim();
  if (!value) return undefined;

  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function getDealInput(formData: FormData): DealInput {
  return {
    companyName: getString(formData, 'companyName'),
    platform: getString(formData, 'platform'),
    sector: getString(formData, 'sector'),
    offeringUrl: getString(formData, 'offeringUrl'),
    minimumInvestment: getNumber(formData, 'minimumInvestment'),
    valuationOrCap: getString(formData, 'valuationOrCap'),
    amountRaised: getNumber(formData, 'amountRaised'),
    revenueStatus: getString(formData, 'revenueStatus') as DealInput['revenueStatus'],
    investorEligibility: getString(formData, 'investorEligibility') as DealInput['investorEligibility'],
    offeringExemption: getString(formData, 'offeringExemption') as DealInput['offeringExemption'],
    securityType: getString(formData, 'securityType') as DealInput['securityType'],
    liquidity: getString(formData, 'liquidity') as DealInput['liquidity'],
    lockupPeriod: getString(formData, 'lockupPeriod'),
    platformFees: getString(formData, 'platformFees'),
    thesis: getString(formData, 'thesis'),
    mainRisk: getString(formData, 'mainRisk'),
    nextMilestone: getString(formData, 'nextMilestone'),
    rawDealText: getString(formData, 'rawDealText'),
    decision: getString(formData, 'decision') as DealDecision,
    shortDescription: getString(formData, 'shortDescription')
  };
}

function renderLegalWarning(deal: Deal): string {
  if (deal.investorEligibility === 'ACCREDITED_ONLY' || deal.offeringExemption === 'REG_D') {
    return `
      <div class="notice notice--danger">
        This appears to be accredited-only or potentially unavailable to non-accredited investors.
        Confirm eligibility before sharing documents, SSN, bank info, or sending funds.
      </div>
    `;
  }

  if (deal.investorEligibility === 'UNCLEAR' || deal.offeringExemption === 'UNKNOWN') {
    return `
      <div class="notice notice--warning">
        Eligibility is unclear. Confirm whether this is Reg CF, Reg A, or another structure before investing.
      </div>
    `;
  }

  return '';
}

function renderRiskLanguageWarning(deal: Deal): string {
  if (!hasUnscoredRiskLanguage(deal)) return '';

  const keywords = getDetectedRiskKeywords(deal).slice(0, 8).join(', ');

  return `
    <div class="notice notice--warning">
      You entered major risk language, but no red flags are checked. Review suggested red flags before relying on this snapshot.
      ${keywords ? `<div class="notice__subtext">Detected: ${escapeHtml(keywords)}</div>` : ''}
    </div>
  `;
}

function renderDetailForm(deal: Deal): string {
  return `
    <div class="card">
      <div class="page-header">
        <h3>Edit Deal Details</h3>
        <p>Keep deal terms, eligibility, and the core thesis current.</p>
      </div>

      <form class="form-grid" id="edit-deal-form" data-deal-id="${deal.id}">
        <div class="form-field">
          <label for="companyName">Company Name</label>
          <input id="companyName" name="companyName" type="text" value="${escapeAttribute(deal.companyName)}" required />
        </div>

        <div class="form-field">
          <label for="platform">Platform</label>
          <input id="platform" name="platform" type="text" value="${escapeAttribute(deal.platform)}" required />
        </div>

        <div class="form-field">
          <label for="sector">Sector</label>
          <input id="sector" name="sector" type="text" value="${escapeAttribute(deal.sector)}" required />
        </div>

        <div class="form-field">
          <label for="offeringUrl">Website / Offering URL</label>
          <input id="offeringUrl" name="offeringUrl" type="url" value="${escapeAttribute(deal.offeringUrl)}" />
        </div>

        <div class="form-field">
          <label for="minimumInvestment">Minimum Investment</label>
          <input id="minimumInvestment" name="minimumInvestment" type="number" min="0" step="1" value="${deal.minimumInvestment ?? ''}" />
        </div>

        <div class="form-field">
          <label for="valuationOrCap">Valuation or Cap</label>
          <input id="valuationOrCap" name="valuationOrCap" type="text" value="${escapeAttribute(deal.valuationOrCap)}" />
        </div>

        <div class="form-field">
          <label for="amountRaised">Amount Raised</label>
          <input id="amountRaised" name="amountRaised" type="number" min="0" step="1" value="${deal.amountRaised ?? ''}" />
        </div>

        <div class="form-field">
          <label for="revenueStatus">Revenue Status</label>
          <select id="revenueStatus" name="revenueStatus">
            <option value="UNCLEAR" ${selected(deal.revenueStatus, 'UNCLEAR')}>Unclear</option>
            <option value="PRE_REVENUE" ${selected(deal.revenueStatus, 'PRE_REVENUE')}>No revenue / pre-revenue</option>
            <option value="EARLY_REVENUE" ${selected(deal.revenueStatus, 'EARLY_REVENUE')}>Early revenue</option>
            <option value="REVENUE" ${selected(deal.revenueStatus, 'REVENUE')}>Revenue</option>
          </select>
        </div>

        <div class="form-field">
          <label for="investorEligibility">Investor Eligibility</label>
          <select id="investorEligibility" name="investorEligibility">
            <option value="UNCLEAR" ${selected(deal.investorEligibility, 'UNCLEAR')}>Unclear</option>
            <option value="NON_ACCREDITED" ${selected(deal.investorEligibility, 'NON_ACCREDITED')}>Non-accredited</option>
            <option value="ACCREDITED_ONLY" ${selected(deal.investorEligibility, 'ACCREDITED_ONLY')}>Accredited only</option>
          </select>
        </div>

        <div class="form-field">
          <label for="offeringExemption">Offering Exemption</label>
          <select id="offeringExemption" name="offeringExemption">
            <option value="UNKNOWN" ${selected(deal.offeringExemption, 'UNKNOWN')}>Unknown</option>
            <option value="REG_CF" ${selected(deal.offeringExemption, 'REG_CF')}>Reg CF</option>
            <option value="REG_A" ${selected(deal.offeringExemption, 'REG_A')}>Reg A</option>
            <option value="REG_D" ${selected(deal.offeringExemption, 'REG_D')}>Reg D</option>
            <option value="OTHER" ${selected(deal.offeringExemption, 'OTHER')}>Other</option>
          </select>
        </div>

        <div class="form-field">
          <label for="securityType">Security Type</label>
          <select id="securityType" name="securityType">
            <option value="UNKNOWN" ${selected(deal.securityType, 'UNKNOWN')}>Unknown</option>
            <option value="SAFE" ${selected(deal.securityType, 'SAFE')}>SAFE</option>
            <option value="EQUITY" ${selected(deal.securityType, 'EQUITY')}>Equity</option>
            <option value="NOTE" ${selected(deal.securityType, 'NOTE')}>Note</option>
            <option value="REVENUE_SHARE" ${selected(deal.securityType, 'REVENUE_SHARE')}>Revenue Share</option>
            <option value="FUND_INTEREST" ${selected(deal.securityType, 'FUND_INTEREST')}>Fund Interest</option>
            <option value="SPV" ${selected(deal.securityType, 'SPV')}>SPV</option>
            <option value="OTHER" ${selected(deal.securityType, 'OTHER')}>Other</option>
          </select>
        </div>

        <div class="form-field">
          <label for="liquidity">Liquidity</label>
          <select id="liquidity" name="liquidity">
            <option value="UNKNOWN" ${selected(deal.liquidity, 'UNKNOWN')}>Unknown</option>
            <option value="ILLIQUID" ${selected(deal.liquidity, 'ILLIQUID')}>Illiquid</option>
            <option value="REDEMPTION_WINDOW" ${selected(deal.liquidity, 'REDEMPTION_WINDOW')}>Redemption window</option>
            <option value="SECONDARY_POSSIBLE" ${selected(deal.liquidity, 'SECONDARY_POSSIBLE')}>Secondary possible</option>
          </select>
        </div>

        <div class="form-field">
          <label for="lockupPeriod">Lockup Period</label>
          <input id="lockupPeriod" name="lockupPeriod" type="text" value="${escapeAttribute(deal.lockupPeriod)}" />
        </div>

        <div class="form-field">
          <label for="platformFees">Platform Fees</label>
          <input id="platformFees" name="platformFees" type="text" value="${escapeAttribute(deal.platformFees)}" />
        </div>

        <div class="form-field">
          <label for="decision">Decision</label>
          <select id="decision" name="decision">
            <option value="WATCH" ${selected(deal.decision, 'WATCH')}>Watch</option>
            <option value="PASS" ${selected(deal.decision, 'PASS')}>Pass</option>
            <option value="INVEST_SMALL" ${selected(deal.decision, 'INVEST_SMALL')}>Invest Small</option>
          </select>
        </div>

        <div class="form-field form-field--full">
          <label for="shortDescription">Deal Summary</label>
          <textarea id="shortDescription" name="shortDescription" rows="3" required>${escapeHtml(deal.shortDescription)}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="thesis">Thesis</label>
          <textarea id="thesis" name="thesis" rows="3">${escapeHtml(deal.thesis)}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="mainRisk">Main Risk</label>
          <textarea id="mainRisk" name="mainRisk" rows="3">${escapeHtml(deal.mainRisk)}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="nextMilestone">Next Milestone</label>
          <textarea id="nextMilestone" name="nextMilestone" rows="3">${escapeHtml(deal.nextMilestone)}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="rawDealText">Raw Deal Text / Notes</label>
          <textarea id="rawDealText" name="rawDealText" rows="5">${escapeHtml(deal.rawDealText)}</textarea>
        </div>

        <div class="form-actions">
          <button type="submit" class="button button--primary">Save Deal Details</button>
        </div>
      </form>
    </div>
  `;
}

function renderOverviewSection(deal: Deal): string {
  const finalScore = getFinalScore(deal);
  const recommendation = getFinalRecommendation(finalScore);
  const dataConfidence = getDataConfidenceScore(deal);
  const dataConfidenceLabel = getDataConfidenceLabel(dataConfidence);
  const riskStatus = getRiskStatus(deal);
  const redFlagCount = getRedFlagCount(deal);
  const offeringLink = deal.offeringUrl
    ? `<a class="table-link" href="${escapeAttribute(deal.offeringUrl)}" target="_blank" rel="noreferrer">Open offering</a>`
    : '-';

  return `
    <div class="card">
      <div class="page-header">
        <h3>Deal Snapshot</h3>
        <p>Scores, access risk, core terms, and the current non-advisory recommendation.</p>
      </div>

      <div class="overview-grid overview-grid--wide">
        <div class="overview-item">
          <div class="overview-label">Final Score</div>
          <div class="overview-value">${finalScore} / 100</div>
          <div class="overview-subtext">Investment attractiveness: ${escapeHtml(recommendation.label)}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Data Confidence</div>
          <div class="overview-value">${dataConfidence} / 100</div>
          <div class="overview-subtext">Evidence quality: ${escapeHtml(dataConfidenceLabel)}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Suggested Check Size</div>
          <div class="overview-value">${escapeHtml(recommendation.suggestedCheckSize)}</div>
          <div class="overview-subtext">Not financial advice</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Risk Status</div>
          <div class="overview-value">
            <span class="risk-chip risk-chip--${riskStatus.tone}">${riskStatus.label}</span>
          </div>
          <div class="overview-subtext">${redFlagCount} red flags checked</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Decision</div>
          <div class="overview-value">${formatDealStatus(deal.decision)}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Minimum Investment</div>
          <div class="overview-value">${formatCurrency(deal.minimumInvestment)}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Valuation / Cap</div>
          <div class="overview-value">${escapeHtml(deal.valuationOrCap || '-')}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Amount Raised</div>
          <div class="overview-value">${formatCurrency(deal.amountRaised)}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Offering</div>
          <div class="overview-value overview-value--small">${offeringLink}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Eligibility</div>
          <div class="overview-value">${formatInvestorEligibility(deal.investorEligibility)}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Exemption</div>
          <div class="overview-value">${formatOfferingExemption(deal.offeringExemption)}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Security</div>
          <div class="overview-value">${formatSecurityType(deal.securityType)}</div>
        </div>

        <div class="overview-item">
          <div class="overview-label">Liquidity</div>
          <div class="overview-value">${formatLiquidity(deal.liquidity)}</div>
        </div>
      </div>

      <div class="summary-grid">
        <div class="summary-block">
          <h4>Thesis</h4>
          <p>${escapeHtml(deal.thesis || 'No thesis captured yet.')}</p>
        </div>

        <div class="summary-block">
          <h4>Main Risk</h4>
          <p>${escapeHtml(deal.mainRisk || 'No main risk captured yet.')}</p>
        </div>

        <div class="summary-block">
          <h4>Next Milestone</h4>
          <p>${escapeHtml(deal.nextMilestone || 'No next milestone defined yet.')}</p>
        </div>

        <div class="summary-block">
          <h4>Review Status</h4>
          <p>${
            deal.review
              ? `Next review: ${escapeHtml(formatDate(deal.review.nextReviewDate))}<br />Thesis: ${formatThesisDirection(deal.review.thesisDirection)}<br /><br />${escapeHtml(deal.review.reviewNote)}`
              : 'No review scheduled yet.'
          }</p>
        </div>
      </div>

      <div class="notice notice--neutral notice--compact">
        The recommendation and check-size ranges are simple personal diligence guardrails, not financial advice.
      </div>
    </div>
  `;
}

function renderScoreCards(deal: Deal): string {
  const quickOutcome = getQuickScreenOutcome(deal.quickScore);
  const deepOutcome =
    typeof deal.deepScore === 'number'
      ? getDeepDiligenceOutcome(deal.deepScore)
      : 'Not scored';

  return `
    <div class="card-grid">
      <div class="card">
        <h3>Quick Score</h3>
        <p class="metric">${deal.quickScore} / 10</p>
        <p class="metric-subtext">${escapeHtml(quickOutcome)}</p>
      </div>

      <div class="card">
        <h3>Deep Score</h3>
        <p class="metric">${deal.deepScore ?? '-'}${typeof deal.deepScore === 'number' ? ' / 25' : ''}</p>
        <p class="metric-subtext">${escapeHtml(deepOutcome)}</p>
      </div>

      <div class="card">
        <h3>Revenue</h3>
        <p class="metric metric--text">${formatRevenueStatus(deal.revenueStatus)}</p>
      </div>

      <div class="card">
        <h3>Fees</h3>
        <p class="metric metric--text">${escapeHtml(deal.platformFees || 'Unknown')}</p>
      </div>
    </div>
  `;
}

function renderRedFlagsSection(deal: Deal): string {
  const riskStatus = getRiskStatus(deal);
  const redFlagCount = getRedFlagCount(deal);
  const redFlagItems = RED_FLAG_DEFINITIONS.map(
    (definition) => `
      <label class="checklist-item">
        <input
          type="checkbox"
          name="${definition.key}"
          ${checked(deal.redFlags[definition.key])}
        />
        <span>${escapeHtml(definition.label)}</span>
      </label>
    `
  ).join('');

  return `
    <div class="card">
      <div class="page-header page-header--row">
        <div>
          <h3>Red Flags</h3>
          <p>Check anything that should slow down or stop the deal.</p>
        </div>

        <span class="risk-chip risk-chip--${riskStatus.tone}">
          ${riskStatus.label}: ${redFlagCount}
        </span>
      </div>

      <form id="red-flags-form" class="stacked-form" data-deal-id="${deal.id}">
        <div class="checklist-grid">
          ${redFlagItems}
        </div>

        <div class="form-actions">
          <button type="submit" class="button button--primary">Save Red Flags</button>
        </div>
      </form>
    </div>
  `;
}

function renderSuggestedRedFlagsSection(deal: Deal): string {
  const suggestions = getSuggestedRedFlags(deal);

  if (!suggestions.length) {
    return `
      <div class="card">
        <div class="page-header">
          <h3>Suggested Red Flags</h3>
          <p>No unreviewed suggestions from the current text and evidence.</p>
        </div>
      </div>
    `;
  }

  return `
    <div class="card">
      <div class="page-header">
        <h3>Suggested Red Flags</h3>
        <p>Detected from pasted text, notes, terms, and evidence. Accept or ignore manually.</p>
      </div>

      <form id="suggested-red-flags-form" class="stacked-form" data-deal-id="${deal.id}">
        <div class="suggestion-list">
          ${suggestions
            .map(
              (suggestion) => `
                <label class="suggestion-item">
                  <input type="checkbox" name="suggestedRedFlag" value="${suggestion.key}" checked />
                  <span>
                    <strong>${escapeHtml(suggestion.label)}</strong>
                    <small>${escapeHtml(suggestion.reason)}</small>
                    <em>Matched: ${escapeHtml(suggestion.matchedKeywords.join(', '))}</em>
                  </span>
                </label>
              `
            )
            .join('')}
        </div>

        <div class="form-actions form-actions--split">
          <button type="submit" class="button button--primary" data-action="accept">Accept Selected</button>
          <button type="submit" class="button button--secondary" data-action="ignore">Ignore Selected</button>
        </div>
      </form>
    </div>
  `;
}

function renderEvidenceSection(deal: Deal): string {
  const rows = deal.evidenceClaims.length
    ? deal.evidenceClaims
        .map(
          (claim) => `
            <tr>
              <td>
                <strong>${escapeHtml(claim.claim)}</strong>
                <div class="table-subtext">${escapeHtml(claim.notes || 'No notes.')}</div>
              </td>
              <td>${formatEvidenceSourceType(claim.sourceType)}</td>
              <td>${formatEvidenceStrength(claim.evidenceStrength)}</td>
              <td>${claim.verified ? 'Yes' : 'No'}</td>
              <td>${escapeHtml(claim.sourceText || '-')}</td>
              <td>
                <button
                  class="button button--danger button--small"
                  type="button"
                  data-delete-claim-id="${claim.id}"
                >
                  Delete
                </button>
              </td>
            </tr>
          `
        )
        .join('')
    : `
      <tr>
        <td colspan="6">No evidence claims yet. Add claims from campaign pages, filings, founder statements, or your notes.</td>
      </tr>
    `;

  return `
    <div class="card">
      <div class="page-header">
        <h3>Evidence / Claim Tracker</h3>
        <p>Track what the deal claims, where it came from, and how strong the support is.</p>
      </div>

      <form class="form-grid" id="evidence-form" data-deal-id="${deal.id}">
        <div class="form-field form-field--full">
          <label for="claim">Claim</label>
          <textarea id="claim" name="claim" rows="2" required placeholder="Example: Company says it has $500k ARR."></textarea>
        </div>

        <div class="form-field">
          <label for="sourceType">Source Type</label>
          <select id="sourceType" name="sourceType">
            <option value="CAMPAIGN_PAGE">Campaign Page</option>
            <option value="FORM_C">Form C</option>
            <option value="OFFERING_CIRCULAR">Offering Circular</option>
            <option value="FOUNDER_STATEMENT">Founder Statement</option>
            <option value="PRESS">Press</option>
            <option value="USER_NOTE">User Note</option>
            <option value="OTHER">Other</option>
          </select>
        </div>

        <div class="form-field">
          <label for="evidenceStrength">Evidence Strength</label>
          <select id="evidenceStrength" name="evidenceStrength">
            <option value="MISSING">Missing</option>
            <option value="WEAK">Weak</option>
            <option value="MEDIUM">Medium</option>
            <option value="STRONG">Strong</option>
          </select>
        </div>

        <label class="toggle-field">
          <input id="verified" name="verified" type="checkbox" />
          <span>Verified against source</span>
        </label>

        <div class="form-field form-field--full">
          <label for="sourceText">Source Text</label>
          <textarea id="sourceText" name="sourceText" rows="3" placeholder="Paste the exact sentence or excerpt supporting the claim."></textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="notes">Notes</label>
          <textarea id="notes" name="notes" rows="3" placeholder="What still needs verification?"></textarea>
        </div>

        <div class="form-actions">
          <button type="submit" class="button button--primary">Add Evidence Claim</button>
        </div>
      </form>

      <div class="evidence-table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>Claim</th>
              <th>Source</th>
              <th>Strength</th>
              <th>Verified</th>
              <th>Source Text</th>
              <th>Action</th>
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

function renderQuickScreenSection(deal: Deal): string {
  const quickScreen = deal.quickScreen;

  return `
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
          <textarea id="whatIsIt" name="whatIsIt" rows="3" required>${escapeHtml(quickScreen?.whatIsIt ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="whyMightItWin">Why might it win?</label>
          <textarea id="whyMightItWin" name="whyMightItWin" rows="3" required>${escapeHtml(quickScreen?.whyMightItWin ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="bestProofPoint">Best proof point</label>
          <textarea id="bestProofPoint" name="bestProofPoint" rows="3" required>${escapeHtml(quickScreen?.bestProofPoint ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="biggestDoubt">Biggest doubt</label>
          <textarea id="biggestDoubt" name="biggestDoubt" rows="3" required>${escapeHtml(quickScreen?.biggestDoubt ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="whySpendingTime">Why spend time on this?</label>
          <textarea id="whySpendingTime" name="whySpendingTime" rows="3" required>${escapeHtml(quickScreen?.whySpendingTime ?? '')}</textarea>
        </div>

        <div class="form-actions">
          <button type="submit" class="button button--primary">Save Quick Screen</button>
        </div>
      </form>
    </div>
  `;
}

function renderDecisionSection(deal: Deal): string {
  const decisionNotes = deal.decisionNotes;

  return `
    <div class="card">
      <div class="page-header">
        <h3>Decision</h3>
        <p>Force a deliberate pass, watch, or invest-small call.</p>
      </div>

      <form class="form-grid" id="decision-form" data-deal-id="${deal.id}">
        <div class="form-field">
          <label for="decisionStatus">Decision</label>
          <select id="decisionStatus" name="decisionStatus" required>
            <option value="WATCH" ${selected(deal.decision, 'WATCH')}>Watch</option>
            <option value="PASS" ${selected(deal.decision, 'PASS')}>Pass</option>
            <option value="INVEST_SMALL" ${selected(deal.decision, 'INVEST_SMALL')}>Invest Small</option>
          </select>
        </div>

        <div class="form-field form-field--full">
          <label for="rationale">Rationale</label>
          <textarea id="rationale" name="rationale" rows="4" required>${escapeHtml(decisionNotes?.rationale ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="whatWouldChangeMyMind">What would change your mind?</label>
          <textarea id="whatWouldChangeMyMind" name="whatWouldChangeMyMind" rows="4" required>${escapeHtml(decisionNotes?.whatWouldChangeMyMind ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="nextMilestoneNeeded">Next milestone needed</label>
          <textarea id="nextMilestoneNeeded" name="nextMilestoneNeeded" rows="4" required>${escapeHtml(decisionNotes?.nextMilestoneNeeded ?? deal.nextMilestone)}</textarea>
        </div>

        <div class="form-actions">
          <button type="submit" class="button button--primary">Save Decision</button>
        </div>
      </form>
    </div>
  `;
}

function renderDeepDiligenceSection(deal: Deal): string {
  const deepDiligence = deal.deepDiligence;

  return `
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
          <textarea id="businessModelNote" name="businessModelNote" rows="3" required>${escapeHtml(deepDiligence?.businessModelNote ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="marketCustomerNote">Market / Customer Note</label>
          <textarea id="marketCustomerNote" name="marketCustomerNote" rows="3" required>${escapeHtml(deepDiligence?.marketCustomerNote ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="tractionQualityNote">Traction Quality Note</label>
          <textarea id="tractionQualityNote" name="tractionQualityNote" rows="3" required>${escapeHtml(deepDiligence?.tractionQualityNote ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="competitiveEdgeNote">Competitive Edge Note</label>
          <textarea id="competitiveEdgeNote" name="competitiveEdgeNote" rows="3" required>${escapeHtml(deepDiligence?.competitiveEdgeNote ?? '')}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="riskNote">Risk Note</label>
          <textarea id="riskNote" name="riskNote" rows="3" required>${escapeHtml(deepDiligence?.riskNote ?? '')}</textarea>
        </div>

        <div class="form-actions">
          <button type="submit" class="button button--primary">Save Deep Diligence</button>
        </div>
      </form>
    </div>
  `;
}

function renderReviewSection(deal: Deal): string {
  return `
    <div class="card">
      <div class="page-header">
        <h3>Review Tracking</h3>
        <p>Track when to revisit the deal and whether the thesis is improving or weakening.</p>
      </div>

      <form class="form-grid" id="review-form" data-deal-id="${deal.id}">
        <div class="form-field">
          <label for="nextReviewDate">Next Review Date</label>
          <input id="nextReviewDate" name="nextReviewDate" type="date" value="${escapeAttribute(deal.review?.nextReviewDate ?? '')}" required />
        </div>

        <div class="form-field">
          <label for="thesisDirection">Thesis Direction</label>
          <select id="thesisDirection" name="thesisDirection" required>
            <option value="STRONGER" ${selected(deal.review?.thesisDirection ?? '', 'STRONGER')}>Stronger</option>
            <option value="UNCHANGED" ${selected(deal.review?.thesisDirection ?? 'UNCHANGED', 'UNCHANGED')}>Unchanged</option>
            <option value="WEAKER" ${selected(deal.review?.thesisDirection ?? '', 'WEAKER')}>Weaker</option>
          </select>
        </div>

        <div class="form-field form-field--full">
          <label for="reviewNote">Review Note</label>
          <textarea id="reviewNote" name="reviewNote" rows="4" required>${escapeHtml(deal.review?.reviewNote ?? '')}</textarea>
        </div>

        <div class="form-actions">
          <button type="submit" class="button button--primary">Save Review</button>
        </div>
      </form>
    </div>
  `;
}

export function renderDealWorkspacePage(path: string): string {
  const id = Number(path.split('/').pop() ?? '');
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
          <h2>${escapeHtml(deal.companyName)}</h2>
          <p>
            ${escapeHtml(deal.sector || 'Unknown sector')}
            &bull;
            ${escapeHtml(deal.platform || 'Unknown platform')}
            &bull;
            ${formatSecurityType(deal.securityType)}
          </p>
        </div>

        <div class="workspace-actions">
          <span class="status-chip status-chip--${deal.decision.toLowerCase().replace('_', '-')}">
            ${formatDealStatus(deal.decision)}
          </span>
          <button type="button" class="button button--danger" id="delete-deal-button">
            Delete Deal
          </button>
        </div>
      </div>

      ${renderLegalWarning(deal)}
      ${renderRiskLanguageWarning(deal)}

      <div class="card">
        <h3>Description</h3>
        <p>${escapeHtml(deal.shortDescription)}</p>
      </div>

      ${renderOverviewSection(deal)}
      ${renderScoreCards(deal)}
      ${renderRedFlagsSection(deal)}
      ${renderSuggestedRedFlagsSection(deal)}
      ${renderEvidenceSection(deal)}
      ${renderDetailForm(deal)}
      ${renderQuickScreenSection(deal)}
      ${renderDecisionSection(deal)}
      ${renderDeepDiligenceSection(deal)}
      ${renderReviewSection(deal)}
    </div>
  `;
}

export function bindDealWorkspacePageEvents(root: HTMLElement, path: string): void {
  const dealId = Number(path.split('/').pop() ?? '');
  const editDealForm = root.querySelector<HTMLFormElement>('#edit-deal-form');
  const deleteDealButton = root.querySelector<HTMLButtonElement>('#delete-deal-button');
  const redFlagsForm = root.querySelector<HTMLFormElement>('#red-flags-form');
  const suggestedRedFlagsForm = root.querySelector<HTMLFormElement>('#suggested-red-flags-form');
  const evidenceForm = root.querySelector<HTMLFormElement>('#evidence-form');
  const quickScreenForm = root.querySelector<HTMLFormElement>('#quick-screen-form');
  const decisionForm = root.querySelector<HTMLFormElement>('#decision-form');
  const deepDiligenceForm = root.querySelector<HTMLFormElement>('#deep-diligence-form');
  const reviewForm = root.querySelector<HTMLFormElement>('#review-form');

  if (editDealForm && dealId) {
    editDealForm.addEventListener('submit', async (event) => {
      event.preventDefault();

      const input = getDealInput(new FormData(editDealForm));

      if (!input.companyName || !input.platform || !input.sector || !input.shortDescription) {
        window.alert('Please complete company name, platform, sector, and deal summary.');
        return;
      }

      try {
        await updateDeal(dealId, input);
        await refreshWorkspaceDeal(dealId);
      } catch (error) {
        console.error('Failed to update deal:', error);
        window.alert('Failed to update deal details.');
      }
    });
  }

  if (deleteDealButton && dealId) {
    deleteDealButton.addEventListener('click', async () => {
      const confirmed = window.confirm(
        'Delete this deal and all associated diligence data? This cannot be undone.'
      );

      if (!confirmed) return;

      try {
        await deleteDeal(dealId);
        navigateTo('/deals');
      } catch (error) {
        console.error('Failed to delete deal:', error);
        window.alert('Failed to delete deal.');
      }
    });
  }

  if (redFlagsForm && dealId) {
    redFlagsForm.addEventListener('submit', async (event) => {
      event.preventDefault();

      const formData = new FormData(redFlagsForm);
      const redFlags = RED_FLAG_DEFINITIONS.reduce((flags, definition) => {
        flags[definition.key] = formData.has(definition.key);
        return flags;
      }, {} as RedFlagMap);

      try {
        await saveRedFlags(dealId, redFlags);
        await refreshWorkspaceDeal(dealId);
      } catch (error) {
        console.error('Failed to save red flags:', error);
        window.alert('Failed to save red flags.');
      }
    });
  }

  if (suggestedRedFlagsForm && dealId) {
    suggestedRedFlagsForm.addEventListener('submit', async (event) => {
      event.preventDefault();

      const submitter = event.submitter as HTMLButtonElement | null;
      const action = submitter?.dataset.action ?? 'accept';
      const formData = new FormData(suggestedRedFlagsForm);
      const redFlagKeys = formData
        .getAll('suggestedRedFlag')
        .map((value) => String(value)) as RedFlagKey[];

      if (!redFlagKeys.length) {
        window.alert('Select at least one suggested red flag.');
        return;
      }

      try {
        if (action === 'ignore') {
          await ignoreSuggestedRedFlags(dealId, redFlagKeys);
        } else {
          await acceptSuggestedRedFlags(dealId, redFlagKeys);
        }

        await refreshWorkspaceDeal(dealId);
      } catch (error) {
        console.error('Failed to update suggested red flags:', error);
        window.alert('Failed to update suggested red flags.');
      }
    });
  }

  if (evidenceForm && dealId) {
    evidenceForm.addEventListener('submit', async (event) => {
      event.preventDefault();

      const formData = new FormData(evidenceForm);
      const input: EvidenceClaimInput = {
        claim: String(formData.get('claim') ?? '').trim(),
        sourceType: String(formData.get('sourceType') ?? 'USER_NOTE') as EvidenceClaimInput['sourceType'],
        sourceText: String(formData.get('sourceText') ?? '').trim(),
        evidenceStrength: String(
          formData.get('evidenceStrength') ?? 'MISSING'
        ) as EvidenceClaimInput['evidenceStrength'],
        verified: formData.has('verified'),
        notes: String(formData.get('notes') ?? '').trim()
      };

      if (!input.claim) {
        window.alert('Please enter a claim.');
        return;
      }

      try {
        await saveEvidenceClaim(dealId, input);
        await refreshWorkspaceDeal(dealId);
      } catch (error) {
        console.error('Failed to save evidence claim:', error);
        window.alert('Failed to save evidence claim.');
      }
    });
  }

  root.querySelectorAll<HTMLButtonElement>('[data-delete-claim-id]').forEach((button) => {
    button.addEventListener('click', async () => {
      const claimId = Number(button.dataset.deleteClaimId);
      if (!dealId || !claimId) return;

      const confirmed = window.confirm('Delete this evidence claim?');
      if (!confirmed) return;

      try {
        await deleteEvidenceClaim(dealId, claimId);
        await refreshWorkspaceDeal(dealId);
      } catch (error) {
        console.error('Failed to delete evidence claim:', error);
        window.alert('Failed to delete evidence claim.');
      }
    });
  });

  if (quickScreenForm && dealId) {
    quickScreenForm.addEventListener('submit', async (event) => {
      event.preventDefault();

      const formData = new FormData(quickScreenForm);
      const input: QuickScreenInput = {
        businessClarity: Number(formData.get('businessClarity')),
        tractionEvidence: Number(formData.get('tractionEvidence')),
        edge: Number(formData.get('edge')),
        priceSanity: Number(formData.get('priceSanity')),
        trustTransparency: Number(formData.get('trustTransparency')),
        whatIsIt: String(formData.get('whatIsIt') ?? '').trim(),
        whyMightItWin: String(formData.get('whyMightItWin') ?? '').trim(),
        bestProofPoint: String(formData.get('bestProofPoint') ?? '').trim(),
        biggestDoubt: String(formData.get('biggestDoubt') ?? '').trim(),
        whySpendingTime: String(formData.get('whySpendingTime') ?? '').trim()
      };

      const numericScores = [
        input.businessClarity,
        input.tractionEvidence,
        input.edge,
        input.priceSanity,
        input.trustTransparency
      ];

      const scoresAreValid = numericScores.every(
        (score) => Number.isInteger(score) && score >= 0 && score <= 2
      );

      if (!scoresAreValid) {
        window.alert('Each quick-screen score must be a whole number from 0 to 2.');
        return;
      }

      if (
        !input.whatIsIt ||
        !input.whyMightItWin ||
        !input.bestProofPoint ||
        !input.biggestDoubt ||
        !input.whySpendingTime
      ) {
        window.alert('Please complete all quick-screen note fields.');
        return;
      }

      try {
        await saveQuickScreen(dealId, input);
        await refreshWorkspaceDeal(dealId);
      } catch (error) {
        console.error('Failed to save quick screen:', error);
        window.alert('Failed to save quick screen.');
      }
    });
  }

  if (decisionForm && dealId) {
    decisionForm.addEventListener('submit', async (event) => {
      event.preventDefault();

      const formData = new FormData(decisionForm);
      const decision = String(formData.get('decisionStatus') ?? '').trim() as DealDecision;
      const rationale = String(formData.get('rationale') ?? '').trim();
      const whatWouldChangeMyMind = String(formData.get('whatWouldChangeMyMind') ?? '').trim();
      const nextMilestoneNeeded = String(formData.get('nextMilestoneNeeded') ?? '').trim();

      if (!decision || !rationale || !whatWouldChangeMyMind || !nextMilestoneNeeded) {
        window.alert('Please complete all decision fields.');
        return;
      }

      try {
        await saveDecision(dealId, {
          decision,
          rationale,
          whatWouldChangeMyMind,
          nextMilestoneNeeded
        });

        await refreshWorkspaceDeal(dealId);
      } catch (error) {
        console.error('Failed to save decision:', error);
        window.alert('Failed to save decision.');
      }
    });
  }

  if (deepDiligenceForm && dealId) {
    deepDiligenceForm.addEventListener('submit', async (event) => {
      event.preventDefault();

      const formData = new FormData(deepDiligenceForm);
      const input: DeepDiligenceInput = {
        businessModelScore: Number(formData.get('businessModelScore')),
        businessModelNote: String(formData.get('businessModelNote') ?? '').trim(),
        marketCustomerScore: Number(formData.get('marketCustomerScore')),
        marketCustomerNote: String(formData.get('marketCustomerNote') ?? '').trim(),
        tractionQualityScore: Number(formData.get('tractionQualityScore')),
        tractionQualityNote: String(formData.get('tractionQualityNote') ?? '').trim(),
        competitiveEdgeScore: Number(formData.get('competitiveEdgeScore')),
        competitiveEdgeNote: String(formData.get('competitiveEdgeNote') ?? '').trim(),
        riskScore: Number(formData.get('riskScore')),
        riskNote: String(formData.get('riskNote') ?? '').trim()
      };

      const numericScores = [
        input.businessModelScore,
        input.marketCustomerScore,
        input.tractionQualityScore,
        input.competitiveEdgeScore,
        input.riskScore
      ];

      const scoresAreValid = numericScores.every(
        (score) => Number.isInteger(score) && score >= 1 && score <= 5
      );

      if (!scoresAreValid) {
        window.alert('Each deep-diligence score must be a whole number from 1 to 5.');
        return;
      }

      if (
        !input.businessModelNote ||
        !input.marketCustomerNote ||
        !input.tractionQualityNote ||
        !input.competitiveEdgeNote ||
        !input.riskNote
      ) {
        window.alert('Please complete all deep-diligence note fields.');
        return;
      }

      try {
        await saveDeepDiligence(dealId, input);
        await refreshWorkspaceDeal(dealId);
      } catch (error) {
        console.error('Failed to save deep diligence:', error);
        window.alert('Failed to save deep diligence.');
      }
    });
  }

  if (reviewForm && dealId) {
    reviewForm.addEventListener('submit', async (event) => {
      event.preventDefault();

      const formData = new FormData(reviewForm);
      const input: ReviewData = {
        nextReviewDate: String(formData.get('nextReviewDate') ?? '').trim(),
        thesisDirection: String(formData.get('thesisDirection') ?? '').trim() as ReviewData['thesisDirection'],
        reviewNote: String(formData.get('reviewNote') ?? '').trim()
      };

      if (!input.nextReviewDate || !input.thesisDirection || !input.reviewNote) {
        window.alert('Please complete all review fields.');
        return;
      }

      try {
        await saveReview(dealId, input);
        await refreshWorkspaceDeal(dealId);
      } catch (error) {
        console.error('Failed to save review:', error);
        window.alert('Failed to save review.');
      }
    });
  }
}
