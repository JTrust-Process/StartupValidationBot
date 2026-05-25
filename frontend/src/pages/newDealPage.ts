import type { DealInput } from '../models/deal';
import { createDeal } from '../services/dealService';
import { navigateTo } from '../utils/router';

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
    decision: getString(formData, 'decision') as DealInput['decision'],
    shortDescription: getString(formData, 'shortDescription')
  };
}

export function renderNewDealPage(): string {
  return `
    <div class="page">
      <div class="page-header">
        <h2>New Deal</h2>
        <p>Add a startup or private-market deal to your local diligence workspace.</p>
      </div>

      <div class="notice notice--neutral">
        This tool is for research organization only and is not financial advice.
      </div>

      <div class="card">
        <form class="form-grid" id="new-deal-form">
          <div class="form-field">
            <label for="companyName">Company Name</label>
            <input id="companyName" name="companyName" type="text" placeholder="Acme Robotics" required />
          </div>

          <div class="form-field">
            <label for="platform">Platform</label>
            <input id="platform" name="platform" type="text" placeholder="Wefunder, StartEngine, Republic..." required />
          </div>

          <div class="form-field">
            <label for="sector">Sector</label>
            <input id="sector" name="sector" type="text" placeholder="AI, Energy, Fintech..." required />
          </div>

          <div class="form-field">
            <label for="offeringUrl">Website / Offering URL</label>
            <input id="offeringUrl" name="offeringUrl" type="url" placeholder="https://..." />
          </div>

          <div class="form-field">
            <label for="minimumInvestment">Minimum Investment</label>
            <input id="minimumInvestment" name="minimumInvestment" type="number" min="0" step="1" placeholder="100" />
          </div>

          <div class="form-field">
            <label for="valuationOrCap">Valuation or Cap</label>
            <input id="valuationOrCap" name="valuationOrCap" type="text" placeholder="$20M valuation cap" />
          </div>

          <div class="form-field">
            <label for="amountRaised">Amount Raised</label>
            <input id="amountRaised" name="amountRaised" type="number" min="0" step="1" placeholder="750000" />
          </div>

          <div class="form-field">
            <label for="revenueStatus">Revenue Status</label>
            <select id="revenueStatus" name="revenueStatus">
              <option value="UNCLEAR">Unclear</option>
              <option value="PRE_REVENUE">No revenue / pre-revenue</option>
              <option value="EARLY_REVENUE">Early revenue</option>
              <option value="REVENUE">Revenue</option>
            </select>
          </div>

          <div class="form-field">
            <label for="investorEligibility">Investor Eligibility</label>
            <select id="investorEligibility" name="investorEligibility">
              <option value="UNCLEAR">Unclear</option>
              <option value="NON_ACCREDITED">Non-accredited</option>
              <option value="ACCREDITED_ONLY">Accredited only</option>
            </select>
          </div>

          <div class="form-field">
            <label for="offeringExemption">Offering Exemption</label>
            <select id="offeringExemption" name="offeringExemption">
              <option value="UNKNOWN">Unknown</option>
              <option value="REG_CF">Reg CF</option>
              <option value="REG_A">Reg A</option>
              <option value="REG_D">Reg D</option>
              <option value="OTHER">Other</option>
            </select>
          </div>

          <div class="form-field">
            <label for="securityType">Security Type</label>
            <select id="securityType" name="securityType">
              <option value="UNKNOWN">Unknown</option>
              <option value="SAFE">SAFE</option>
              <option value="EQUITY">Equity</option>
              <option value="NOTE">Note</option>
              <option value="REVENUE_SHARE">Revenue Share</option>
              <option value="FUND_INTEREST">Fund Interest</option>
              <option value="SPV">SPV</option>
              <option value="OTHER">Other</option>
            </select>
          </div>

          <div class="form-field">
            <label for="liquidity">Liquidity</label>
            <select id="liquidity" name="liquidity">
              <option value="UNKNOWN">Unknown</option>
              <option value="ILLIQUID">Illiquid</option>
              <option value="REDEMPTION_WINDOW">Redemption window</option>
              <option value="SECONDARY_POSSIBLE">Secondary possible</option>
            </select>
          </div>

          <div class="form-field">
            <label for="lockupPeriod">Lockup Period</label>
            <input id="lockupPeriod" name="lockupPeriod" type="text" placeholder="5 years, unknown..." />
          </div>

          <div class="form-field">
            <label for="platformFees">Platform Fees</label>
            <input id="platformFees" name="platformFees" type="text" placeholder="2%, none disclosed, unknown..." />
          </div>

          <div class="form-field">
            <label for="decision">Decision</label>
            <select id="decision" name="decision">
              <option value="WATCH">Watch</option>
              <option value="PASS">Pass</option>
              <option value="INVEST_SMALL">Invest Small</option>
            </select>
          </div>

          <div class="form-field form-field--full">
            <label for="shortDescription">Deal Summary</label>
            <textarea id="shortDescription" name="shortDescription" rows="3" placeholder="What does the company do and what is being offered?" required></textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="thesis">Thesis</label>
            <textarea id="thesis" name="thesis" rows="3" placeholder="Why might this be worth tracking?"></textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="mainRisk">Main Risk</label>
            <textarea id="mainRisk" name="mainRisk" rows="3" placeholder="What could make this a clear pass?"></textarea>
          </div>

          <div class="form-field form-field--full">
            <label for="nextMilestone">Next Milestone</label>
            <textarea id="nextMilestone" name="nextMilestone" rows="3" placeholder="What proof or update should you wait for?"></textarea>
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

    const input = getDealInput(new FormData(form));

    if (!input.companyName || !input.platform || !input.sector || !input.shortDescription) {
      window.alert('Please complete company name, platform, sector, and deal summary.');
      return;
    }

    try {
      const newDeal = await createDeal(input);
      navigateTo(`/deals/${newDeal.id}`);
    } catch (error) {
      console.error('Failed to create deal:', error);
      window.alert('Failed to create deal.');
    }
  });
}
