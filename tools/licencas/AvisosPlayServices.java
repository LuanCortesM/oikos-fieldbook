import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Gera app/src/main/assets/licencas/google_play_services.txt, os avisos de
 * componentes de terceiros que vem dentro dos AARs do Google Play services
 * (third_party_licenses.json indica inicio e tamanho, em bytes, de cada texto
 * no third_party_licenses.txt). O app mostra esse arquivo no Sobre.
 *
 * Rodar de novo sempre que a versao do play-services-location mudar:
 *
 *   ./gradlew :app:dependencies --configuration releaseRuntimeClasspath   (ver as versoes)
 *   java tools/licencas/AvisosPlayServices.java app/src/main/assets/licencas/google_play_services.txt \
 *     ~/.gradle/caches/modules-2/files-2.1/com.google.android.gms/play-services-{base,basement,location,tasks}/VERSAO/HASH/*.aar
 *
 * Saida: "### Nome" e o texto de cada componente. Quando dois AARs trazem
 * textos diferentes com o mesmo nome, os dois entram.
 */
public class AvisosPlayServices {
    private static final Pattern ENTRY = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\\{([^}]*)\\}");
    private static final Pattern START = Pattern.compile("\"start\"\\s*:\\s*(\\d+)");
    private static final Pattern LENGTH = Pattern.compile("\"length\"\\s*:\\s*(\\d+)");

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("uso: java AvisosPlayServices.java saida.txt arquivo1.aar [arquivo2.aar ...]");
            System.exit(1);
        }
        Map<String, List<String>> texts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 1; i < args.length; i++) {
            try (ZipFile aar = new ZipFile(args[i])) {
                ZipEntry jsonEntry = aar.getEntry("third_party_licenses.json");
                ZipEntry txtEntry = aar.getEntry("third_party_licenses.txt");
                if (jsonEntry == null || txtEntry == null) {
                    System.err.println("sem avisos de terceiros: " + args[i]);
                    continue;
                }
                String json = new String(read(aar.getInputStream(jsonEntry)), StandardCharsets.UTF_8);
                byte[] all = read(aar.getInputStream(txtEntry));
                Matcher entry = ENTRY.matcher(json);
                while (entry.find()) {
                    Matcher start = START.matcher(entry.group(2));
                    Matcher length = LENGTH.matcher(entry.group(2));
                    if (!start.find() || !length.find()) throw new IOException("entrada sem start/length em " + args[i]);
                    int from = Integer.parseInt(start.group(1));
                    int size = Integer.parseInt(length.group(1));
                    String text = new String(all, from, size, StandardCharsets.UTF_8).strip();
                    List<String> variants = texts.computeIfAbsent(entry.group(1), k -> new ArrayList<>());
                    if (!variants.contains(text)) variants.add(text);
                }
            }
        }
        StringBuilder out = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, List<String>> component : texts.entrySet()) {
            for (String text : component.getValue()) {
                out.append("### ").append(component.getKey()).append("\n\n").append(text).append("\n\n");
                count++;
            }
        }
        Path target = Path.of(args[0]);
        if (target.getParent() != null) Files.createDirectories(target.getParent());
        Files.writeString(target, out.toString().stripTrailing() + "\n", StandardCharsets.UTF_8);
        System.out.println(count + " textos de " + texts.size() + " componentes -> " + target);
    }

    private static byte[] read(InputStream in) throws IOException {
        try (in) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            in.transferTo(buffer);
            return buffer.toByteArray();
        }
    }
}
