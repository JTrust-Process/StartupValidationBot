import type { DealInput } from '../models/deal';
import { createDeal, loadDeals } from '../services/dealService';
import { getDeals } from '../services/dealService';
import { getDiligencePacket, getRadarCompany, getRadarOffering } from '../services/radarService';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { navigateTo } from '../utils/router';
import { safeExternalUrl } from '../utils/urls';

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
    radarCompanyId: getNumber(formData, 'radarCompanyId'),
    offeringDiscoveryId: getNumber(formData, 'offeringDiscoveryId'),
    secFilingUrl: getString(formData, 'secFilingUrl'),
    offeringDeadline: getString(formData, 'offeringDeadline'),
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
        <p>Add a startup or private-market offering to your private diligence workspace.</p>
      </div>

      <div class="notice notice--neutral">
        This tool is for research organization only and is not financial advice.
      </div>

      <div id="radar-deal-origin-status"></div>

      <div class="card">
        <form class="form-grid" id="new-deal-form">
          <input id="radarCompanyId" name="radarCompanyId" type="hidden" />
          <input id="offeringDiscoveryId" name="offeringDiscoveryId" type="hidden" />
          <input id="secFilingUrl" name="secFilingUrl" type="hidden" />
          <input id="offeringDeadline" name="offeringDeadline" type="hidden" />
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
            <select id="decision" name="decision" required>
              <option value="" selected>Choose after review</option>
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

