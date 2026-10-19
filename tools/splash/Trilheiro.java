import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vetoriza o trilheiro desenhado pelo Luan (trilheiro_desenho.png) em tres partes para a
 * caminhada da tela de abertura: corpo (com cabeca, mochila, braco e bastao), perna proxima
 * (a cheia, na frente no desenho) e perna distante (a contornada, atras no desenho).
 *
 * As pernas sao cortadas na linha do quadril (CORTE) e giram cada uma em torno do meio do
 * proprio topo; um meio disco acima do corte, escondido sob o quadril do corpo, cobre a
 * emenda quando a perna gira. Contornos por marching squares no alfa do desenho (meio pixel
 * de precisao) e simplificados por Ramer-Douglas-Peucker com TOLERANCIA pixels.
 *
 * Saidas:
 *   app/src/main/res/drawable/trilheiro.xml  (VectorDrawable lido pelo app; pivos nos grupos)
 *   tools/splash/trilheiro.svg                (as tres partes, para conferir e reusar)
 *
 * Rodar na raiz do projeto:
 *   java tools/splash/Trilheiro.java [pasta para as imagens de conferencia]
 */
public class Trilheiro {
    static final int CORTE = 690;
    static final double TOLERANCIA = 0.35;
    static final double MARGEM = 4;
    static final double SOBRA = 3;
    static int W, H;

    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File("tools/splash/trilheiro_desenho.png"));
        W = img.getWidth();
        H = img.getHeight();
        float[][] alfa = new float[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                alfa[y][x] = (img.getRGB(x, y) >>> 24) / 255f;

        // Pecas abaixo do quadril: cada componente ligado vira perna proxima, perna distante
        // ou parte do corpo (o bastao), pela posicao.
        int[][] rotulo = new int[H][W];
        List<int[]> pecas = new ArrayList<>();
        for (int y = CORTE; y < H; y++)
            for (int x = 0; x < W; x++)
                if (rotulo[y][x] == 0 && alfa[y][x] >= 40 / 255f) pecas.add(preencher(alfa, rotulo, x, y, pecas.size() + 1));
        int[] tipoDaPeca = new int[pecas.size() + 1];
        for (int[] p : pecas) {
            int largura = p[2] - p[1];
            double centro = (p[1] + p[2]) / 2.0;
            if (p[0] < 30 || (largura < 100 && p[1] > 800)) tipoDaPeca[p[5]] = CORPO;
            else if (centro < 600) tipoDaPeca[p[5]] = DISTANTE;
            else tipoDaPeca[p[5]] = PROXIMA;
        }
        // Franja suave do antisserrilhado (abaixo do limiar da busca) segue a peca vizinha.
        int[][] tipo = new int[H][W];
        for (int y = CORTE; y < H; y++)
            for (int x = 0; x < W; x++) {
                if (rotulo[y][x] != 0) { tipo[y][x] = tipoDaPeca[rotulo[y][x]]; continue; }
                if (alfa[y][x] == 0) continue;
                tipo[y][x] = CORPO;
                busca:
                for (int r = 1; r <= 3; r++)
                    for (int j = -r; j <= r; j++)
                        for (int i = -r; i <= r; i++) {
                            int nx = x + i, ny = y + j;
                            if (nx >= 0 && ny >= CORTE && nx < W && ny < H && rotulo[ny][nx] != 0) {
                                tipo[y][x] = tipoDaPeca[rotulo[ny][nx]];
                                break busca;
                            }
                        }
            }

        float[][] corpo = new float[H][W], proxima = new float[H][W], distante = new float[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                if (y < CORTE || tipo[y][x] == CORPO) corpo[y][x] = alfa[y][x];
                else if (tipo[y][x] == PROXIMA) proxima[y][x] = alfa[y][x];
                else if (tipo[y][x] == DISTANTE) distante[y][x] = alfa[y][x];
            }
        double[] pivoProxima = pivo(proxima), pivoDistante = pivo(distante);
        junta(proxima, corpo, pivoProxima);
        junta(distante, corpo, pivoDistante);

        // Recorte no contorno do desenho inteiro.
        int minX = W, minY = H, maxX = 0, maxY = 0;
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if (alfa[y][x] > 0.02f) {
                    minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                }
        double dx = MARGEM - minX, dy = MARGEM - minY;
        double vw = maxX - minX + 2 * MARGEM, vh = maxY - minY + 2 * MARGEM;

        List<List<double[]>> lCorpo = contornos(corpo, dx, dy);
        List<List<double[]>> lProxima = contornos(proxima, dx, dy);
        List<List<double[]>> lDistante = contornos(distante, dx, dy);
        double[] pp = {pivoProxima[0] + dx, pivoProxima[1] + dy}, pd = {pivoDistante[0] + dx, pivoDistante[1] + dy};

        double larguraDp = 15 * vw / vh;
        String xml = String.format(Locale.ROOT,
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- Trilheiro desenhado pelo Luan (tools/splash/trilheiro_desenho.png), em tres partes,\n"
                + "     gerado por tools/splash/Trilheiro.java. Nao editar a mao: rodar o gerador de novo.\n"
                + "     As pernas giram em torno do pivo do proprio grupo. -->\n"
                + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:width=\"%.1fdp\"\n"
                + "    android:height=\"15dp\"\n"
                + "    android:viewportWidth=\"%.1f\"\n"
                + "    android:viewportHeight=\"%.1f\">\n"
                + "%s%s%s"
                + "</vector>\n",
            larguraDp, vw, vh,
            grupo("perna_distante", pd, lDistante),
            grupo("perna_proxima", pp, lProxima),
            caminhoXml("corpo", lCorpo, "    "));
        Files.write(new File("app/src/main/res/drawable/trilheiro.xml").toPath(), xml.getBytes(StandardCharsets.UTF_8));
        String svg = String.format(Locale.ROOT,
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.1f %.1f\" width=\"%.0f\" height=\"%.0f\">\n"
                + "  <rect width=\"100%%\" height=\"100%%\" fill=\"#0B0B0D\"/>\n"
                + "  <path id=\"perna_distante\" fill=\"#FFFFFF\" fill-rule=\"evenodd\" d=\"%s\"/>\n"
                + "  <path id=\"perna_proxima\" fill=\"#FFFFFF\" fill-rule=\"evenodd\" d=\"%s\"/>\n"
                + "  <path id=\"corpo\" fill=\"#FFFFFF\" fill-rule=\"evenodd\" d=\"%s\"/>\n"
                + "</svg>\n",
            vw, vh, vw, vh, dados(lDistante), dados(lProxima), dados(lCorpo));
        Files.write(new File("tools/splash/trilheiro.svg").toPath(), svg.getBytes(StandardCharsets.UTF_8));
        System.out.printf(Locale.ROOT, "viewport %.1f x %.1f; vertices corpo %d, proxima %d, distante %d; pivos %.1f,%.1f e %.1f,%.1f%n",
            vw, vh, vertices(lCorpo), vertices(lProxima), vertices(lDistante), pp[0], pp[1], pd[0], pd[1]);

        if (args.length > 0) conferir(new File(args[0]), lCorpo, lProxima, lDistante, pp, pd, vw, vh);
    }

    static final int CORPO = 1, PROXIMA = 2, DISTANTE = 3;

    /** Busca em largura de um componente; devolve {pixels, minX, maxX, minY, maxY, rotulo}. */
    static int[] preencher(float[][] alfa, int[][] rotulo, int x0, int y0, int id) {
        ArrayDeque<int[]> fila = new ArrayDeque<>();
        fila.add(new int[]{x0, y0});
        rotulo[y0][x0] = id;
        int n = 0, minX = x0, maxX = x0, minY = y0, maxY = y0;
        while (!fila.isEmpty()) {
            int[] p = fila.poll();
            n++;
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
            for (int j = -1; j <= 1; j++)
                for (int i = -1; i <= 1; i++) {
                    int nx = p[0] + i, ny = p[1] + j;
                    if (nx >= 0 && ny >= CORTE && nx < W && ny < H && rotulo[ny][nx] == 0 && alfa[ny][nx] >= 40 / 255f) {
                        rotulo[ny][nx] = id;
                        fila.add(new int[]{nx, ny});
                    }
                }
        }
        return new int[]{n, minX, maxX, minY, maxY, id};
    }

    /** Meio do topo da perna na linha do corte, com o meio comprimento do topo como raio. */
    static double[] pivo(float[][] perna) {
        int a = -1, b = -1;
        for (int x = 0; x < W; x++)
            if (perna[CORTE][x] >= 0.5f) { if (a < 0) a = x; b = x; }
        return new double[]{(a + b + 1) / 2.0, CORTE, (b - a + 1) / 2.0 + 2};
    }

    /**
     * Articulacao do quadril: o disco em volta do pivo passa para o corpo e a perna perde o
     * mesmo disco (menos SOBRA px, escondidos sob o corpo). Girando em torno do centro do
     * disco, o topo da perna desliza por ele sem abrir fenda nem deixar quina para fora.
     */
    static void junta(float[][] perna, float[][] corpo, double[] p) {
        double r = p[2];
        for (int y = (int) (p[1] - r) - 2; y <= p[1] + r + 2; y++)
            for (int x = (int) (p[0] - r) - 2; x <= p[0] + r + 2; x++) {
                double d = Math.hypot(x - p[0], y - p[1]);
                double dentro = Math.max(0, Math.min(1, r - d + 0.5)), fora = Math.max(0, Math.min(1, d - (r - SOBRA) + 0.5));
                if (y >= CORTE) corpo[y][x] = Math.max(corpo[y][x], (float) (perna[y][x] * dentro));
                perna[y][x] = (float) (perna[y][x] * fora);
            }
    }

    // ---------- contornos ----------

    /** Marching squares no limiar 0,5, com o ponto de cada aresta interpolado no alfa. */
    static List<List<double[]>> contornos(float[][] f, double dx, double dy) {
        Map<Integer, double[]> ponto = new HashMap<>();
        Map<Integer, List<Integer>> vizinhos = new HashMap<>();
        for (int y = -1; y < H; y++)
            for (int x = -1; x < W; x++) {
                double c0 = v(f, x, y), c1 = v(f, x + 1, y), c2 = v(f, x + 1, y + 1), c3 = v(f, x, y + 1);
                boolean i0 = c0 >= 0.5, i1 = c1 >= 0.5, i2 = c2 >= 0.5, i3 = c3 >= 0.5;
                int e0 = -1, e1 = -1, e2 = -1, e3 = -1;
                if (i0 != i1) e0 = aresta(ponto, 0, x, y, x + t(c0, c1), y);
                if (i1 != i2) e1 = aresta(ponto, 1, x + 1, y, x + 1, y + t(c1, c2));
                if (i3 != i2) e2 = aresta(ponto, 0, x, y + 1, x + t(c3, c2), y + 1);
                if (i0 != i3) e3 = aresta(ponto, 1, x, y, x, y + t(c0, c3));
                List<int[]> seg = new ArrayList<>();
                int[] es = {e0, e1, e2, e3};
                int quantas = 0;
                for (int e : es) if (e >= 0) quantas++;
                if (quantas == 2) {
                    int a = -1, b = -1;
                    for (int e : es) if (e >= 0) { if (a < 0) a = e; else b = e; }
                    seg.add(new int[]{a, b});
                } else if (quantas == 4) {
                    boolean centro = (c0 + c1 + c2 + c3) / 4 >= 0.5;
                    if (i0 == centro) { seg.add(new int[]{e0, e1}); seg.add(new int[]{e2, e3}); }
                    else { seg.add(new int[]{e3, e0}); seg.add(new int[]{e1, e2}); }
                }
                for (int[] s : seg) {
                    vizinhos.computeIfAbsent(s[0], k -> new ArrayList<>()).add(s[1]);
                    vizinhos.computeIfAbsent(s[1], k -> new ArrayList<>()).add(s[0]);
                }
            }
        List<List<double[]>> laços = new ArrayList<>();
        java.util.Set<Integer> usados = new java.util.HashSet<>();
        for (Integer inicio : vizinhos.keySet()) {
            if (usados.contains(inicio)) continue;
            List<double[]> laço = new ArrayList<>();
            int anterior = -1, atual = inicio;
            while (true) {
                usados.add(atual);
                double[] p = ponto.get(atual);
                laço.add(new double[]{p[0] + dx, p[1] + dy});
                List<Integer> n = vizinhos.get(atual);
                int proximo = n.get(0) != anterior ? n.get(0) : n.get(1);
                if (n.size() == 2 && n.get(0).equals(n.get(1))) proximo = n.get(0);
                anterior = atual;
                atual = proximo;
                if (atual == inicio) break;
            }
            if (laço.size() >= 8) laços.add(simplificarFechado(laço));
        }
        return laços;
    }

    static double v(float[][] f, int x, int y) {
        return x < 0 || y < 0 || x >= W || y >= H ? 0 : f[y][x];
    }

    static double t(double a, double b) {
        return (0.5 - a) / (b - a);
    }

    static int aresta(Map<Integer, double[]> ponto, int tipo, int x, int y, double px, double py) {
        int chave = (((y + 1) * (W + 2)) + (x + 1)) * 2 + tipo;
        ponto.putIfAbsent(chave, new double[]{px, py});
        return chave;
    }

    static List<double[]> simplificarFechado(List<double[]> p) {
        int longe = 0;
        double max = -1;
        for (int i = 0; i < p.size(); i++) {
            double d = Math.hypot(p.get(i)[0] - p.get(0)[0], p.get(i)[1] - p.get(0)[1]);
            if (d > max) { max = d; longe = i; }
        }
        List<double[]> a = rdp(p.subList(0, longe + 1));
        List<double[]> volta = new ArrayList<>(p.subList(longe, p.size()));
        volta.add(p.get(0));
        List<double[]> b = rdp(volta);
        List<double[]> r = new ArrayList<>(a.subList(0, a.size() - 1));
        r.addAll(b.subList(0, b.size() - 1));
        return r;
    }

    static List<double[]> rdp(List<double[]> p) {
        if (p.size() < 3) return new ArrayList<>(p);
        double[] a = p.get(0), b = p.get(p.size() - 1);
        int idx = 0;
        double max = 0;
        for (int i = 1; i < p.size() - 1; i++) {
            double d = Line2D.ptSegDist(a[0], a[1], b[0], b[1], p.get(i)[0], p.get(i)[1]);
            if (d > max) { max = d; idx = i; }
        }
        if (max <= TOLERANCIA) {
            List<double[]> r = new ArrayList<>();
            r.add(a);
            r.add(b);
            return r;
        }
        List<double[]> e = rdp(p.subList(0, idx + 1)), d = rdp(p.subList(idx, p.size()));
        List<double[]> r = new ArrayList<>(e.subList(0, e.size() - 1));
        r.addAll(d);
        return r;
    }

    static int vertices(List<List<double[]>> l) {
        int n = 0;
        for (List<double[]> c : l) n += c.size();
        return n;
    }

    static String dados(List<List<double[]>> laços) {
        StringBuilder s = new StringBuilder();
        for (List<double[]> c : laços) {
            for (int i = 0; i < c.size(); i++) {
                s.append(i == 0 ? "M" : i == 1 ? "L" : " ");
                s.append(String.format(Locale.ROOT, "%.1f,%.1f", c.get(i)[0], c.get(i)[1]));
            }
            s.append("Z");
        }
        return s.toString();
    }

    static String caminhoXml(String nome, List<List<double[]>> l, String recuo) {
        return String.format(Locale.ROOT,
            "%s<path\n%s    android:name=\"%s\"\n%s    android:fillColor=\"#FFFFFF\"\n%s    android:fillType=\"evenOdd\"\n%s    android:pathData=\"%s\" />\n",
            recuo, recuo, nome, recuo, recuo, recuo, dados(l));
    }

    static String grupo(String nome, double[] pivo, List<List<double[]>> l) {
        return String.format(Locale.ROOT, "    <group\n        android:name=\"%s\"\n        android:pivotX=\"%.1f\"\n        android:pivotY=\"%.1f\">\n%s    </group>\n",
            nome, pivo[0], pivo[1], caminhoXml(nome, l, "        "));
    }

    // ---------- conferencia: a mesma cinematica do app ----------

    static Path2D forma(List<List<double[]>> laços) {
        Path2D.Double p = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (List<double[]> c : laços) {
            p.moveTo(c.get(0)[0], c.get(0)[1]);
            for (int i = 1; i < c.size(); i++) p.lineTo(c.get(i)[0], c.get(i)[1]);
            p.closePath();
        }
        return p;
    }

    /** Angulo do pe (media dos vertices nos 90 px de baixo) em relacao ao pivo, a partir da vertical. */
    static double anguloDoPe(List<List<double[]>> perna, double[] pivo) {
        double fundo = 0, sx = 0, sy = 0;
        int n = 0;
        for (List<double[]> c : perna) for (double[] p : c) fundo = Math.max(fundo, p[1]);
        for (List<double[]> c : perna) for (double[] p : c) if (p[1] > fundo - 90) { sx += p[0]; sy += p[1]; n++; }
        return Math.atan2(sx / n - pivo[0], sy / n - pivo[1]);
    }

    static double[] girar(double[] p, double[] pivo, double a) {
        double c = Math.cos(a), s = Math.sin(a), x = p[0] - pivo[0], y = p[1] - pivo[1];
        return new double[]{pivo[0] + x * c - y * s, pivo[1] + x * s + y * c};
    }

    static void conferir(File pasta, List<List<double[]>> corpo, List<List<double[]>> proxima, List<List<double[]>> distante,
                         double[] pp, double[] pd, double vw, double vh) throws Exception {
        pasta.mkdirs();
        double abertura = anguloDoPe(proxima, pp) - anguloDoPe(distante, pd);
        System.out.printf(Locale.ROOT, "abertura das pernas %.1f graus%n", Math.toDegrees(abertura));
        int poses = 8;
        for (int escala : new int[]{6, 1, 2}) {
            double s = escala == 6 ? 0.22 : escala == 2 ? 0.6 : 48 / vh;
            int cw = (int) Math.ceil(vw * s) + 10, ch = (int) Math.ceil(vh * s) + 14;
            BufferedImage out = new BufferedImage(cw * poses, ch, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            g.setColor(new Color(0x0B0B0D));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Path2D fc = forma(corpo), fp = forma(proxima), fd = forma(distante);
            for (int k = 0; k < poses; k++) {
                double fase = 2 * Math.PI * k / poses, giro = abertura * (1 - Math.cos(fase)) / 2;
                double chao = vh - MARGEM, desce = Double.MAX_VALUE;
                for (List<double[]> c : proxima) for (double[] p : c) desce = Math.min(desce, chao - girar(p, pp, giro)[1]);
                for (List<double[]> c : distante) for (double[] p : c) desce = Math.min(desce, chao - girar(p, pd, -giro)[1]);
                AffineTransform base = new AffineTransform();
                base.translate(k * cw + 5, 4);
                base.scale(s, s);
                base.translate(0, desce);
                g.setColor(new Color(0xFF6A00));
                g.setStroke(new BasicStroke(1f));
                g.drawLine(k * cw, (int) Math.round(4 + chao * s), k * cw + cw - 4, (int) Math.round(4 + chao * s));
                g.setColor(Color.WHITE);
                AffineTransform t = new AffineTransform(base);
                t.rotate(-giro, pd[0], pd[1]);
                g.fill(t.createTransformedShape(fd));
                t = new AffineTransform(base);
                t.rotate(giro, pp[0], pp[1]);
                g.fill(t.createTransformedShape(fp));
                g.fill(base.createTransformedShape(fc));
            }
            g.dispose();
            ImageIO.write(out, "png", new File(pasta, escala == 6 ? "poses_grandes.png" : escala == 2 ? "poses_detalhe.png" : "poses_tamanho_real.png"));
        }
    }
}
