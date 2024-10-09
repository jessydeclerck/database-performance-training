import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Counter, Trend } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
// Create separate counters for each strategy
const multipleTransactionsRecords = new Counter('records_multiple_transactions');
const singleTransactionRecords = new Counter('records_single_transaction');
const batchValuesRecords = new Counter('records_batch_values');
const batchUnnestRecords = new Counter('records_batch_unnest');

// Execution time trends for each strategy
const multipleTransactionsTime = new Trend('execution_time_multiple_transactions');
const singleTransactionTime = new Trend('execution_time_single_transaction');
const batchValuesTime = new Trend('execution_time_batch_values');
const batchUnnestTime = new Trend('execution_time_batch_unnest');

export const options = {
    scenarios: {
        multiple_transactions: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '10s', target: 1 },
                { duration: '10s', target: 5 },
                { duration: '20s', target: 5 },
                { duration: '10s', target: 0 },
            ],
            exec: 'multipleTransactions',
            startTime: '0s',
        },
        single_transaction: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '10s', target: 1 },
                { duration: '10s', target: 5 },
                { duration: '20s', target: 5 },
                { duration: '10s', target: 0 },
            ],
            exec: 'singleTransaction',
            startTime: '60s',
        },
        batch_values: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '10s', target: 1 },
                { duration: '10s', target: 5 },
                { duration: '20s', target: 5 },
                { duration: '10s', target: 0 },
            ],
            exec: 'batchValues',
            startTime: '120s',
        },
        batch_unnest: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '10s', target: 1 },
                { duration: '10s', target: 5 },
                { duration: '20s', target: 5 },
                { duration: '10s', target: 0 },
            ],
            exec: 'batchUnnest',
            startTime: '180s',
        },
    },
    thresholds: {
        errors: ['rate<0.1'],
        'http_req_duration': ['p(95)<10000'],
    },
};

const BASE_URL = 'http://app:8080/api/orders/bulk-inserts';
const HEADERS = {
    'Content-Type': 'application/json',
};

const TEST_SIZES = [
    { orders: 10, itemsPerOrder: 5 },
    { orders: 50, itemsPerOrder: 5 },
    { orders: 100, itemsPerOrder: 5 },
];

// Helper function to add metrics based on strategy
function addMetrics(strategy, records, executionTime) {
    switch (strategy) {
        case 'multiple-transactions':
            multipleTransactionsRecords.add(records);
            multipleTransactionsTime.add(executionTime);
            break;
        case 'single-transaction':
            singleTransactionRecords.add(records);
            singleTransactionTime.add(executionTime);
            break;
        case 'batch-values':
            batchValuesRecords.add(records);
            batchValuesTime.add(executionTime);
            break;
        case 'batch-unnest':
            batchUnnestRecords.add(records);
            batchUnnestTime.add(executionTime);
            break;
    }
}

// Helper function to run a test for a specific endpoint
function runBulkTest(endpoint, size) {
    const strategy = endpoint.substring(1); // Remove leading slash
    const payload = JSON.stringify({
        numberOfOrders: size.orders,
        itemsPerOrder: size.itemsPerOrder,
    });

    const response = http.post(`${BASE_URL}${endpoint}`, payload, {
        headers: HEADERS,
        timeout: '300s',
    });

    const success = check(response, {
        'is status 200': (r) => r.status === 200,
        'has valid response': (r) => {
            try {
                const body = JSON.parse(r.body);
                return (
                    body.strategy &&
                    typeof body.totalRecords === 'number' &&
                    typeof body.executionTimeMs === 'number'
                );
            } catch {
                return false;
            }
        },
    });

    errorRate.add(!success);

    if (success) {
        const result = JSON.parse(response.body);
        const totalRecords = result.totalRecords;
        const executionTimeMs = result.executionTimeMs;

        // Add metrics for this strategy
        addMetrics(strategy, totalRecords, executionTimeMs);

        console.log(
            `Strategy: ${strategy}, Batch Size: ${size.orders}, ` +
            `Records: ${totalRecords}, Time: ${executionTimeMs / 1000}s`
        );
    }
}

// Test functions for each endpoint
export function multipleTransactions() {
    TEST_SIZES.forEach((size) => {
        runBulkTest('/multiple-transactions', size);
    });
}

export function singleTransaction() {
    TEST_SIZES.forEach((size) => {
        runBulkTest('/single-transaction', size);
    });
}

export function batchValues() {
    TEST_SIZES.forEach((size) => {
        runBulkTest('/batch-values', size);
    });
}

export function batchUnnest() {
    TEST_SIZES.forEach((size) => {
        runBulkTest('/batch-unnest', size);
    });
}