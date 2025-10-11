import axios from 'axios';

export interface DirectionalArbitrage {
  symbol: string;
  from_exchange: string;
  to_exchange: string;
  from_price: string;
  to_price: string;
  price_difference: string;
  profit_percentage: string;
  estimated_profit_after_fees: string;
  total_fees: string;
  is_profitable: boolean;
  fx_type: string;    // "usdkrw" | "usdtkrw"
  fx_rate: string;    // applied rate
}

export async function fetchArbitrageList(params: {
  from: string;
  to: string;
  fx?: 'usdkrw' | 'usdtkrw';
  fees?: 'include' | 'exclude';
  limit?: number;
}): Promise<DirectionalArbitrage[]> {
  const { from, to, fx = 'usdkrw', fees = 'exclude', limit = 100 } = params;
  const res = await api.get<DirectionalArbitrage[]>('/api/v1/arbitrage', {
    params: { from, to, fx, fees, limit },
  });
  return res.data;
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080',
});

export async function fetchArbitrage(params: {
  symbol: string;
  from: string;
  to: string;
  fx?: 'usdkrw' | 'usdtkrw';
  fees?: 'include' | 'exclude';
}): Promise<DirectionalArbitrage> {
  const { symbol, from, to, fx = 'usdtkrw', fees = 'include' } = params;
  const res = await api.get<DirectionalArbitrage>(`/api/v1/arbitrage/${symbol}`, {
    params: { from, to, fx, fees },
  });
  return res.data;
}

export async function fetchArbitrageMany(params: {
  symbols: string[];
  from: string;
  to: string;
  fx?: 'usdkrw' | 'usdtkrw';
  fees?: 'include' | 'exclude';
}): Promise<DirectionalArbitrage[]> {
  const { symbols, from, to, fx = 'usdtkrw', fees = 'include' } = params;
  const tasks = symbols.map((s) =>
    api
      .get<DirectionalArbitrage>(`/api/v1/arbitrage/${s}`, { params: { from, to, fx, fees } })
      .then((r) => r.data)
      .catch(() => null)
  );
  const results = await Promise.all(tasks);
  return results.filter((x): x is DirectionalArbitrage => !!x);
}
