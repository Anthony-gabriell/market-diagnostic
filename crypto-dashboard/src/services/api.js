import axios from 'axios';

// ── Axios instances ───────────────────────────────────────────────────────────

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
});

const binance = axios.create({
  baseURL: 'https://api.binance.com/api/v3',
});

// ── Market scanner ────────────────────────────────────────────────────────────

/** Returns the last saved ranked list without triggering a new scan. */
export const getOpportunities = () =>
  api.get('/analysis/top-opportunities').then((res) => res.data);

/** Triggers a full market scan, persists results, returns ranked list. */
export const runScan = () =>
  api.get('/analysis/scan').then((res) => res.data);

// ── Per-symbol analysis ───────────────────────────────────────────────────────

/** Full diagnostic: RSI, volume, volatility, R/R, score, signals, action plan. */
export const getDiagnostic = (symbol) =>
  api.get(`/analysis/${symbol}/diagnostic`).then((res) => res.data);

/** Support / resistance / breakout annotations for the chart. */
export const getAnnotations = (symbol) =>
  api.get(`/analysis/${symbol}/annotations`).then((res) => res.data);

// ── Price history (direct Binance public API) ─────────────────────────────────

/**
 * Returns the last `limit` 1-hour candles for a symbol.
 * Each candle is a raw Binance array — PriceChart.jsx normalises it automatically.
 */
export const getKlines = (symbol, interval = '1h', limit = 24) =>
  binance.get('/klines', { params: { symbol, interval, limit } }).then((res) => res.data);

export default api;
