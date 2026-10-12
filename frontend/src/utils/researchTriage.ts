import type { ResearchEntry } from '../models/radar';

export interface ResearchFilters {
  status: string;
  lifecycle: string;
  completeness: string;
  platform: string;
  sort: 'attention' | 'deadline' | 'updated';
}
export const defaultResearchFilters: ResearchFilters = {
  status: '', lifecycle: '', completeness: '', platform: '', sort: 'attention'
};
export function filterResearchEntries(entries: ResearchEntry[], filters: ResearchFilters): ResearchEntry[] {
  return entries.filter((entry) => {
    const packet = entry.packet;
    return (!filters.status || packet.status === filters.status)
      && (!filters.lifecycle || (filters.lifecycle === 'ENDED' ? entry.category === 'ENDED' : entry.lifecycle === filters.lifecycle))
      && (!filters.platform || packet.platform === filters.platform)
      && (!filters.completeness || (filters.completeness === 'high' ? packet.completeness >= 80
        : filters.completeness === 'medium' ? packet.completeness >= 50 && packet.completeness < 80 : packet.completeness < 50));
  });
}
// Preserve backend research order; filtering never invents a new score.
export function researchAttention(entries: ResearchEntry[]): ResearchEntry[] {
  const companies = new Set<number>();
  return entries.filter((entry) => {
    if (entry.category === 'ENDED' || entry.category === 'BLOCKED' || entry.researchPriority <= 0
        || companies.has(entry.packet.radarCompanyId)) return false;
    companies.add(entry.packet.radarCompanyId);
    return true;
  }).slice(0, 5);
}
export function sortResearchEntries(entries: ResearchEntry[], sort: ResearchFilters['sort']): ResearchEntry[] {
  if (sort === 'attention') return [...entries];
  return [...entries].sort((a, b) => {
    const left = sort === 'deadline' ? a.packet.deadline : a.packet.lastRefreshedAt;
    const right = sort === 'deadline' ? b.packet.deadline : b.packet.lastRefreshedAt;
    if (left === null && right !== null) return 1;
    if (right === null && left !== null) return -1;
    const difference = (left || '').localeCompare(right || '');
    return (sort === 'updated' ? -difference : difference) || a.packet.id - b.packet.id;
  });
}
