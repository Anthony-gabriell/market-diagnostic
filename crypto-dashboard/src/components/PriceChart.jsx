import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  ReferenceLine,
  CartesianGrid,
} from 'recharts';
import { TrendingUp, TrendingDown, Minus } from 'lucide-react';

// ── Palette ───────────────────────────────────────────────────────────────────

const COLORS = {
  grid:        '#1e293b',   // slate-800
  axis:        '#475569',   // slate-600
  line:        '#818cf8',   // indigo-400
  lineGlow:    '#6366f1',   // indigo-500
  tooltip:     '#0f172a',   // slate-900
  border:      '#1e293b',   // slate-800
  resistance:  '#f87171',   // red-400
  support:     '#34d399',   // emerald-400
  breakout:    '#fbbf24',   // amber-400
  muted:       '#64748b',   // slate-500
};

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Kline data from Binance: each candle is an array where
 *   index 0 = open time (ms)
 *   index 4 = close price (string)
 * Accepts either raw Binance arrays or pre-shaped { time, price } objects.
 */
function normalizeData(data) {
  if (!Array.isArray(data) || data.length === 0) return [];
  if (Array.isArray(data[0])) {
    return data.map((k) => ({
      time:  new Date(k[0]).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      price: parseFloat(k[4]),
    }));
  }
  return data;
}

function levelColor(type) {
  if (type === 'RESISTANCE') return COLORS.resistance;
  if (type === 'SUPPORT')    return COLORS.support;
  return COLORS.breakout;
}

function priceRange(points) {
  if (points.length === 0) return [0, 1];
  const prices = points.map((p) => p.price);
  const min = Math.min(...prices);
  const max = Math.max(...prices);
  const pad = (max - min) * 0.08 || max * 0.02;
  return [min - pad, max + pad];
}

function formatPrice(value) {
  if (value >= 1000) return '$' + (value / 1000).toFixed(1) + 'k';
  if (value >= 1)    return '$' + value.toFixed(2);
  return '$' + value.toFixed(4);
}

function PriceDelta({ data }) {
  if (data.length < 2) return null;
  const first = data[0].price;
  const last  = data[data.length - 1].price;
  const pct   = ((last - first) / first) * 100;
  const up    = pct >= 0;

  return (
    <span className={`flex items-center gap-0.5 text-xs font-semibold ${up ? 'text-emerald-400' : 'text-red-400'}`}>
      {up ? <TrendingUp className="h-3 w-3" /> : <TrendingDown className="h-3 w-3" />}
      {up ? '+' : ''}{pct.toFixed(2)}%
    </span>
  );
}

// ── Custom Tooltip ────────────────────────────────────────────────────────────

function CustomTooltip({ active, payload, label }) {
  if (!active || !payload?.length) return null;
  const price = payload[0]?.value;
  return (
    <div
      className="rounded-lg border border-slate-700 bg-slate-900/95 px-3 py-2 shadow-xl backdrop-blur-sm"
      style={{ minWidth: 120 }}
    >
      <p className="mb-1 text-[10px] text-slate-500">{label}</p>
      <p className="text-sm font-bold text-slate-100">{formatPrice(price)}</p>
    </div>
  );
}

// ── Custom ReferenceLine label ────────────────────────────────────────────────

function LevelLabel({ viewBox, label, color }) {
  const { x, y, width } = viewBox;
  return (
    <g>
      <rect
        x={x + width - 72}
        y={y - 10}
        width={70}
        height={16}
        rx={3}
        fill={color + '22'}
        stroke={color + '55'}
        strokeWidth={1}
      />
      <text
        x={x + width - 6}
        y={y + 3}
        textAnchor="end"
        fill={color}
        fontSize={9}
        fontWeight={600}
        fontFamily="monospace"
      >
        {label}
      </text>
    </g>
  );
}

// ── Empty state ───────────────────────────────────────────────────────────────

function EmptyChart({ symbol }) {
  return (
    <div className="flex h-full items-center justify-center">
      <div className="text-center">
        <Minus className="mx-auto mb-2 h-6 w-6 text-slate-700" />
        <p className="text-xs text-slate-500">
          {symbol ? `No price data for ${symbol}` : 'Select an asset to load the chart'}
        </p>
      </div>
    </div>
  );
}

// ── PriceChart ────────────────────────────────────────────────────────────────

/**
 * Props
 * ─────
 * symbol        string                   — e.g. "BTCUSDT"
 * data          array                    — raw Binance klines or { time, price }[]
 * annotations   ChartAnnotationDTO[]     — from GET /{symbol}/annotations
 *                 { type, priceLevel, label, color }
 * support       number | null            — shorthand when not using annotations
 * resistance    number | null            — shorthand when not using annotations
 * height        number                   — default 220
 * loading       boolean
 */
