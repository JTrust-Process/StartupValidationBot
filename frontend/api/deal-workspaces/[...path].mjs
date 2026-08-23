import { proxyRadarRequest } from '../../server/radarProxyCore.mjs';

export default async function handler(request, response) {
  await proxyRadarRequest(request, response);
}
