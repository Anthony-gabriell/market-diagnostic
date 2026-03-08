import { useState, useEffect, useCallback } from 'react';
import {
  RefreshCw, TrendingUp, TrendingDown, Clock, Activity,
  X, Bitcoin, BarChart2, CheckCircle, AlertTriangle,
} from 'lucide-react';

import PriceChart from './components/PriceChart';
import {
  getOpportunities,
  runScan,
  getDiagnostic,
  getAnnotations,
  getKlines,
} from './services/api';
import './App.css';

// ── Helpers ───────────────────────────────────────────────────────────────────

function isCrypto(symbol) {
  return symbol.endsWith('USDT') || symbol.endsWith('BUSD') || symbol.endsWith('BTC');
}

function cleanSymbol(sym) {
  return sym.replace('USDT', '').replace('BUSD', '');
}

function formatDateTime(dt) {
  if (!dt) return null;
  return new Date(dt).toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  });
}

function rsiLabel(rsi) {
  const v = Number(rsi);
  if (v < 30) return 'Sobrevendido';
  if (v > 70) return 'Sobrecomprado';
  return 'Neutro';
}

function rsiColor(rsi) {
  const v = Number(rsi);
  if (v < 30) return '#4A7856';
  if (v > 70) return '#EF4444';
  return '#94a3b8';
}

function scoreColor(score) {
  if (score >= 8) return '#4A7856';
  if (score >= 6) return '#EAB308';
  if (score >= 4) return '#F97316';
  return '#EF4444';
}

function scoreLabel(score) {
  if (score >= 8) return 'Forte Compra';
  if (score >= 6) return 'Observar';
  if (score >= 4) return 'Fraco';
  return 'Evitar';
}

function volumeLabel(profile) {
  if (!profile) return '—';
  const map = { HIGH: 'Alto', MEDIUM: 'Médio', LOW: 'Baixo', VERY_HIGH: 'Muito Alto', VERY_LOW: 'Muito Baixo' };
  return map[profile.toUpperCase()] ?? profile;
}

function volatilityLabel(level) {
  if (!level) return '—';
  const map = { HIGH: 'Alta', MEDIUM: 'Média', LOW: 'Baixa', VERY_HIGH: 'Muito Alta', EXTREME: 'Extrema' };
  return map[level.toUpperCase()] ?? level;
}

function actionColor(plan) {
  if (!plan) return '#94a3b8';
  if (plan.startsWith('ENTER'))  return '#4A7856';
  if (plan.startsWith('WAIT'))   return '#EAB308';
  if (plan.startsWith('REDUCE')) return '#F97316';
  return '#EF4444';
}

// ── RadialScore ───────────────────────────────────────────────────────────────

function RadialScore({ score, size = 60 }) {
  const r    = size / 2 - 6;
  const cx   = size / 2;
  const cy   = size / 2;
  const circ = 2 * Math.PI * r;
  const fill = Math.min(score / 10, 1) * circ;
  const color = scoreColor(score);

  return (
    <svg width={size} height={size} className="shrink-0">
      <circle cx={cx} cy={cy} r={r} fill="none" stroke="rgba(255,255,255,0.07)" strokeWidth="5" />
      <circle
        cx={cx} cy={cy} r={r}
        fill="none"
        stroke={color}
        strokeWidth="5"
        strokeDasharray={`${fill} ${circ - fill}`}
        strokeLinecap="round"
        transform={`rotate(-90 ${cx} ${cy})`}
        style={{ filter: `drop-shadow(0 0 5px ${color}90)` }}
      />
      <text
        x={cx} y={cy}
        textAnchor="middle" dominantBaseline="middle"
        fill="white" fontSize={size * 0.22} fontWeight="700"
        fontFamily="-apple-system, BlinkMacSystemFont, sans-serif"
      >
        {score.toFixed(1)}
      </text>
    </svg>
  );
}

