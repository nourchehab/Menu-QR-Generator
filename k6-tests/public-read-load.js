import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8081';

const RESTAURANT_ID = 4;
const BRANCH_ID = 138;

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '30s', target: 100 },
    { duration: '30s', target: 150 },
    { duration: '30s', target: 200 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
    const res1 = http.get(`${BASE_URL}/api/public/restaurants/${RESTAURANT_ID}/items`);

    check(res1, {
        'restaurant items 200': function (r) {
            return r.status === 200;
        },
    });

    const res2 = http.get(`${BASE_URL}/api/public/branch/${BRANCH_ID}/items`);

    check(res2, {
        'branch items 200': function (r) {
            return r.status === 200;
        },
    });
}