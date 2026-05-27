import type { Deal } from '../models/deal';
import {
  getDeals,
  getFinalRecommendation,
  getFinalScore,
  getRedFlagCount,
  getRiskStatus
} from '../services/dealService';
import {
  getDataConfidenceLabel,
  getDataConfidenceScore
} from '../services/riskAnalysis';
import { getScoutDashboardSummary } from '../services/dealScoutService';
import { getAllDocumentRisks as getDocumentRiskItems } from '../services/documentIntelligence';
import {
  formatDate,
  formatDealStatus,
  formatInvestorEligibility,
  formatOfferingExemption
} from '../utils/formatters';
import { escapeHtml } from '../utils/html';

function getAverage(values: number[]): string {
  if (values.length === 0) return '-';

  const total = values.reduce((sum, value) => sum + value, 0);
  return Math.round(total / values.length).toString();
}

function countBy<T extends string>(values: T[]): Array<{ label: T; count: number }> {
  const counts = values.reduce((map, value) => {
    map.set(value, (map.get(value) ?? 0) + 1);
    return map;
  }, new Map<T, number>());

  return Array.from(counts.entries())
    .map(([label, count]) => ({ label, count }))
    .sort((a, b) => b.count - a.count || a.label.localeCompare(b.label));
}

function renderCountList(items: Array<{ label: string; count: number }>, emptyText: string): string {
  if (!items.length) {
    return `<p class="empty-copy">${emptyText}</p>`;
  }

  return `
    <div class="mini-list">
      ${items
        .map(
          (item) => `
            <div class="mini-list__item">
              <span>${escapeHtml(item.label || 'Unknown')}</span>
              <strong>${item.count}</strong>
            </div>
          `
        )
        .join('')}
    </div>
  `;
}

function renderDealRows(deals: Deal[], emptyText: string): string {
  if (!deals.length) {
    return `
      <tr>
        <td colspan="5">${escapeHtml(emptyText)}</td>
      </tr>
    `;
  }

  return deals
    .map((deal) => {
      const finalScore = getFinalScore(deal);
      const recommendation = getFinalRecommendation(finalScore);
      const riskStatus = getRiskStatus(deal);

      return `
        <tr>
          <td><a class="table-link" href="#/deals/${deal.id}">${escapeHtml(deal.companyName)}</a></td>
          <td>${escapeHtml(deal.platform || '-')}</td>
          <td>${formatDealStatus(deal.decision)}</td>
          <td>
            <div>${finalScore}</div>
            <div class="table-subtext">${escapeHtml(recommendation.label)}</div>
          </td>
          <td>
            <span class="risk-chip risk-chip--${riskStatus.tone}">${riskStatus.label}</span>
            <div class="table-subtext">${getRedFlagCount(deal)} flags</div>
          </td>
        </tr>
      `;
    })
    .join('');
}

function getImportRecords(deals: Deal[]): Array<{ deal: Deal; record: Deal['importRecords'][number] }> {
  return deals.flatMap((deal) =>
    deal.importRecords.map((record) => ({
      deal,
      record
    }))
  );
}

function importNeedsReview(record: Deal['importRecords'][number]): boolean {
  const unreviewedFields = record.fieldSuggestions.some(
    (suggestion) => !suggestion.accepted && !suggestion.ignored
  );
  const unreviewedRisks = record.suggestedRedFlags.some(
    (suggestion) => !suggestion.accepted && !suggestion.ignored
  );

  return unreviewedFields || unreviewedRisks;
}

function hasAcceptedImportField(deal: Deal): boolean {
  return deal.importRecords.some((record) =>
    record.fieldSuggestions.some((suggestion) => suggestion.accepted)
  );
}

function needsFollowUp(deal: Deal, today: string): boolean {
  if (deal.decision !== 'WATCH') return false;
  if (!deal.review?.nextReviewDate) return true;

  return deal.review.nextReviewDate <= today;
}

