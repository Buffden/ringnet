# RING-01 · Generate Synthetic Fraud Dataset

## Description

As a developer, I want to generate a realistic synthetic fraud dataset so that Neo4j has meaningful data to load, query, and demonstrate fraud ring detection against.

This class is the entry point of the entire pipeline. Everything downstream — loading, verification, and all Cypher queries — depends on the CSVs this class produces. The dataset must contain both legitimate accounts and planted fraud rings, with shared identifiers deliberately embedded across ring members so that the graph structure reflects real fraud patterns.

The data is fake but the patterns are real. Fraud rings reuse phone numbers, emails, and devices across multiple accounts. That reuse is what the graph queries detect. This class creates that reuse intentionally.

---

## Prerequisites

None. This class has no upstream dependency. It only requires the project to compile and a writable filesystem at `data/raw/`.

---

## Acceptance Criteria

- [ ] `data/raw/` directory is created if it does not exist
- [ ] 125 legitimate accounts generated with IDs `ACC-0001` to `ACC-0125`
- [ ] 3 fraud rings generated with sizes 5, 8, and 12 accounts
- [ ] Fraud ring members share phone, email, and device identifiers within their ring
- [ ] 4 transaction categories generated covering legit-to-legit, intra-ring, ring-to-legit, and legit-to-ring flows
- [ ] 6 CSV files written to `data/raw/` with correct headers
- [ ] Duplicate identifier values intentionally present in phones, emails, and devices CSVs
- [ ] Program prints a generation summary to stdout on completion

---

## Sub-tasks

### RING-01-1 · Create output directory

Create `data/raw/` using `Files.createDirectories(Paths.get("data/raw"))`. This must run before any file writes. The method is idempotent — it does nothing if the directory already exists.

---

### RING-01-2 · Generate legitimate accounts

Create 125 accounts with sequential IDs (`ACC-0001` to `ACC-0125`), fake full names via `java-faker`, a random `created_at` timestamp between 2020–2023, and `fraud_confirmed = false`.

For each account, attach identifiers using probabilistic sampling to simulate real-world data sparsity — not every real account has every identifier on file:

| Identifier | Probability | Expected Count |
|---|---|---|
| Phone | 64% | ~80 |
| Email | 68% | ~85 |
| Device | 55% | ~69 |
| Address | 80% | ~100 |

Each identifier is unique per legitimate account — no sharing between legitimate accounts.

---

### RING-01-3 · Generate fraud rings

Create 3 fraud rings. Ring accounts get sequential IDs starting at `FRAUD-0001` and `fraud_confirmed = true`.

| Ring | Accounts | Shared Phones | Shared Emails | Shared Devices |
|---|---|---|---|---|
| Ring 1 | 5 | 2 | 2 | 1 |
| Ring 2 | 8 | 3 | 3 | 2 |
| Ring 3 | 12 | 4 | 4 | 3 |

For each ring, generate a small pool of shared identifiers first. Then for each member in the ring, randomly assign 1–2 phones from that pool, 1–2 emails from that pool, and 1 device from that pool. Each member gets a unique address — addresses are not a shared signal in this model.

This produces intentional duplicate phone, email, and device values across rows in the output CSVs. When `LoadData.java` runs `MERGE` on these values, duplicates collapse into a single shared node in Neo4j. That shared node is the structural fraud signal — the connection that fraud detection queries traverse.

---

### RING-01-4 · Generate transactions

Generate 4 categories of transactions. Each category simulates a different phase of fraud behavior:

| Category | Count | Amount Range | Status | Purpose |
|---|---|---|---|---|
| Legit-to-legit | 200 | $10 – $1,000 | 95% completed, 5% failed | Normal baseline activity |
| Intra-ring | `ring_size × 6` per ring | $500 – $5,000 | completed | Money layering between ring members |
| Ring-to-legit | `ring_size × 3` per ring | $200 – $2,000 | completed | Cash-out from ring to outside |
| Legit-to-ring | `ring_size × 1` per ring | $50 – $500 | completed | Victims sending money into the ring |

Total transactions: 200 (legit) + 150 (intra-ring: 30+48+72) + 75 (ring-to-legit: 15+24+36) + 25 (legit-to-ring: 5+8+12) = **450**.

Intra-ring amounts are deliberately high ($500–$5,000) so that velocity checks in `04_velocity_checks.cypher` produce a clear signal. Ring-to-legit transactions simulate the bust-out cash-out pattern.

---

### RING-01-5 · Write CSVs

Write one file per entity type to `data/raw/`:

| File | Headers |
|---|---|
| `accounts.csv` | `id, name, created_at, fraud_confirmed` |
| `phones.csv` | `account_id, number, created_at` |
| `emails.csv` | `account_id, address, created_at` |
| `devices.csv` | `account_id, device_id, device_type, last_seen` |
| `addresses.csv` | `account_id, street, city, zip, type` |
| `transactions.csv` | `id, from_account_id, to_account_id, amount, timestamp, status` |

Values containing commas or quotes must be wrapped in double quotes and internal quotes escaped. Use UTF-8 encoding for all files.

---

## How to Run

```bash
mvn compile exec:java -Dexec.mainClass="ringnet.GenerateData"
```

Expected output:

```
Generation complete:
  Accounts:     150
  Phone links:  ~118
  Email links:  ~123
  Device links: ~94
  Addresses:    ~125
  Transactions: 450
```

Accounts and Transactions are deterministic. Phone, Email, Device, and Address counts vary slightly per run due to probabilistic sampling.

---

## Dependencies

- `java-faker 1.0.2` — generates fake names, phone numbers, emails, and addresses
- `java.util.Random` seeded at `42` — ensures reproducible output across runs
- No Neo4j connection required — this class is pure file I/O
