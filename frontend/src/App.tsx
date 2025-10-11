import { useState, useEffect, useRef } from 'react';
import { fetchArbitrageList, DirectionalArbitrage, fetchKimchiHistory, KimchiHistoryPoint } from './api/arbitrage';

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
  const [hist, setHist] = useState<KimchiHistoryPoint[]>([]);
  const [rangeMin, setRangeMin] = useState<number>(60);
  const [showAll, setShowAll] = useState<boolean>(false);

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

  useEffect(() => {
    fetchData();
    const id = setInterval(fetchData, 2000);
    return () => clearInterval(id);
  }, [fromEx, toEx, fx]);

  useEffect(() => {
    fetchKimchiHistory({ symbol: 'ETH', from: fromEx, to: toEx, minutes: rangeMin })
      .then(setHist)
      .catch(() => setHist([]));
  }, [fromEx, toEx, rangeMin]);

  const Chart = () => {
    const w = 720; // inner view width
    const h = 200;
    const padL = 44; // left for y-axis labels
    const padB = 22; // bottom for x-axis labels
    if (hist.length === 0) return <div className="card" style={{padding:12, marginBottom:12}}>히스토리 없음</div>;
    const pts = hist.map(p => ({
      ts: p.ts,
      x: new Date(p.ts).getTime(),
      y: parseFloat(p.profit_percentage),
      fp: parseFloat(p.from_price_krw),
      tp: parseFloat(p.to_price_krw),
      fn: p.from_notional_24h ? parseFloat(String(p.from_notional_24h)) : NaN,
      tn: p.to_notional_24h ? parseFloat(String(p.to_notional_24h)) : NaN,
    }));
    const minX = Math.min(...pts.map(p=>p.x));
    const maxX = Math.max(...pts.map(p=>p.x));
    // y-domain: include zero and add padding
    let minY = Math.min(0, ...pts.map(p=>p.y));
    let maxY = Math.max(0, ...pts.map(p=>p.y));
    const span = Math.max(1e-6, maxY - minY);
    minY -= span * 0.08;
    maxY += span * 0.08;
    const sx = (x:number) => padL + (w - padL - 10) * ((x - minX) / Math.max(1, (maxX - minX)));
    const sy = (y:number) => (h - padB) - (h - padB - 10) * ((y - minY) / Math.max(1e-9, (maxY - minY)));
    const d = pts.map((p,i)=> `${i===0? 'M':'L'}${sx(p.x)},${sy(p.y)}`).join(' ');
    const [hoverX, setHoverX] = useState<number | null>(null);
    const nearest = (() => {
      if (hoverX == null) return null;
      let best = 0, bestDist = Number.MAX_VALUE;
      for (let i=0;i<pts.length;i++) { const dx = Math.abs(sx(pts[i].x) - hoverX); if (dx < bestDist) { bestDist = dx; best = i; } }
      return pts[best];
    })();
    const xTicks = 4;
    const yTicks = 4;
    const yTickVals = Array.from({length:yTicks+1}, (_,i)=> minY + (i*(maxY-minY)/yTicks));
    const fmtTime = (t:number) => new Date(t).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    return (
      <div className="card" style={{padding:12, marginBottom:12, position:'relative'}}>
        <div style={{display:'flex',justifyContent:'space-between',alignItems:'center',marginBottom:8}}>
          <div style={{fontWeight:700}}>ETH 김프 추이</div>
          <select className="select" value={rangeMin} onChange={e=>setRangeMin(parseInt(e.target.value,10))}>
            <option value={60}>60분</option>
            <option value={360}>6시간</option>
            <option value={1440}>24시간</option>
          </select>
        </div>
        <svg width="100%" height={h} viewBox={`0 0 ${w} ${h}`}
             onMouseMove={e=>{
               const svg = e.currentTarget as SVGSVGElement;
               const ctm = svg.getScreenCTM();
               if (!ctm) return;
               const pt = svg.createSVGPoint();
               pt.x = e.clientX;
               pt.y = e.clientY;
               const loc = pt.matrixTransform(ctm.inverse());
               const clampedX = Math.max(padL, Math.min(w - 10, loc.x));
               setHoverX(clampedX);
             }}
             onMouseLeave={()=>setHoverX(null)}>
          {/* Axes */}
          <line x1={padL} y1={10} x2={padL} y2={h-padB} stroke="#e5e7eb" />
          <line x1={padL} y1={h-padB} x2={w-10} y2={h-padB} stroke="#e5e7eb" />
          {/* Y grid & ticks */}
          {yTickVals.map((v,i)=>{
            const y = sy(v);
            const isZero = Math.abs(v) < 1e-9;
            return (
              <g key={i}>
                <line x1={padL} y1={y} x2={w-10} y2={y} stroke={isZero? '#d1d5db' : '#eef2f7'} strokeDasharray={isZero? '': '3 3'} />
                <text x={padL-8} y={y+4} fontSize="10" textAnchor="end" fill="#6b7280">{v.toFixed(2)}%</text>
              </g>
            );
          })}
          {/* X ticks */}
          {Array.from({length:xTicks+1}, (_,i)=>{
            const t = minX + (i*(maxX-minX)/xTicks);
            const x = sx(t);
            return (
              <text key={i} x={x} y={h-6} fontSize="10" textAnchor="middle" fill="#6b7280">{fmtTime(t)}</text>
            );
          })}
          {/* Area fill under line */}
          <defs>
            <linearGradient id="kpFill" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor="#4f46e5" stopOpacity="0.18" />
              <stop offset="100%" stopColor="#4f46e5" stopOpacity="0.02" />
            </linearGradient>
          </defs>
          <path d={`${d} L ${sx(pts[pts.length-1].x)},${sy(0)} L ${sx(pts[0].x)},${sy(0)} Z`} fill="url(#kpFill)" opacity="0.8" />
          {/* Line */}
          <path d={d} fill="none" stroke="#4f46e5" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
          {/* Hover */}
          {hoverX != null && (
            <g>
              <line x1={hoverX} y1={10} x2={hoverX} y2={h-padB} stroke="#374151" strokeDasharray="4 4" />
              {nearest && <circle cx={sx(nearest.x)} cy={sy(nearest.y)} r="3" fill="#4f46e5" />}
            </g>
          )}
        </svg>
        {hoverX != null && nearest && (
          <div style={{position:'absolute', left: Math.min(w-200, Math.max(padL, hoverX+8)), top: 32, background:'var(--card)', border:'1px solid var(--border)', borderRadius:6, padding:'8px 10px', fontSize:12, boxShadow:'0 4px 12px rgba(0,0,0,0.12)'}}>
            <div style={{fontWeight:700, marginBottom:4}}>{new Date(nearest.x).toLocaleString()}</div>
            <div>김프: <span className="num">{nearest.y.toFixed(2)}%</span></div>
            <div>From: <span className="num">₩ {fmtNumber(nearest.fp, 0)}</span></div>
            <div>To: <span className="num">₩ {fmtNumber(nearest.tp, 0)}</span></div>
            <div>거래대금 합: <span className="num">₩ {fmtNumber((isFinite(nearest.fn)?nearest.fn:0)+(isFinite(nearest.tn)?nearest.tn:0), 0)}</span></div>
          </div>
        )}
      </div>
    );
  };

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

          <Chart />

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
                {(showAll ? rows : rows.slice(0, 15)).map((data)=> {
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
            {!showAll && rows.length > 15 && (
              <div style={{display:'flex', justifyContent:'center', padding:'12px'}}>
                <button className="btn" onClick={()=>setShowAll(true)}>더보기</button>
              </div>
            )}
            {showAll && rows.length > 15 && (
              <div style={{display:'flex', justifyContent:'center', padding:'12px'}}>
                <button className="btn" onClick={()=>setShowAll(false)}>접기</button>
              </div>
            )}
          </div>

          {err && <div style={{ color: 'var(--danger)', marginTop: 12 }}>{String(err)}</div>}
        </div>
      </section>
    </div>
  );
}