// ── OpportunityCard ───────────────────────────────────────────────────────────

function OpportunityCard({ coin, onClick, featured = false }) {
  const crypto = isCrypto(coin.symbol);
  const color  = scoreColor(coin.opportunityScore);

  return (
    <div
      onClick={onClick}
      className={`${featured ? 'glass-card-featured' : 'glass-card'} cursor-pointer rounded-xl p-4 hover:scale-[1.02] hover:shadow-xl`}
      style={featured ? { borderColor: `${color}60` } : {}}
    >
      <div className="flex items-start gap-3">
        {/* Left */}
        <div className="min-w-0 flex-1">
          <div className="mb-1 flex items-center gap-2">
            <span className="truncate text-base font-bold text-white">
              {cleanSymbol(coin.symbol)}
            </span>
            <span
              className="shrink-0 rounded px-1.5 py-0.5 text-[9px] font-semibold uppercase tracking-wide"
              style={crypto
                ? { background: 'rgba(99,102,241,0.15)', color: '#a5b4fc' }
                : { background: 'rgba(74,120,86,0.15)',  color: '#86efac' }}
            >
              {crypto ? 'Crypto' : 'B3'}
            </span>
          </div>

          <p className="mb-2 text-xs font-semibold" style={{ color }}>
            {scoreLabel(coin.opportunityScore)}
          </p>

          {/* RSI */}
          <div className="mb-2 flex items-center gap-1.5">
            <span className="text-[10px] uppercase tracking-wide text-slate-500">RSI</span>
            <span className="tabular-nums text-sm font-bold" style={{ color: rsiColor(coin.rsi) }}>
              {Number(coin.rsi).toFixed(1)}
            </span>
            <span className="text-[10px]" style={{ color: rsiColor(coin.rsi) }}>
              {rsiLabel(coin.rsi)}
            </span>
          </div>

          {/* Volume badge */}
          {coin.volumeProfile && (
            <span className="rounded bg-white/5 px-1.5 py-0.5 text-[9px] text-slate-400">
              Vol: {volumeLabel(coin.volumeProfile)}
            </span>
          )}

          {/* Summary */}
          {coin.summary && (
            <p className={`mt-2 text-[11px] leading-relaxed text-slate-400 ${featured ? '' : 'line-clamp-2'}`}>
              {coin.summary}
            </p>
          )}
        </div>

        {/* Radial score */}
        <RadialScore score={coin.opportunityScore} size={featured ? 72 : 58} />
      </div>

      {/* Footer */}
      {coin.scannedAt && (
        <div className="mt-3 flex items-center gap-1 border-t border-white/5 pt-2">
          <Clock className="h-2.5 w-2.5 text-slate-700" />
          <span className="text-[9px] text-slate-600">{formatDateTime(coin.scannedAt)}</span>
        </div>
      )}
    </div>
  );
}

// ── FilterToggle ──────────────────────────────────────────────────────────────

