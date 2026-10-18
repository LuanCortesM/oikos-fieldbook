import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Gera as listas de especies que vem dentro do app (app/src/main/assets/listas):
 * um Darwin Core Archive enxuto de cada fonte, so com o que o app usa. O app
 * importa esse arquivo pelo mesmo caminho de uma lista baixada (ChecklistParser).
 *
 * O que muda em relacao ao arquivo oficial, para caber em poucos MB:
 * - scientificName sai sem a autoria (que vai em scientificNameAuthorship);
 * - acceptedNameUsage so nos sinonimos; sem taxonID repetido, genero e epitetos;
 * - higherClassification so com os dois primeiros niveis (grupo da Flora e Funga);
 * - linhas ordenadas por reino, filo, classe, ordem, familia e nome (comprime melhor);
 * - distribuicao em uma linha por taxon: locationID com os estados separados por
 *   " | " e o mesmo establishmentMeans/occurrenceRemarks da fonte.
 *
 * Fontes: o archive.zip oficial (IPT do JBRJ) ou a copia do ChecklistBank
 * (Flora e Funga do Brasil: dataset 2031; Catalogo Taxonomico da Fauna do Brasil:
 * dataset 281817). Licenca das duas: CC BY 4.0; o eml.xml original vai junto.
 *
 *   java tools/listas/ListasEmbutidas.java lista_especies_flora_brasil.zip app/src/main/assets/listas/flora_brasil.zip
 *   java tools/listas/ListasEmbutidas.java catalogo_taxonomico_da_fauna_do_brasil.zip app/src/main/assets/listas/fauna_brasil.zip
 */
public class ListasEmbutidas {
    private static final String DWC = "http://rs.tdwg.org/dwc/terms/";
    private static final String TAXON = DWC + "Taxon";
    private static final String VERNACULAR = "http://rs.gbif.org/terms/1.0/VernacularName";
    private static final String DISTRIBUTION = "http://rs.gbif.org/terms/1.0/Distribution";
    private static final String LANGUAGE = "http://purl.org/dc/terms/language";

    /** Colunas do taxon.txt gerado, na ordem. */
    private static final String[] TAXON_OUT = {
        "acceptedNameUsageID", "scientificName", "scientificNameAuthorship", "acceptedNameUsage", "taxonRank", "taxonomicStatus",
        "higherClassification", "kingdom", "phylum", "class", "order", "family", "genus"
    };

