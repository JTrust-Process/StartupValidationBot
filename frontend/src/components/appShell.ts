import { getCurrentRoute, navigateTo, onRouteChange } from '../utils/router';
import { bindDashboardPageEvents, renderDashboardPage } from '../pages/dashboardPage';
import { renderDealsPage } from '../pages/dealsPage';
import { bindNewDealPageEvents, renderNewDealPage } from '../pages/newDealPage';
import {
  bindDealWorkspacePageEvents,
  renderDealWorkspacePage
} from '../pages/dealWorkspacePage';
import { getDealById, loadDealById } from '../services/dealService';

function getPageHtml(path: string): string {
  if (path === '/dashboard') return renderDashboardPage();
  if (path === '/deals') return renderDealsPage();
  if (path === '/deals/new') return renderNewDealPage();
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
      (route === '/deals' && path.startsWith('/deals/') && path !== '/deals/new');

    link.classList.toggle('active', Boolean(isActive));
  });
}

function bindPageEvents(root: HTMLDivElement, path: string): void {
  const pageContent = root.querySelector<HTMLElement>('#page-content');
  if (!pageContent) return;

  if (path === '/dashboard') {
    bindDashboardPageEvents();
    return;
  }

  if (path === '/deals/new') {
    bindNewDealPageEvents(pageContent);
    return;
  }

  if (path.startsWith('/deals/')) {
    bindDealWorkspacePageEvents(pageContent, path);
  }
}

async function ensureWorkspaceDealLoaded(path: string): Promise<void> {
  if (!path.startsWith('/deals/')) return;
  if (path === '/deals/new') return;

  const id = Number(path.split('/').pop() ?? '');
  if (!id) return;

  const existingDeal = getDealById(id);
  if (existingDeal) return;

  await loadDealById(id);
}

async function renderLayout(root: HTMLDivElement): Promise<void> {
  const currentRoute = getCurrentRoute();

  await ensureWorkspaceDealLoaded(currentRoute.path);

  root.innerHTML = `
    <div class="app-shell">
      <aside class="sidebar">
        <div class="sidebar__brand">Startup Validation Bot</div>

        <nav class="sidebar__nav">
          <a href="#/dashboard" class="nav-link" data-route="/dashboard">Dashboard</a>
          <a href="#/deals" class="nav-link" data-route="/deals">Deals</a>
          <a href="#/deals/new" class="nav-link" data-route="/deals/new">New Deal</a>
        </nav>
      </aside>

      <main class="main-content">
        <header class="topbar">
          <div>
            <h1 class="topbar__title">Deal Diligence Workstation</h1>
            <p class="topbar__subtitle">
              Structured startup diligence, scoring, and thesis tracking for real investing decisions.
            </p>
          </div>
        </header>

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
      root.innerHTML = `
        <div style="padding: 24px; color: white;">
          <h2>Failed to render page</h2>
          <p>Please make sure the backend is running and try again.</p>
        </div>
      `;
    }
  };

  onRouteChange(() => {
    void rerender();
  });

  if (!window.location.hash) {
    window.location.hash = '/dashboard';
  }

  void rerender();
}