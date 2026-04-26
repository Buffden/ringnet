# RING-02 · Load CSV Data into Neo4j

## Description

As a developer, I want to load all generated CSV files into Neo4j so that the graph is fully populated and ready for fraud detection queries.

This class is the bridge between raw data and the graph. It reads every CSV produced by `GenerateData.java` and constructs the full property graph in Neo4j — nodes, relationships, constraints, and indexes — in a single sequential run.

The loading order is strict. Constraints must exist before data is loaded so that `MERGE` operations are index-backed. Accounts must exist before any identifier or transaction rows can be processed, since those rows reference account IDs via `MATCH`. Getting the order wrong causes silent failures — rows are skipped without error when a `MATCH` finds nothing.

The most important detail in this class is the distinction between `MERGE` and `CREATE`. Shared identifiers (phones, emails, devices) use `MERGE` to collapse duplicates into single nodes — that is what physically creates the graph connections between ring members. Addresses use `CREATE` because they are not shared signals and must remain as individual nodes per account.

---

## Prerequisites

- Neo4j container running: `docker compose up -d`
- CSVs present in `data/raw/` — run `RING-01` first
- `data/raw/` is volume-mounted to `/var/lib/neo4j/import` inside the container, making files reachable via `file:///filename.csv`
- `.env` file present at project root with `NEO4J_URI`, `NEO4J_USER`, and `NEO4J_PASSWORD`

---

## Acceptance Criteria

- [ ] Uniqueness constraints created for all 5 labels before any data is loaded
- [ ] All 150 Account nodes loaded with correct properties including boolean `fraud_confirmed`
- [ ] Shared phone numbers collapsed into single `Phone` nodes with multiple `HAS_PHONE` edges
- [ ] Shared emails collapsed into single `Email` nodes with multiple `HAS_EMAIL` edges
- [ ] Shared devices collapsed into single `Device` nodes with multiple `HAS_DEVICE` edges
- [ ] Each address is a separate node — no deduplication across accounts
- [ ] Each transaction creates both a `Transaction` node and a `TRANSFERRED_TO` relationship
- [ ] Each load step prints its label and completion status to stdout
- [ ] Class is safe to re-run without creating duplicates

---

## Sub-tasks

### RING-02-1 · Create constraints and indexes

Run before any data is loaded. Uniqueness constraints serve two purposes: they prevent duplicate nodes from being created by repeated `MERGE` calls, and they automatically create an index on that property so `MERGE` lookups are fast rather than scanning all nodes.

| Label | Constrained Property |
|---|---|
| `Account` | `id` |
| `Phone` | `number` |
| `Email` | `address` |
| `Device` | `device_id` |
| `Transaction` | `id` |

Use `IF NOT EXISTS` on each constraint so the class is safe to re-run without throwing errors.

---

### RING-02-2 · Load accounts

`MERGE` on `Account.id`. Set `name`, `created_at`, and `fraud_confirmed` using `SET`. The `fraud_confirmed` column arrives as a string from the CSV — coerce it to boolean by evaluating `(row.fraud_confirmed = 'true')`.

---

### RING-02-3 · Load phones → HAS_PHONE

For each row in `phones.csv`:

- `MATCH` the Account node by `account_id`
- `MERGE` the Phone node on `number` — if two rows carry the same number, only one Phone node is created
- `MERGE` the `HAS_PHONE` relationship with `created_at` as a relationship property

This is the step where ring connections become physically real in the graph. Two ring members who share `+1-555-0101` will both have a `HAS_PHONE` edge pointing to the same Phone node. That shared node is what fraud detection queries traverse to link those two accounts.

---

### RING-02-4 · Load emails → HAS_EMAIL

For each row in `emails.csv`:

- `MATCH` the Account node by `account_id`
- `MERGE` the Email node on `address` — shared email addresses across ring members collapse into a single node
- `MERGE` the `HAS_EMAIL` relationship with `created_at` as a relationship property

The result is the same graph pattern as phones: multiple accounts pointing to one shared Email node, creating a traversable connection between ring members.

---

### RING-02-5 · Load devices → HAS_DEVICE

For each row in `devices.csv`:

- `MATCH` the Account node by `account_id`
- `MERGE` the Device node on `device_id` — shared devices within a ring collapse into a single node
- `SET` the `device_type` property on the Device node
- `MERGE` the `HAS_DEVICE` relationship with `last_seen` as a relationship property

Device nodes represent physical hardware. A single device node shared by multiple ring accounts means the same machine was used to operate all of them — a strong fraud signal.

---

### RING-02-6 · Load addresses → HAS_ADDRESS

For each row in `addresses.csv`:

- `MATCH` the Account node by `account_id`
- `CREATE` a new Address node with `street`, `city`, and `zip`
- `CREATE` the `HAS_ADDRESS` relationship with `type` (billing or shipping) as a property

Use `CREATE` — not `MERGE`. Addresses are not shared identifiers in this model. Every account gets its own Address node regardless of whether street or city values coincide with another account. Using `CREATE` prevents accidental graph connections between completely unrelated accounts that happen to share a city or zip code.

---

### RING-02-7 · Load transactions

For each row in `transactions.csv`:

- `MATCH` the `from` Account by `from_account_id`
- `MATCH` the `to` Account by `to_account_id`
- `MERGE` the `Transaction` node on `id`, then `SET` `amount`, `timestamp`, `status`
- `MERGE` the `TRANSFERRED_TO` relationship from `from` to `to` with `amount`, `timestamp`, and `transaction_id` as relationship properties

Both a Transaction node and a direct account-to-account relationship are created. The Transaction node exists to support future metadata attachment (fraud flags, dispute records, review status) without changing the edge. The `TRANSFERRED_TO` relationship enables direct account-to-account traversal for velocity and flow queries.

---

## How to Run

```bash
mvn exec:java -Dexec.mainClass="ringnet.LoadData"
```

Expected output:

```
Creating constraints and indexes... done.
Loading accounts... done.
Loading phones → HAS_PHONE relationships... done.
Loading emails → HAS_EMAIL relationships... done.
Loading devices → HAS_DEVICE relationships... done.
Loading addresses → HAS_ADDRESS relationships... done.
Loading transactions... done.

Load complete. Run VerifyLoad to confirm counts.
```

---

## Dependencies

- Requires `RING-01` to have completed successfully
- Neo4j Java Driver `5.x`
- `dotenv-java` for reading `.env` credentials
- Neo4j running at `bolt://localhost:7687`