function FilterToggle({ filter, onChange, counts }) {
  const opts = [
    { key: 'all',    label: 'Todos',        count: counts.all,    icon: null },
    { key: 'crypto', label: 'Criptomoedas', count: counts.crypto, icon: <Bitcoin  className="h-3 w-3" /> },
    { key: 'b3',     label: 'Ações B3',     count: counts.b3,     icon: <BarChart2 className="h-3 w-3" /> },
  ];

  return (
    <div className="flex items-center gap-1 rounded-xl bg-white/5 p-1">
      {opts.map(opt => (
        <button
          key={opt.key}
          onClick={() => onChange(opt.key)}
          className="flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-semibold transition-all"
          style={filter === opt.key
            ? { background: '#4A7856', color: '#fff' }
            : { color: '#94a3b8' }}
        >
          {opt.icon}
          {opt.label}
          {opt.count > 0 && (
            <span
              className="rounded-full px-1 text-[9px]"
              style={{ background: 'rgba(255,255,255,0.12)' }}
            >
              {opt.count}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}

// ── MetricCard ────────────────────────────────────────────────────────────────

function MetricCard({ label, value, sub, subColor }) {
  return (
    <div className="glass-card rounded-lg px-3 py-2.5">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">{label}</p>
      <p className="mt-0.5 text-base font-semibold text-white">{value}</p>
      {sub && <p className="mt-0.5 text-[10px]" style={{ color: subColor ?? '#64748b' }}>{sub}</p>}
    </div>
  );
}

// ── AnalysisModal ─────────────────────────────────────────────────────────────

function AnalysisModal({ symbol, diagnostic, klines, annotations, loadingDiag, loadingChart, onClose }) {
  const crypto = isCrypto(symbol);

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-4 sm:items-center">
      <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={onClose} />
      <div
        className="glass-card relative w-full max-w-2xl rounded-2xl overflow-hidden"
        style={{ maxHeight: '88vh', overflowY: 'auto' }}
      >
        {/* Header */}
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-white/5 bg-[#1c1c1c]/90 px-5 py-4 backdrop-blur-md">
          <div className="flex items-center gap-3">
            <h3 className="text-lg font-bold text-white">{cleanSymbol(symbol)}</h3>
            <span
              className="rounded px-1.5 py-0.5 text-[9px] font-semibold uppercase"
              style={crypto
                ? { background: 'rgba(99,102,241,0.15)', color: '#a5b4fc' }
                : { background: 'rgba(74,120,86,0.15)',  color: '#86efac' }}
            >
              {crypto ? 'Crypto' : 'B3'}
            </span>
            {diagnostic && <RadialScore score={diagnostic.opportunityScore} size={44} />}
          </div>
          <button
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-white/10 hover:text-white"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        <div className="p-5 space-y-4">
          {loadingDiag ? (
            <div className="flex items-center justify-center py-16">
              <div className="h-6 w-6 animate-spin rounded-full border-2 border-t-transparent" style={{ borderColor: '#4A7856', borderTopColor: 'transparent' }} />
            </div>
          ) : diagnostic ? (
            <>
              {/* Metrics */}
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
                <MetricCard
                  label="RSI"
                  value={Number(diagnostic.rsi).toFixed(1)}
                  sub={rsiLabel(diagnostic.rsi)}
                  subColor={rsiColor(diagnostic.rsi)}
                />
                <MetricCard label="Volume"       value={volumeLabel(diagnostic.volumeProfile)} />
                <MetricCard label="Volatilidade" value={volatilityLabel(diagnostic.volatilityLevel)} />
                <MetricCard
                  label="Risco/Retorno"
                  value={`${diagnostic.riskRewardRatio?.toFixed(2) ?? '—'}:1`}
                />
              </div>

              {/* Chart (crypto only) */}
              {crypto && (
                <PriceChart
                  symbol={symbol}
                  data={klines}
                  annotations={annotations}
                  height={220}
                  loading={loadingChart}
                />
              )}

              {/* Diagnóstico AI */}
              <div className="glass-card rounded-xl p-4">
                <div className="mb-3 flex items-center gap-2">
                  <div className="flex h-5 w-5 items-center justify-center rounded-full" style={{ background: 'rgba(74,120,86,0.2)' }}>
                    <span className="text-[9px] font-bold text-green-400">AI</span>
                  </div>
                  <span className="text-[10px] font-semibold uppercase tracking-widest text-slate-400">
                    Diagnóstico
                  </span>
                </div>

                {diagnostic.signals?.length > 0 && (
                  <div className="mb-3 space-y-1.5">
                    {diagnostic.signals.map((signal, i) => (
                      <div key={i} className="flex items-start gap-2">
                        <AlertTriangle className="mt-0.5 h-3 w-3 shrink-0 text-yellow-400" />
                        <p className="text-xs text-yellow-300/80">{signal}</p>
                      </div>
                    ))}
                  </div>
                )}

                <p className="mb-3 text-xs leading-relaxed text-slate-300">{diagnostic.summary}</p>

                {diagnostic.actionPlan && (
                  <div className="rounded-lg border border-white/5 bg-black/20 p-3">
                    <div className="mb-1.5 flex items-center gap-1.5">
                      <CheckCircle className="h-3 w-3 text-slate-600" />
                      <span className="text-[10px] font-semibold uppercase tracking-widest text-slate-500">
                        Plano de Ação
                      </span>
                    </div>
                    <p className="text-xs font-medium leading-relaxed" style={{ color: actionColor(diagnostic.actionPlan) }}>
                      {diagnostic.actionPlan}
                    </p>
                  </div>
                )}
              </div>
            </>
          ) : (
            <p className="py-16 text-center text-sm text-slate-500">Falha ao carregar diagnóstico.</p>
          )}
        </div>
      </div>
    </div>
  );
}

// ── App ───────────────────────────────────────────────────────────────────────

export default function App() {
  const [coins, setCoins]               = useState([]);
  const [filter, setFilter]             = useState('all');
  const [selectedSymbol, setSelected]   = useState(null);
  const [diagnostic, setDiagnostic]     = useState(null);
  const [klines, setKlines]             = useState([]);
  const [annotations, setAnnotations]   = useState([]);
  const [scanning, setScanning]         = useState(false);
  const [loadingDiag, setLoadingDiag]   = useState(false);
  const [loadingChart, setLoadingChart] = useState(false);

  const loadOpportunities = useCallback(async () => {
    try {
      const data = await getOpportunities();
      setCoins(data);
    } catch (err) {
      console.error('Falha ao carregar oportunidades:', err);
    }
  }, []);

  const handleScan = async () => {
    setScanning(true);
    try {
      const data = await runScan();
      setCoins(data);
    } catch (err) {
      console.error('Scan falhou:', err);
    } finally {
      setScanning(false);
    }
  };

  const selectSymbol = async (symbol) => {
    if (symbol === selectedSymbol) { setSelected(null); return; }

    setSelected(symbol);
    setDiagnostic(null);
    setKlines([]);
    setAnnotations([]);
    setLoadingDiag(true);
    setLoadingChart(true);

    const crypto = isCrypto(symbol);
    const [diagResult, klinesResult, annotationsResult] = await Promise.allSettled([
      getDiagnostic(symbol),
      crypto ? getKlines(symbol) : Promise.resolve([]),
      crypto ? getAnnotations(symbol) : Promise.resolve([]),
    ]);

    if (diagResult.status === 'fulfilled')       setDiagnostic(diagResult.value);
    setLoadingDiag(false);
    if (klinesResult.status === 'fulfilled')     setKlines(klinesResult.value);
    if (annotationsResult.status === 'fulfilled') setAnnotations(annotationsResult.value);
    setLoadingChart(false);
  };

  useEffect(() => { loadOpportunities(); }, [loadOpportunities]);

  const cryptoCoins    = coins.filter(c => isCrypto(c.symbol));
  const b3Coins        = coins.filter(c => !isCrypto(c.symbol));
  const filteredCoins  = filter === 'crypto' ? cryptoCoins : filter === 'b3' ? b3Coins : coins;
  const top3           = coins.slice(0, 3);
  const lastUpdated    = coins[0]?.scannedAt ?? null;

  return (
    <div className="min-h-screen font-sans antialiased text-slate-100" style={{ background: '#121212' }}>

      {/* ── Header ─────────────────────────────────────────────────────────── */}
      <header
        className="sticky top-0 z-40 border-b px-5 py-3"
        style={{ background: 'rgba(18,18,18,0.92)', borderColor: 'rgba(74,120,86,0.15)', backdropFilter: 'blur(16px)' }}
      >
        <div className="mx-auto flex max-w-7xl items-center gap-3">
          <div className="flex items-center gap-2">
            <div
              className="flex h-7 w-7 items-center justify-center rounded-lg"
              style={{ background: 'rgba(74,120,86,0.2)' }}
            >
              <TrendingUp className="h-4 w-4 text-green-400" />
            </div>
            <span className="font-bold tracking-tight text-white">ATECH</span>
            <span className="text-slate-600">·</span>
            <span className="text-sm text-slate-400">Crypto Interpreter</span>
          </div>

          <div className="ml-auto flex items-center gap-3">
            {lastUpdated && (
              <div className="hidden items-center gap-1.5 sm:flex">
                <Clock className="h-3 w-3 text-slate-600" />
                <span className="text-[11px] text-slate-500">
                  Última atualização: {formatDateTime(lastUpdated)}
                </span>
              </div>
            )}

            <button
              onClick={handleScan}
              disabled={scanning}
              className="flex items-center gap-2 rounded-lg px-3 py-1.5 text-xs font-semibold text-white transition-all disabled:opacity-60"
              style={{ background: scanning ? '#3A6244' : '#4A7856' }}
            >
              <RefreshCw className={`h-3.5 w-3.5 ${scanning ? 'animate-spin' : ''}`} />
              {scanning ? 'Analisando…' : 'Novo Scan'}
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl space-y-8 px-5 py-6">

        {/* ── Top 3 Oportunidades ──────────────────────────────────────────── */}
        {top3.length > 0 && (
          <section>
            <div className="mb-3 flex items-center gap-2">
              <Activity className="h-4 w-4 text-green-400" />
              <h2 className="text-xs font-bold uppercase tracking-widest text-slate-400">
                Top 3 Oportunidades
              </h2>
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              {top3.map(coin => (
                <OpportunityCard
                  key={coin.symbol}
                  coin={coin}
                  featured
                  onClick={() => selectSymbol(coin.symbol)}
                />
              ))}
            </div>
          </section>
        )}

        {/* ── Todos os ativos ──────────────────────────────────────────────── */}
        <section>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
            <FilterToggle
              filter={filter}
              onChange={setFilter}
              counts={{ all: coins.length, crypto: cryptoCoins.length, b3: b3Coins.length }}
            />
            {coins.length > 0 && (
              <span className="text-[11px] text-slate-600">
                {filteredCoins.length} ativo{filteredCoins.length !== 1 ? 's' : ''}
              </span>
            )}
          </div>

          {filteredCoins.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-24 text-center">
              <Activity className="mb-3 h-8 w-8 text-slate-700" />
              <p className="text-sm text-slate-600">Nenhum dado — execute um novo scan.</p>
            </div>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
              {filteredCoins.map(coin => (
                <OpportunityCard
                  key={coin.symbol}
                  coin={coin}
                  onClick={() => selectSymbol(coin.symbol)}
                />
              ))}
            </div>
          )}
        </section>

        {/* Score legend */}
        {coins.length > 0 && (
          <div className="flex flex-wrap items-center gap-4 pt-2 text-[10px]">
            <span className="text-slate-700">Legenda:</span>
            {[
              { color: '#4A7856', label: '≥8 Forte Compra' },
              { color: '#EAB308', label: '≥6 Observar' },
              { color: '#F97316', label: '≥4 Fraco' },
              { color: '#EF4444', label: '<4 Evitar' },
            ].map(item => (
              <span key={item.label} style={{ color: item.color }}>{item.label}</span>
            ))}
          </div>
        )}
      </main>

      {/* ── Modal de análise ─────────────────────────────────────────────────── */}
      {selectedSymbol && (
        <AnalysisModal
          symbol={selectedSymbol}
          diagnostic={diagnostic}
          klines={klines}
          annotations={annotations}
          loadingDiag={loadingDiag}
          loadingChart={loadingChart}
          onClose={() => setSelected(null)}
        />
      )}
    </div>
  );
}
