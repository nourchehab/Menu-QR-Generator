import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8081';
const BRANCH_ID = 25;

// put your copied cookie into env var before running
const JSESSIONID = __ENV.JSESSIONID;
const REMEMBER_ME = __ENV.REMEMBER_ME || '';

if (JSESSIONID === undefined || JSESSIONID === '') {
    throw new Error('Missing JSESSIONID environment variable');
}

function buildCookieHeader() {
    let cookie = `JSESSIONID=${JSESSIONID}`;
    if (REMEMBER_ME !== '') {
        cookie += `; remember-me=${REMEMBER_ME}`;
    }
    return cookie;
}

export const options = {
    stages: [
        { duration: '20s', target: 5 },
        { duration: '30s', target: 15 },
        { duration: '40s', target: 30 },
        { duration: '20s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<1500'],
        http_req_failed: ['rate<0.05'],
    },
};

export default function () {
    const params = {
        headers: {
            Cookie: buildCookieHeader(),
        },
    };

    const res1 = http.get(`${BASE_URL}/api/items`, params);
    check(res1, {
        'api items status 200': function (r) {
            return r.status === 200;
        },
    });

    const res2 = http.get(`${BASE_URL}/api/branch/${BRANCH_ID}/items`, params);
    check(res2, {
        'branch items status 200': function (r) {
            return r.status === 200;
        },
    });

    sleep(0.5);
}