import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Gera app/src/main/assets/municipios.bin, a malha municipal que o Oikos usa
 * offline para dizer em que municipio e estado esta uma coordenada.
 *
 * Fonte: IBGE, Malha Municipal 2022 pela API de malhas (v3), qualidade maxima,
 * e nomes pela API de localidades (v1). Em 17/10/2026 a API so entregava o
 * periodo 2022 (2023 a 2025 respondiam erro 500). Para atualizar:
 *
 *   curl -o malha.json "https://servicodados.ibge.gov.br/api/v3/malhas/paises/BR?formato=application/vnd.geo+json&qualidade=maxima&intrarregiao=municipio&periodo=2022"
 *   curl -o nomes.json "https://servicodados.ibge.gov.br/api/v1/localidades/municipios"
 *   java -Xmx4g tools/ibge/MalhaMunicipal.java malha.json nomes.json app/src/main/assets/municipios.bin
 *
 * Com uma quarta malha (outra qualidade) o programa tambem mede quanto as duas
 * discordam em pontos sorteados e o desvio das divisas em metros. Medido em
 * 17/10/2026, intermediaria contra maxima: municipio diferente em 0,31% dos
 * pontos; desvio mediano 55 m, p95 379 m, p99 768 m. Por isso o app usa a
 * maxima (5,6 MB) e nao a intermediaria (2,0 MB).
 *
 * Formato (big-endian): "OIKM", versao (1 byte), escala (int, 10000 = 1e-4 grau),
 * quantidade (int); por municipio: codigo (int), nome (UTF), UF (UTF),
 * caixa minLon minLat maxLon maxLat (int, em unidades da escala), tamanho em
 * bytes dos aneis (varint) e os aneis: quantidade (varint), e por anel o numero
 * de vertices (varint) e pares lon/lat em diferenca do anterior (zigzag varint).
 */
public class MalhaMunicipal {

    static final int SCALE = 10000;

