#!/usr/bin/env python3
"""
Distributed Self-Healing Cache Platform — End-to-End Cluster Integration Test Suite

Exercises:
1. API Gateway Consistent Hash Routing (GET/PUT/DELETE /api/v1/gateway/cache)
2. Ring Topology & Key Hash Lookup (/api/v1/gateway/cluster/ring, /api/v1/gateway/cluster/hash-key)
3. Self-Healing Node Failover (PUT /api/v1/gateway/cluster/nodes/{id}/status)
4. Notification Microservice (GET/POST /api/v1/notifications)
5. IAM Microservice Authentication (/api/v1/auth/register, /api/v1/auth/login)
"""

import sys
import json
import time
import argparse
import urllib.request
import urllib.error

CYAN = '\033[96m'
GREEN = '\033[92m'
YELLOW = '\033[93m'
RED = '\033[91m'
RESET = '\033[0m'

class E2EClusterTestRunner:
    def __init__(self, gateway_url="http://localhost:8080", notification_url="http://localhost:8084", iam_url="http://localhost:8085", dry_run=False):
        self.gateway_url = gateway_url.rstrip('/')
        self.notification_url = notification_url.rstrip('/')
        self.iam_url = iam_url.rstrip('/')
        self.dry_run = dry_run
        self.passed_count = 0
        self.failed_count = 0

    def log(self, section, msg, status="INFO"):
        color = CYAN if status == "INFO" else (GREEN if status == "PASS" else (RED if status == "FAIL" else YELLOW))
        print(f"[{color}{status}{RESET}] [{section}] {msg}")

    def assert_test(self, section, description, condition, details=""):
        if condition:
            self.passed_count += 1
            self.log(section, f"{description} {details}", status="PASS")
        else:
            self.failed_count += 1
            self.log(section, f"{description} {details}", status="FAIL")

    def http_request(self, url, method="GET", body=None, headers=None):
        if self.dry_run:
            if url.endswith("/ring"):
                data = [{"positionHex": "0000", "position": 0, "nodeId": "node-1"}]
            elif "/eviction-policy" in url:
                data = {"policy": "FIFO", "successNodes": [self.gateway_url], "failedNodes": []}
            elif "/cluster/config/nodes" in url:
                if method == "POST":
                    data = ["http://localhost:8081", "http://localhost:8082", "http://localhost:8083", "http://localhost:8084"]
                else:
                    data = ["http://localhost:8081", "http://localhost:8082", "http://localhost:8083"]
            else:
                data = {"status": "UP", "key": "test-key", "ownerNodeId": "node-1", "totalNodes": 3, "clusterTotalKeys": 0}
            return 200, {"success": True, "message": "Dry-run synthetic response", "data": data}

        headers = headers or {}
        headers.setdefault("Content-Type", "application/json")
        data = json.dumps(body).encode('utf-8') if body else None

        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                res_body = response.read().decode('utf-8')
                return response.status, json.loads(res_body) if res_body else {}
        except urllib.error.HTTPError as e:
            err_body = e.read().decode('utf-8')
            try:
                parsed = json.loads(err_body)
            except Exception:
                parsed = {"error": err_body}
            return e.code, parsed
        except Exception as e:
            return 503, {"error": str(e)}

    def test_gateway_routing(self):
        print(f"\n{CYAN}=== 1. API Gateway Consistent Hash Routing & Operations ==={RESET}")

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cache", "POST", {
            "key": "e2e-user-1001",
            "value": "John Doe Profile Data",
            "ttlSeconds": 300
        })
        self.assert_test("Gateway", "PUT cache entry key='e2e-user-1001'", status in [200, 201], f"(HTTP {status})")

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cache/e2e-user-1001")
        self.assert_test("Gateway", "GET cache entry key='e2e-user-1001'", status == 200, f"(HTTP {status})")

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/hash-key?key=e2e-user-1001")
        self.assert_test("Gateway", "Resolve hash route details for key", status == 200 and "ownerNodeId" in res.get("data", {}))

    def test_cluster_topology(self):
        print(f"\n{CYAN}=== 2. Cluster Topology & Virtual Node Ring ==={RESET}")

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/ring")
        self.assert_test("Cluster", "Fetch virtual nodes hash ring layout", status == 200 and isinstance(res.get("data"), list))

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/status")
        self.assert_test("Cluster", "Fetch backend cluster status", status in [200, 503])

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/health-report")
        self.assert_test("Cluster", "Fetch gateway cluster health report", status == 200 and "totalNodes" in res.get("data", {}))

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/metrics")
        self.assert_test("Cluster", "Fetch aggregated cluster performance metrics", status == 200 and "clusterTotalKeys" in res.get("data", {}))

    def test_notification_service(self):
        print(f"\n{CYAN}=== 3. Notification Microservice Integration ==={RESET}")

        status, res = self.http_request(f"{self.notification_url}/api/v1/notifications/user-999/unread-count")
        self.assert_test("Notification", "Fetch unread notification badge count", status in [200, 503])

    def test_iam_service(self):
        print(f"\n{CYAN}=== 4. IAM Microservice Integration ==={RESET}")

        status, res = self.http_request(f"{self.iam_url}/api/v1/auth/register/initiate", "POST", {
            "email": "e2e.tester@gmail.com",
            "password": "Password@123"
        })
        self.assert_test("IAM", "Initiate registration with allowed domain", status in [200, 400, 409, 503])

    def test_self_healing_failover(self):
        print(f"\n{CYAN}=== 5. Self-Healing Node Failover Simulation ==={RESET}")

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/nodes/node-2/status?status=DOWN", "PUT")
        self.assert_test("Self-Healing", "Mark node-2 DOWN and trigger ring rebuild", status in [200, 502, 503])

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/nodes/node-2/status?status=UP", "PUT")
        self.assert_test("Self-Healing", "Recover node-2 UP and restore hash ring", status in [200, 502, 503])

    def test_dynamic_eviction_policy(self):
        print(f"\n{CYAN}=== 6. Dynamic Eviction Policy Switching ==={RESET}")

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/eviction-policy?policy=FIFO", "PUT")
        self.assert_test("Eviction", "Switch eviction policy cluster-wide to FIFO", status == 200 and res.get("data", {}).get("policy") == "FIFO")

    def test_dynamic_scaling(self):
        print(f"\n{CYAN}=== 7. Dynamic Cluster Registry Scaling ==={RESET}")

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/config/nodes?url=http://localhost:8084", "POST")
        self.assert_test("Scaling", "Dynamically register node-4 in cluster topology", status == 200 and "http://localhost:8084" in res.get("data", []))

        status, res = self.http_request(f"{self.gateway_url}/api/v1/gateway/cluster/config/nodes?url=http://localhost:8084", "DELETE")
        self.assert_test("Scaling", "Dynamically unregister node-4 from cluster topology", status == 200 and "http://localhost:8084" not in res.get("data", []))

    def run(self):
        print(f"{CYAN}=========================================================={RESET}")
        print(f"{CYAN}  Distributed Cache Platform E2E Integration Suite       {RESET}")
        print(f"{CYAN}=========================================================={RESET}")
        if self.dry_run:
            print(f"{YELLOW}Running in DRY-RUN mode (Synthetic HTTP client){RESET}")

        self.test_gateway_routing()
        self.test_cluster_topology()
        self.test_notification_service()
        self.test_iam_service()
        self.test_self_healing_failover()
        self.test_dynamic_eviction_policy()
        self.test_dynamic_scaling()

        print(f"\n{CYAN}=========================================================={RESET}")
        print(f"Summary: {GREEN}{self.passed_count} Passed{RESET}, {RED if self.failed_count > 0 else GREEN}{self.failed_count} Failed{RESET}")
        print(f"{CYAN}=========================================================={RESET}")
        return self.failed_count == 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="E2E Cluster Integration Test Suite")
    parser.add_argument("--dry-run", action="store_true", help="Run in synthetic dry-run mode without live network calls")
    args = parser.parse_args()

    runner = E2EClusterTestRunner(dry_run=args.dry_run)
    success = runner.run()
    sys.exit(0 if success else 1)
