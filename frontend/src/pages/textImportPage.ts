import type {
  DealImportRecordInput,
  DealInput,
  ImportFieldSuggestion,
  ImportMode,
  ImportRedFlagSuggestion,
  RedFlagKey
} from '../models/deal';
import { parseDealText, type ParsedDealText } from '../services/dealTextParser';
import { createDeal } from '../services/dealService';
import { escapeAttribute, escapeHtml } from '../utils/html';
import { navigateTo } from '../utils/router';

const FIELD_LABELS: Partial<Record<keyof DealInput, string>> = {
  companyName: 'Company Name',
  platform: 'Platform',
  sector: 'Sector',
  offeringUrl: 'Offering URL',
  minimumInvestment: 'Minimum Investment',
  valuationOrCap: 'Valuation / Cap',
  amountRaised: 'Amount Raised',
  revenueStatus: 'Revenue Status',
  investorEligibility: 'Investor Eligibility',
  offeringExemption: 'Offering Exemption',
  securityType: 'Security Type',
  liquidity: 'Liquidity',
  lockupPeriod: 'Lockup Period',
  platformFees: 'Platform Fees',
  shortDescription: 'Deal Summary',
  thesis: 'Thesis',
  mainRisk: 'Main Risk',
  nextMilestone: 'Next Milestone'
};

function confidenceClass(confidence: string): string {
  return confidence.toLowerCase();
}

function readSuggestionValues(root: HTMLElement): ImportFieldSuggestion[] {
  return Array.from(root.querySelectorAll<HTMLElement>('[data-field-suggestion-id]')).map(
    (item) => {
      const checkbox = item.querySelector<HTMLInputElement>('[data-field-accepted]');
      const valueInput = item.querySelector<HTMLInputElement | HTMLTextAreaElement>('[data-field-value]');
      return {
        id: item.dataset.fieldSuggestionId ?? '',
        fieldName: (item.dataset.fieldName ?? '') as keyof DealInput,
        suggestedValue: valueInput?.value.trim() ?? '',
        confidence: (item.dataset.confidence ?? 'LOW') as ImportFieldSuggestion['confidence'],
        sourceSnippet: item.dataset.sourceSnippet ?? '',
        accepted: checkbox?.checked === true,
        ignored: item.dataset.ignored === 'true'
      };
    }
  );
}

function readRedFlagSuggestions(root: HTMLElement): ImportRedFlagSuggestion[] {
  return Array.from(root.querySelectorAll<HTMLElement>('[data-red-flag-suggestion-id]')).map(
    (item) => {
      const checkbox = item.querySelector<HTMLInputElement>('[data-red-flag-accepted]');
      return {
        id: item.dataset.redFlagSuggestionId ?? '',
        redFlagKey: (item.dataset.redFlagKey ?? '') as RedFlagKey,
        label: item.dataset.label ?? '',
        confidence: (item.dataset.confidence ?? 'LOW') as ImportRedFlagSuggestion['confidence'],
        sourceSnippet: item.dataset.sourceSnippet ?? '',
        accepted: checkbox?.checked === true,
        ignored: item.dataset.ignored === 'true'
      };
    }
  );
}

function setFieldValue(form: HTMLFormElement, fieldName: keyof DealInput, value: string): void {
  const field = form.elements.namedItem(String(fieldName));
  if (field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement || field instanceof HTMLSelectElement) {
    field.value = value;
  }
}

function applyAcceptedSuggestions(root: HTMLElement): void {
  const form = root.querySelector<HTMLFormElement>('#import-approval-form');
  if (!form) return;

  readSuggestionValues(root)
    .filter((suggestion) => suggestion.accepted && !suggestion.ignored)
    .forEach((suggestion) => {
      setFieldValue(form, suggestion.fieldName, suggestion.suggestedValue);
    });
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

function getDealInput(
  formData: FormData,
  parsed: ParsedDealText,
  root: HTMLElement
): DealInput {
  const fieldSuggestions = readSuggestionValues(root);
  const suggestedRedFlags = readRedFlagSuggestions(root);
  const importRecord: DealImportRecordInput = {
    ...parsed.importRecord,
    fieldSuggestions,
    suggestedRedFlags
  };

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
    rawDealText: parsed.importRecord.rawText,
    importRecord,
    initialRedFlags: suggestedRedFlags
      .filter((suggestion) => suggestion.accepted && !suggestion.ignored)
      .map((suggestion) => suggestion.redFlagKey),
    decision: getString(formData, 'decision') as DealInput['decision'],
    shortDescription: getString(formData, 'shortDescription')
  };
}

