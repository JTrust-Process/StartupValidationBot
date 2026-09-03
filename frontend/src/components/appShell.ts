import { getCurrentRoute, navigateTo, onRouteChange } from '../utils/router';
import { bindDashboardPageEvents, renderDashboardPage } from '../pages/dashboardPage';
import { bindDealsPageEvents, renderDealsPage } from '../pages/dealsPage';
import { bindNewDealPageEvents, renderNewDealPage } from '../pages/newDealPage';
import { bindScoutPageEvents, renderScoutPage } from '../pages/scoutPage';
import { bindTextImportPageEvents, renderTextImportPage } from '../pages/textImportPage';
import { bindRadarHomePageEvents, renderRadarHomePage } from '../pages/radarHomePage';
import { bindRadarPageEvents, renderRadarPage } from '../pages/radarPage';
import { bindWatchlistPageEvents, renderWatchlistPage } from '../pages/watchlistPage';
import { bindTrendsPageEvents, renderTrendsPage } from '../pages/trendsPage';
import { bindRadarCompanyPageEvents, renderRadarCompanyPage } from '../pages/radarCompanyPage';
import { bindRadarAdminPageEvents, renderRadarAdminPage } from '../pages/radarAdminPage';
import { bindOfferingsPageEvents, renderOfferingsPage } from '../pages/offeringsPage';
import { bindReviewQueuePageEvents, renderReviewQueuePage } from '../pages/reviewQueuePage';
import { bindDiligenceDetailPageEvents, renderDiligenceDetailPage } from '../pages/diligenceDetailPage';
import {
  bindDealWorkspacePageEvents,
  renderDealWorkspacePage
} from '../pages/dealWorkspacePage';
import { getDealById, loadDealById } from '../services/dealService';

function renderSidebar(): string {
  return `
    <aside class="sidebar">
      <a class="sidebar__brand" href="#/radar" aria-label="Startup Intelligence home">
        <span class="sidebar__brand-mark" aria-hidden="true">SI</span>
        <span>Startup Intelligence</span>
      </a>

      <nav class="sidebar__nav" aria-label="Primary navigation">
        <div class="nav-group">
          <span class="nav-section-label">Intelligence</span>
          <a href="#/radar" class="nav-link" data-route="/radar">Intelligence Feed</a>
          <a href="#/radar/all" class="nav-link" data-route="/radar/all">All Companies</a>
          <a href="#/watchlist" class="nav-link" data-route="/watchlist">Watchlist</a>
          <a href="#/trends" class="nav-link" data-route="/trends">Trends</a>
        </div>
        <div class="nav-group">
          <span class="nav-section-label">Diligence</span>
          <a href="#/dashboard" class="nav-link" data-route="/dashboard">Dashboard</a>
          <a href="#/offerings" class="nav-link" data-route="/offerings">Offerings</a>
          <a href="#/review" class="nav-link" data-route="/review">Review Queue</a>
          <a href="#/deals" class="nav-link" data-route="/deals">Deals</a>
          <a href="#/deals/new" class="nav-link" data-route="/deals/new">New Deal</a>
          <a href="#/import-text" class="nav-link" data-route="/import-text">Text Import</a>
          <a href="#/scout" class="nav-link" data-route="/scout">Deal Scout</a>
        </div>
        <div class="nav-group">
          <span class="nav-section-label">System</span>
          <a href="#/radar-admin" class="nav-link" data-route="/radar-admin">Admin</a>
        </div>
      </nav>
    </aside>
  `;
}

function getPageHtml(path: string): string {
  if (path === '/radar') return renderRadarHomePage();
  if (path === '/radar/all') return renderRadarPage();
  if (path === '/watchlist') return renderWatchlistPage();
  if (path === '/trends') return renderTrendsPage();
  if (path.startsWith('/radar/company/')) return renderRadarCompanyPage();
  if (path === '/radar-admin') return renderRadarAdminPage();
  if (path === '/offerings') return renderOfferingsPage();
  if (path === '/review') return renderReviewQueuePage();
  if (path.startsWith('/review/')) return renderDiligenceDetailPage();
  if (path === '/dashboard') return renderDashboardPage();
  if (path === '/deals') return renderDealsPage();
  if (path.startsWith('/deals/new')) return renderNewDealPage();
  if (path === '/import-text') return renderTextImportPage();
  if (path === '/scout') return renderScoutPage();
  if (path.startsWith('/deals/')) return renderDealWorkspacePage(path);

  return renderDashboardPage();
}

function bindNavEvents(root: HTMLDivElement): void {
  const navLinks = root.querySelectorAll<HTMLElement>('[data-route]');

  navLinks.forEach((link) => {
    link.addEventListener('click', (event) => {
      event.preventDefault();
      const route = link.dataset.route;

      if (!route) return;
      navigateTo(route);
    });
  });
}

