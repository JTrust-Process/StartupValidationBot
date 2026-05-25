import './styles/main.css';
import { renderApp } from './components/appShell';
import { loadDeals } from './services/dealService';

const root = document.querySelector<HTMLDivElement>('#app');

if (!root) {
  throw new Error('App root not found');
}

function renderBootLoading(rootElement: HTMLDivElement): void {
  rootElement.innerHTML = `
    <div class="page page--centered">
      <div class="card card--status">
        <h2>Loading workspace</h2>
        <p>Loading your local deal research...</p>
      </div>
    </div>
  `;
}

function renderBootError(rootElement: HTMLDivElement): void {
  rootElement.innerHTML = `
    <div class="page page--centered">
      <div class="card card--status">
        <h2>Failed to load app data</h2>
        <p>The local browser storage could not be read. Try again or restore from a JSON backup.</p>
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
    await loadDeals();
    renderApp(rootElement);
  } catch (error) {
    console.error('Failed to bootstrap app:', error);
    renderBootError(rootElement);
  }
}

void bootstrap(root);
