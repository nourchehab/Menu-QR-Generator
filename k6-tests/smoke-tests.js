import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 5,
    duration: '20s',
};

export default function () {
    const res = http.get('http://localhost:8081/health');

    check(res, {
        'status is 200': function (r) {
            return r.status === 200;
        },
    }); 

    sleep(1);
}