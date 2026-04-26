# RING-03 · Verify Graph Load

## Description

As a developer, I want to verify that all data was loaded into Neo4j correctly so that I can be confident the graph is complete and structurally accurate before running any fraud detection queries.

This class is the safety gate between loading and querying. It connects to Neo4j, counts nodes per label, and then runs a Java BFS to detect fraud rings by traversing shared identifier edges. If the counts are wrong or the rings are not found, there is a bug in `GenerateData.java` or `LoadData.java` that must be fixed before moving forward.

The ring detection here does not use Cypher path-finding or any external library — it is implemented as a Java BFS that issues simple two-hop neighbor queries to Neo4j per visited node. This keeps verification self-contained and dependency-free, and also demonstrates that ring detection logic can live outside the database entirely.

---

## Prerequisites

- Neo4j running with data already loaded — run `RING-02` first
- `.env` file present at project root with `NEO4J_URI`, `NEO4J_USER`, and `NEO4J_PASSWORD`

---

## Acceptance Criteria

- [ ] Node counts printed for all 6 labels: Account, Phone, Email, Device, Address, Transaction
- [ ] Account count is exactly 150
- [ ] Exactly 3 fraud rings detected
- [ ] Largest ring size is exactly 12
- [ ] Ring breakdown printed showing account IDs per ring
- [ ] No APOC or GDS dependency

---

## Sub-tasks

### RING-03-1 · Print node counts per label

Run one `MATCH (n:Label) RETURN count(n)` query per label and print results. Account count is always exactly 150. All other counts vary slightly due to probabilistic sampling in `GenerateData.java`.

| Label | Expected Count | Notes |
|---|---|---|
| Account | 150 | Deterministic: 125 legit + 25 fraud |
| Phone | ~118 | Varies with RNG seed |
| Email | ~123 | Varies with RNG seed |
| Device | ~94 | Varies with RNG seed |
| Address | ~125 | Varies with RNG seed |
| Transaction | 450 | Deterministic: 200 + 150 + 75 + 25 |

If Account count is not 150 or Transaction count is not 450, the load failed and must be re-run after clearing the database.

---

### RING-03-2 · Detect fraud rings via Java BFS

Fetch all accounts where `fraud_confirmed = true` — there are always exactly 25. For each unvisited fraud account, run BFS to find its full connected component through shared Phone, Email, and Device nodes. Each connected component is one fraud ring.

**BFS expansion query** — issued once per visited node:

```cypher
MATCH (a:Account {id: $id})
      -[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE]->(shared)
      <-[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE]-
      (b:Account {fraud_confirmed: true})
WHERE b.id <> $id
RETURN DISTINCT b.id AS neighborId
```

This is a two-hop traversal: start Account → shared identifier node ← neighboring fraud Account. The identifier node in the middle (the shared phone, email, or device) is what links ring members together. Each neighbor found is added to the queue if not already visited. BFS continues until the queue is empty, at which point the full ring has been found and is recorded as one connected component.

**Why BFS over DFS:**
Both BFS and DFS will find all members of a connected component correctly. BFS is chosen here because it processes nodes level by level — it fully explores all direct connections of an account before moving to indirect ones. This makes the traversal order more predictable and easier to reason about when debugging ring membership.

---

### RING-03-3 · Print ring summary and breakdown

After BFS completes across all fraud accounts, print the total count of rings found, the size of the largest ring, and a per-ring breakdown with member IDs.

Expected output:

```
--- Fraud ring summary ---
Fraud rings detected: 3
Largest ring size:   12

--- Ring breakdown ---
  Ring 1: 5 accounts — [FRAUD-0001, FRAUD-0002, ...]
  Ring 2: 8 accounts — [FRAUD-0006, FRAUD-0007, ...]
  Ring 3: 12 accounts — [FRAUD-0014, FRAUD-0015, ...]
```

If ring count is not 3 or the largest ring is not 12, the shared identifier data was not loaded correctly in `RING-02`. Check that `MERGE` was used for phones, emails, and devices — not `CREATE`.

---

## How to Run

```bash
mvn exec:java -Dexec.mainClass="ringnet.VerifyLoad"
```

---

## Dependencies

- Requires `RING-02` to have completed successfully
- Neo4j Java Driver `5.x`
- `dotenv-java` for reading `.env` credentials
- No APOC or GDS dependency
