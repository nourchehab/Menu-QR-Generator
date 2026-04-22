import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8081';

// CHANGE THESE
const RESTAURANT_ID = 25;
const BRANCH_ID = 25;

export const options = {
    stages: [
        { duration: '20s', target: 10 },   // ramp up
        { duration: '40s', target: 30 },   // moderate load
        { duration: '40s', target: 60 },   // heavier load
        { duration: '20s', target: 0 },    // ramp down
    ],
    thresholds: {
        http_req_duration: ['p(95)<1500'],
        http_req_failed: ['rate<0.05'],
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

    sleep(0.5);
}