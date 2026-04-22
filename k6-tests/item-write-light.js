import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8081';
const BRANCH_ID = 25;
const JSESSIONID = __ENV.JSESSIONID;

if (JSESSIONID === undefined || JSESSIONID === '') {
    throw new Error('Missing JSESSIONID');
}

export const options = {
    stages: [
        { duration: '10s', target: 2 },
        { duration: '20s', target: 5 },
        { duration: '10s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.1'],
    },
};

function buildPayload() {
    const random = Math.floor(Math.random() * 100000);

    return {
        itemName: `Test Item ${random}`,
        itemDescription: `Load test item ${random}`,
        itemPrice: '10.5',
        category: 'Test Category',
    };
}

export default function () {
    const payload = buildPayload();

    const res = http.post(
        `${BASE_URL}/api/branch/${BRANCH_ID}/items`,
        payload,
        {
            headers: {
                Cookie: `JSESSIONID=${JSESSIONID}`,
            },
        }
    );

    console.log(`Status: ${res.status}`);
    console.log(`Body: ${res.body}`);

    check(res, {
        'item created status 200': function (r) {
            return r.status === 200;
        },
    });

    sleep(1);
}