function updateActiveNav(root: HTMLDivElement, path: string): void {
  const navLinks = root.querySelectorAll<HTMLElement>('[data-route]');

  navLinks.forEach((link) => {
    const route = link.dataset.route;
    const isActive =
      route === path ||
      (route === '/deals/new' && path.startsWith('/deals/new')) ||
      (route === '/radar' && path.startsWith('/radar/company/')) ||
      (route === '/radar/all' && path === '/radar/all') ||
      (route === '/review' && path.startsWith('/review/')) ||
      (route === '/deals' && path.startsWith('/deals/') && !path.startsWith('/deals/new'));

    link.classList.toggle('active', Boolean(isActive));
  });
}

function bindPageEvents(root: HTMLDivElement, path: string): void {
  const pageContent = root.querySelector<HTMLElement>('#page-content');
  if (!pageContent) return;

  if (path === '/radar') {
    bindRadarHomePageEvents(pageContent);
    return;
  }

  if (path === '/radar/all') {
    bindRadarPageEvents(pageContent);
    return;
  }

  if (path === '/watchlist') {
    bindWatchlistPageEvents(pageContent);
    return;
  }

  if (path === '/trends') {
    bindTrendsPageEvents(pageContent);
    return;
  }

  if (path.startsWith('/radar/company/')) {
    bindRadarCompanyPageEvents(pageContent, path);
    return;
  }

  if (path === '/radar-admin') {
    bindRadarAdminPageEvents(pageContent);
    return;
  }

  if (path === '/offerings') {
    bindOfferingsPageEvents(pageContent);
    return;
  }

  if (path === '/review') {
    bindReviewQueuePageEvents(pageContent);
    return;
  }

  if (path.startsWith('/review/')) {
    bindDiligenceDetailPageEvents(pageContent, path);
    return;
  }

  if (path === '/dashboard') {
    bindDashboardPageEvents(pageContent);
    return;
  }

  if (path === '/deals') {
    bindDealsPageEvents(pageContent);
    return;
  }

  if (path.startsWith('/deals/new')) {
    bindNewDealPageEvents(pageContent, path);
    return;
  }

  if (path === '/import-text') {
    bindTextImportPageEvents(pageContent);
    return;
  }

  if (path === '/scout') {
    bindScoutPageEvents(pageContent);
    return;
  }

  if (path.startsWith('/deals/')) {
    bindDealWorkspacePageEvents(pageContent, path);
  }
}

async function ensureWorkspaceDealLoaded(path: string): Promise<void> {
  if (!path.startsWith('/deals/')) return;
  if (path.startsWith('/deals/new')) return;

  const id = Number(path.split('/').pop() ?? '');
  if (!id) return;

  const existingDeal = getDealById(id);
  if (existingDeal) return;

  await loadDealById(id);
}

function renderPageError(root: HTMLDivElement, message: string): void {
  root.innerHTML = `
    <div class="app-shell">
      ${renderSidebar()}

      <main class="main-content">
        <section class="page-content" id="page-content">
          <div class="page page--centered">
            <div class="card card--status">
              <h2>Page failed to load</h2>
              <p>${message}</p>
              <div class="form-actions form-actions--start">
                <button id="retry-page-button" class="button button--primary" type="button">
                  Retry
                </button>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>
  `;

  bindNavEvents(root);

  const retryButton = root.querySelector<HTMLButtonElement>('#retry-page-button');
  if (!retryButton) return;

  retryButton.addEventListener('click', () => {
    window.dispatchEvent(new HashChangeEvent('hashchange'));
  });
}

async function renderLayout(root: HTMLDivElement): Promise<void> {
  const currentRoute = getCurrentRoute();

  await ensureWorkspaceDealLoaded(currentRoute.path);

  root.innerHTML = `
    <div class="app-shell">
      ${renderSidebar()}

      <main class="main-content">
        <section class="page-content" id="page-content">
          ${getPageHtml(currentRoute.path)}
        </section>
      </main>
    </div>
  `;

  bindNavEvents(root);
  updateActiveNav(root, currentRoute.path);
  bindPageEvents(root, currentRoute.path);
}

export function renderApp(root: HTMLDivElement): void {
  const rerender = async () => {
    try {
      await renderLayout(root);
    } catch (error) {
      console.error('Failed to render app layout:', error);
      renderPageError(root, 'The selected deal could not be loaded. It may have been deleted or is temporarily unavailable.');
    }
  };

  onRouteChange(() => {
    void rerender();
  });

  if (!window.location.hash) {
    window.location.hash = '/radar';
  }

  void rerender();
}
