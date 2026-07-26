import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
const rate = Number(__ENV.RATE || 1000);
const duration = __ENV.DURATION || '60s';
const preAllocatedVUs = Number(__ENV.PRE_ALLOCATED_VUS || 200);
const maxVUs = Number(__ENV.MAX_VUS || 1000);

export const options = {
  scenarios: {
    outbox_insert_burst: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs,
      maxVUs,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(99)<5000'],
    checks: ['rate>0.95'],
  },
  tags: {
    testid: __ENV.TEST_ID || 'outbox-polling-baseline',
  },
};

export default function () {
  const eventId = `${__VU}-${__ITER}-${Date.now()}`;
  const formData = {
    request: http.file(
      JSON.stringify({
        title: `load-test-${eventId}`,
        content: 'Outbox polling load test',
      }),
      'request.json',
      'application/json',
    ),
    file: http.file(
      `outbox-load-test-${eventId}`,
      `load-test-${eventId}.txt`,
      'text/plain',
    ),
  };

  const response = http.post(`${baseUrl}/api/v1/posts`, formData, {
    tags: {
      endpoint: 'create-post',
    },
  });

  check(response, {
    'create post returns 200': (result) => result.status === 200,
  });
}
