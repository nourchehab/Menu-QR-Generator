import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8081';

// CHANGE THESE TO A REAL TEST ACCOUNT
const EMAIL = __ENV.LOGIN_EMAIL;
const PASSWORD = __ENV.LOGIN_PASSWORD;

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
    const payload = JSON.stringify({
        email: EMAIL,
        password: PASSWORD,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(`${BASE_URL}/api/login/verify-credentials`, payload, params);

    check(res, {
        'status is 200': function (r) {
            return r.status === 200;
        },
        'response says credentials valid': function (r) {
            return r.body.includes('Credentials valid');
        },
    });

    sleep(0.5);
}