export default function PriceChart({
  symbol,
  data = [],
  annotations = [],
  support,
  resistance,
  height = 220,
  loading = false,
}) {
  const points    = normalizeData(data);
  const [yMin, yMax] = priceRange(points);
  const lastPrice = points.length > 0 ? points[points.length - 1].price : null;

  // Merge shorthand props with annotation array so callers can use either style
  const levels = [
    ...annotations.map((a) => ({
      type:  a.type,
      value: parseFloat(a.priceLevel ?? a.value),
      label: a.label,
      color: levelColor(a.type),
    })),
    ...(resistance != null && !annotations.some((a) => a.type === 'RESISTANCE')
      ? [{ type: 'RESISTANCE', value: resistance, label: 'Resistance', color: COLORS.resistance }]
      : []),
    ...(support != null && !annotations.some((a) => a.type === 'SUPPORT')
      ? [{ type: 'SUPPORT',    value: support,    label: 'Support',    color: COLORS.support }]
      : []),
  ];

  return (
    <div
      className="flex flex-col rounded-xl border border-slate-800 bg-slate-950 overflow-hidden"
      style={{ height }}
    >
      {/* ── Header ──────────────────────────────────────────────────── */}
      <div className="flex items-center justify-between border-b border-slate-800 px-4 py-2">
        <div className="flex items-center gap-2">
          <TrendingUp className="h-3.5 w-3.5 text-indigo-400" />
          <span className="text-xs font-semibold text-slate-200">
            {symbol ?? 'Price Chart'}
          </span>
          <span className="text-[10px] text-slate-600">1h · 15 candles</span>
        </div>
        <div className="flex items-center gap-3">
          {lastPrice && (
            <span className="text-xs font-bold text-slate-100">{formatPrice(lastPrice)}</span>
          )}
          {points.length > 1 && <PriceDelta data={points} />}
        </div>
      </div>

      {/* ── Chart body ──────────────────────────────────────────────── */}
      <div className="relative flex-1">
        {loading && (
          <div className="absolute inset-0 z-10 flex items-center justify-center bg-slate-950/60 backdrop-blur-sm">
            <div className="h-4 w-4 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" />
          </div>
        )}

        {points.length === 0 && !loading ? (
          <EmptyChart symbol={symbol} />
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart
              data={points}
              margin={{ top: 8, right: 12, bottom: 4, left: 0 }}
            >
              {/* Background grid */}
              <CartesianGrid
                strokeDasharray="3 4"
                stroke={COLORS.grid}
                vertical={false}
              />

              {/* Axes */}
              <XAxis
                dataKey="time"
                tick={{ fill: COLORS.axis, fontSize: 9, fontFamily: 'monospace' }}
                tickLine={false}
                axisLine={{ stroke: COLORS.grid }}
                interval="preserveStartEnd"
                minTickGap={40}
              />
              <YAxis
                domain={[yMin, yMax]}
                tick={{ fill: COLORS.axis, fontSize: 9, fontFamily: 'monospace' }}
                tickLine={false}
                axisLine={false}
                tickFormatter={formatPrice}
                width={58}
              />

              {/* Tooltip */}
              <Tooltip
                content={<CustomTooltip />}
                cursor={{ stroke: COLORS.muted, strokeWidth: 1, strokeDasharray: '4 2' }}
              />

              {/* Support / Resistance / Breakout lines */}
              {levels.map((lvl, i) => (
                <ReferenceLine
                  key={i}
                  y={lvl.value}
                  stroke={lvl.color}
                  strokeWidth={1}
                  strokeDasharray={lvl.type === 'BREAKOUT' ? '6 3' : '4 3'}
                  label={(props) => (
                    <LevelLabel
                      viewBox={props.viewBox}
                      label={`${lvl.label} ${formatPrice(lvl.value)}`}
                      color={lvl.color}
                    />
                  )}
                />
              ))}

              {/* Price line */}
              <Line
                type="monotone"
                dataKey="price"
                stroke={COLORS.line}
                strokeWidth={2}
                dot={false}
                activeDot={{
                  r: 4,
                  fill: COLORS.lineGlow,
                  stroke: '#0f172a',
                  strokeWidth: 2,
                }}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      {/* ── Level legend ────────────────────────────────────────────── */}
      {levels.length > 0 && (
        <div className="flex items-center gap-4 border-t border-slate-800 px-4 py-1.5">
          {levels.map((lvl, i) => (
            <div key={i} className="flex items-center gap-1.5">
              <span
                className="inline-block h-px w-4"
                style={{ backgroundColor: lvl.color, borderTop: `2px dashed ${lvl.color}` }}
              />
              <span className="text-[10px] font-medium" style={{ color: lvl.color }}>
                {lvl.type}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
