// k6 benchmark: monolit vs. mikroservisi (preko API Gateway-a).
//
//   k6 run -e TARGET=monolith perf/benchmark.js   -> http://localhost:8084
//   k6 run -e TARGET=services perf/benchmark.js   -> http://localhost:8090 (gateway)
//
// Dva scenarija, jedan poslije drugog:
//   pregled  - 20 korisnika 60s nasumicno cita repertoar, predstave, slobodna mjesta...
//   kupovina -  5 blagajnika 60s: slobodna mjesta -> kupovina karte (POST /tickets).
//              Kod servisa ovo ukljucuje poziv ticketing -> users (2 REST poziva).
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const TARGET = __ENV.TARGET || 'monolith';
const BASE = __ENV.BASE_URL || (TARGET === 'monolith' ? 'http://localhost:8084' : 'http://localhost:8090');
const DURATION = __ENV.DURATION || '60s';
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

// ID-evi iz seed podataka (scripts/seed/generate_seed.py)
const PERFORMANCES = Array.from({ length: 81 }, (_, i) => `izv-${String(i + 1).padStart(3, '0')}`);
const PLAYS = Array.from({ length: 28 }, (_, i) => `pr-${String(i + 1).padStart(2, '0')}`);
const REPERTORIES = ['9-26', '10-26', '11-26', '12-26'];
const AGENTS = ['blag-01', 'blag-02', 'blag-03', 'blag-04', 'blag-05'];

const seatTaken = new Counter('kupovina_zauzeto_mjesto');

export const options = {
  scenarios: {
    pregled: {
      executor: 'constant-vus', vus: 20, duration: DURATION,
      exec: 'pregled',
    },
    kupovina: {
      executor: 'constant-vus', vus: 5, duration: DURATION,
      exec: 'kupovina', startTime: DURATION, // pocinje kad pregled zavrsi
    },
  },
  // pravi pragove po endpointu i scenariju, da se pojave u sazetku
  thresholds: {
    'http_req_duration{scenario:pregled}': ['p(95)>=0'],
    'http_req_duration{scenario:kupovina}': ['p(95)>=0'],
    'http_req_duration{name:GET /plays}': ['p(95)>=0'],
    'http_req_duration{name:GET /performances}': ['p(95)>=0'],
    'http_req_duration{name:GET /performances/by-play}': ['p(95)>=0'],
    'http_req_duration{name:GET /repertory}': ['p(95)>=0'],
    'http_req_duration{name:GET /spectators/jmbg}': ['p(95)>=0'],
    'http_req_duration{name:POST available-seats}': ['p(95)>=0'],
    'http_req_duration{name:POST /tickets}': ['p(95)>=0'],
    'http_reqs{scenario:pregled}': ['count>=0'],
    'http_reqs{name:POST /tickets}': ['count>=0'],
    'checks': ['rate>=0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const JSON_HEADERS = { headers: { 'Content-Type': 'application/json' } };
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

export function setup() {
  // JMBG-ovi gledalaca (jedan poziv, van mjerenja)
  const res = http.get(`${BASE}/spectators`);
  const spectators = res.json().map((s) => s.jmbg);
  if (!spectators.length) throw new Error('Nema gledalaca - da li su baze napunjene?');
  return { spectators };
}

function availableSeats(performanceId) {
  return http.post(`${BASE}/tickets/performance/available-seats`,
    JSON.stringify({ id: performanceId }),
    { ...JSON_HEADERS, tags: { name: 'POST available-seats' } });
}

export function pregled(data) {
  const r = Math.random();
  let res;
  if (r < 0.2) res = http.get(`${BASE}/plays`, { tags: { name: 'GET /plays' } });
  else if (r < 0.4) res = http.get(`${BASE}/performances`, { tags: { name: 'GET /performances' } });
  else if (r < 0.6) res = http.get(`${BASE}/performances/by-play/${pick(PLAYS)}`, { tags: { name: 'GET /performances/by-play' } });
  else if (r < 0.7) res = http.get(`${BASE}/repertory/${pick(REPERTORIES)}`, { tags: { name: 'GET /repertory' } });
  else if (r < 0.8) res = http.get(`${BASE}/spectators/${pick(data.spectators)}`, { tags: { name: 'GET /spectators/jmbg' } });
  else res = availableSeats(pick(PERFORMANCES));
  check(res, { 'status 200': (x) => x.status === 200 });
}

export function kupovina(data) {
  const performanceId = pick(PERFORMANCES);
  const seats = availableSeats(performanceId);
  if (!check(seats, { 'status 200': (x) => x.status === 200 })) return;
  const free = seats.json();
  if (!free.length) return;

  const seat = String(pick(free));
  const id = `k6-${RUN_ID}-${__VU}-${__ITER}`;
  const agent = pick(AGENTS);
  const jmbg = pick(data.spectators);
  // monolit ocekuje ugnijezdene objekte, ticketing-service samo ID-eve
  const body = TARGET === 'monolith'
    ? { id, price: '1200 RSD', numberOfSeatInAuditorium: seat, ticketAgent: { id: agent }, spectator: { jmbg }, performance: { id: performanceId } }
    : { id, price: '1200 RSD', numberOfSeatInAuditorium: seat, ticketAgentId: agent, spectatorId: jmbg, performance: { id: performanceId } };

  const res = http.post(`${BASE}/tickets`, JSON.stringify(body),
    { ...JSON_HEADERS, tags: { name: 'POST /tickets' },
      responseCallback: http.expectedStatuses(200, 400) });
  if (res.status === 400) seatTaken.add(1); // dva blagajnika istovremeno uzela isto mjesto
  check(res, { 'status 200': (x) => x.status === 200 || x.status === 400 });
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`perf/results/${TARGET}-${RUN_ID}.json`]: JSON.stringify(data, null, 2),
  };
}
