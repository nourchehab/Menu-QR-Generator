import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8081';

// CHANGE THESE
const RESTAURANT_ID = 25;
const BRANCH_ID = 25;

export const options = {
    stages: [
        { duration: '30s', target: 20 },
        { duration: '30s', target: 40 },
        { duration: '30s', target: 60 },
        { duration: '1m', target: 60 },  // hold at 60
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<3000'], // slightly relaxed
    },
};

export default function () {
    const res1 = http.get(`${BASE_URL}/api/public/restaurants/${RESTAURANT_ID}/items`);
    check(res1, {
        'restaurant items 200': (r) => r.status === 200,
    });

    const res2 = http.get(`${BASE_URL}/api/public/branch/${BRANCH_ID}/items`);
    check(res2, {
        'branch items 200': (r) => r.status === 200,
    });
}