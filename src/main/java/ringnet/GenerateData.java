package ringnet;

import java.io.IOException;
import java.nio.file.*;

public class GenerateData {

    public static void main(String[] args) throws IOException {
        Path dataDir = Paths.get("data/raw");
        Files.createDirectories(dataDir);
    }
}