export function bindNewDealPageEvents(root: HTMLElement, path: string): void {
  const form = root.querySelector<HTMLFormElement>('#new-deal-form');

  if (!form) return;

  const query = path.includes('?') ? path.slice(path.indexOf('?') + 1) : '';
  const params = new URLSearchParams(query);
  const radarCompanyId = Number(params.get('radarCompanyId'));
  const offeringDiscoveryId = Number(params.get('offeringDiscoveryId'));
  const diligencePacketId = Number(params.get('diligencePacketId'));
  if (Number.isInteger(diligencePacketId) && diligencePacketId > 0) {
    void prefillFromDiligence(root, form, diligencePacketId);
  } else if (Number.isInteger(offeringDiscoveryId) && offeringDiscoveryId > 0) {
    void prefillFromOffering(root, form, offeringDiscoveryId);
  } else
  if (Number.isInteger(radarCompanyId) && radarCompanyId > 0) {
    void prefillFromRadar(root, form, radarCompanyId);
  }

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

async function prefillFromDiligence(root: HTMLElement, form: HTMLFormElement, packetId: number): Promise<void> {
  const status = root.querySelector<HTMLElement>('#radar-deal-origin-status');
  await loadDeals();
  try {
    const packet = await getDiligencePacket(packetId);
    const duplicate = getDeals().find((deal) => deal.offeringDiscoveryId === packet.offeringId
      || deal.radarCompanyId === packet.radarCompanyId);
    if (duplicate) {
      if (status) status.innerHTML = `<div class="notice notice--warning">This public offering already has a Deal Scout workspace.
        <a href="#/deals/${duplicate.id}">Open ${escapeHtml(duplicate.companyName)}</a>.</div>`;
      return;
    }
    const company = await getRadarCompany(packet.radarCompanyId);
    const securityType = /safe/i.test(packet.securityType || '') ? 'SAFE'
      : /note|debt/i.test(packet.securityType || '') ? 'NOTE'
        : /stock|equity|share/i.test(packet.securityType || '') ? 'EQUITY' : 'UNKNOWN';
    const financial = packet.financials.map((period) => `${period.period}: revenue ${period.revenue ?? 'unknown'}, net income ${period.netIncome ?? 'unknown'}`).join('; ');
    const references = packet.evidence.map((item) => item.sourceUrl).filter((value, index, values) => value && values.indexOf(value) === index).slice(0, 5);
    const fields: Record<string, string> = {
      radarCompanyId: String(packet.radarCompanyId),
      offeringDiscoveryId: String(packet.offeringId),
      secFilingUrl: packet.secFilingUrl || '',
      offeringDeadline: packet.deadline || '',
      companyName: packet.companyName,
      platform: packet.platform || 'Unknown',
      sector: company.company.sector,
      offeringUrl: packet.campaignUrl || packet.secFilingUrl || '',
      minimumInvestment: packet.minimumInvestment === null ? '' : String(packet.minimumInvestment),
      valuationOrCap: packet.valuationOrCap || '',
      amountRaised: packet.amountRaised === null ? '' : String(packet.amountRaised),
      investorEligibility: 'NON_ACCREDITED',
      offeringExemption: 'REG_CF',
      securityType,
      liquidity: 'ILLIQUID',
      shortDescription: `${packet.summary}${financial ? ` Financial snapshot: ${financial}.` : ''}`,
      thesis: `${packet.bullCase.join(' ')}${references.length ? ` Public evidence: ${references.join(', ')}` : ''}`,
      mainRisk: packet.keyRisks.join(' '),
      nextMilestone: packet.nextMonitoringMilestones.join(' ')
    };
    Object.entries(fields).forEach(([name, value]) => {
      const field = form.elements.namedItem(name);
      if (field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement || field instanceof HTMLSelectElement) field.value = value;
    });
    if (status) status.innerHTML = `<div class="notice notice--neutral">Public facts were prefilled from
      <a href="#/review/${packet.id}">diligence packet ${packet.id}</a>. Review every field before explicitly creating a Deal Scout workspace.
      Filed facts and issuer claims remain separate in the source packet.</div>`;
  } catch (error) {
    if (status) status.innerHTML = `<div class="notice notice--warning">Could not load the diligence packet. ${escapeHtml(error instanceof Error ? error.message : '')}</div>`;
  }
}

async function prefillFromOffering(root: HTMLElement, form: HTMLFormElement, offeringId: number): Promise<void> {
  const status = root.querySelector<HTMLElement>('#radar-deal-origin-status');
  await loadDeals();
  const duplicate = getDeals().find((deal) => deal.offeringDiscoveryId === offeringId);
  if (duplicate) {
    if (status) status.innerHTML = `<div class="notice notice--warning">This offering is already in Deal Scout.
      <a href="#/deals/${duplicate.id}">Open ${escapeHtml(duplicate.companyName)}</a>.</div>`;
    return;
  }
  try {
    const offering = await getRadarOffering(offeringId);
    const securityType = /safe/i.test(offering.securityType || '') ? 'SAFE'
      : /note|debt/i.test(offering.securityType || '') ? 'NOTE'
        : /stock|equity|share/i.test(offering.securityType || '') ? 'EQUITY' : 'UNKNOWN';
    const fields: Record<string, string> = {
      radarCompanyId: String(offering.radarCompanyId || ''),
      offeringDiscoveryId: String(offering.id),
      secFilingUrl: offering.secFilingUrl || '',
      offeringDeadline: offering.deadline || '',
      companyName: offering.companyName || offering.issuerName,
      platform: offering.platform,
      offeringUrl: offering.offeringUrl || offering.secFilingUrl || '',
      minimumInvestment: offering.minimumInvestment === null ? '' : String(offering.minimumInvestment),
      valuationOrCap: offering.valuationOrCap || '',
      amountRaised: offering.amountRaised === null ? '' : String(offering.amountRaised),
      investorEligibility: 'NON_ACCREDITED',
      offeringExemption: 'REG_CF',
      securityType,
      liquidity: 'ILLIQUID',
      shortDescription: offering.secFilingUrl
        ? `SEC-filed Regulation Crowdfunding offering (${offering.filingType}). Filing: ${offering.secFilingUrl}`
        : `Public ${offering.platform} Regulation Crowdfunding offering. SEC reconciliation: ${offering.reconciliationStatus.replaceAll('_', ' ')}`
    };
    Object.entries(fields).forEach(([name, value]) => {
      const field = form.elements.namedItem(name);
      if (field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement || field instanceof HTMLSelectElement) field.value = value;
    });
    if (offering.radarCompanyId) {
      const company = await getRadarCompany(offering.radarCompanyId);
      const sector = form.elements.namedItem('sector');
      if (sector instanceof HTMLInputElement) sector.value = company.company.sector;
    }
    const filingUrl = safeExternalUrl(offering.secFilingUrl);
    if (status) status.innerHTML = `<div class="notice notice--neutral">Public offering facts were prefilled from
      ${filingUrl ? `<a href="${escapeAttribute(filingUrl)}" target="_blank" rel="noreferrer">an SEC-filed offering statement</a>`
        : `a public ${escapeHtml(offering.platform)} campaign listing`}.
      Review every field before saving. Public source evidence does not verify every issuer claim.</div>`;
  } catch (error) {
    if (status) status.innerHTML = `<div class="notice notice--warning">Could not load offering evidence. ${escapeHtml(error instanceof Error ? error.message : '')}</div>`;
  }
}

async function prefillFromRadar(root: HTMLElement, form: HTMLFormElement, radarCompanyId: number): Promise<void> {
  const status = root.querySelector<HTMLElement>('#radar-deal-origin-status');
  await loadDeals();
  const duplicate = getDeals().find((deal) => deal.radarCompanyId === radarCompanyId);
  if (duplicate) {
    if (status) status.innerHTML = `
      <div class="notice notice--warning">
        A Deal Scout workspace already links to this Radar company.
        <a href="#/deals/${duplicate.id}">Open ${escapeHtml(duplicate.companyName)}</a> before creating another.
      </div>`;
    return;
  }

  try {
    const detail = await getRadarCompany(radarCompanyId);
    const fields: Record<string, string> = {
      radarCompanyId: String(radarCompanyId),
      companyName: detail.company.name,
      sector: detail.company.sector,
      shortDescription: detail.company.description,
      offeringUrl: detail.company.websiteUrl ?? ''
    };
    Object.entries(fields).forEach(([name, value]) => {
      const field = form.elements.namedItem(name);
      if (field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement) field.value = value;
    });
    if (status) status.innerHTML = `
      <div class="notice notice--neutral">
        Prefilled public company facts from <a href="#/radar/company/${radarCompanyId}">Startup Radar</a>.
        Add the actual investment platform and offering terms before saving.
      </div>`;
  } catch (error) {
    if (status) status.innerHTML = `<div class="notice notice--warning">Could not load the linked Radar company. ${escapeHtml(error instanceof Error ? error.message : '')}</div>`;
  }
}
