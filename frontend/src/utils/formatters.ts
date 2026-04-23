import type { DealStatus, ThesisDirection } from '../models/api';

export function formatDealStatus(status: DealStatus | string): string {
  return status
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

export function formatThesisDirection(direction: ThesisDirection | string): string {
  return direction
    .toLowerCase()
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase());
}