import { useState, useEffect, useRef } from 'react';
import { fetchArbitrageList, DirectionalArbitrage } from './api/arbitrage';

const EXCHANGES = ['Binance', 'Upbit', 'Bithumb'] as const;
type FxType = 'usdtkrw' | 'usdkrw';

export default function App() {
  const [fromEx, setFromEx] = useState<typeof EXCHANGES[number]>('Binance');
  const [toEx, setToEx] = useState<typeof EXCHANGES[number]>('Upbit');
  const [fx, setFx] = useState<FxType>('usdkrw');
  const [rows, setRows] = useState<DirectionalArbitrage[]>([]);
  const [loading, setLoading] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<string>('');
  const firstLoaded = useRef<boolean>(false);

  const fmtNumber = (val: string | number, digits = 2) => {
    const n = typeof val === 'number' ? val : parseFloat(String(val));
    if (!isFinite(n)) return String(val);
    return n.toLocaleString(undefined, { minimumFractionDigits: 0, maximumFractionDigits: digits });
  };

  const fmtPercent = (val: string | number, digits = 2) => {
    const n = typeof val === 'number' ? val : parseFloat(String(val));
    if (!isFinite(n)) return String(val);
    return `${n.toFixed(digits)}`;
  };

  const fetchData = async () => {
    setErr(null);
    if (!firstLoaded.current) setLoading(true);
    try {
      const data = await fetchArbitrageList({ from: fromEx, to: toEx, fx, fees: 'exclude', limit: 200 });
      // 첫 로딩에는 KRW 거래대금 합(from+to) 내림차순으로 한 번 정렬, 이후에는 기존 순서 유지
      if (!firstLoaded.current) {
        const notionalSum = (d: DirectionalArbitrage) => {
          const fn = d.from_notional_24h ? parseFloat(String(d.from_notional_24h)) : NaN;
          const tn = d.to_notional_24h ? parseFloat(String(d.to_notional_24h)) : NaN;
          const fs = isFinite(fn) ? fn : 0;
          const ts = isFinite(tn) ? tn : 0;
          const sum = fs + ts;
          return isFinite(sum) ? sum : Number.NEGATIVE_INFINITY;
        };
        data.sort((a,b)=> notionalSum(b) - notionalSum(a));
        setRows(data);
        firstLoaded.current = true;
      } else {
        const bySymbol = new Map(data.map(d => [d.symbol, d] as const));
        setRows(prev => prev.map(r => bySymbol.get(r.symbol) ?? r));
      }
      setLastUpdated(new Date().toLocaleTimeString());
    } catch (e: any) {
      setErr(e?.response?.data || e?.message || 'Failed');
      // 에러 시 기존 표시 유지
    } finally { setLoading(false); }
  };

  // polling every 2s; refresh when controls change
  useEffect(() => {
    fetchData();
    const id = setInterval(fetchData, 2000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fromEx, toEx, fx]);

  return (
    <div className="app">
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="brand"><span className="dot" />Crypto Arb</div>
        <nav className="nav">
          <h4>탐색</h4>
          <a href="#">김치 프리미엄</a>
        </nav>
        <div style={{marginTop:'auto',fontSize:12,color:'var(--muted)'}}>v0.1.0</div>
      </aside>

      {/* Content */}
      <section className="content">
        {/* Header controls */}
        <header className="header">
          <div style={{fontWeight:700}}>김치 프리미엄</div>
          <div className="controls">
            <select className="select" value={fromEx} onChange={e=>setFromEx(e.target.value as any)}>
              {EXCHANGES.map(x=> <option key={x} value={x}>{x}</option>)}
            </select>
            <select className="select" value={toEx} onChange={e=>setToEx(e.target.value as any)}>
              {EXCHANGES.map(x=> <option key={x} value={x}>{x}</option>)}
            </select>
            <select className="select" value={fx} onChange={e=>setFx(e.target.value as FxType)}>
              <option value="usdtkrw">USDT/KRW</option>
              <option value="usdkrw">USD/KRW</option>
            </select>
            <span className="hint">업데이트: {lastUpdated || '-'}</span>
            <span className="hint" style={{marginLeft:8}}>코인: {rows.length}개</span>
            {rows.length > 0 && (
              <span className="hint" style={{marginLeft:8}}>
                환율: {rows[0].fx_type.toUpperCase()} {fmtNumber(parseFloat(rows[0].fx_rate), 4)}
              </span>
            )}
          </div>
        </header>

        {/* Page */}
        <div className="page">
          <div className="hero card">
            <div style={{display:'flex',alignItems:'center',justifyContent:'space-between'}}>
              <div>
                <div style={{fontSize:18,fontWeight:700}}>내 환급 수수료 확인하기</div>
                <div className="hint">USDT 환전 기준 수수료를 확인하고 더 나은 방향을 찾으세요.</div>
              </div>
              <div>
                <span className="kbd">F</span><span className="hint"> 빠른 검색</span>
              </div>
            </div>
          </div>

          <div className="card table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>코인</th>
                  <th>From</th>
                  <th>To</th>
                  <th>프리미엄</th>
                  <th>거래대금(합·KRW)</th>
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 && !loading && (
                  <tr>
                    <td colSpan={4} style={{color:'var(--muted)',padding:'18px'}}>데이터 준비 중...</td>
                  </tr>
                )}
                {rows.map((data)=> {
                  const fn = data.from_notional_24h ? parseFloat(String(data.from_notional_24h)) : NaN;
                  const tn = data.to_notional_24h ? parseFloat(String(data.to_notional_24h)) : NaN;
                  const sum = (isFinite(fn)?fn:0) + (isFinite(tn)?tn:0);
                  return (
                  <tr key={`${data.symbol}-${data.from_exchange}-${data.to_exchange}`}>
                    <td style={{fontWeight:700}}>{data.symbol}</td>
                    <td><span className="num price">{fmtNumber(data.from_price, 2)}</span></td>
                    <td><span className="num price">{fmtNumber(data.to_price, 2)}</span></td>
                    <td>
                      <span className={`badge ${Number(data.profit_percentage) >= 0 ? 'up':'down'} num pct`}>
                        {fmtPercent(data.profit_percentage, 2)}%
                      </span>
                    </td>
                    <td><span className="num">₩ {fmtNumber(sum, 0)}</span></td>
                  </tr>
                )})}
              </tbody>
            </table>
          </div>

          {err && <div style={{ color: 'var(--danger)', marginTop: 12 }}>{String(err)}</div>}
        </div>
      </section>
    </div>
  );
}
