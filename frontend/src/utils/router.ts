export interface RouteInfo {
  path: string;
}

export function getCurrentRoute(): RouteInfo {
  const hash = window.location.hash.replace('#', '') || '/dashboard';
  return { path: hash };
}

export function navigateTo(path: string): void {
  window.location.hash = path;
}

export function onRouteChange(callback: () => void): void {
  window.addEventListener('hashchange', callback);
}