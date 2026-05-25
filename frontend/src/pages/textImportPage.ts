import type { DealInput } from '../models/deal';
import { parseDealText, type ParsedDealText } from '../services/dealTextParser';
import { createDeal } from '../services/dealService';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { navigateTo } from '../utils/router';

function selected(currentValue: string, optionValue: string): string {
  return currentValue === optionValue ? 'selected' : '';
}

function fieldList(fields: string[]): string {
  if (!fields.length) return '<p class="empty-copy">No structured fields detected yet.</p>';

  return `
    <div class="suggestion-tags">
      ${fields.map((field) => `<span>${escapeHtml(field)}</span>`).join('')}
    </div>
  `;
}

function riskSnippetList(snippets: string[]): string {
  if (!snippets.length) return '<p class="empty-copy">No obvious risk snippets detected.</p>';

  return `
    <div class="mini-list">
      ${snippets
        .map(
          (snippet) => `
            <div class="mini-list__item mini-list__item--stacked">
              <span>${escapeHtml(snippet)}</span>
            </div>
          `
        )
        .join('')}
    </div>
  `;
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
    decision: getString(formData, 'decision') as DealInput['decision'],
    shortDescription: getString(formData, 'shortDescription')
  };
}

function renderSuggestionForm(parsed: ParsedDealText): string {
  const input = parsed.input;

  return `
    <div class="split-grid">
      <div class="card">
        <div class="page-header">
          <h3>Detected Fields</h3>
          <p>These are simple parser guesses. Review before saving.</p>
        </div>
        ${fieldList(parsed.detectedFields)}
      </div>

      <div class="card">
        <div class="page-header">
          <h3>Risk Snippets</h3>
          <p>Language that may deserve red flags or evidence checks.</p>
        </div>
        ${riskSnippetList(parsed.riskSnippets)}
      </div>
    </div>

    <div class="card">
      <div class="page-header">
        <h3>Review Suggested Deal</h3>
        <p>Edit anything the parser got wrong, then create the deal.</p>
      </div>

      <form class="form-grid" id="import-approval-form">
        <input name="rawDealText" type="hidden" value="${escapeAttribute(input.rawDealText ?? '')}" />

        <div class="form-field">
          <label for="companyName">Company Name</label>
          <input id="companyName" name="companyName" type="text" value="${escapeAttribute(input.companyName)}" required />
        </div>

        <div class="form-field">
          <label for="platform">Platform</label>
          <input id="platform" name="platform" type="text" value="${escapeAttribute(input.platform)}" required />
        </div>

        <div class="form-field">
          <label for="sector">Sector</label>
          <input id="sector" name="sector" type="text" value="${escapeAttribute(input.sector)}" required />
        </div>

        <div class="form-field">
          <label for="offeringUrl">Website / Offering URL</label>
          <input id="offeringUrl" name="offeringUrl" type="url" value="${escapeAttribute(input.offeringUrl)}" />
        </div>

        <div class="form-field">
          <label for="minimumInvestment">Minimum Investment</label>
          <input id="minimumInvestment" name="minimumInvestment" type="number" min="0" step="1" value="${input.minimumInvestment ?? ''}" />
        </div>

        <div class="form-field">
          <label for="valuationOrCap">Valuation or Cap</label>
          <input id="valuationOrCap" name="valuationOrCap" type="text" value="${escapeAttribute(input.valuationOrCap)}" />
        </div>

        <div class="form-field">
          <label for="amountRaised">Amount Raised</label>
          <input id="amountRaised" name="amountRaised" type="number" min="0" step="1" value="${input.amountRaised ?? ''}" />
        </div>

        <div class="form-field">
          <label for="revenueStatus">Revenue Status</label>
          <select id="revenueStatus" name="revenueStatus">
            <option value="UNCLEAR" ${selected(input.revenueStatus, 'UNCLEAR')}>Unclear</option>
            <option value="PRE_REVENUE" ${selected(input.revenueStatus, 'PRE_REVENUE')}>No revenue / pre-revenue</option>
            <option value="EARLY_REVENUE" ${selected(input.revenueStatus, 'EARLY_REVENUE')}>Early revenue</option>
            <option value="REVENUE" ${selected(input.revenueStatus, 'REVENUE')}>Revenue</option>
          </select>
        </div>

        <div class="form-field">
          <label for="investorEligibility">Investor Eligibility</label>
          <select id="investorEligibility" name="investorEligibility">
            <option value="UNCLEAR" ${selected(input.investorEligibility, 'UNCLEAR')}>Unclear</option>
            <option value="NON_ACCREDITED" ${selected(input.investorEligibility, 'NON_ACCREDITED')}>Non-accredited</option>
            <option value="ACCREDITED_ONLY" ${selected(input.investorEligibility, 'ACCREDITED_ONLY')}>Accredited only</option>
          </select>
        </div>

        <div class="form-field">
          <label for="offeringExemption">Offering Exemption</label>
          <select id="offeringExemption" name="offeringExemption">
            <option value="UNKNOWN" ${selected(input.offeringExemption, 'UNKNOWN')}>Unknown</option>
            <option value="REG_CF" ${selected(input.offeringExemption, 'REG_CF')}>Reg CF</option>
            <option value="REG_A" ${selected(input.offeringExemption, 'REG_A')}>Reg A</option>
            <option value="REG_D" ${selected(input.offeringExemption, 'REG_D')}>Reg D</option>
            <option value="OTHER" ${selected(input.offeringExemption, 'OTHER')}>Other</option>
          </select>
        </div>

        <div class="form-field">
          <label for="securityType">Security Type</label>
          <select id="securityType" name="securityType">
            <option value="UNKNOWN" ${selected(input.securityType, 'UNKNOWN')}>Unknown</option>
            <option value="SAFE" ${selected(input.securityType, 'SAFE')}>SAFE</option>
            <option value="EQUITY" ${selected(input.securityType, 'EQUITY')}>Equity</option>
            <option value="NOTE" ${selected(input.securityType, 'NOTE')}>Note</option>
            <option value="REVENUE_SHARE" ${selected(input.securityType, 'REVENUE_SHARE')}>Revenue Share</option>
            <option value="FUND_INTEREST" ${selected(input.securityType, 'FUND_INTEREST')}>Fund Interest</option>
            <option value="SPV" ${selected(input.securityType, 'SPV')}>SPV</option>
            <option value="OTHER" ${selected(input.securityType, 'OTHER')}>Other</option>
          </select>
        </div>

        <div class="form-field">
          <label for="liquidity">Liquidity</label>
          <select id="liquidity" name="liquidity">
            <option value="UNKNOWN" ${selected(input.liquidity, 'UNKNOWN')}>Unknown</option>
            <option value="ILLIQUID" ${selected(input.liquidity, 'ILLIQUID')}>Illiquid</option>
            <option value="REDEMPTION_WINDOW" ${selected(input.liquidity, 'REDEMPTION_WINDOW')}>Redemption window</option>
            <option value="SECONDARY_POSSIBLE" ${selected(input.liquidity, 'SECONDARY_POSSIBLE')}>Secondary possible</option>
          </select>
        </div>

        <div class="form-field">
          <label for="lockupPeriod">Lockup Period</label>
          <input id="lockupPeriod" name="lockupPeriod" type="text" value="${escapeAttribute(input.lockupPeriod)}" />
        </div>

        <div class="form-field">
          <label for="platformFees">Platform Fees</label>
          <input id="platformFees" name="platformFees" type="text" value="${escapeAttribute(input.platformFees)}" />
        </div>

        <div class="form-field">
          <label for="decision">Decision</label>
          <select id="decision" name="decision">
            <option value="WATCH" ${selected(input.decision, 'WATCH')}>Watch</option>
            <option value="PASS" ${selected(input.decision, 'PASS')}>Pass</option>
            <option value="INVEST_SMALL" ${selected(input.decision, 'INVEST_SMALL')}>Invest Small</option>
          </select>
        </div>

        <div class="form-field form-field--full">
          <label for="shortDescription">Deal Summary</label>
          <textarea id="shortDescription" name="shortDescription" rows="3" required>${escapeHtml(input.shortDescription)}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="thesis">Thesis</label>
          <textarea id="thesis" name="thesis" rows="3">${escapeHtml(input.thesis)}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="mainRisk">Main Risk</label>
          <textarea id="mainRisk" name="mainRisk" rows="3">${escapeHtml(input.mainRisk)}</textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="nextMilestone">Next Milestone</label>
          <textarea id="nextMilestone" name="nextMilestone" rows="3">${escapeHtml(input.nextMilestone)}</textarea>
        </div>

        <div class="form-actions">
          <button type="submit" class="button button--primary">Create Deal From Text</button>
        </div>
      </form>
    </div>
  `;
}

