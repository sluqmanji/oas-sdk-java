# Sequence Chain Tests

A test type, `sequence`, that builds one chain family per POST in an
OpenAPI spec and emits a compile-ready pytest bundle that runs the
chains end-to-end against a live server. Every POST in the spec — top-
level or sub-resource — appears in at least one emitted chain, so no
path is skipped.

Primary source: `src/main/java/egain/oassdk/testgenerators/sequence/SequenceChainTestGenerator.java`
Chain logic: `src/main/java/egain/oassdk/core/sequence/`
Tests: `src/test/java/egain/oassdk/core/sequence/`, `src/test/java/egain/oassdk/testgenerators/sequence/`

---

## The idea in 60 seconds

**Problem.** For a typical REST API you want end-to-end tests that call
operations *in sequence*: create a resource, read it back, maybe update
it, maybe delete it. Sub-resources are even worse — `POST /orders/{orderId}/items`
can't fire without a live `orderId` from a prior `POST /orders`.
Hand-writing these chains is tedious. Picking orderings randomly
(fuzz-style) gives statistical coverage but produces noisy test reports
where a 404 might mean "server broken" or "the random draw just didn't
satisfy the preconditions".

**What this generator does.** Given one OpenAPI spec it:

1. Extracts every operation into a flat list (`ApiCallInfo`).
2. For **every POST** in the spec — top-level or sub-resource — builds a
   chain family. The family's prefix is the predecessor POSTs whose
   responses resolve the seed's path parameters (recursively); the tail
   is path-templated consumers (GET/PUT/PATCH/DELETE) under the seed's
   resource tree.
3. Mechanically enumerates every valid permutation up to a bounded
   length.
4. Emits one pytest file per seed POST's resource, one
   `def test_<shape>()` per chain.
5. Each generated test asserts `2xx` at every step. Each POST's output
   id is captured into a variable named after the path parameter that
   later steps consume (e.g. `order_id`, `item_id`).

**Why enumeration, not random.** If the chain is guaranteed valid by
construction, a non-`2xx` at any step is by definition a real bug — not
a "maybe the chain was nonsense". That gives failures you can act on.

Random/property-based coverage is still useful and is handled separately
by the Schemathesis bundle (`testgenerators/schemathesis`), which
complements this generator rather than overlapping with it.

---

## Quick example — single top-level POST

Given this minimal folder spec:

```yaml
paths:
  /folders:
    post: { operationId: createFolder, requestBody: { content: { application/json: { schema: { $ref: "#/components/schemas/FolderCreate" } } } } }
  /folders/{folderID}:
    get: { operationId: getFolder }
components:
  schemas:
    FolderCreate:
      type: object
      properties:
        name: { type: string }
```

the generator emits (at `<outputDir>/sequence/test_chain_folders.py`):

```python
import pytest
from conftest import extract_id

def test_folders_post(api_client, auth_headers, base_url):
    # Step 1 — POST /folders (expected 2xx)
    r = api_client.post(f"{base_url}/folders", json={"name": "mock_name"}, headers=auth_headers)
    assert 200 <= r.status_code < 300, f"Step 1 POST /folders: {r.status_code} {r.text}"


def test_folders_post_get(api_client, auth_headers, base_url):
    # Step 1 — POST /folders (expected 2xx)
    r = api_client.post(f"{base_url}/folders", json={"name": "mock_name"}, headers=auth_headers)
    assert 200 <= r.status_code < 300, f"Step 1 POST /folders: {r.status_code} {r.text}"
    folder_id = extract_id(r, hint="folderID")
    assert folder_id is not None, f"Step 1 POST response had no extractable 'folderID'"
    # Step 2 — GET /folders/{folderID} (expected 2xx)
    r = api_client.get(f"{base_url}/folders/{folder_id}", headers=auth_headers)
    assert 200 <= r.status_code < 300, f"Step 2 GET /folders/{{folderID}}: {r.status_code} {r.text}"
```

---

## Quick example — sub-resource POST with a producer prefix

Given `POST /orders`, `POST /orders/{orderId}/items`, and
`GET /orders/{orderId}/items/{itemId}`, the `items` family (anchored on
the sub-resource POST) emits into `test_chain_items.py`:

```python
def test_items_post_post_get(api_client, auth_headers, base_url):
    # Step 1 — POST /orders (expected 2xx)
    r = api_client.post(f"{base_url}/orders", headers=auth_headers)
    assert 200 <= r.status_code < 300, f"Step 1 POST /orders: {r.status_code} {r.text}"
    order_id = extract_id(r, hint="orderId")
    assert order_id is not None, f"Step 1 POST response had no extractable 'orderId'"
    # Step 2 — POST /orders/{orderId}/items (expected 2xx)
    r = api_client.post(f"{base_url}/orders/{order_id}/items", headers=auth_headers)
    assert 200 <= r.status_code < 300, f"Step 2 POST /orders/{{orderId}}/items: {r.status_code} {r.text}"
    item_id = extract_id(r, hint="itemId")
    assert item_id is not None, f"Step 2 POST response had no extractable 'itemId'"
    # Step 3 — GET /orders/{orderId}/items/{itemId} (expected 2xx)
    r = api_client.get(f"{base_url}/orders/{order_id}/items/{item_id}", headers=auth_headers)
    assert 200 <= r.status_code < 300, f"Step 3 GET /orders/{{orderId}}/items/{{itemId}}: {r.status_code} {r.text}"
```

