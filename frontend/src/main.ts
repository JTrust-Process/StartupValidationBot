import './styles/main.css';
import { renderApp } from './components/appShell';
import { clearDealsCache, loadDeals } from './services/dealService';
import { getRadarAdminSession } from './services/radarService';

const root = document.querySelector<HTMLDivElement>('#app');

if (!root) {
  throw new Error('App root not found');
}

function renderBootLoading(rootElement: HTMLDivElement): void {
  rootElement.innerHTML = `
    <div class="page page--centered">
      <div class="card card--status">
        <h2>Loading workspace</h2>
        <p>Loading your private research workspace...</p>
      </div>
    </div>
  `;
}

function renderBootError(rootElement: HTMLDivElement): void {
  rootElement.innerHTML = `
    <div class="page page--centered">
      <div class="card card--status">
        <h2>Failed to load app data</h2>
        <p>The private workspace server could not be reached. Try again after checking the backend.</p>
        <div class="form-actions form-actions--start">
          <button id="retry-bootstrap-button" class="button button--primary" type="button">
            Retry
          </button>
        </div>
      </div>
    </div>
  `;

  const retryButton = rootElement.querySelector<HTMLButtonElement>('#retry-bootstrap-button');
  if (!retryButton) return;

  retryButton.addEventListener('click', () => {
    void bootstrap(rootElement);
  });
}

async function bootstrap(rootElement: HTMLDivElement): Promise<void> {
  renderBootLoading(rootElement);

  try {
    const session = await getRadarAdminSession();
    if (session.authenticated) await loadDeals();
    renderApp(rootElement);
  } catch (error) {
    console.error('Failed to bootstrap app:', error);
    renderBootError(rootElement);
  }
}

window.addEventListener('radar-authenticated', () => {
  void loadDeals()
    .then(() => window.dispatchEvent(new HashChangeEvent('hashchange')))
    .catch((error) => console.error('Failed to load deal workspace:', error));
});
window.addEventListener('radar-logged-out', () => {
  clearDealsCache();
  window.dispatchEvent(new HashChangeEvent('hashchange'));
});

void bootstrap(root);
