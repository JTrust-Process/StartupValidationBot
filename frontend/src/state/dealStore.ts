import type {
  Deal,
  DecisionData,
  DeepDiligenceData,
  QuickScreenData
} from '../models/deal';

const STORAGE_KEY = 'startup-validation-bot.deals';

const initialDeals: Deal[] = [
  {
    id: '1',
    companyName: 'Aegis Motion',
    sector: 'Defense Tech',
    platform: 'Wefunder',
    roundType: 'SAFE',
    shortDescription: 'Autonomous mobility systems for contested logistics.',
    quickScore: 8,
    deepScore: 18,
    status: 'watch',
    minimumInvestment: 100,
    valuation: 12000000,
    createdAt: new Date().toISOString(),
    quickScreen: {
      businessClarity: 2,
      tractionEvidence: 1,
      edge: 2,
      priceSanity: 1,
      trustTransparency: 2,
      total: 8,
      whatIsIt: 'Autonomous logistics systems for military and contested environments.',
      whyMightItWin: 'Strong defense use case with clear operational need.',
      bestProofPoint: 'Pilot engagement with defense-adjacent logistics operators.',
      biggestDoubt: 'Need stronger proof of repeatable deployment.',
      whySpendingTime: 'Fits dual-use / defense interest area.'
    },
    decision: {
      status: 'watch',
      rationale: 'Interesting defense-aligned opportunity, but needs more execution proof.',
      whatWouldChangeMyMind: 'Clear pilot conversion and evidence of repeat deployments.',
      nextMilestoneNeeded: 'Signed production or repeat pilot contract.'
    },
    deepDiligence: {
      businessModelScore: 4,
      businessModelNote: 'Clear defense logistics use case and monetization path.',
      marketCustomerScore: 3,
      marketCustomerNote: 'Customer is understandable, but procurement path still needs proof.',
      tractionQualityScore: 3,
      tractionQualityNote: 'Some pilot evidence, but still early.',
      competitiveEdgeScore: 4,
      competitiveEdgeNote: 'Strong positioning in a valuable defense workflow.',
      riskScore: 4,
      riskNote: 'Execution risk remains high despite clear need.',
      total: 18
    }
  },
  {
    id: '2',
    companyName: 'Harbor Grid',
    sector: 'Energy',
    platform: 'Republic',
    roundType: 'Equity',
    shortDescription: 'Grid resilience software for industrial energy operators.',
    quickScore: 6,
    deepScore: 14,
    status: 'pass',
    minimumInvestment: 50,
    valuation: 8000000,
    createdAt: new Date().toISOString(),
    deepDiligence: {
      businessModelScore: 3,
      businessModelNote: 'Business model is understandable.',
      marketCustomerScore: 3,
      marketCustomerNote: 'Customer is identifiable but buyer urgency is mixed.',
      tractionQualityScore: 2,
      tractionQualityNote: 'Limited evidence of strong traction.',
      competitiveEdgeScore: 3,
      competitiveEdgeNote: 'Some positioning, but no clear moat.',
      riskScore: 3,
      riskNote: 'Moderate risk, but not enough upside to offset.',
      total: 14
    }
  },
  {
    id: '3',
    companyName: 'Sentinel Forge',
    sector: 'Dual Use',
    platform: 'StartEngine',
    roundType: 'SAFE',
    shortDescription: 'Manufacturing intelligence platform for defense suppliers.',
    quickScore: 9,
    deepScore: 21,
    status: 'invest-small',
    minimumInvestment: 250,
    valuation: 15000000,
    createdAt: new Date().toISOString(),
    deepDiligence: {
      businessModelScore: 4,
      businessModelNote: 'Clear software workflow tied to industrial operations.',
      marketCustomerScore: 4,
      marketCustomerNote: 'Customer segment is attractive and understandable.',
      tractionQualityScore: 4,
      tractionQualityNote: 'Better traction profile than most early deals.',
      competitiveEdgeScore: 5,
      competitiveEdgeNote: 'Strong niche positioning in a valuable dual-use area.',
      riskScore: 4,
      riskNote: 'Still early-stage, but risk/reward looks attractive.',
      total: 21
    }
  }
];

function saveDealsToStorage(deals: Deal[]): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(deals));
}

function loadDealsFromStorage(): Deal[] | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;

    const parsed = JSON.parse(raw) as Deal[];
    if (!Array.isArray(parsed)) return null;

    return parsed;
  } catch (error) {
    console.error('Failed to load deals from localStorage:', error);
    return null;
  }
}

let deals: Deal[] = loadDealsFromStorage() ?? [...initialDeals];

if (!loadDealsFromStorage()) {
  saveDealsToStorage(deals);
}

function updateDeals(nextDeals: Deal[]): void {
  deals = nextDeals;
  saveDealsToStorage(deals);
}

export function getDealsState(): Deal[] {
  return deals;
}

export function getDealStateById(id: string): Deal | undefined {
  return deals.find((deal) => deal.id === id);
}

export function addDealState(
  dealInput: Omit<Deal, 'id' | 'createdAt'>
): Deal {
  const newDeal: Deal = {
    ...dealInput,
    id: crypto.randomUUID(),
    createdAt: new Date().toISOString()
  };

  updateDeals([newDeal, ...deals]);
  return newDeal;
}

export function updateDealQuickScreenState(
  dealId: string,
  quickScreen: QuickScreenData
): Deal | undefined {
  let updatedDeal: Deal | undefined;

  const nextDeals = deals.map((deal) => {
    if (deal.id !== dealId) return deal;

    updatedDeal = {
      ...deal,
      quickScore: quickScreen.total,
      quickScreen
    };

    return updatedDeal;
  });

  updateDeals(nextDeals);
  return updatedDeal;
}

export function updateDealDecisionState(
  dealId: string,
  decision: DecisionData
): Deal | undefined {
  let updatedDeal: Deal | undefined;

  const nextDeals = deals.map((deal) => {
    if (deal.id !== dealId) return deal;

    updatedDeal = {
      ...deal,
      status: decision.status,
      decision
    };

    return updatedDeal;
  });

  updateDeals(nextDeals);
  return updatedDeal;
}

export function updateDealDeepDiligenceState(
  dealId: string,
  deepDiligence: DeepDiligenceData
): Deal | undefined {
  let updatedDeal: Deal | undefined;

  const nextDeals = deals.map((deal) => {
    if (deal.id !== dealId) return deal;

    updatedDeal = {
      ...deal,
      deepScore: deepDiligence.total,
      deepDiligence
    };

    return updatedDeal;
  });

  updateDeals(nextDeals);
  return updatedDeal;
}

export function resetDealsState(): void {
  updateDeals([...initialDeals]);
}