`POST /orders` is the prefix producer: it runs first so its response
provides the `orderId` value. The sub-resource POST provides `itemId`
for the final GET.

Running either bundle:

```bash
cd <outputDir>/sequence
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export API_BASE_URL=http://localhost:8080
pytest -v
```

---

## Validity rules

Every emitted chain satisfies all of these at enumeration time. There is
no runtime filter — if a chain is emitted, every step is expected to be
`2xx`.

1. **Anchored on a seed POST.** Each chain is built around exactly one
   POST (top-level or sub-resource). That POST is always in the chain.
2. **Producer prefix.** If the seed POST has path parameters, the chain
   begins with the POSTs that produce each parameter's value (resolved
   recursively — `POST /a/{aId}/b/{bId}/c` seeds a family whose prefix
   is `POST /a, POST /a/{aId}/b`).
3. **Path-templated consumer tail.** Tail steps (GET/PUT/PATCH/DELETE)
   are scoped to the seed POST's resource tree. Ancestor consumers
   belong to a different seed's family; sibling sub-resource POSTs are
   their own seeds, not tail members.
4. **DELETE is terminal** (when `deleteLastOnly = true`, the default).
5. **No repeats** (when `allowRepeats = false`, the default).

The decision procedure lives in
`ChainEnumerator.enumerate` and `ChainEnumerator.buildPrefix`.

### Unresolved path parameters

A sub-resource POST whose path parameter has no producer POST in the
spec (e.g. the spec has `POST /orphans/{parentId}/children` but no
`POST /orphans`) triggers `UnresolvedParamPolicy`:

- **`SKIP`** (default) — the family is dropped. The POST still exists in
  the spec; it just doesn't get auto-generated tests.
- **`EMIT_WITH_MARKER`** — the family is emitted, with `pytest.skip(...)`
  at the top of each test so the unresolved case is visible in the
  report rather than silently omitted.

---

## Configuration

Pass `TestConfig.additionalProperties` keys:

| Key                                 | Default | Meaning |
| ---                                 | ---     | --- |
| `sequence.maxChainLength`           | `4`     | Cap on total chain length (prefix + seed + tail). |
| `sequence.allowRepeats`             | `false` | Allow a tail consumer to appear twice (e.g. `[POST, PATCH, PATCH]`). |
| `sequence.deleteLastOnly`           | `true`  | Filter chains where DELETE isn't terminal. Turn off to exercise post-delete behavior. |
| `sequence.unresolvedParamPolicy`    | `SKIP`  | `SKIP` or `EMIT_WITH_MARKER`. How to handle sub-resource POSTs whose path params have no producer POST in the spec. |
| `sequence.baseUrl`                  | first `servers[].url` → `http://localhost:8080` | Baked into `conftest.py` as the `API_BASE_URL` default. Env var overrides. |

Chain count grows with the number of POSTs and consumers. For a resource
with `c` consumers and max length `L`, the tail contribution is bounded
by `1 + Σ P(c, k)` for `k = 1..L-prefixSize-1`. Sub-resource POSTs add
prefix length, shrinking the tail budget for the same `maxChainLength`.
Four consumers at `L = 4` on a top-level POST seed yields 26 chains per
resource. Tune `maxChainLength` downward if your spec has many deep
sub-resources.

---

## Id variable naming

Each POST in a chain captures its response id into a Python variable
named by snake-casing the path parameter that subsequent steps consume:

| Path param    | Variable name |
| ---           | ---           |
| `{folderID}`  | `folder_id`   |
| `{orderId}`   | `order_id`    |
| `{user_id}`   | `user_id`     |
| `{id}`        | `id`          |

`extract_id(response, hint=<paramName>)` receives the original path-
parameter name as a hint. The conftest helper tries the hint field
(and a snake_case variant) in the response body first, then falls back
to the generic id heuristic (`id`, `ID`, `_id`, `uuid`, `resourceId`,
any field ending in `id`).

---

## What this generator is NOT

- **Not a random sequence tester.** Random coverage lives in the
  Schemathesis bundle — stateful mode is enabled by default
  (`schemathesis.phases = coverage,stateful`).
- **Not a response-schema validator.** Each step asserts only that the
  status is `2xx`. Schemathesis' `--checks all` covers response schema,
  status code conformance, content type, missing headers.
- **Not a cross-root-resource workflow tester.** Chain predecessors are
  only the POSTs needed to resolve path parameters. Arbitrary workflows
  that combine unrelated resources (e.g. `POST /users` then `POST /orders`
  whose body references `userId`) are Schemathesis' lane.
- **Not a payload fuzzer.** Request bodies come from a single-level
  property walk (`ApiCallExtractor.buildRequestBodyForOperation`):
  strings get `"mock_<field>"`, numbers `1`, booleans `true`. Anything
  more structured needs Schemathesis.

---

## End-to-end recipe

```bash
mvn exec:java -Dexec.mainClass=egain.oassdk.cli.OASSDKCLI \
    -Dexec.args="tests --spec openapi.yaml --output out --type sequence"
cd out/sequence
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
API_BASE_URL=http://localhost:8080 API_TOKEN="Bearer $TOKEN" pytest -v
```

Failures look like:

```
FAILED test_chain_items.py::test_items_post_post_get_delete -
  AssertionError: Step 3 GET /orders/{orderId}/items/{itemId}: 404 {"detail":"not found"}
```

The per-step location and the status/body pinpoint the bug.