function renderSections(parsed: ParsedDealText): string {
  return `
    <div class="split-grid">
      ${parsed.sections
        .map(
          (section) => `
            <div class="summary-block import-section import-section--${section.sectionName.toLowerCase()}">
              <h4>${escapeHtml(section.label)} (${section.lineCount})</h4>
              <p>${escapeHtml(section.text || 'No lines classified here.')}</p>
            </div>
          `
        )
        .join('')}
    </div>
  `;
}

function renderFieldSuggestions(parsed: ParsedDealText): string {
  if (!parsed.fieldSuggestions.length) {
    return '<p class="empty-copy">No field suggestions found. You can still create a deal manually below.</p>';
  }

  return `
    <div class="suggestion-list">
      ${parsed.fieldSuggestions
        .map(
          (suggestion) => `
            <div
              class="suggestion-item suggestion-item--field"
              data-field-suggestion-id="${escapeAttribute(suggestion.id)}"
              data-field-name="${escapeAttribute(suggestion.fieldName)}"
              data-confidence="${suggestion.confidence}"
              data-source-snippet="${escapeAttribute(suggestion.sourceSnippet)}"
            >
              <input type="checkbox" data-field-accepted />
              <span>
                <strong>
                  ${escapeHtml(FIELD_LABELS[suggestion.fieldName] ?? String(suggestion.fieldName))}
                  <span class="confidence-chip confidence-chip--${confidenceClass(suggestion.confidence)}">${suggestion.confidence}</span>
                </strong>
                <input data-field-value type="text" value="${escapeAttribute(suggestion.suggestedValue)}" />
                <small>${escapeHtml(suggestion.sourceSnippet)}</small>
              </span>
            </div>
          `
        )
        .join('')}
    </div>
  `;
}

function renderRedFlagSuggestions(parsed: ParsedDealText): string {
  if (!parsed.suggestedRedFlags.length) {
    return '<p class="empty-copy">No risk language suggestions found.</p>';
  }

  return `
    <div class="suggestion-list">
      ${parsed.suggestedRedFlags
        .map(
          (suggestion) => `
            <div
              class="suggestion-item"
              data-red-flag-suggestion-id="${escapeAttribute(suggestion.id)}"
              data-red-flag-key="${suggestion.redFlagKey}"
              data-label="${escapeAttribute(suggestion.label)}"
              data-confidence="${suggestion.confidence}"
              data-source-snippet="${escapeAttribute(suggestion.sourceSnippet)}"
            >
              <input type="checkbox" data-red-flag-accepted />
              <span>
                <strong>
                  ${escapeHtml(suggestion.label)}
                  <span class="confidence-chip confidence-chip--${confidenceClass(suggestion.confidence)}">${suggestion.confidence}</span>
                </strong>
                <small>${escapeHtml(suggestion.sourceSnippet)}</small>
              </span>
            </div>
          `
        )
        .join('')}
    </div>
  `;
}

function renderDealForm(parsed: ParsedDealText): string {
  return `
    <div class="card">
      <div class="page-header">
        <h3>Deal Profile Draft</h3>
        <p>Accept suggestions above, edit the profile here, then create the deal.</p>
      </div>

      <form class="form-grid" id="import-approval-form">
        <div class="form-field">
          <label for="companyName">Company Name</label>
          <input id="companyName" name="companyName" type="text" required />
        </div>

        <div class="form-field">
          <label for="platform">Platform</label>
          <input id="platform" name="platform" type="text" required />
        </div>

        <div class="form-field">
          <label for="sector">Sector</label>
          <input id="sector" name="sector" type="text" required />
        </div>

        <div class="form-field">
          <label for="offeringUrl">Website / Offering URL</label>
          <input id="offeringUrl" name="offeringUrl" type="url" value="${escapeAttribute(parsed.importRecord.sourceUrl)}" />
        </div>

        <div class="form-field">
          <label for="minimumInvestment">Minimum Investment</label>
          <input id="minimumInvestment" name="minimumInvestment" type="number" min="0" step="1" />
        </div>

        <div class="form-field">
          <label for="valuationOrCap">Valuation or Cap</label>
          <input id="valuationOrCap" name="valuationOrCap" type="text" />
        </div>

        <div class="form-field">
          <label for="amountRaised">Amount Raised</label>
          <input id="amountRaised" name="amountRaised" type="number" min="0" step="1" />
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
          <input id="lockupPeriod" name="lockupPeriod" type="text" />
        </div>

        <div class="form-field">
          <label for="platformFees">Platform Fees</label>
          <input id="platformFees" name="platformFees" type="text" />
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
          <textarea id="shortDescription" name="shortDescription" rows="3" required></textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="thesis">Thesis</label>
          <textarea id="thesis" name="thesis" rows="3"></textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="mainRisk">Main Risk</label>
          <textarea id="mainRisk" name="mainRisk" rows="3"></textarea>
        </div>

        <div class="form-field form-field--full">
          <label for="nextMilestone">Next Milestone</label>
          <textarea id="nextMilestone" name="nextMilestone" rows="3"></textarea>
        </div>

        <div class="form-actions">
          <button type="submit" class="button button--primary">Create Deal From Reviewed Import</button>
        </div>
      </form>
    </div>
  `;
}

