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
  const [priceTip, setPriceTip] = useState<{x:number,y:number,text:string}|null>(null);
  const [tab, setTab] = useState<'scalping'|'kimchi'>(() => {
    const v = localStorage.getItem('app_tab');
    return (v === 'scalping' || v === 'kimchi') ? (v as 'scalping'|'kimchi') : 'kimchi';
  });
  const [tz, setTz] = useState<'KST'|'UTC'>(() => {
    const v = localStorage.getItem('app_tz');
    return (v === 'KST' || v === 'UTC') ? (v as 'KST'|'UTC') : 'KST';
  });
  const [ticks, setTicks] = useState<Record<string,string>>({});
  const [ticksMs, setTicksMs] = useState<Record<string,number>>({});
  const [ticksNext, setTicksNext] = useState<Record<string,number>>({}); // absolute UTC ms of next boundary
  const [countTip, setCountTip] = useState<{x:number,y:number,text:string}|null>(null);

  const fmtKrwCompact = (val: number) => {
    if (!isFinite(val)) return '';
    const abs = Math.abs(val);
    if (abs >= 1e12) return `${(val/1e12).toFixed(2)}조`;
    if (abs >= 1e8) return `${(val/1e8).toFixed(2)}억`;
    return '';
  };

  // -------- Countdown helpers --------
  const pad2 = (n:number)=> (n<10?`0${n}`:`${n}`);
  const fmtRemain = (ms:number)=>{
    if (ms < 0) ms = 0;
    const s = Math.floor(ms/1000);
    const hh = Math.floor(s/3600);
    const mm = Math.floor((s%3600)/60);
    const ss = s%60;
    return hh>0? `${pad2(hh)}:${pad2(mm)}:${pad2(ss)}` : `${pad2(mm)}:${pad2(ss)}`;
  };
  const offsetMsForTz = (zone:'KST'|'UTC') => zone==='KST'? 9*3600*1000 : 0;
  const nextBoundaryMs = (zone:'KST'|'UTC', frame:'1m'|'5m'|'1h'|'6h'|'1d') => {
    const now = Date.now();
    const off = offsetMsForTz(zone);
    const loc = new Date(now + off);
    const y = loc.getUTCFullYear();
    const mon = loc.getUTCMonth();
    const d = loc.getUTCDate();
    const h = loc.getUTCHours();
    const m = loc.getUTCMinutes();
    if (frame==='1m') {
      const next = Date.UTC(y,mon,d,h,m,0,0) + 60*1000;
      return next - (now + off);
    }
    if (frame==='5m') {
      const bucket = Math.floor(m/5)*5;
      const nextMinute = bucket + 5;
      const next = Date.UTC(y,mon,d,h,nextMinute,0,0);
      return next - (now + off);
    }
    if (frame==='1h') {
      const next = Date.UTC(y,mon,d,h,0,0,0) + 3600*1000;
      return next - (now + off);
    }
    if (frame==='6h') {
      const bucket = Math.floor(h/6)*6;
      const nextHour = bucket + 6;
      const next = Date.UTC(y,mon,d,nextHour,0,0,0);
      return next - (now + off);
    }
    // 1d
    const next = Date.UTC(y,mon,d,0,0,0,0) + 24*3600*1000;
    return next - (now + off);
  };

  const nextBoundaryAbs = (zone:'KST'|'UTC', frame:'1m'|'5m'|'1h'|'6h'|'1d') => {
    // nextBoundaryMs returns delta-to-next-boundary accounting for zone.
    // Absolute next boundary in UTC ms = now(UTC) + delta.
    return Date.now() + nextBoundaryMs(zone, frame);
  };

  const fmtAbs = (zone:'KST'|'UTC', absUtcMs:number) => {
    const off = offsetMsForTz(zone);
    const dt = new Date(absUtcMs + off);
    const y = dt.getUTCFullYear(); const mo = pad2(dt.getUTCMonth()+1); const da = pad2(dt.getUTCDate());
    const hh = pad2(dt.getUTCHours()); const mm = pad2(dt.getUTCMinutes()); const ss = pad2(dt.getUTCSeconds());
    return `${y}-${mo}-${da} ${hh}:${mm}:${ss} ${zone}`;
  };

  useEffect(()=>{
    if (tab !== 'scalping') return;
    const update = () => {
      const m1 = nextBoundaryMs(tz,'1m');
      const m5 = nextBoundaryMs(tz,'5m');
      const h1 = nextBoundaryMs(tz,'1h');
      const h6 = nextBoundaryMs(tz,'6h');
      const d1u = nextBoundaryMs('UTC','1d');
      const m1Abs = nextBoundaryAbs(tz,'1m');
      const m5Abs = nextBoundaryAbs(tz,'5m');
      const h1Abs = nextBoundaryAbs(tz,'1h');
      const h6Abs = nextBoundaryAbs(tz,'6h');
      const d1uAbs = nextBoundaryAbs('UTC','1d');
      setTicks({
        '1분봉': fmtRemain(m1),
        '5분봉': fmtRemain(m5),
        '1시간봉': fmtRemain(h1),
        '6시간봉': fmtRemain(h6),
        '1일봉': fmtRemain(d1u),
      });
      setTicksMs({
        '1분봉': m1,
        '5분봉': m5,
        '1시간봉': h1,
        '6시간봉': h6,
        '1일봉': d1u,
      });
      setTicksNext({
        '1분봉': m1Abs,
        '5분봉': m5Abs,
        '1시간봉': h1Abs,
        '6시간봉': h6Abs,
        '1일봉': d1uAbs,
      });
    };
    update();
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, [tz, tab]);

  const getIconUrl = (symbol: string) =>
    `https://cdn.jsdelivr.net/gh/spothq/cryptocurrency-icons@latest/svg/color/${symbol.toLowerCase()}.svg`;

  // persist UI prefs
  useEffect(()=>{ localStorage.setItem('app_tab', tab); }, [tab]);
  useEffect(()=>{ localStorage.setItem('app_tz', tz); }, [tz]);

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
    const [tip, setTip] = useState<{x:number,y:number,text:string}|null>(null);
    const [isHover, setIsHover] = useState<boolean>(false);
    const containerRef = useRef<HTMLDivElement | null>(null);
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
    // Global hide guards
    useEffect(()=>{
      const hide = () => { setTip(null); setHoverX(null); };
      const onMove = (e: MouseEvent) => {
        const el = containerRef.current;
        if (!el) return;
        const rect = el.getBoundingClientRect();
        const x = e.clientX, y = e.clientY;
        const inside = x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
        if (!inside) hide();
      };
      window.addEventListener('scroll', hide, true);
      window.addEventListener('resize', hide);
      window.addEventListener('blur', hide);
      document.addEventListener('visibilitychange', ()=>{ if (document.hidden) hide(); });
      window.addEventListener('mousemove', onMove, true);
      return ()=>{
        window.removeEventListener('scroll', hide, true);
        window.removeEventListener('resize', hide);
        window.removeEventListener('blur', hide);
        document.removeEventListener('visibilitychange', ()=>{ if (document.hidden) hide(); });
        window.removeEventListener('mousemove', onMove, true);
      };
    }, []);
    return (
      <div
        ref={containerRef}
        className="card"
        style={{padding:12, marginBottom:12, position:'relative'}}
        onMouseEnter={()=> setIsHover(true)}
        onMouseLeave={()=>{ setIsHover(false); setTip(null); setHoverX(null); }}
      >
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
               // Only show when inside inner plot horizontally
               if (loc.x < padL || loc.x > (w - 10)) {
                 setHoverX(null);
                 setTip(null);
                 return;
               }
               const clampedX = loc.x;
               setHoverX(loc.x);
               // tooltip at cursor (screen coords)
               const nearestPt = (()=>{
                 let best = 0, bestDist = Number.MAX_VALUE;
                 for (let i=0;i<pts.length;i++) { const dx = Math.abs(sx(pts[i].x) - clampedX); if (dx < bestDist) { bestDist = dx; best = i; } }
                 return pts[best];
               })();
               if (nearestPt) {
                 const text = `${new Date(nearestPt.x).toLocaleString()}\n김프 ${nearestPt.y.toFixed(2)}%  |  From ₩ ${fmtNumber(nearestPt.fp,0)}  |  To ₩ ${fmtNumber(nearestPt.tp,0)}`;
                 setTip({ x: e.clientX + 12, y: e.clientY + 12, text });
               }
             }}
             onMouseLeave={()=>{ setHoverX(null); setTip(null); }}>
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
        {isHover && tip && (
          <div style={{position:'fixed', left: tip.x, top: tip.y, whiteSpace:'pre', background:'var(--card)', border:'1px solid var(--border)', borderRadius:6, padding:'8px 10px', fontSize:12, boxShadow:'0 4px 12px rgba(0,0,0,0.12)', pointerEvents:'none'}}>
            {tip.text}
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
          <a href="#" onClick={(e)=>{e.preventDefault(); setTab('scalping');}} style={{background: tab==='scalping' ? 'var(--chip)' : undefined}}>단타</a>
          <a href="#" onClick={(e)=>{e.preventDefault(); setTab('kimchi');}} style={{background: tab==='kimchi' ? 'var(--chip)' : undefined}}>김치 프리미엄</a>
        </nav>
        <div style={{marginTop:'auto',fontSize:12,color:'var(--muted)'}}>v0.1.0</div>
      </aside>

      {/* Content */}
      <section className="content">
        {/* Header controls */}
        {tab === 'scalping' && (
          <>
            <header className="header">
              <div style={{fontWeight:700}}>단타</div>
              <div className="controls">
                <select className="select" value={tz} onChange={e=>setTz(e.target.value as 'KST'|'UTC')}>
                  <option value="KST">KST (UTC+9)</option>
                  <option value="UTC">UTC</option>
                </select>
              </div>
            </header>

            {/* Page */}
            <div className="page">
              <div className="hero card">
                <div style={{display:'flex',alignItems:'center',justifyContent:'space-between'}}>
                  <div>
                    <div style={{fontSize:18,fontWeight:700}}>주요 시간대 카운트다운</div>
                    <div className="hint">타임프레임 리셋까지 남은 시간과 일봉 리셋(KST/UTC)을 확인하세요.</div>
                  </div>
                </div>
              </div>

              <div className="card" style={{padding:16, position:'relative'}}>
                <div className="pill-row">
                  {Object.entries(ticks).map(([k,v])=> {
                    const ms = ticksMs[k] ?? 0;
                    // Use floor like fmtRemain() does so thresholds match the visible seconds
                    const secs = Math.floor(ms / 1000);
                    const isLong = (k==='1시간봉' || k==='6시간봉' || k==='1일봉');
                    let cls = 'pill';
                    if (secs <= 1) {
                      cls = 'pill danger';
                    } else if (isLong) {
                      // Long frames: customize thresholds per frame
                      if (k==='1시간봉') {
                        // 1h: warn <= 3m, danger <= 1m
                        cls = secs <= 60 ? 'pill danger' : (secs <= 180 ? 'pill warn' : 'pill');
                      } else {
                        // 6h, 1d: warn <= 30m, danger <= 10m
                        cls = secs <= 600 ? 'pill danger' : (secs <= 1800 ? 'pill warn' : 'pill');
                      }
                    } else if (k==='1분봉') {
                      // 1m: warn <= 15s, danger <= 5s
                      cls = secs <= 5 ? 'pill danger' : (secs <= 15 ? 'pill warn' : 'pill');
                    } else if (k==='5분봉') {
                      // 5m: warn <= 60s, danger <= 30s
                      cls = secs <= 30 ? 'pill danger' : (secs <= 60 ? 'pill warn' : 'pill');
                    }
                    const zoneFor: 'KST'|'UTC' = (k==='1일봉(UTC)') ? 'UTC' : tz;
                    const abs = ticksNext[k];
                    return (
                      <div
                        key={k}
                        className={cls}
                        onMouseMove={(e)=>{
                          if (!abs) return;
                          setCountTip({ x: e.clientX+12, y: e.clientY+12, text: `다음 갱신: ${fmtAbs(zoneFor, abs)}`});
                        }}
                        onMouseLeave={()=> setCountTip(null)}
                      >
                        <span className="label">{k}</span><span className="time">{v}</span>
                      </div>
                    );
                  })}
                </div>
                {countTip && (
                  <div style={{position:'fixed', left: countTip.x, top: countTip.y, background:'var(--card)', border:'1px solid var(--border)', borderRadius:6, padding:'6px 8px', fontSize:12, boxShadow:'0 4px 12px rgba(0,0,0,0.12)', pointerEvents:'none'}}>
                    {countTip.text}
                  </div>
                )}
              </div>
            </div>
          </>
        )}
        {tab === 'kimchi' && (
          <>
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
            {priceTip && (
              <div style={{position:'fixed', left: priceTip.x, top: priceTip.y, background:'var(--card)', border:'1px solid var(--border)', borderRadius:6, padding:'6px 8px', fontSize:12, boxShadow:'0 4px 12px rgba(0,0,0,0.12)', pointerEvents:'none'}}>
                {priceTip.text}
              </div>
            )}
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
                    const sumCompact = fmtKrwCompact(sum);
                    const fx = parseFloat(String(data.fx_rate));
                    const fromIsBinance = data.from_exchange === 'Binance';
                    const toIsBinance = data.to_exchange === 'Binance';
                    const fromQuoteCcy = (data.fx_type === 'usdtkrw') ? 'USDT' : 'USD';
                    const toQuoteCcy = (data.fx_type === 'usdtkrw') ? 'USDT' : 'USD';
                    const fromQuote = fromIsBinance && isFinite(fx) && fx !== 0 ? (parseFloat(String(data.from_price)) / fx) : null;
                    const toQuote = toIsBinance && isFinite(fx) && fx !== 0 ? (parseFloat(String(data.to_price)) / fx) : null;
                    return (
                    <tr key={`${data.symbol}-${data.from_exchange}-${data.to_exchange}`}>
                      <td>
                        <div className="coin">
                          <img
                            src={getIconUrl(data.symbol)}
                            alt={data.symbol}
                            onError={(e)=>{
                              const img = e.currentTarget as HTMLImageElement;
                              const tried = img.getAttribute('data-fallback');
                              if (!tried) {
                                img.setAttribute('data-fallback','png');
                                img.src = `https://cdn.jsdelivr.net/gh/spothq/cryptocurrency-icons@latest/png/color/${data.symbol.toLowerCase()}.png`;
                              } else {
                                img.style.display='none';
                              }
                            }}
                          />
                          <span style={{fontWeight:700}}>{data.symbol}</span>
                        </div>
                      </td>
                      <td>
                        <span
                          className="num price"
                          onMouseMove={e=>{
                            if (fromQuote != null) setPriceTip({ x: e.clientX+12, y: e.clientY+12, text: `≈ ${fmtNumber(fromQuote, 4)} ${fromQuoteCcy}`});
                          }}
                          onMouseLeave={()=> setPriceTip(null)}
                        >{fmtNumber(data.from_price, 2)}</span> <span className="unit">KRW</span>
                      </td>
                      <td>
                        <span
                          className="num price"
                          onMouseMove={e=>{
                            if (toQuote != null) setPriceTip({ x: e.clientX+12, y: e.clientY+12, text: `≈ ${fmtNumber(toQuote, 4)} ${toQuoteCcy}`});
                          }}
                          onMouseLeave={()=> setPriceTip(null)}
                        >{fmtNumber(data.to_price, 2)}</span> <span className="unit">KRW</span>
                      </td>
                      <td>
                        <span className={`badge ${Number(data.profit_percentage) >= 0 ? 'up':'down'} num pct`}>
                          {fmtPercent(data.profit_percentage, 2)}%
                        </span>
                      </td>
                      <td>
                        <span className="num">₩ {fmtNumber(sum, 0)}</span>
                        {sumCompact && <span className="unit" style={{marginLeft:6}}>({sumCompact})</span>}
                      </td>
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
          </>
        )}
      </section>
    </div>
  );
}
