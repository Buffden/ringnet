package ringnet;

import com.github.javafaker.Faker;
import ringnet.model.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.*;

public class GenerateData {

    static final Properties CONFIG = loadConfig();
    static final Faker FAKER = new Faker(new Locale("en-US"));
    static final Random RNG = new Random(Long.parseLong(CONFIG.getProperty("rng.seed")));
    static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get("data/raw");
        Files.createDirectories(dataDir);

        List<Account> accounts = new ArrayList<>();
        List<Phone> phones = new ArrayList<>();
        List<Email> emails = new ArrayList<>();
        List<Device> devices = new ArrayList<>();
        List<Address> addresses = new ArrayList<>();

        List<String> legitIds = generateLegitAccounts(accounts, phones, emails, devices, addresses);
        List<List<String>> rings = generateFraudRings(accounts, phones, emails, devices, addresses);
        List<Transaction> transactions = generateTransactions(legitIds, rings);

        writeCsvs(dataDir, accounts, phones, emails, devices, addresses, transactions);

        System.out.printf("Accounts: %d%n", accounts.size());
        System.out.printf("Phones: %d%n", phones.size());
        System.out.printf("Emails: %d%n", emails.size());
        System.out.printf("Devices: %d%n", devices.size());
        System.out.printf("Addresses: %d%n", addresses.size());
        System.out.printf("Rings: %d%n", rings.size());
        System.out.printf("Transactions: %d%n", transactions.size());
    }

    static List<String> generateLegitAccounts(
            List<Account> accounts,
            List<Phone> phones,
            List<Email> emails,
            List<Device> devices,
            List<Address> addresses) {

        int count = Integer.parseInt(CONFIG.getProperty("legit.count"));
        List<String> legitIds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String id = String.format("ACC-%04d", i);
            String createdAt = randomTs(2020, 2023);
            accounts.add(new Account(id, FAKER.name().fullName(), createdAt, false));
            legitIds.add(id);
            attachIdentifiers(phones, emails, devices, addresses, id, createdAt);
        }
        return legitIds;
    }

    static void attachIdentifiers(
            List<Phone> phones,
            List<Email> emails,
            List<Device> devices,
            List<Address> addresses,
            String id,
            String createdAt) {

        double phoneProb = Double.parseDouble(CONFIG.getProperty("legit.phone.probability"));
        double emailProb = Double.parseDouble(CONFIG.getProperty("legit.email.probability"));
        double deviceProb = Double.parseDouble(CONFIG.getProperty("legit.device.probability"));
        double addressProb = Double.parseDouble(CONFIG.getProperty("legit.address.probability"));

        if (RNG.nextDouble() < phoneProb) {
            phones.add(new Phone(id, FAKER.phoneNumber().cellPhone(), createdAt));
        }
        if (RNG.nextDouble() < emailProb) {
            emails.add(new Email(id, FAKER.internet().emailAddress(), createdAt));
        }
        if (RNG.nextDouble() < deviceProb) {
            devices.add(new Device(id, randomDeviceId(), randomDeviceType(), randomTs(2022, 2024)));
        }
        if (RNG.nextDouble() < addressProb) {
            addresses.add(new Address(id,
                    FAKER.address().streetAddress(),
                    FAKER.address().city(),
                    FAKER.address().zipCode(),
                    randomAddressType()));
        }
    }

    // --- Transaction generation ---

    static List<Transaction> generateTransactions(List<String> legitIds, List<List<String>> rings) {
        List<Transaction> transactions = new ArrayList<>();
        int[] txId = {0};
        addLegitTransactions(transactions, legitIds, txId);
        addIntraRingTransactions(transactions, rings, txId);
        addRingToLegitTransactions(transactions, rings, legitIds, txId);
        addLegitToRingTransactions(transactions, rings, legitIds, txId);
        return transactions;
    }

    static void addLegitTransactions(List<Transaction> transactions, List<String> legitIds, int[] txId) {
        int count = Integer.parseInt(CONFIG.getProperty("tx.legit.count"));
        double failProb = Double.parseDouble(CONFIG.getProperty("tx.legit.failed.probability"));
        double amtMin = Double.parseDouble(CONFIG.getProperty("tx.legit.amount.min"));
        double amtMax = Double.parseDouble(CONFIG.getProperty("tx.legit.amount.max"));

        for (int t = 0; t < count; t++) {
            String from = pick(legitIds);
            String to;
            do {
                to = pick(legitIds);
            } while (to.equals(from));
            String status = RNG.nextDouble() < failProb ? "failed" : "completed";
            transactions.add(tx(++txId[0], from, to, amtMin, amtMax, status));
        }
    }

    static void addIntraRingTransactions(List<Transaction> transactions, List<List<String>> rings, int[] txId) {
        int multiplier = Integer.parseInt(CONFIG.getProperty("tx.intra.multiplier"));
        double amtMin = Double.parseDouble(CONFIG.getProperty("tx.intra.amount.min"));
        double amtMax = Double.parseDouble(CONFIG.getProperty("tx.intra.amount.max"));

        for (List<String> ring : rings) {
            for (int t = 0; t < ring.size() * multiplier; t++) {
                String from = pick(ring);
                String to;
                do {
                    to = pick(ring);
                } while (to.equals(from));
                transactions.add(tx(++txId[0], from, to, amtMin, amtMax, "completed"));
            }
        }
    }

    static void addRingToLegitTransactions(List<Transaction> transactions, List<List<String>> rings, List<String> legitIds, int[] txId) {
        int multiplier = Integer.parseInt(CONFIG.getProperty("tx.ring.to.legit.multiplier"));
        double amtMin = Double.parseDouble(CONFIG.getProperty("tx.ring.to.legit.amount.min"));
        double amtMax = Double.parseDouble(CONFIG.getProperty("tx.ring.to.legit.amount.max"));

        for (List<String> ring : rings) {
            for (int t = 0; t < ring.size() * multiplier; t++) {
                transactions.add(tx(++txId[0], pick(ring), pick(legitIds), amtMin, amtMax, "completed"));
            }
        }
    }

    static void addLegitToRingTransactions(List<Transaction> transactions, List<List<String>> rings, List<String> legitIds, int[] txId) {
        int multiplier = Integer.parseInt(CONFIG.getProperty("tx.legit.to.ring.multiplier"));
        double amtMin = Double.parseDouble(CONFIG.getProperty("tx.legit.to.ring.amount.min"));
        double amtMax = Double.parseDouble(CONFIG.getProperty("tx.legit.to.ring.amount.max"));

        for (List<String> ring : rings) {
            for (int t = 0; t < ring.size() * multiplier; t++) {
                transactions.add(tx(++txId[0], pick(legitIds), pick(ring), amtMin, amtMax, "completed"));
            }
        }
    }

    // --- CSV writing ---

    static void writeCsvs(
            Path dir,
            List<Account> accounts,
            List<Phone> phones,
            List<Email> emails,
            List<Device> devices,
            List<Address> addresses,
            List<Transaction> transactions) throws IOException {

        writeCsv(dir.resolve("accounts.csv"),
                "id,name,created_at,fraud_confirmed",
                accounts.stream().map(a -> csv(a.id(), a.name(), a.createdAt(), String.valueOf(a.fraudConfirmed()))));

        writeCsv(dir.resolve("phones.csv"),
                "account_id,number,created_at",
                phones.stream().map(p -> csv(p.accountId(), p.number(), p.createdAt())));

        writeCsv(dir.resolve("emails.csv"),
                "account_id,address,created_at",
                emails.stream().map(e -> csv(e.accountId(), e.address(), e.createdAt())));

        writeCsv(dir.resolve("devices.csv"),
                "account_id,device_id,device_type,last_seen",
                devices.stream().map(d -> csv(d.accountId(), d.deviceId(), d.deviceType(), d.lastSeen())));

        writeCsv(dir.resolve("addresses.csv"),
                "account_id,street,city,zip,type",
                addresses.stream().map(a -> csv(a.accountId(), a.street(), a.city(), a.zip(), a.type())));

        writeCsv(dir.resolve("transactions.csv"),
                "id,from_account_id,to_account_id,amount,timestamp,status",
                transactions.stream().map(t -> csv(t.id(), t.fromAccountId(), t.toAccountId(),
                        t.amount(), t.timestamp(), t.status())));
    }

    static void writeCsv(Path path, String header, Stream<String> rows) throws IOException {
        try (PrintWriter pw = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(
                        Files.newOutputStream(path), StandardCharsets.UTF_8)))) {
            pw.println(header);
            rows.forEach(pw::println);
        }
        System.out.println("Written: " + path);
    }

    static String csv(String... values) {
        return Arrays.stream(values)
                .map(v -> {
                    if (v == null) {
                        return "";
                    }
                    if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                        return "\"" + v.replace("\"", "\"\"") + "\"";
                    }
                    return v;
                })
                .collect(Collectors.joining(","));
    }

    static Transaction tx(int id, String from, String to, double amtMin, double amtMax, String status) {
        double amount = amtMin + RNG.nextDouble() * (amtMax - amtMin);
        return new Transaction(
                String.format("TX-%05d", id), from, to,
                String.format("%.2f", amount),
                randomTs(2023, 2024),
                status);
    }

    static String pick(List<String> list) {
        return list.get(RNG.nextInt(list.size()));
    }

    // --- Fraud ring generation ---

    static List<List<String>> generateFraudRings(
            List<Account> accounts,
            List<Phone> phones,
            List<Email> emails,
            List<Device> devices,
            List<Address> addresses) {

        int[] ringSizes = Arrays.stream(CONFIG.getProperty("ring.sizes").split(","))
                .mapToInt(Integer::parseInt).toArray();

        List<List<String>> rings = new ArrayList<>();
        int[] idCounter = {1};

        for (int ringIdx = 0; ringIdx < ringSizes.length; ringIdx++) {
            List<String> ringIds = createRingMembers(accounts, ringSizes[ringIdx], idCounter);
            assignSharedIdentifiers(phones, emails, devices, addresses, ringIds, ringIdx);
            rings.add(ringIds);
        }
        return rings;
    }

    static List<String> createRingMembers(List<Account> accounts, int size, int[] idCounter) {
        List<String> ringIds = new ArrayList<>();
        for (int j = 0; j < size; j++) {
            String id = String.format("FRAUD-%04d", idCounter[0]++);
            accounts.add(new Account(id, FAKER.name().fullName(), randomTs(2023, 2024), true));
            ringIds.add(id);
        }
        return ringIds;
    }

    static void assignSharedIdentifiers(
            List<Phone> phones,
            List<Email> emails,
            List<Device> devices,
            List<Address> addresses,
            List<String> ringIds,
            int ringIdx) {

        int numPhones = Integer.parseInt(CONFIG.getProperty("ring.shared.phones.base")) + ringIdx;
        int numEmails = Integer.parseInt(CONFIG.getProperty("ring.shared.emails.base")) + ringIdx;
        int numDevices = Integer.parseInt(CONFIG.getProperty("ring.shared.devices.base")) + ringIdx;

        List<String> phonePool = generatePhonePool(numPhones);
        List<String> emailPool = generateEmailPool(numEmails);
        List<String[]> devicePool = generateDevicePool(numDevices, ringIdx);

        for (String memberId : ringIds) {
            String joinedAt = randomTs(2023, 2024);
            pickFromPool(phonePool, numPhones).forEach(n -> phones.add(new Phone(memberId, n, joinedAt)));
            pickFromPool(emailPool, numEmails).forEach(a -> emails.add(new Email(memberId, a, joinedAt)));
            String[] dev = devicePool.get(RNG.nextInt(devicePool.size()));
            devices.add(new Device(memberId, dev[0], dev[1], joinedAt));
            addresses.add(new Address(memberId,
                    FAKER.address().streetAddress(),
                    FAKER.address().city(),
                    FAKER.address().zipCode(),
                    "billing"));
        }
    }

    static List<String> generatePhonePool(int count) {
        List<String> pool = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pool.add(FAKER.phoneNumber().cellPhone());
        }
        return pool;
    }

    static List<String> generateEmailPool(int count) {
        List<String> pool = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            pool.add(FAKER.internet().emailAddress());
        }
        return pool;
    }

    static List<String[]> generateDevicePool(int count, int ringIdx) {
        List<String[]> pool = new ArrayList<>();
        for (int d = 0; d < count; d++) {
            pool.add(new String[]{String.format("DEV-RING%d-%02d", ringIdx + 1, d + 1), randomDeviceType()});
        }
        return pool;
    }

    static List<String> pickFromPool(List<String> pool, int poolSize) {
        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, RNG);
        int count = 1 + RNG.nextInt(Math.min(2, poolSize));
        return shuffled.subList(0, count);
    }

    static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = GenerateData.class.getClassLoader().getResourceAsStream("config.properties")) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Could not load config.properties", e);
        }
        return props;
    }

    static String randomDeviceId() {
        return "DEV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    static String randomDeviceType() {
        return RNG.nextBoolean() ? "mobile" : "desktop";
    }

    static String randomAddressType() {
        return RNG.nextBoolean() ? "billing" : "shipping";
    }

    static String randomTs(int fromYear, int toYear) {
        LocalDateTime from = LocalDateTime.of(fromYear, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(toYear, 12, 31, 23, 59);
        long range = ChronoUnit.SECONDS.between(from, to);
        return from.plusSeconds((long) (RNG.nextDouble() * range)).format(TS_FMT);
    }
}
