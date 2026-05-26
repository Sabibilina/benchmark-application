import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.K6_VUS || 5),
  duration: __ENV.K6_DURATION || '30s',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

const baseUrl = __ENV.BASE_URL || 'http://gateway:8080';

export default function () {
  const suffix = `${__VU}-${__ITER}-${Date.now()}`;
  const credentials = {
    email: `k6-${suffix}@example.com`,
    password: 'CorrectHorse123',
  };

  const register = http.post(`${baseUrl}/auth/register`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(register, { 'register accepted': (response) => response.status === 201 || response.status === 409 });

  const login = http.post(`${baseUrl}/auth/login`, JSON.stringify(credentials), {
    headers: { 'Content-Type': 'application/json' },
  });
  check(login, { 'login ok': (response) => response.status === 200 });

  const token = login.json('accessToken');
  const authHeaders = { Authorization: `Bearer ${token}` };

  check(http.get(`${baseUrl}/catalog/songs?page=0&size=10`, { headers: authHeaders }), {
    'catalog ok': (response) => response.status === 200,
  });
  check(http.get(`${baseUrl}/search?q=love&size=10`, { headers: authHeaders }), {
    'search ok': (response) => response.status === 200,
  });
  check(http.get(`${baseUrl}/recommend/daily-mix`, { headers: authHeaders }), {
    'recommend ok': (response) => response.status === 200,
  });

  sleep(1);
}
