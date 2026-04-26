# Queries

Progressive Cypher queries for fraud ring detection. Run them in order — each builds on the previous in complexity and concept.

Open the Neo4j browser at [localhost:7474](http://localhost:7474) and paste each query to see results.

---

## Table of Contents

| Order | Topic | File |
|---|---|---|
| 1 | [Basic Traversal](#1-basic-traversal) | [basic_traversal.cypher](basic_traversal.cypher) |
| 2 | [Shared Identifiers](#2-shared-identifiers) | [shared_identifiers.cypher](shared_identifiers.cypher) |
| 3 | [Ring Detection](#3-ring-detection) | [ring_detection.cypher](ring_detection.cypher) |

---

## 1. Basic Traversal

**File:** `basic_traversal.cypher`

### Goal

Find accounts that share a phone number.

### Why start here

Before detecting rings, you need to understand the fundamental graph pattern: two accounts connected through a shared node. A phone number in this model is not a property on an account — it is its own node. If two accounts registered the same phone, they both point to the same `Phone` node. That shared node is the connection.

This is the simplest possible traversal — one hop through one identifier type. Get comfortable with the pattern here before adding complexity.

### Things to think about

- What does the path between two accounts sharing a phone look like in the graph?
- How do you avoid returning the same pair twice in both directions?
- What properties are worth returning to make the output useful?

---

## 2. Shared Identifiers

**File:** `shared_identifiers.cypher`

### Goal

Find accounts connected through any shared identifier — phone, email, or device.

### Why this matters

Real fraud rings do not rely on a single shared identifier. A fraudster controlling multiple accounts may reuse a phone number across some, an email across others, and the same device across all of them. Checking only phones misses the full picture.

This query extends the pattern from basic traversal across all three identifier types and combines the results into a single output.

### Things to think about

- How do you run the same pattern for email and device?
- How do you combine results from three separate patterns into one result set?
- What column tells you *how* two accounts are connected, not just *that* they are?
- What does it mean if the same pair of accounts appears more than once in the output?

---

## 3. Ring Detection

**File:** `ring_detection.cypher`

### Goal

Starting from a confirmed fraud account, find all accounts reachable within 6 hops through any shared identifier.

### Why this is the core query

Queries 01 and 02 find pairs — two accounts directly sharing one identifier. But fraud rings are not just pairs. A ring is a network: A shares a phone with B, B shares an email with C, C shares a device with D. No single hop connects them all, but they are all in the same ring.

This query traverses the graph outward from a known fraud account through any identifier relationship, following the chain as far as it goes. This is the query that demonstrates why graph databases outperform SQL for this problem — in SQL, each additional hop requires another self-join or recursive CTE level. In Cypher, you change one number.

### Things to think about

- Why can't you use a fixed number of hops like in a normal path pattern?
- Why do you traverse without direction (`-` instead of `->`) here?
- What happens if the graph has cycles and you use no upper bound?
- Why use `DISTINCT` in the return?
- What does it mean if a non-fraud account shows up in the results?