function getFollowUpReasons(deal: Deal, today: string): string[] {
  const reasons: string[] = [];
  const dataConfidence = getDataConfidenceScore(deal);

  if (deal.investorEligibility === 'UNCLEAR') reasons.push('Eligibility unclear');
  if (deal.offeringExemption === 'UNKNOWN') reasons.push('Exemption unknown');
  if (deal.documents.length === 0) reasons.push('Documents missing');
  if (getDocumentRiskItems(deal).length > 0) reasons.push('Document risks unreviewed');
  if (deal.redFlags.missingOfferingDocuments) reasons.push('Offering docs missing');
  if (deal.revenueStatus === 'UNCLEAR') reasons.push('Revenue unclear');
  if (!deal.platformFees || /unknown|unclear/i.test(deal.platformFees)) reasons.push('Fees unclear');
  if (dataConfidence < 50) reasons.push('Low data confidence');
  if (!deal.dealMemo?.content) reasons.push('Memo missing');
  if (deal.nextMilestone) reasons.push('Next milestone');
  if (deal.review?.nextReviewDate && deal.review.nextReviewDate <= today) reasons.push('Review due');

  return reasons;
}

export function renderDashboardPage(): string {
  const deals = getDeals();
  const scoutSummary = getScoutDashboardSummary();
  const today = new Date().toISOString().slice(0, 10);
  const finalScores = deals.map((deal) => getFinalScore(deal));

  const totalDeals = deals.length;
  const importRecords = getImportRecords(deals);
  const passCount = deals.filter((deal) => deal.decision === 'PASS').length;
  const watchCount = deals.filter((deal) => deal.decision === 'WATCH').length;
  const investSmallCount = deals.filter((deal) => deal.decision === 'INVEST_SMALL').length;
  const eligibilityRiskDeals = deals.filter(
    (deal) =>
      deal.investorEligibility === 'ACCREDITED_ONLY' ||
      deal.investorEligibility === 'UNCLEAR' ||
      deal.offeringExemption === 'REG_D' ||
      deal.offeringExemption === 'UNKNOWN'
  );
  const watchlistNeedsFollowUp = deals.filter((deal) => needsFollowUp(deal, today));
  const followUpQueue = deals
    .map((deal) => ({
      deal,
      reasons: getFollowUpReasons(deal, today)
    }))
    .filter((item) => item.reasons.length)
    .sort(
      (a, b) =>
        a.deal.review?.nextReviewDate?.localeCompare(b.deal.review?.nextReviewDate ?? '') ||
        getDataConfidenceScore(a.deal) - getDataConfidenceScore(b.deal)
    );
  const lowConfidenceCount = deals.filter((deal) => getDataConfidenceScore(deal) < 50).length;
  const missingDocumentsCount = deals.filter((deal) => deal.documents.length === 0).length;
  const unsavedDocumentRisksCount = deals.filter(
    (deal) => getDocumentRiskItems(deal).length > 0
  ).length;
  const generatedMemosCount = deals.filter((deal) => Boolean(deal.dealMemo?.content)).length;
  const readyForReviewCount = deals.filter(
    (deal) =>
      deal.documents.length > 0 &&
      Boolean(deal.dealMemo?.content) &&
      getDataConfidenceScore(deal) >= 50 &&
      getDocumentRiskItems(deal).length === 0
  ).length;
  const importsNeedingReviewCount = importRecords.filter(({ record }) =>
    importNeedsReview(record)
  ).length;
  const lowConfidenceSuggestionsCount = importRecords.reduce(
    (count, { record }) =>
      count +
      record.fieldSuggestions.filter(
        (suggestion) => suggestion.confidence === 'LOW' && !suggestion.ignored
      ).length,
    0
  );
  const rawImportsNoAcceptedFieldsCount = deals.filter(
    (deal) => deal.importRecords.length > 0 && !hasAcceptedImportField(deal)
  ).length;

  const platformCounts = countBy(deals.map((deal) => deal.platform || 'Unknown'));
  const sectorCounts = countBy(deals.map((deal) => deal.sector || 'Unknown'));
  const highestScoringDeals = [...deals]
    .sort((a, b) => getFinalScore(b) - getFinalScore(a))
    .slice(0, 5);

  return `
    <div class="page">
      <div class="page-header">
        <h2>Dashboard</h2>
        <p>Local-first startup deal diligence for non-accredited private-market research.</p>
      </div>

      <div class="notice notice--neutral">
        This is a personal research workflow, not financial, legal, or tax advice.
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Total Reviewed</h3>
          <p class="metric">${totalDeals}</p>
          <p class="metric-subtext">Deals saved locally</p>
        </div>

        <div class="card">
          <h3>Pass</h3>
          <p class="metric">${passCount}</p>
          <p class="metric-subtext">No check planned</p>
        </div>

        <div class="card">
          <h3>Watch</h3>
          <p class="metric">${watchCount}</p>
          <p class="metric-subtext">${watchlistNeedsFollowUp.length} need follow-up</p>
        </div>

        <div class="card">
          <h3>Invest Small</h3>
          <p class="metric">${investSmallCount}</p>
          <p class="metric-subtext">Small-check candidates</p>
        </div>
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Average Final Score</h3>
          <p class="metric">${getAverage(finalScores)}</p>
          <p class="metric-subtext">Quick and deep score blend</p>
        </div>

        <div class="card">
          <h3>Access Risk</h3>
          <p class="metric">${eligibilityRiskDeals.length}</p>
          <p class="metric-subtext">Unclear, Reg D, or accredited-only</p>
        </div>

        <div class="card">
          <h3>Low Confidence</h3>
          <p class="metric">${lowConfidenceCount}</p>
          <p class="metric-subtext">Needs better evidence</p>
        </div>

        <div class="card">
          <h3>Follow-Up Queue</h3>
          <p class="metric">${followUpQueue.length}</p>
          <p class="metric-subtext">Deals needing review</p>
        </div>
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Missing Documents</h3>
          <p class="metric">${missingDocumentsCount}</p>
          <p class="metric-subtext">No pasted document records</p>
        </div>

        <div class="card">
          <h3>Unsaved Doc Risks</h3>
          <p class="metric">${unsavedDocumentRisksCount}</p>
          <p class="metric-subtext">Detected risks need review</p>
        </div>

        <div class="card">
          <h3>Generated Memos</h3>
          <p class="metric">${generatedMemosCount}</p>
          <p class="metric-subtext">Deals with saved memos</p>
        </div>

        <div class="card">
          <h3>Ready for Review</h3>
          <p class="metric">${readyForReviewCount}</p>
          <p class="metric-subtext">Documents, memo, and confidence ready</p>
        </div>
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Imports Needing Review</h3>
          <p class="metric">${importsNeedingReviewCount}</p>
          <p class="metric-subtext">Unaccepted field or risk suggestions</p>
        </div>

        <div class="card">
          <h3>Low-Confidence Suggestions</h3>
          <p class="metric">${lowConfidenceSuggestionsCount}</p>
          <p class="metric-subtext">Parser hints to verify manually</p>
        </div>

        <div class="card">
          <h3>Raw Imports Only</h3>
          <p class="metric">${rawImportsNoAcceptedFieldsCount}</p>
          <p class="metric-subtext">Imports saved with no accepted fields</p>
        </div>

        <div class="card">
          <h3>Total Raw Imports</h3>
          <p class="metric">${importRecords.length}</p>
          <p class="metric-subtext">Original pasted text records preserved</p>
        </div>
      </div>

      <div class="card-grid">
        <div class="card">
          <h3>Scout Sources</h3>
          <p class="metric">${scoutSummary.enabledSourceCount}</p>
          <p class="metric-subtext">${scoutSummary.sourceCount} total monitored sources</p>
        </div>

        <div class="card">
          <h3>Scout Errors</h3>
          <p class="metric">${scoutSummary.sourcesWithErrors.length}</p>
          <p class="metric-subtext">Sources needing attention</p>
        </div>

        <div class="card">
          <h3>Top Scout Candidate</h3>
          <p class="metric">${scoutSummary.topCandidates[0]?.score ?? '-'}</p>
          <p class="metric-subtext">${escapeHtml(scoutSummary.topCandidates[0]?.companyName ?? 'No candidate yet')}</p>
        </div>

        <div class="card">
          <h3>Next Digest</h3>
          <p class="metric metric--text">${escapeHtml(scoutSummary.nextDigestLabel)}</p>
          <p class="metric-subtext">Research shortlist preview</p>
        </div>
      </div>

      <div class="split-grid">
        <div class="card">
          <div class="page-header">
            <h3>Top Review Candidates</h3>
            <p>Companies to consider researching, not investment recommendations.</p>
          </div>
          ${
            scoutSummary.topCandidates.length
              ? `<div class="mini-list">${scoutSummary.topCandidates
                  .slice(0, 5)
                  .map(
                    (candidate) => `
                      <div class="mini-list__item mini-list__item--stacked">
                        <strong><a class="table-link" href="${escapeHtml(candidate.appLink)}">${escapeHtml(candidate.companyName)}</a> (${candidate.score})</strong>
                        <span>${escapeHtml(candidate.whyMatched.slice(0, 2).join('; '))}</span>
                      </div>
                    `
                  )
                  .join('')}</div>`
              : '<p class="empty-copy">No scout candidates yet.</p>'
          }
        </div>

        <div class="card">
          <div class="page-header">
            <h3>Scout Review Needs</h3>
            <p>Source changes or sources needing manual paste/update.</p>
          </div>
          ${
            scoutSummary.notableSnapshots.length || scoutSummary.manualUpdateSources.length
              ? `<div class="mini-list">${[
                  ...scoutSummary.notableSnapshots.slice(0, 4).map(
                    (snapshot) => `
                      <div class="mini-list__item mini-list__item--stacked">
                        <strong>Source ${snapshot.sourceId}: ${formatDate(snapshot.checkedAt)}</strong>
                        <span>${escapeHtml(snapshot.notableChanges.join(', '))}</span>
                      </div>
                    `
                  ),
                  ...scoutSummary.manualUpdateSources.slice(0, 4).map(
                    (source) => `
                      <div class="mini-list__item mini-list__item--stacked">
                        <strong>${escapeHtml(source.companyName || 'Manual source needed')}</strong>
                        <span>${escapeHtml(source.lastError || 'Paste allowed source text before the next scout run.')}</span>
                      </div>
                    `
                  )
                ].join('')}</div>`
              : '<p class="empty-copy">No scout follow-up needed.</p>'
          }
        </div>
      </div>

      <div class="split-grid">
        <div class="card">
          <div class="page-header">
            <h3>Count by Platform</h3>
            <p>Where your deal flow is coming from.</p>
          </div>
          ${renderCountList(platformCounts, 'No platforms yet.')}
        </div>

        <div class="card">
          <div class="page-header">
            <h3>Count by Sector</h3>
            <p>Which markets you are reviewing most.</p>
          </div>
          ${renderCountList(sectorCounts, 'No sectors yet.')}
        </div>
      </div>

      <div class="card table-card">
        <div class="page-header">
          <h3>Highest Scoring Deals</h3>
          <p>Top final-score deals, still risky and still requiring judgment.</p>
        </div>

        <table class="data-table">
          <thead>
            <tr>
              <th>Company</th>
              <th>Platform</th>
              <th>Decision</th>
              <th>Final</th>
              <th>Risk</th>
            </tr>
          </thead>
          <tbody>
            ${renderDealRows(highestScoringDeals, 'No deals yet.')}
          </tbody>
        </table>
      </div>

      <div class="card table-card">
        <div class="page-header">
          <h3>Eligibility Risks</h3>
          <p>Deals that may be unclear or unavailable to non-accredited investors.</p>
        </div>

        <table class="data-table">
          <thead>
            <tr>
              <th>Company</th>
              <th>Platform</th>
              <th>Eligibility</th>
              <th>Exemption</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            ${
              eligibilityRiskDeals.length
                ? eligibilityRiskDeals
                    .map(
                      (deal) => `
                        <tr>
                          <td><a class="table-link" href="#/deals/${deal.id}">${escapeHtml(deal.companyName)}</a></td>
                          <td>${escapeHtml(deal.platform || '-')}</td>
                          <td>${formatInvestorEligibility(deal.investorEligibility)}</td>
                          <td>${formatOfferingExemption(deal.offeringExemption)}</td>
                          <td>${formatDate(deal.updatedAt)}</td>
                        </tr>
                      `
                    )
                    .join('')
                : `
                  <tr>
                    <td colspan="5">No unclear, Reg D, or accredited-only deals.</td>
                  </tr>
                `
            }
          </tbody>
        </table>
      </div>

      <div class="card table-card">
        <div class="page-header">
          <h3>Watchlist Follow-Up</h3>
          <p>Watch decisions with no review date or a review date due today or earlier.</p>
        </div>

        <table class="data-table">
          <thead>
            <tr>
              <th>Company</th>
              <th>Platform</th>
              <th>Next Review</th>
              <th>Next Milestone</th>
              <th>Final</th>
            </tr>
          </thead>
          <tbody>
            ${
              watchlistNeedsFollowUp.length
                ? watchlistNeedsFollowUp
                    .map(
                      (deal) => `
                        <tr>
                          <td><a class="table-link" href="#/deals/${deal.id}">${escapeHtml(deal.companyName)}</a></td>
                          <td>${escapeHtml(deal.platform || '-')}</td>
                          <td>${formatDate(deal.review?.nextReviewDate)}</td>
                          <td>${escapeHtml(deal.nextMilestone || deal.decisionNotes?.nextMilestoneNeeded || '-')}</td>
                          <td>${getFinalScore(deal)}</td>
                        </tr>
                      `
                    )
                    .join('')
                : `
                  <tr>
                    <td colspan="5">No watchlist follow-up due.</td>
                  </tr>
                `
            }
          </tbody>
        </table>
      </div>

      <div class="card table-card">
        <div class="page-header">
          <h3>Evidence Follow-Up Queue</h3>
          <p>Deals with unclear access, missing support, low confidence, milestones, or due reviews.</p>
        </div>

        <table class="data-table">
          <thead>
            <tr>
              <th>Company</th>
              <th>Data Confidence</th>
              <th>Reasons</th>
              <th>Next Review</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            ${
              followUpQueue.length
                ? followUpQueue
                    .map(({ deal, reasons }) => {
                      const dataConfidence = getDataConfidenceScore(deal);

                      return `
                        <tr>
                          <td><a class="table-link" href="#/deals/${deal.id}">${escapeHtml(deal.companyName)}</a></td>
                          <td>
                            <div>${dataConfidence}</div>
                            <div class="table-subtext">${escapeHtml(getDataConfidenceLabel(dataConfidence))}</div>
                          </td>
                          <td>
                            <div class="suggestion-tags suggestion-tags--inline">
                              ${reasons.map((reason) => `<span>${escapeHtml(reason)}</span>`).join('')}
                            </div>
                          </td>
                          <td>${formatDate(deal.review?.nextReviewDate)}</td>
                          <td>${formatDate(deal.updatedAt)}</td>
                        </tr>
                      `;
                    })
                    .join('')
                : `
                  <tr>
                    <td colspan="5">No evidence follow-up needed.</td>
                  </tr>
                `
            }
          </tbody>
        </table>
      </div>
    </div>
  `;
}

export function bindDashboardPageEvents(): void {
  // Static local dashboard for now.
}
