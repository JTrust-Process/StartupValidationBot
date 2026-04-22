import './styles/main.css';
import { renderApp } from './components/appShell';

const root = document.querySelector<HTMLDivElement>('#app');

if (!root) {
  throw new Error('App root not found');
}

renderApp(root);