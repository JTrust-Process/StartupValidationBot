import { renderCompanyRows, renderRadarError } from '../components/radarUi';
import { getRadarAdminSession, listRadarAdminCompanies } from '../services/radarService';

export function renderWatchlistPage(): string {
  return `
    <div class="page radar-page">
      <div class="page-header">
        <h2>Startup Watchlist</h2>
        <p>Companies you are following for product, traction, funding, or strategic changes.</p>
      </div>
      <section class="radar-company-list" id="radar-watchlist"><div class="radar-empty">Loading watchlist...</div></section>
    </div>
  `;
}

export function bindWatchlistPageEvents(root: HTMLElement): void {
  const list = root.querySelector<HTMLElement>('#radar-watchlist');
  if (!list) return;
  const load = async () => {
    try {
      const session = await getRadarAdminSession();
      if (!session.authenticated) {
        list.innerHTML = '<div class="radar-empty">Sign in under <a href="#/radar-admin">Admin</a> to view the private watchlist.</div>';
        return;
      }
      const companies = await listRadarAdminCompanies({ watched: true });
      list.innerHTML = renderCompanyRows(companies);
    } catch (error) {
      list.innerHTML = renderRadarError(error);
    }
  };
  void load();
}
