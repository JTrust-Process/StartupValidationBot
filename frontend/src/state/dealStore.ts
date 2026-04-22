import type { Deal, DecisionData, QuickScreenData } from '../models/deal';

const initialDeals: Deal[] = [
  {
    id: '1',
    companyName: 'Aegis Motion',
    sector: 'Defense Tech',
    platform: 'Wefunder',
    roundType: 'SAFE',
    shortDescription: 'Autonomous mobility systems for contested logistics.',
    quickScore: 8,
    deepScore: 34,
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
    deepScore: 24,
    status: 'pass',
    minimumInvestment: 50,
    valuation: 8000000,
    createdAt: new Date().toISOString()
  },
  {
    id: '3',
    companyName: 'Sentinel Forge',
    sector: 'Dual Use',
    platform: 'StartEngine',
    roundType: 'SAFE',
    shortDescription: 'Manufacturing intelligence platform for defense suppliers.',
    quickScore: 9,
    deepScore: 39,
    status: 'invest-small',
    minimumInvestment: 250,
    valuation: 15000000,
    createdAt: new Date().toISOString()
  }
];

let deals: Deal[] = [...initialDeals];

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

  deals = [newDeal, ...deals];
  return newDeal;
}

export function updateDealQuickScreenState(
  dealId: string,
  quickScreen: QuickScreenData
): Deal | undefined {
  let updatedDeal: Deal | undefined;

  deals = deals.map((deal) => {
    if (deal.id !== dealId) return deal;

    updatedDeal = {
      ...deal,
      quickScore: quickScreen.total,
      quickScreen
    };

    return updatedDeal;
  });

  return updatedDeal;
}

export function updateDealDecisionState(
  dealId: string,
  decision: DecisionData
): Deal | undefined {
  let updatedDeal: Deal | undefined;

  deals = deals.map((deal) => {
    if (deal.id !== dealId) return deal;

    updatedDeal = {
      ...deal,
      status: decision.status,
      decision
    };

    return updatedDeal;
  });

  return updatedDeal;
}