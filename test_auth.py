import urllib.request
import json
import ssl

def post_login(username, password):
    url = "http://localhost:8080/api/v1/auth/login"
    data = json.dumps({"username": username, "password": password}).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    try:
        res = urllib.request.urlopen(req)
        body = res.read().decode("utf-8")
        print(f"Login {username}: STATUS {res.getcode()} - {body}")
        if res.getcode() == 200:
            return json.loads(body).get("token")
    except urllib.error.HTTPError as e:
        print(f"Login {username}: STATUS {e.code} - {e.read().decode('utf-8')}")
    return None

def test_endpoint(token, endpoint="http://localhost:8080/api/v1/complaints/1/triage?status=VERIFIED", method="PATCH"):
    req = urllib.request.Request(endpoint, method=method, headers={"Authorization": f"Bearer {token}"})
    try:
        res = urllib.request.urlopen(req)
        print(f"Endpoint: STATUS {res.getcode()} - {res.read().decode('utf-8')}")
    except urllib.error.HTTPError as e:
        print(f"Endpoint: STATUS {e.code}")

if __name__ == "__main__":
    print("Testing valid operator login...")
    op_token = post_login("operator", "password")
    
    print("\nTesting valid admin login...")
    admin_token = post_login("admin", "password")
    
    print("\nTesting wrong password login...")
    post_login("operator", "wrong")
    
    if op_token:
        print("\nTesting protected endpoint with OPERATOR token...")
        test_endpoint(op_token)

    if admin_token:
        print("\nTesting protected endpoint with ADMIN token...")
        test_endpoint(admin_token)

    print("\nTesting protected endpoint with FAKE token...")
    test_endpoint("eyJhbGciOiAiSFMyNTYiLCAidHlwIjogIkpXVCJ9.eyJzdWIiOiAiYWRtaW4iLCAidGVuYW50X2lkIjogMSwgInJvbGVzIjogWyJUT1RBTF9TVFJBTkdFUiJdfQ.ZmFrZV9zaWduYXR1cmU")