export function renderTextImportPage(): string {
  return `
    <div class="page">
      <div class="page-header">
        <h2>Deal Text Import</h2>
        <p>Paste campaign, Form C, offering document, or notes text and approve parser suggestions.</p>
      </div>

      <div class="notice notice--neutral">
        Parsing is local and transparent. It uses string matching only, and nothing is saved until you approve it.
      </div>

      <div class="card">
        <form class="stacked-form" id="parse-text-form">
          <div class="form-field">
            <label for="rawDealText">Raw Deal Text</label>
            <textarea id="rawDealText" name="rawDealText" rows="14" placeholder="Paste campaign page text, Form C excerpts, offering circular text, founder notes, or your own notes..." required></textarea>
          </div>

          <div class="form-actions">
            <button type="submit" class="button button--primary">Parse Text</button>
          </div>
        </form>
      </div>

      <div id="import-preview"></div>
    </div>
  `;
}

export function bindTextImportPageEvents(root: HTMLElement): void {
  const parseForm = root.querySelector<HTMLFormElement>('#parse-text-form');
  const preview = root.querySelector<HTMLElement>('#import-preview');

  parseForm?.addEventListener('submit', (event) => {
    event.preventDefault();

    const rawDealText = String(new FormData(parseForm).get('rawDealText') ?? '').trim();
    if (!rawDealText) {
      window.alert('Paste deal text before parsing.');
      return;
    }

    const parsed = parseDealText(rawDealText);
    if (!preview) return;

    preview.innerHTML = renderSuggestionForm(parsed);

    const approvalForm = preview.querySelector<HTMLFormElement>('#import-approval-form');
    approvalForm?.addEventListener('submit', async (submitEvent) => {
      submitEvent.preventDefault();

      const input = getDealInput(new FormData(approvalForm));

      if (!input.companyName || !input.platform || !input.sector || !input.shortDescription) {
        window.alert('Please complete company name, platform, sector, and deal summary.');
        return;
      }

      try {
        const deal = await createDeal(input);
        navigateTo(`/deals/${deal.id}`);
      } catch (error) {
        console.error('Failed to create deal from text:', error);
        window.alert('Failed to create deal from text.');
      }
    });
  });
}