function renderImportReview(parsed: ParsedDealText): string {
  return `
    <div class="card">
      <div class="page-header">
        <h3>Raw Text Preview</h3>
        <p>Original text is preserved with the deal. Lazy mode uses cleaned text for parsing.</p>
      </div>
      <pre class="import-preview">${escapeHtml(parsed.importRecord.rawText.slice(0, 1600))}${parsed.importRecord.rawText.length > 1600 ? '\n...' : ''}</pre>
    </div>

    <div class="card">
      <div class="page-header">
        <h3>Detected Sections</h3>
        <p>Messy text is bucketed before suggestions are generated.</p>
      </div>
      ${renderSections(parsed)}
    </div>

    <div class="split-grid">
      <div class="card">
        <div class="page-header">
          <h3>Field Suggestions</h3>
          <p>Edit values, accept selected suggestions, or accept high-confidence suggestions.</p>
        </div>
        <div class="workspace-actions workspace-actions--wrap">
          <button type="button" class="button button--secondary" id="accept-high-confidence-button">Accept All High Confidence</button>
          <button type="button" class="button button--primary" id="apply-selected-suggestions-button">Apply Selected</button>
          <button type="button" class="button button--secondary" id="ignore-selected-suggestions-button">Ignore Selected</button>
        </div>
        <div class="section-gap">${renderFieldSuggestions(parsed)}</div>
      </div>

      <div class="card">
        <div class="page-header">
          <h3>Suggested Red Flags</h3>
          <p>Risk language is suggested for review and is not auto-checked unless accepted.</p>
        </div>
        <div class="workspace-actions workspace-actions--wrap">
          <button type="button" class="button button--secondary" id="accept-high-risk-button">Accept High Confidence Risks</button>
          <button type="button" class="button button--secondary" id="ignore-selected-risks-button">Ignore Selected</button>
        </div>
        <div class="section-gap">${renderRedFlagSuggestions(parsed)}</div>
      </div>
    </div>

    ${renderDealForm(parsed)}
  `;
}

