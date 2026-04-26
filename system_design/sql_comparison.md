# SQL vs Cypher: Fraud Ring Detection

The same query — find all accounts connected to a confirmed fraud account through shared identifiers — written in both SQL and Cypher.

This comparison makes the graph advantage concrete rather than theoretical.

---

## The Query

> Starting from any confirmed fraud account, find all accounts reachable within 3 hops through shared phone numbers, emails, or devices.

---

## SQL (PostgreSQL — Recursive CTE)

```sql
WITH RECURSIVE fraud_ring AS (

  -- Base case: start from confirmed fraud accounts
  SELECT a.id, a.name, a.fraud_confirmed, 0 AS depth
  FROM accounts a
  WHERE a.fraud_confirmed = true

  UNION

  -- Recursive case: follow shared phone numbers
  SELECT a2.id, a2.name, a2.fraud_confirmed, fr.depth + 1
  FROM fraud_ring fr
  JOIN account_phones ap1 ON fr.id = ap1.account_id
  JOIN account_phones ap2 ON ap1.phone_number = ap2.phone_number
  JOIN accounts a2 ON ap2.account_id = a2.id
  WHERE fr.depth < 3
    AND a2.id != fr.id

  UNION

  -- Recursive case: follow shared emails
  SELECT a2.id, a2.name, a2.fraud_confirmed, fr.depth + 1
  FROM fraud_ring fr
  JOIN account_emails ae1 ON fr.id = ae1.account_id
  JOIN account_emails ae2 ON ae1.email_address = ae2.email_address
  JOIN accounts a2 ON ae2.account_id = a2.id
  WHERE fr.depth < 3
    AND a2.id != fr.id

  UNION

  -- Recursive case: follow shared devices
  SELECT a2.id, a2.name, a2.fraud_confirmed, fr.depth + 1
  FROM fraud_ring fr
  JOIN account_devices ad1 ON fr.id = ad1.account_id
  JOIN account_devices ad2 ON ad1.device_id = ad2.device_id
  JOIN accounts a2 ON ad2.account_id = a2.id
  WHERE fr.depth < 3
    AND a2.id != fr.id
)

SELECT DISTINCT id, name, fraud_confirmed
FROM fraud_ring
WHERE id NOT IN (SELECT id FROM accounts WHERE fraud_confirmed = true)
ORDER BY name;
```

---

## Cypher (Neo4j)

```cypher
MATCH (start:Account {fraud_confirmed: true})
      -[:HAS_PHONE|HAS_EMAIL|HAS_DEVICE*1..3]-
      (connected:Account)
WHERE start <> connected
RETURN DISTINCT connected.name, connected.fraud_confirmed
ORDER BY connected.name
```

---

## Comparison

| Dimension | SQL | Cypher |
|---|---|---|
| Lines of code | ~35 | 5 |
| Adding a 4th hop | Restructure the CTE | Change `3` to `4` |
| Adding a 4th identifier type (e.g. SSN) | Add another recursive UNION block | Add `\|HAS_SSN` to the relationship list |
| Query structure | Reconstructs connections at runtime via joins | Follows stored connections via pointer traversal |
| Performance at depth | Degrades exponentially with each hop | Constant cost per hop (index-free adjacency) |
| Readability | Requires understanding recursive CTEs | Pattern visually resembles the graph |

---

## Why This Matters

The SQL version is not wrong — it produces correct results. The problem is what happens when requirements change:

- **Deeper rings:** adding a 4th hop to SQL means restructuring the entire recursive query. In Cypher, it is changing one number.
- **More identifier types:** adding SSN sharing to SQL means another UNION block with two more joins. In Cypher, it is adding one relationship type to a list.
- **Performance at scale:** each recursive iteration in SQL performs index lookups across join tables. In Neo4j, each hop is a pointer dereference on the node — cost does not grow with database size.

The SQL query encodes the *depth* and *identifier types* into its structure. The Cypher query treats them as parameters. This is the fundamental modeling difference: SQL derives connections at query time, graphs store them as data.
