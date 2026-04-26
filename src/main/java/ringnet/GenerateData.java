package ringnet;

import com.github.javafaker.Faker;
import ringnet.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

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

        System.out.printf("Accounts: %d%n", accounts.size());
        System.out.printf("Phones:   %d%n", phones.size());
        System.out.printf("Emails:   %d%n", emails.size());
        System.out.printf("Devices:  %d%n", devices.size());
        System.out.printf("Addresses:%d%n", addresses.size());
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

        double phoneProb   = Double.parseDouble(CONFIG.getProperty("legit.phone.probability"));
        double emailProb   = Double.parseDouble(CONFIG.getProperty("legit.email.probability"));
        double deviceProb  = Double.parseDouble(CONFIG.getProperty("legit.device.probability"));
        double addressProb = Double.parseDouble(CONFIG.getProperty("legit.address.probability"));

        if (RNG.nextDouble() < phoneProb)
            phones.add(new Phone(id, FAKER.phoneNumber().cellPhone(), createdAt));
        if (RNG.nextDouble() < emailProb)
            emails.add(new Email(id, FAKER.internet().emailAddress(), createdAt));
        if (RNG.nextDouble() < deviceProb)
            devices.add(new Device(id, randomDeviceId(), randomDeviceType(), randomTs(2022, 2024)));
        if (RNG.nextDouble() < addressProb)
            addresses.add(new Address(id,
                    FAKER.address().streetAddress(),
                    FAKER.address().city(),
                    FAKER.address().zipCode(),
                    randomAddressType()));
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
