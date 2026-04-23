import './styles/main.css';
import { renderApp } from './components/appShell';
import { loadDeals } from './services/dealService';

const root = document.querySelector<HTMLDivElement>('#app');

if (!root) {
  throw new Error('App root not found');
}

async function bootstrap(rootElement: HTMLDivElement): Promise<void> {
  try {
    await loadDeals();
    renderApp(rootElement);
  } catch (error) {
    console.error('Failed to bootstrap app:', error);
    rootElement.innerHTML = `
      <div style="padding: 24px; color: white;">
        <h2>Failed to load app data</h2>
        <p>Make sure the Spring Boot backend is running on port 8080.</p>
      </div>
    `;
  }
}

void bootstrap(root);