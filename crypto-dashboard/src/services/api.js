import axios from 'axios';

// ── Axios instances ───────────────────────────────────────────────────────────

const api = axios.create({
    baseURL: 'https://crypto-interpreter-backend.onrender.com/api',
});

const SYMBOL_TO_ID = {
    BTCUSDT: 'bitcoin', ETHUSDT: 'ethereum', BNBUSDT: 'binancecoin',
    SOLUSDT: 'solana', XRPUSDT: 'ripple', ADAUSDT: 'cardano',
    AVAXUSDT: 'avalanche-2', DOGEUSDT: 'dogecoin', DOTUSDT: 'polkadot',
    MATICUSDT: 'matic-network', LINKUSDT: 'chainlink', LTCUSDT: 'litecoin',
    UNIUSDT: 'uniswap', ATOMUSDT: 'cosmos', XLMUSDT: 'stellar',
    VETUSDT: 'vechain', FILUSDT: 'filecoin', TRXUSDT: 'tron',
    ETCUSDT: 'ethereum-classic', ALGOUSDT: 'algorand', AAVEUSDT: 'aave',
    FTMUSDT: 'fantom', SANDUSDT: 'the-sandbox', MANAUSDT: 'decentraland',
    AXSUSDT: 'axie-infinity',
};

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

// ── Price history (CoinGecko public API) ─────────────────────────────────────

/**
 * Returns hourly candles for a symbol via CoinGecko.
 * Response is normalised to [timestamp, open, high, low, close, volume]
 * so PriceChart.jsx continues to read index 4 as the close price.
 */
export const getKlines = (symbol, days = 1) => {
    const id = SYMBOL_TO_ID[symbol] || symbol.toLowerCase();
    return axios
        .get(`https://api.coingecko.com/api/v3/coins/${id}/market_chart`, {
            params: { vs_currency: 'usd', days, interval: 'hourly' },
            headers: { 'x-cg-demo-api-key': 'CG-jemHpZ2Q9fFStzHKqXAKdDS3' },
        })
        .then((res) =>
            res.data.prices.map(([timestamp, price]) => [timestamp, 0, 0, 0, price, 0])
        );
};

export default api;