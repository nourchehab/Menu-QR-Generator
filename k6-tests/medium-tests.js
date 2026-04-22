import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 20 },
        { duration: '30s', target: 30 },
        { duration: '30s', target: 0 },
    ],
};

export default function () {
    const url = 'http://localhost:8081/menu/preview?branchId=25';

    const res = http.get(url);

    check(res, {
        'status is 200': function (r) {
            return r.status === 200;
        },
        'response time under 2000ms': function (r) {
            return r.timings.duration < 2000;
        },
    });

    sleep(1);
}