export function renderTextImportPage(): string {
  return `
    <div class="page">
      <div class="page-header">
        <h2>Deal Text Import</h2>
        <p>Paste focused deal text or full-page dumps, then review parser suggestions before saving.</p>
      </div>

      <div class="notice notice--neutral">
        Parsing is local and transparent. Lazy Import removes repeated/page-noise lines for suggestions while preserving the original raw text.
      </div>

      <div class="card">
        <form class="form-grid" id="parse-text-form">
          <div class="form-field">
            <label for="importMode">Import Mode</label>
            <select id="importMode" name="importMode">
              <option value="CLEAN">Clean Import</option>
              <option value="LAZY">Lazy Import</option>
            </select>
          </div>

          <div class="form-field">
            <label for="importTitle">Import Title</label>
            <input id="importTitle" name="importTitle" type="text" placeholder="Republic page paste, Form C risk factors..." required />
          </div>

          <div class="form-field form-field--full">
            <label for="sourceUrl">Source URL</label>
            <input id="sourceUrl" name="sourceUrl" type="url" placeholder="https://..." />
          </div>

          <div class="form-field form-field--full">
            <label for="rawDealText">Raw Deal Text</label>
            <textarea id="rawDealText" name="rawDealText" rows="14" placeholder="Paste campaign page text, full-page copy/paste, Form C excerpts, offering circular text, founder notes, or your own notes..." required></textarea>
          </div>

          <div class="form-actions">
            <button type="submit" class="button button--primary">Parse Import</button>
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
  let latestParsed: ParsedDealText | null = null;

  parseForm?.addEventListener('submit', (event) => {
    event.preventDefault();

    const formData = new FormData(parseForm);
    const rawText = String(formData.get('rawDealText') ?? '').trim();
    const title = String(formData.get('importTitle') ?? '').trim();
    const sourceUrl = String(formData.get('sourceUrl') ?? '').trim();
    const importMode = String(formData.get('importMode') ?? 'CLEAN') as ImportMode;

    if (!rawText || !title) {
      window.alert('Add an import title and pasted text before parsing.');
      return;
    }

    latestParsed = parseDealText({
      importMode,
      title,
      sourceUrl,
      rawText
    });

    if (!preview) return;
    preview.innerHTML = renderImportReview(latestParsed);

    const acceptHighButton = preview.querySelector<HTMLButtonElement>('#accept-high-confidence-button');
    const applySelectedButton = preview.querySelector<HTMLButtonElement>('#apply-selected-suggestions-button');
    const ignoreSelectedButton = preview.querySelector<HTMLButtonElement>('#ignore-selected-suggestions-button');
    const acceptHighRiskButton = preview.querySelector<HTMLButtonElement>('#accept-high-risk-button');
    const ignoreSelectedRisksButton = preview.querySelector<HTMLButtonElement>('#ignore-selected-risks-button');
    const approvalForm = preview.querySelector<HTMLFormElement>('#import-approval-form');

    acceptHighButton?.addEventListener('click', () => {
      preview
        .querySelectorAll<HTMLElement>('[data-field-suggestion-id][data-confidence="HIGH"]')
        .forEach((item) => {
          if (item.dataset.ignored === 'true') return;
          const checkbox = item.querySelector<HTMLInputElement>('[data-field-accepted]');
          if (checkbox) checkbox.checked = true;
        });
      applyAcceptedSuggestions(preview);
    });

    applySelectedButton?.addEventListener('click', () => {
      applyAcceptedSuggestions(preview);
    });

    ignoreSelectedButton?.addEventListener('click', () => {
      preview.querySelectorAll<HTMLElement>('[data-field-suggestion-id]').forEach((item) => {
        const checkbox = item.querySelector<HTMLInputElement>('[data-field-accepted]');
        if (!checkbox?.checked) return;
        checkbox.checked = false;
        item.dataset.ignored = 'true';
        item.classList.add('suggestion-item--ignored');
      });
    });

    acceptHighRiskButton?.addEventListener('click', () => {
      preview
        .querySelectorAll<HTMLElement>('[data-red-flag-suggestion-id][data-confidence="HIGH"]')
        .forEach((item) => {
          if (item.dataset.ignored === 'true') return;
          const checkbox = item.querySelector<HTMLInputElement>('[data-red-flag-accepted]');
          if (checkbox) checkbox.checked = true;
        });
    });

    ignoreSelectedRisksButton?.addEventListener('click', () => {
      preview.querySelectorAll<HTMLElement>('[data-red-flag-suggestion-id]').forEach((item) => {
        const checkbox = item.querySelector<HTMLInputElement>('[data-red-flag-accepted]');
        if (!checkbox?.checked) return;
        checkbox.checked = false;
        item.dataset.ignored = 'true';
        item.classList.add('suggestion-item--ignored');
      });
    });

    approvalForm?.addEventListener('submit', async (submitEvent) => {
      submitEvent.preventDefault();
      if (!latestParsed) return;

      applyAcceptedSuggestions(preview);
      const input = getDealInput(new FormData(approvalForm), latestParsed, preview);

      if (!input.companyName || !input.platform || !input.sector || !input.shortDescription) {
        window.alert('Please complete company name, platform, sector, and deal summary.');
        return;
      }

      try {
        const deal = await createDeal(input);
        navigateTo(`/deals/${deal.id}`);
      } catch (error) {
        console.error('Failed to create deal from import:', error);
        window.alert('Failed to create deal from import.');
      }
    });
  });
}
