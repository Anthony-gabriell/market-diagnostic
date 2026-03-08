import {
  Activity,
  RefreshCw,
  TrendingUp,
  TrendingDown,
  Minus,
  ArrowUpDown,
} from 'lucide-react';

// ── Helpers ───────────────────────────────────────────────────────────────────

function cleanSymbol(sym) {
  return sym.replace('USDT', '').replace('BUSD', '');
}

function formatPrice(price) {
  if (price == null) return '—';
  const n = Number(price);
  if (n >= 1000) return '$' + n.toLocaleString(undefined, { maximumFractionDigits: 0 });
  if (n >= 1)    return '$' + n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  return '$' + n.toFixed(4);
}

function scoreBg(score) {
  if (score >= 8) return 'bg-emerald-500/10 text-emerald-400 ring-emerald-500/20';
  if (score >= 6) return 'bg-yellow-500/10  text-yellow-400  ring-yellow-500/20';
  if (score >= 4) return 'bg-orange-500/10  text-orange-400  ring-orange-500/20';
  return             'bg-red-500/10     text-red-400     ring-red-500/20';
}

function scoreLabel(score) {
  if (score >= 8) return 'Strong Buy';
  if (score >= 6) return 'Watch';
  if (score >= 4) return 'Weak';
  return 'Avoid';
}

function rsiColor(rsi) {
  const v = Number(rsi);
  if (v < 30) return 'text-emerald-400';
  if (v > 70) return 'text-red-400';
  return 'text-slate-300';
}

function RsiIcon({ rsi }) {
  const v = Number(rsi);
  if (v < 30) return <TrendingUp  className="h-3 w-3 text-emerald-400" />;
  if (v > 70) return <TrendingDown className="h-3 w-3 text-red-400" />;
  return <Minus className="h-3 w-3 text-slate-500" />;
}

// ── Sub-components ────────────────────────────────────────────────────────────

function ScoreBadge({ score }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${scoreBg(score)}`}
    >
      {score.toFixed(1)}
      <span className="hidden text-[9px] font-normal opacity-70 sm:inline">
        {scoreLabel(score)}
      </span>
    </span>
  );
}

function TableHeader({ children, center, right }) {
  const align = right ? 'text-right' : center ? 'text-center' : 'text-left';
  return (
    <th
      className={`px-4 py-2.5 text-[10px] font-semibold uppercase tracking-widest text-slate-500 ${align}`}
    >
      {children}
    </th>
  );
}

function EmptyState() {
  return (
    <tr>
      <td colSpan={4} className="px-4 py-12 text-center">
        <Activity className="mx-auto mb-2 h-6 w-6 text-slate-700" />
        <p className="text-xs text-slate-600">No data — run a market scan first</p>
      </td>
    </tr>
  );
}

// ── CoinTable ─────────────────────────────────────────────────────────────────

/**
 * Props
 * ─────
 * coins           TopOpportunityDTO[]  — ranked list from the scanner
 * selectedSymbol  string | null        — currently selected symbol
 * onSelect        (symbol: string) => void
 * onScan          () => void
 * scanning        boolean
 *
 * Each coin may optionally carry a `price` field (number | string).
 * When absent the Price column renders "—".
 */
export default function CoinTable({ coins = [], selectedSymbol, onSelect, onScan, scanning }) {
  return (
    <div className="flex h-full flex-col overflow-hidden rounded-xl border border-slate-800 bg-slate-900">

      {/* ── Panel header ─────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between border-b border-slate-800 px-4 py-3">
        <div className="flex items-center gap-2">
          <Activity className="h-4 w-4 text-indigo-400" />
          <span className="text-sm font-semibold text-slate-100">Market Scanner</span>
          {coins.length > 0 && (
            <span className="rounded-full bg-slate-800 px-1.5 py-0.5 text-[10px] font-medium text-slate-400">
              {coins.length}
            </span>
          )}
        </div>

        <button
          onClick={onScan}
          disabled={scanning}
          className="flex items-center gap-1.5 rounded-md bg-indigo-600 px-2.5 py-1.5 text-xs font-medium text-white transition hover:bg-indigo-500 active:scale-95 disabled:opacity-50"
        >
          <RefreshCw className={`h-3 w-3 ${scanning ? 'animate-spin' : ''}`} />
          {scanning ? 'Scanning…' : 'Scan now'}
        </button>
      </div>

      {/* ── Table ────────────────────────────────────────────────────────── */}
      <div className="flex-1 overflow-y-auto">
        <table className="w-full border-collapse">

          <thead className="sticky top-0 z-10 bg-slate-900/95 backdrop-blur-sm">
            <tr className="border-b border-slate-800">
              <TableHeader>
                <span className="flex items-center gap-1">
                  Symbol <ArrowUpDown className="h-2.5 w-2.5 opacity-40" />
                </span>
              </TableHeader>
              <TableHeader right>Price</TableHeader>
              <TableHeader center>RSI</TableHeader>
              <TableHeader right>Score</TableHeader>
            </tr>
          </thead>

          <tbody className="divide-y divide-slate-800/60">
            {coins.length === 0 ? (
              <EmptyState />
            ) : (
              coins.map((coin) => {
                const isSelected = selectedSymbol === coin.symbol;
                return (
                  <tr
                    key={coin.symbol}
                    onClick={() => onSelect?.(coin.symbol)}
                    className={`cursor-pointer transition-colors hover:bg-slate-800/50 ${
                      isSelected ? 'bg-slate-800/70' : ''
                    }`}
                  >
                    {/* Symbol */}
                    <td className="px-4 py-3">
                      <div className="flex items-center gap-2">
                        {isSelected && (
                          <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-indigo-400" />
                        )}
                        <div>
                          <p className="text-sm font-semibold text-slate-100">
                            {cleanSymbol(coin.symbol)}
                          </p>
                          <p className="text-[10px] text-slate-500">{coin.volumeProfile}</p>
                        </div>
                      </div>
                    </td>

                    {/* Price */}
                    <td className="px-4 py-3 text-right">
                      <span className="text-sm font-medium text-slate-200">
                        {formatPrice(coin.price)}
                      </span>
                    </td>

                    {/* RSI */}
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-center gap-1">
                        <RsiIcon rsi={coin.rsi} />
                        <span className={`text-sm font-semibold tabular-nums ${rsiColor(coin.rsi)}`}>
                          {Number(coin.rsi).toFixed(1)}
                        </span>
                      </div>
                    </td>

                    {/* Score */}
                    <td className="px-4 py-3 text-right">
                      <ScoreBadge score={coin.opportunityScore} />
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* ── Footer ───────────────────────────────────────────────────────── */}
      {coins.length > 0 && (
        <div className="border-t border-slate-800 px-4 py-2">
          <div className="flex items-center justify-between text-[10px] text-slate-600">
            <span>Score legend</span>
            <div className="flex items-center gap-3">
              <span className="text-emerald-400">≥8 Strong Buy</span>
              <span className="text-yellow-400">≥6 Watch</span>
              <span className="text-orange-400">≥4 Weak</span>
              <span className="text-red-400">&lt;4 Avoid</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
