# ADR: Graph Database for Fraud Ring Detection

**Status:** Accepted

---

## Context

I am building a system to detect fraud rings — networks of accounts connected through shared identifiers such as phone numbers, emails, and devices. A single fraudster typically controls multiple accounts and reuses identifiers across them. The fraud signal lives in the *connections* between accounts, not in any individual account's properties.

The system needs to:
- Model accounts, identifiers, and transactions as connected entities
- Detect rings of 5–15+ accounts linked through shared identifiers
- Traverse connections up to N hops deep from a known fraud account
- Score accounts by proximity to confirmed fraud nodes and behavioral signals

The core technical question: should this be built on a relational database or a graph database?

---

## Decision

Use **Neo4j** (graph database) as the primary data store, with the **Labeled Property Graph (LPG)** model.

---

## Rationale

### The problem is a graph problem

Fraud rings are connected components. Finding them requires traversal — following edges from node to node until the component is exhausted. This is not a retrieval problem (fetch rows matching a filter) or an aggregation problem (sum, count, group). It is a traversal problem, and traversal is what graph databases are built for.

### Relational databases model connections poorly at depth

In a relational database, a shared phone number between two accounts is detected with a self-join:

```sql
SELECT a1.id, a2.id
FROM accounts a1
JOIN account_phones ap1 ON a1.id = ap1.account_id
JOIN account_phones ap2 ON ap1.phone = ap2.phone
JOIN accounts a2 ON ap2.account_id = a2.id
WHERE a1.id != a2.id
```

At two hops this is manageable. At three hops — find accounts connected to accounts connected to fraud accounts — it requires another self-join layer. At four hops, a recursive CTE. Each additional hop multiplies the query complexity and execution cost. The join graph grows exponentially with depth.

### Graph databases traverse at constant cost per hop

In Neo4j, each node stores direct pointers to its neighboring nodes. Following a relationship does not require a lookup table or index scan — it is a pointer dereference. This property is called **index-free adjacency**. The cost of one hop is constant and does not depend on the total size of the database.

The ring detection query at any depth is a single Cypher statement:

```cypher
MATCH (start:Account {fraud_confirmed: true})
      -[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE*1..6]-
      (connected:Account)
WHERE start <> connected
RETURN DISTINCT connected.name
```

Increasing the ring depth from 4 hops to 6 hops changes one number. The equivalent SQL change would require restructuring the entire query.

### Shared identifiers are naturally modeled as nodes

A phone number shared by three accounts is one Phone node with three edges. In a relational schema, it is a repeated value in a join table — the sharing relationship is implicit, reconstructed at query time. In the graph, it is explicit: the three edges pointing to the same Phone node *are* the fraud signal, stored as structure rather than derived as a query result.

---

## Alternatives Considered

### PostgreSQL with recursive CTEs

Viable for 1–2 hop queries. Becomes impractical at 3+ hops due to exponential join growth. Recursive CTEs are complex to write, difficult to optimize, and do not scale to real fraud ring sizes at production data volumes.

### PostgreSQL with Apache AGE (graph extension)

Adds Cypher query support to PostgreSQL. Keeps the operational simplicity of a single database. However, it does not implement index-free adjacency — traversal still uses index lookups under the hood, losing the core performance advantage. Suitable for light graph workloads, not for deep multi-hop traversal at scale.

### Neo4j with Graph Data Science (GDS) library

GDS adds algorithms like PageRank, Louvain community detection, and Weakly Connected Components directly in Neo4j. These would replace the BFS-based ring detection in `VerifyLoad.java` with purpose-built graph algorithms. This is the right direction for production — but adds operational complexity for a project focused on demonstrating the core graph model and Cypher queries.

---

## Consequences

**Positive:**
- Ring detection queries are simple, readable, and performant regardless of ring size
- Shared identifiers as nodes make fraud connections explicit in the data model
- The graph schema maps directly to the domain — accounts, identifiers, and transactions are modeled as they actually relate to each other

**Negative:**
- Neo4j is a specialized database — teams unfamiliar with graph databases face a learning curve
- Operational complexity increases (separate database to run, monitor, and back up)
- Neo4j Community Edition does not support clustering — production deployments require Enterprise licensing for high availability