    record Municipio(int code, List<int[][]> rings, int[] bbox) {}

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("uso: java MalhaMunicipal.java malha.json nomes.json saida.bin [outra-malha.json]");
            System.exit(2);
        }
        Map<Integer, Municipio> mesh = readMesh(Path.of(args[0]));
        Map<Integer, String[]> names = readNames(Path.of(args[1]));
        byte[] encoded = encode(mesh, names);
        try (FileOutputStream out = new FileOutputStream(args[2])) {
            out.write(encoded);
        }
        long vertices = mesh.values().stream().flatMap(m -> m.rings().stream()).mapToLong(r -> r.length).sum();
        System.out.printf("%d municipios, %d vertices, %d bytes%n", mesh.size(), vertices, encoded.length);
        long missing = mesh.keySet().stream().filter(code -> !names.containsKey(code)).count();
        if (missing > 0) System.out.printf("ATENCAO: %d municipios sem nome%n", missing);
        if (args.length > 3) compare(mesh, readMesh(Path.of(args[3])));
    }

    // ----------------------------------------------------------- leitura

    @SuppressWarnings("unchecked")
    static Map<Integer, Municipio> readMesh(Path file) throws IOException {
        Object root = new Json(Files.readString(file, StandardCharsets.UTF_8)).value();
        Map<Integer, Municipio> result = new TreeMap<>();
        for (Object item : (List<Object>) ((Map<String, Object>) root).get("features")) {
            Map<String, Object> feature = (Map<String, Object>) item;
            int code = Integer.parseInt((String) ((Map<String, Object>) feature.get("properties")).get("codarea"));
            Map<String, Object> geometry = (Map<String, Object>) feature.get("geometry");
            List<Object> coordinates = (List<Object>) geometry.get("coordinates");
            List<List<Object>> polygons = new ArrayList<>();
            if ("Polygon".equals(geometry.get("type"))) polygons.add(coordinates);
            else for (Object polygon : coordinates) polygons.add((List<Object>) polygon);
            List<int[][]> rings = new ArrayList<>();
            int[] bbox = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            for (List<Object> polygon : polygons) {
                for (Object ringObject : polygon) {
                    List<Object> ring = (List<Object>) ringObject;
                    int[][] points = new int[ring.size()][2];
                    for (int i = 0; i < ring.size(); i++) {
                        List<Object> point = (List<Object>) ring.get(i);
                        int lon = (int) Math.round(((Number) point.get(0)).doubleValue() * SCALE);
                        int lat = (int) Math.round(((Number) point.get(1)).doubleValue() * SCALE);
                        points[i][0] = lon;
                        points[i][1] = lat;
                        bbox[0] = Math.min(bbox[0], lon);
                        bbox[1] = Math.min(bbox[1], lat);
                        bbox[2] = Math.max(bbox[2], lon);
                        bbox[3] = Math.max(bbox[3], lat);
                    }
                    rings.add(points);
                }
            }
            result.put(code, new Municipio(code, rings, bbox));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static Map<Integer, String[]> readNames(Path file) throws IOException {
        Object root = new Json(Files.readString(file, StandardCharsets.UTF_8)).value();
        Map<Integer, String[]> result = new HashMap<>();
        for (Object item : (List<Object>) root) {
            Map<String, Object> municipio = (Map<String, Object>) item;
            int code = ((Number) municipio.get("id")).intValue();
            String name = (String) municipio.get("nome");
            // A UF vem pela microrregiao; nos municipios novos ela pode faltar e
            // os dois primeiros digitos do codigo sao o codigo da UF.
            String uf = ufFromCode(code);
            result.put(code, new String[]{name, uf});
        }
        return result;
    }

    static String ufFromCode(int code) {
        return switch (code / 100000) {
            case 11 -> "RO"; case 12 -> "AC"; case 13 -> "AM"; case 14 -> "RR"; case 15 -> "PA"; case 16 -> "AP"; case 17 -> "TO";
            case 21 -> "MA"; case 22 -> "PI"; case 23 -> "CE"; case 24 -> "RN"; case 25 -> "PB"; case 26 -> "PE"; case 27 -> "AL";
            case 28 -> "SE"; case 29 -> "BA"; case 31 -> "MG"; case 32 -> "ES"; case 33 -> "RJ"; case 35 -> "SP"; case 41 -> "PR";
            case 42 -> "SC"; case 43 -> "RS"; case 50 -> "MS"; case 51 -> "MT"; case 52 -> "GO"; case 53 -> "DF";
            default -> throw new IllegalArgumentException("codigo de UF desconhecido: " + code);
        };
    }

    // ----------------------------------------------------------- escrita

    static byte[] encode(Map<Integer, Municipio> mesh, Map<Integer, String[]> names) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeBytes("OIKM");
        out.writeByte(1);
        out.writeInt(SCALE);
        out.writeInt(mesh.size());
        for (Municipio m : mesh.values()) {
            String[] name = names.getOrDefault(m.code(), new String[]{String.valueOf(m.code()), ufFromCode(m.code())});
            out.writeInt(m.code());
            out.writeUTF(name[0]);
            out.writeUTF(name[1]);
            for (int v : m.bbox()) out.writeInt(v);
            ByteArrayOutputStream ringBytes = new ByteArrayOutputStream();
            writeVarint(ringBytes, m.rings().size());
            for (int[][] ring : m.rings()) {
                writeVarint(ringBytes, ring.length);
                int lastLon = 0, lastLat = 0;
                for (int[] p : ring) {
                    writeVarint(ringBytes, zigzag(p[0] - lastLon));
                    writeVarint(ringBytes, zigzag(p[1] - lastLat));
                    lastLon = p[0];
                    lastLat = p[1];
                }
            }
            writeVarint(out, ringBytes.size());
            ringBytes.writeTo(out);
        }
        out.flush();
        return bytes.toByteArray();
    }

    static int zigzag(int v) { return (v << 1) ^ (v >> 31); }

    static void writeVarint(java.io.OutputStream out, int value) throws IOException {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }

    // -------------------------------------------------------- comparacao

    static Integer locate(Map<Integer, Municipio> mesh, int lon, int lat) {
        for (Municipio m : mesh.values()) {
            int[] b = m.bbox();
            if (lon < b[0] || lon > b[2] || lat < b[1] || lat > b[3]) continue;
            boolean inside = false;
            for (int[][] ring : m.rings()) {
                for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
                    if ((ring[i][1] > lat) != (ring[j][1] > lat)
                        && lon < (double) (ring[j][0] - ring[i][0]) * (lat - ring[i][1]) / (ring[j][1] - ring[i][1]) + ring[i][0]) {
                        inside = !inside;
                    }
                }
            }
            if (inside) return m.code();
        }
        return null;
    }

    static void compare(Map<Integer, Municipio> a, Map<Integer, Municipio> b) {
        Random random = new Random(42);
        int samples = 200_000, inBoth = 0, differ = 0, onlyOne = 0;
        for (int i = 0; i < samples; i++) {
            int lon = (int) ((-74.0 + random.nextDouble() * 39.2) * SCALE);
            int lat = (int) ((-33.8 + random.nextDouble() * 39.1) * SCALE);
            Integer ca = locate(a, lon, lat), cb = locate(b, lon, lat);
            if (ca == null && cb == null) continue;
            if (ca == null || cb == null) { onlyOne++; continue; }
            inBoth++;
            if (!ca.equals(cb)) differ++;
        }
        System.out.printf("pontos dentro do Brasil nas duas: %d; municipio diferente: %d (%.3f%%); dentro de uma so (costa/fronteira): %d%n",
            inBoth, differ, 100.0 * differ / inBoth, onlyOne);
        // Desvio: distancia de cada vertice da outra malha ate a divisa desta.
        List<Double> distances = new ArrayList<>();
        for (Municipio other : b.values()) {
            Municipio mine = a.get(other.code());
            if (mine == null) continue;
            for (int[][] ring : other.rings()) {
                for (int k = 0; k < ring.length; k += 5) distances.add(distanceToBoundary(mine, ring[k][0], ring[k][1]));
            }
        }
        distances.sort(null);
        System.out.printf("desvio das divisas (m): mediana %.0f, p90 %.0f, p95 %.0f, p99 %.0f, max %.0f (%d vertices)%n",
            pct(distances, 50), pct(distances, 90), pct(distances, 95), pct(distances, 99), distances.get(distances.size() - 1), distances.size());
    }

    static double pct(List<Double> sorted, double p) {
        return sorted.get((int) Math.min(sorted.size() - 1, Math.round(p / 100 * (sorted.size() - 1))));
    }

    /** Distancia em metros, aproximacao plana local (suficiente para centenas de metros). */
    static double distanceToBoundary(Municipio m, int lon, int lat) {
        double kx = Math.cos(Math.toRadians(lat / (double) SCALE)) * 111320.0 / SCALE;
        double ky = 110574.0 / SCALE;
        double best = Double.MAX_VALUE;
        for (int[][] ring : m.rings()) {
            for (int i = 0, j = ring.length - 1; i < ring.length; j = i++) {
                double ax = (ring[j][0] - lon) * kx, ay = (ring[j][1] - lat) * ky;
                double bx = (ring[i][0] - lon) * kx, by = (ring[i][1] - lat) * ky;
                double dx = bx - ax, dy = by - ay;
                double len = dx * dx + dy * dy;
                double t = len == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len));
                double px = ax + t * dx, py = ay + t * dy;
                best = Math.min(best, Math.sqrt(px * px + py * py));
            }
        }
        return best;
    }

    // ------------------------------------------------- JSON minimo, sem biblioteca

    static final class Json {
        private final String s;
        private int i;

        Json(String s) { this.s = s; }

        Object value() {
            skip();
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            skip();
            if (s.charAt(i) == '}') { i++; return map; }
            while (true) {
                skip();
                String key = string();
                skip();
                i++; // :
                map.put(key, value());
                skip();
                if (s.charAt(i++) == '}') return map;
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            i++;
            skip();
            if (s.charAt(i) == ']') { i++; return list; }
            while (true) {
                list.add(value());
                skip();
                if (s.charAt(i++) == ']') return list;
            }
        }

        private String string() {
            StringBuilder b = new StringBuilder();
            i++;
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'u' -> { b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        default -> b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
        }

        private Number number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            return Double.parseDouble(s.substring(start, i));
        }

        private void skip() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
    }
}