    private record Spec(String location, char separator, Character quote, int headerLines, int idIndex, Map<String, Integer> columns) {
        String cell(List<String> cells, String term) {
            Integer index = columns.get(term.startsWith("http") ? term : DWC + term);
            return index == null || index >= cells.size() ? "" : cells.get(index).trim();
        }
        String id(List<String> cells) {
            return idIndex < cells.size() ? cells.get(idIndex).trim() : "";
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("uso: java ListasEmbutidas.java archive.zip saida.zip");
            System.exit(1);
        }
        Path output = Path.of(args[1]);
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (ZipFile in = new ZipFile(args[0]);
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output))) {
            out.setLevel(Deflater.BEST_COMPRESSION);
            Map<String, Spec> specs = parseMeta(read(in, "meta.xml"));
            Spec core = specs.get(TAXON);
            if (core == null) throw new IOException("sem core Taxon");
            StringBuilder meta = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<archive xmlns=\"http://rs.tdwg.org/dwc/text/\" metadata=\"eml.xml\">\n");

            int taxa = writeTaxa(in, out, core);
            meta.append(spec("core", TAXON, "taxon.txt", prefixed(TAXON_OUT)));

            Spec vernacular = specs.get(VERNACULAR);
            int vernaculars = 0;
            if (vernacular != null) {
                vernaculars = writeVernaculars(in, out, vernacular);
                meta.append(spec("extension", VERNACULAR, "vernacularname.txt", new String[] {DWC + "vernacularName", LANGUAGE}));
            }
            Spec distribution = specs.get(DISTRIBUTION);
            int distributions = 0;
            if (distribution != null) {
                distributions = writeDistribution(in, out, distribution);
                meta.append(spec("extension", DISTRIBUTION, "distribution.txt", new String[] {DWC + "locationID", DWC + "establishmentMeans", DWC + "occurrenceRemarks"}));
            }
            meta.append("</archive>\n");
            put(out, "meta.xml", meta.toString());
            put(out, "eml.xml", read(in, "eml.xml"));
            System.out.printf("%s: %d nomes, %d nomes populares, %d taxons com distribuicao%n", output.getFileName(), taxa, vernaculars, distributions);
        }
        System.out.printf("tamanho: %.2f MB%n", Files.size(output) / 1048576.0);
    }

    private static int writeTaxa(ZipFile in, ZipOutputStream out, Spec spec) throws IOException {
        List<String[]> rows = new ArrayList<>();
        forEachRow(in, spec, cells -> {
            String id = spec.id(cells);
            String fullName = spec.cell(cells, "scientificName");
            if (id.isEmpty() || fullName.isEmpty()) return;
            String authorship = spec.cell(cells, "scientificNameAuthorship");
            // Linhas da Fauna com o proprio nome no lugar da autoria ("Maldanidae" / "Maldanidae").
            if (authorship.equals(fullName)) authorship = "";
            String status = spec.cell(cells, "taxonomicStatus");
            boolean synonym = status.toLowerCase().contains("synonym") || status.toLowerCase().contains("sinonimo");
            String higher = spec.cell(cells, "higherClassification");
            String[] levels = higher.split(";");
            if (levels.length > 2) higher = levels[0] + ";" + levels[1];
            rows.add(new String[] {
                id,
                spec.cell(cells, "acceptedNameUsageID"),
                canonical(fullName, authorship, spec.cell(cells, "genus"), spec.cell(cells, "specificEpithet"), spec.cell(cells, "infraspecificEpithet")),
                authorship,
                synonym ? spec.cell(cells, "acceptedNameUsage") : "",
                spec.cell(cells, "taxonRank"),
                status,
                higher,
                spec.cell(cells, "kingdom"),
                spec.cell(cells, "phylum"),
                spec.cell(cells, "class"),
                spec.cell(cells, "order"),
                spec.cell(cells, "family"),
                spec.cell(cells, "genus")
            });
        });
        // Vizinhos parecidos comprimem melhor: classificacao primeiro, depois o nome.
        rows.sort(Comparator.<String[], String>comparing(r -> r[8]).thenComparing(r -> r[9]).thenComparing(r -> r[10])
            .thenComparing(r -> r[11]).thenComparing(r -> r[12]).thenComparing(r -> r[2]));
        List<String> header = new ArrayList<>(List.of("id"));
        header.addAll(List.of(TAXON_OUT));
        writeEntry(out, "taxon.txt", header, rows);
        return rows.size();
    }

    private static int writeVernaculars(ZipFile in, ZipOutputStream out, Spec spec) throws IOException {
        List<String[]> rows = new ArrayList<>();
        forEachRow(in, spec, cells -> {
            String id = spec.id(cells);
            String name = spec.cell(cells, "vernacularName");
            if (!id.isEmpty() && !name.isEmpty()) rows.add(new String[] {id, name, spec.cell(cells, LANGUAGE)});
        });
        writeEntry(out, "vernacularname.txt", List.of("coreid", "vernacularName", "language"), rows);
        return rows.size();
    }

    /** Uma linha por taxon: estados juntos, meios de estabelecimento e observacoes sem repeticao. */
    private static int writeDistribution(ZipFile in, ZipOutputStream out, Spec spec) throws IOException {
        Map<String, TreeSet<String>[]> byTaxon = new LinkedHashMap<>();
        forEachRow(in, spec, cells -> {
            String id = spec.id(cells);
            if (id.isEmpty()) return;
            @SuppressWarnings("unchecked")
            TreeSet<String>[] parts = byTaxon.computeIfAbsent(id, k -> new TreeSet[] {new TreeSet<>(), new TreeSet<>(), new TreeSet<>()});
            String location = spec.cell(cells, "locationID");
            if (!location.isEmpty()) parts[0].add(location);
            String means = spec.cell(cells, "establishmentMeans");
            if (!means.isEmpty()) parts[1].add(means);
            String remarks = spec.cell(cells, "occurrenceRemarks");
            if (!remarks.isEmpty()) parts[2].add(remarks);
        });
        List<String[]> rows = new ArrayList<>();
        byTaxon.forEach((id, parts) -> rows.add(new String[] {id, String.join(" | ", parts[0]), String.join(" | ", parts[1]), String.join(" | ", parts[2])}));
        writeEntry(out, "distribution.txt", List.of("coreid", "locationID", "establishmentMeans", "occurrenceRemarks"), rows);
        return rows.size();
    }

    /** Mesma regra do app (ChecklistParser.canonical): nome sem a autoria. */
    static String canonical(String fullName, String authorship, String genus, String epithet, String infra) {
        String name = fullName.trim();
        String author = authorship.trim();
        if (!author.isEmpty() && name.endsWith(author)) return name.substring(0, name.length() - author.length()).trim();
        if (!genus.isEmpty() && !epithet.isEmpty()) {
            String prefix = genus + " " + epithet;
            if (name.startsWith(prefix)) {
                String marker = null;
                for (String word : name.substring(prefix.length()).trim().split(" ")) {
                    if (List.of("var.", "subsp.", "ssp.", "f.", "fo.").contains(word)) { marker = word; break; }
                }
                if (infra.isEmpty()) return prefix;
                return marker == null ? prefix + " " + infra : prefix + " " + marker + " " + infra;
            }
        }
        return name;
    }

    private interface RowHandler { void accept(List<String> cells); }

    private static void forEachRow(ZipFile in, Spec spec, RowHandler handler) throws IOException {
        ZipEntry source = in.getEntry(spec.location());
        if (source == null) throw new IOException("falta " + spec.location());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in.getInputStream(source), StandardCharsets.UTF_8), 1 << 16)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                if (++lineNumber <= spec.headerLines()) continue;
                handler.accept(split(line, spec.separator(), spec.quote()));
            }
        }
    }

    private static void writeEntry(ZipOutputStream out, String name, List<String> header, List<String[]> rows) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 1 << 16);
        writer.write(String.join("\t", header));
        writer.write('\n');
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) writer.write('\t');
                writer.write(row[i].replace('\t', ' ').replace('\n', ' ').replace('\r', ' '));
            }
            writer.write('\n');
        }
        writer.flush();
        out.closeEntry();
    }

    private static String[] prefixed(String[] terms) {
        return java.util.Arrays.stream(terms).map(t -> DWC + t).toArray(String[]::new);
    }

    private static String spec(String tag, String rowType, String location, String[] terms) {
        StringBuilder xml = new StringBuilder();
        xml.append("  <").append(tag).append(" encoding=\"UTF-8\" fieldsTerminatedBy=\"\\t\" linesTerminatedBy=\"\\n\" fieldsEnclosedBy=\"\" ignoreHeaderLines=\"1\" rowType=\"")
            .append(rowType).append("\">\n    <files><location>").append(location).append("</location></files>\n");
        xml.append(tag.equals("core") ? "    <id index=\"0\"/>\n" : "    <coreid index=\"0\"/>\n");
        for (int i = 0; i < terms.length; i++) {
            xml.append("    <field index=\"").append(i + 1).append("\" term=\"").append(terms[i]).append("\"/>\n");
        }
        xml.append("  </").append(tag).append(">\n");
        return xml.toString();
    }

    private static Map<String, Spec> parseMeta(String xml) {
        Map<String, Spec> specs = new LinkedHashMap<>();
        Matcher block = Pattern.compile("<(core|extension)\\b([^>]*)>(.*?)</\\1>", Pattern.DOTALL).matcher(xml);
        while (block.find()) {
            String attributes = block.group(2);
            String body = block.group(3);
            String separator = unescape(attribute(attributes, "fieldsTerminatedBy"));
            String quote = unescape(attribute(attributes, "fieldsEnclosedBy"));
            Map<String, Integer> columns = new LinkedHashMap<>();
            Matcher field = Pattern.compile("<field\\s+index=\"(\\d+)\"\\s+term=\"([^\"]+)\"").matcher(body);
            while (field.find()) columns.put(field.group(2), Integer.parseInt(field.group(1)));
            specs.put(attribute(attributes, "rowType"), new Spec(
                first(body, "<location>\\s*(.*?)\\s*</location>"),
                separator.isEmpty() ? ',' : separator.charAt(0),
                quote.isEmpty() ? null : quote.charAt(0),
                parseInt(attribute(attributes, "ignoreHeaderLines"), 0),
                parseInt(first(body, "<(?:id|coreid)\\s+index=\"(\\d+)\""), 0),
                columns));
        }
        return specs;
    }

    private static List<String> split(String line, char separator, Character quote) {
        List<String> cells = new ArrayList<>();
        if (quote == null) {
            int start = 0;
            for (int i = 0; i <= line.length(); i++) {
                if (i == line.length() || line.charAt(i) == separator) {
                    cells.add(line.substring(start, i));
                    start = i + 1;
                }
            }
            return cells;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == quote && quoted && i + 1 < line.length() && line.charAt(i + 1) == quote) { current.append(c); i++; }
            else if (c == quote) quoted = !quoted;
            else if (c == separator && !quoted) { cells.add(current.toString()); current.setLength(0); }
            else current.append(c);
        }
        cells.add(current.toString());
        return cells;
    }

    private static String attribute(String attributes, String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(attributes);
        return m.find() ? m.group(1) : "";
    }

    private static String first(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private static String unescape(String value) {
        return value.replace("\\t", "\t").replace("\\n", "\n").replace("&quot;", "\"");
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static String read(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) throw new IOException("falta " + name);
        try (InputStream input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void put(ZipOutputStream out, String name, String text) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
    }
}
