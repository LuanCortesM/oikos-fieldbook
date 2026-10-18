import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Gera app/src/main/assets/geoide_egm2008.bin, a ondulacao do geoide que o
 * Oikos usa para converter a altitude do GPS (sobre o elipsoide WGS84) em
 * altitude sobre o nivel medio do mar: H = h - N.
 *
 * Fonte: modelo EGM2008 da NGA, na grade de 5 minutos distribuida pelo
 * GeographicLib (egm2008-5.pgm, de 29/08/2009; o cabecalho informa erro maximo
 * de interpolacao bilinear de 0,478 m e RMS de 0,012 m em relacao ao modelo
 * completo). Para atualizar:
 *
 *   curl -L -o egm2008-5.tar.bz2 "https://sourceforge.net/projects/geographiclib/files/geoids-distrib/egm2008-5.tar.bz2/download"
 *   tar -xjf egm2008-5.tar.bz2
 *   java tools/geoide/GeoideEgm2008.java geoids/egm2008-5.pgm 3 app/src/main/assets/geoide_egm2008.bin
 *
 * O segundo argumento e o passo da grade reduzida, em celulas de 5 minutos
 * (3 = 15 minutos = 0,25 grau). O programa mede o erro da grade reduzida
 * contra a de 5 minutos em todos os nos dela. Medido em 18/10/2026 com passo
 * 3 (15', 2,1 MB): erro maximo 2,54 m e RMS 0,07 m no mundo; no Brasil, maximo
 * 1,65 m e RMS 0,09 m. Com passo 6 (30') o maximo mundial subia a 6,5 m.
 *
 * Formato (big-endian): "OIKG", versao (1 byte), passo em minutos de arco
 * (short), colunas (short), linhas (short); depois linhas x colunas valores
 * short em centimetros, da linha 90 N para 90 S e da coluna 0 grau para leste.
 */
public class GeoideEgm2008 {

    public static void main(String[] args) throws IOException {
        if (args.length == 4) {
            // Valores de referencia da grade de 5' em pontos "lat,lon;lat,lon" (para os testes).
            Grid reference = readPgm(args[0]);
            short[] asShort = new short[reference.values.length];
            for (int i = 0; i < asShort.length; i++) asShort[i] = (short) Math.round(reference.values[i] * 100.0);
            for (String point : args[3].split(";")) {
                String[] parts = point.split(",");
                double lat = Double.parseDouble(parts[0]), lon = Double.parseDouble(parts[1]);
                System.out.printf(java.util.Locale.US, "N(%s, %s) = %.2f m%n", parts[0], parts[1], bilinear(asShort, reference.cols, reference.rows, 1.0 / 12.0, lat, lon) / 100.0);
            }
        }
        if (args.length < 3) {
            System.err.println("uso: java GeoideEgm2008.java egm2008-5.pgm passo saida.bin");
            System.exit(1);
        }
        Grid full = readPgm(args[0]);
        int step = Integer.parseInt(args[1]);
        int cols = full.cols / step;
        int rows = (full.rows - 1) / step + 1;
        short[] reduced = new short[rows * cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                double value = full.values[(r * step) * full.cols + c * step];
                reduced[r * cols + c] = (short) Math.round(value * 100.0);
            }
        }
        // Erro da grade reduzida, interpolada, contra cada no da grade de 5'.
        double max = 0, sumSquares = 0;
        long count = 0;
        double fullStep = 1.0 / 12.0;
        double reducedStep = fullStep * step;
        for (int r = 0; r < full.rows; r++) {
            double lat = 90.0 - r * fullStep;
            for (int c = 0; c < full.cols; c++) {
                double lon = c * fullStep;
                double estimate = bilinear(reduced, cols, rows, reducedStep, lat, lon) / 100.0;
                double error = Math.abs(estimate - full.values[r * full.cols + c]);
                max = Math.max(max, error);
                sumSquares += error * error;
                count++;
            }
        }
        System.out.printf("grade %d' : %d x %d, erro max %.3f m, RMS %.4f m%n", 5 * step, cols, rows, max, Math.sqrt(sumSquares / count));
        // So o Brasil (caixa de 34 S a 6 N, 74 W a 28 W).
        double maxBr = 0, sumBr = 0;
        long countBr = 0;
        for (int r = 0; r < full.rows; r++) {
            double lat = 90.0 - r * fullStep;
            if (lat < -34 || lat > 6) continue;
            for (int c = 0; c < full.cols; c++) {
                double lon = c * fullStep - 360.0;
                if (lon < -74 || lon > -28) continue;
                double error = Math.abs(bilinear(reduced, cols, rows, reducedStep, lat, lon) / 100.0 - full.values[r * full.cols + c]);
                maxBr = Math.max(maxBr, error);
                sumBr += error * error;
                countBr++;
            }
        }
        System.out.printf("  Brasil: erro max %.3f m, RMS %.4f m%n", maxBr, Math.sqrt(sumBr / countBr));
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(args[2]))) {
            out.write("OIKG".getBytes(StandardCharsets.US_ASCII));
            out.writeByte(1);
            out.writeShort(5 * step);
            out.writeShort(cols);
            out.writeShort(rows);
            for (short value : reduced) out.writeShort(value);
        }
    }

    /** Mesma interpolacao do app (Geoid.kt). */
    static double bilinear(short[] grid, int cols, int rows, double step, double lat, double lon) {
        double y = (90.0 - lat) / step;
        double x = ((lon % 360.0) + 360.0) % 360.0 / step;
        int r0 = Math.min((int) Math.floor(y), rows - 2);
        int c0 = (int) Math.floor(x) % cols;
        int c1 = (c0 + 1) % cols;
        double fy = y - r0;
        double fx = x - Math.floor(x);
        double a = grid[r0 * cols + c0], b = grid[r0 * cols + c1];
        double c = grid[(r0 + 1) * cols + c0], d = grid[(r0 + 1) * cols + c1];
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }

    static class Grid {
        int cols, rows;
        double[] values;
    }

    static Grid readPgm(String path) throws IOException {
        try (InputStream raw = new BufferedInputStream(new FileInputStream(path))) {
            double offset = 0, scale = 1;
            if (!readToken(raw).equals("P5")) throw new IOException("nao e PGM binario");
            Grid grid = new Grid();
            int[] header = new int[3];
            int found = 0;
            while (found < 3) {
                String token = readToken(raw);
                if (token.startsWith("#")) {
                    String line = token + readLine(raw);
                    if (line.startsWith("# Offset")) offset = Double.parseDouble(line.substring(8).trim());
                    if (line.startsWith("# Scale")) scale = Double.parseDouble(line.substring(7).trim());
                    continue;
                }
                header[found++] = Integer.parseInt(token);
            }
            grid.cols = header[0];
            grid.rows = header[1];
            grid.values = new double[grid.cols * grid.rows];
            DataInputStream in = new DataInputStream(raw);
            for (int i = 0; i < grid.values.length; i++) grid.values[i] = offset + scale * in.readUnsignedShort();
            return grid;
        }
    }

    /** Palavra seguinte do cabecalho; consome um separador depois dela. */
    static String readToken(InputStream in) throws IOException {
        StringBuilder token = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1 && Character.isWhitespace(ch)) { }
        if (ch == '#') return "#";
        while (ch != -1 && !Character.isWhitespace(ch)) {
            token.append((char) ch);
            ch = in.read();
        }
        return token.toString();
    }

    static String readLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = in.read()) != -1 && ch != '\n') line.append((char) ch);
        return line.toString();
    }
}
