import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Vetoriza o contorno do Itaguare desenhado pelo Luan (itaguare_desenho.png) para a
 * barra de carregamento da tela de abertura.
 *
 * Linha central do traco: esqueleto (Zhang-Suen) ordenado de ponta a ponta, recentrado
 * pelo centroide da tinta na normal e suavizado; depois curvas de Bezier cubicas
 * (Schneider, "An algorithm for automatically fitting digitized curves", Graphics Gems,
 * 1990) com erro maximo de ERRO_PX pixels do desenho.
 *
 * Saidas:
 *   app/src/main/res/drawable/itaguare_contorno.xml  (VectorDrawable lido pelo app)
 *   tools/splash/itaguare_contorno.svg                (o mesmo traco, para conferir e reusar)
 *
 * Rodar na raiz do projeto:
 *   java tools/splash/ContornoItaguare.java [pasta para as imagens de conferencia]
 */
public class ContornoItaguare {
    static final double ERRO_PX = 0.35;
    static final double MARGEM = 4;
    static final String ORANGE = "#FF6A00";
    static float[][] tinta;
    static int W, H;

    public static void main(String[] args) throws Exception {
        BufferedImage img = ImageIO.read(new File("tools/splash/itaguare_desenho.png"));
        W = img.getWidth();
        H = img.getHeight();
        tinta = new float[H][W];
        boolean[][] mascara = new boolean[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                tinta[y][x] = (img.getRGB(x, y) >>> 24) / 255f;
                mascara[y][x] = tinta[y][x] > 0.3f;
            }
        }
        mascara = erodir(dilatar(mascara));
        afinar(mascara);
        List<double[]> pts = caminhoMaisLongo(mascara);
        if (pts.get(0)[0] > pts.get(pts.size() - 1)[0]) Collections.reverse(pts);
        pts = suavizar(pts, 1.5);
        pts = reamostrar(pts, 1.0);
        pts = recentrar(pts);
        pts = suavizar(pts, 2.0);
        pts = estenderPontas(pts);
        pts = reamostrar(pts, 1.0);

        List<double[][]> curvas = new ArrayList<>();
        double[][] d = pts.toArray(new double[0][]);
        ajustar(d, 0, d.length - 1, tangente(d, 0, +1), tangente(d, d.length - 1, -1), ERRO_PX * ERRO_PX, curvas);

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] p : pts) {
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        double dx = MARGEM - minX, dy = MARGEM - minY;
        double vw = maxX - minX + 2 * MARGEM, vh = maxY - minY + 2 * MARGEM;
        StringBuilder path = new StringBuilder();
        path.append(String.format(Locale.ROOT, "M%.2f,%.2f", curvas.get(0)[0][0] + dx, curvas.get(0)[0][1] + dy));
        for (double[][] c : curvas) {
            path.append(String.format(Locale.ROOT, "C%.2f,%.2f %.2f,%.2f %.2f,%.2f",
                c[1][0] + dx, c[1][1] + dy, c[2][0] + dx, c[2][1] + dy, c[3][0] + dx, c[3][1] + dy));
        }

        double larguraDp = 160, alturaDp = larguraDp * vh / vw;
        String xml = String.format(Locale.ROOT,
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<!-- Contorno do Itaguare, vetorizado do desenho do Luan (tools/splash/itaguare_desenho.png)\n"
                + "     por tools/splash/ContornoItaguare.java. Nao editar a mao: rodar o gerador de novo. -->\n"
                + "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    android:width=\"%.0fdp\"\n"
                + "    android:height=\"%.1fdp\"\n"
                + "    android:viewportWidth=\"%.2f\"\n"
                + "    android:viewportHeight=\"%.2f\">\n"
                + "    <path\n"
                + "        android:name=\"contorno\"\n"
                + "        android:pathData=\"%s\"\n"
                + "        android:strokeColor=\"%s\"\n"
                + "        android:strokeWidth=\"4\"\n"
                + "        android:strokeLineCap=\"round\"\n"
                + "        android:strokeLineJoin=\"round\" />\n"
                + "</vector>\n",
            larguraDp, alturaDp, vw, vh, path, ORANGE);
        Files.write(new File("app/src/main/res/drawable/itaguare_contorno.xml").toPath(), xml.getBytes(StandardCharsets.UTF_8));
        String svg = String.format(Locale.ROOT,
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.2f %.2f\" width=\"%.0f\" height=\"%.0f\">\n"
                + "  <path d=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"4\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n"
                + "</svg>\n",
            vw, vh, vw, vh, path, ORANGE);
        Files.write(new File("tools/splash/itaguare_contorno.svg").toPath(), svg.getBytes(StandardCharsets.UTF_8));
        System.out.printf(Locale.ROOT, "%d pontos, %d curvas, viewport %.2f x %.2f%n", pts.size(), curvas.size(), vw, vh);

        if (args.length > 0) conferir(new File(args[0]), img, curvas, pts);
    }

    // ---------- esqueleto ----------

    static boolean[][] dilatar(boolean[][] m) {
        boolean[][] r = new boolean[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                for (int j = -1; j <= 1 && !r[y][x]; j++)
                    for (int i = -1; i <= 1; i++)
                        if (dentro(x + i, y + j) && m[y + j][x + i]) { r[y][x] = true; break; }
        return r;
    }

    static boolean[][] erodir(boolean[][] m) {
        boolean[][] r = new boolean[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                boolean todos = m[y][x];
                for (int j = -1; j <= 1 && todos; j++)
                    for (int i = -1; i <= 1; i++)
                        if (!dentro(x + i, y + j) || !m[y + j][x + i]) { todos = false; break; }
                r[y][x] = todos;
            }
        return r;
    }

    static boolean dentro(int x, int y) {
        return x >= 0 && y >= 0 && x < W && y < H;
    }

    /** Afinamento de Zhang-Suen (1984): reduz o traco a uma linha de 1 pixel. */
    static void afinar(boolean[][] m) {
        boolean mudou = true;
        while (mudou) {
            mudou = false;
            for (int passo = 0; passo < 2; passo++) {
                List<int[]> apagar = new ArrayList<>();
                for (int y = 1; y < H - 1; y++) {
                    for (int x = 1; x < W - 1; x++) {
                        if (!m[y][x]) continue;
                        boolean[] v = {m[y - 1][x], m[y - 1][x + 1], m[y][x + 1], m[y + 1][x + 1],
                            m[y + 1][x], m[y + 1][x - 1], m[y][x - 1], m[y - 1][x - 1]};
                        int b = 0, a = 0;
                        for (int k = 0; k < 8; k++) {
                            if (v[k]) b++;
                            if (!v[k] && v[(k + 1) % 8]) a++;
                        }
                        if (b < 2 || b > 6 || a != 1) continue;
                        boolean c1 = passo == 0 ? !(v[0] && v[2] && v[4]) : !(v[0] && v[2] && v[6]);
                        boolean c2 = passo == 0 ? !(v[2] && v[4] && v[6]) : !(v[0] && v[4] && v[6]);
                        if (c1 && c2) apagar.add(new int[]{x, y});
                    }
                }
                for (int[] p : apagar) m[p[1]][p[0]] = false;
                if (!apagar.isEmpty()) mudou = true;
            }
        }
    }

    /** Maior caminho do esqueleto (diametro por duas buscas em largura): ignora rebarbas. */
    static List<double[]> caminhoMaisLongo(boolean[][] m) {
        int total = 0, inicio = -1;
        for (int x = 0; x < W && inicio < 0; x++)
            for (int y = 0; y < H; y++)
                if (m[y][x]) { inicio = y * W + x; break; }
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (m[y][x]) total++;
        int[] pai = new int[W * H];
        int a = buscar(m, inicio, pai, total);
        int b = buscar(m, a, pai, total);
        List<double[]> pts = new ArrayList<>();
        for (int p = b; p != -1; p = pai[p]) pts.add(new double[]{p % W, p / W});
        return pts;
    }

    static int buscar(boolean[][] m, int origem, int[] pai, int total) {
        int[] dist = new int[W * H];
        java.util.Arrays.fill(dist, -1);
        ArrayDeque<Integer> fila = new ArrayDeque<>();
        dist[origem] = 0;
        pai[origem] = -1;
        fila.add(origem);
        int ultimo = origem, visitados = 0;
        while (!fila.isEmpty()) {
            int p = fila.poll();
            visitados++;
            ultimo = p;
            int x = p % W, y = p / W;
            for (int j = -1; j <= 1; j++)
                for (int i = -1; i <= 1; i++) {
                    int nx = x + i, ny = y + j;
                    if ((i != 0 || j != 0) && dentro(nx, ny) && m[ny][nx] && dist[ny * W + nx] < 0) {
                        dist[ny * W + nx] = dist[p] + 1;
                        pai[ny * W + nx] = p;
                        fila.add(ny * W + nx);
                    }
                }
        }
        if (visitados != total) {
            throw new IllegalStateException("Traco partido: " + visitados + " de " + total + " pixels ligados");
        }
        return ultimo;
    }

    // ---------- linha central ----------

    static double tintaEm(double x, double y) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0, s = 0;
        for (int j = 0; j <= 1; j++)
            for (int i = 0; i <= 1; i++) {
                int px = x0 + i, py = y0 + j;
                if (dentro(px, py)) s += tinta[py][px] * (i == 0 ? 1 - fx : fx) * (j == 0 ? 1 - fy : fy);
            }
        return s;
    }

    static double[] direcao(List<double[]> p, int i, int alcance) {
        int a = Math.max(0, i - alcance), b = Math.min(p.size() - 1, i + alcance);
        double dx = p.get(b)[0] - p.get(a)[0], dy = p.get(b)[1] - p.get(a)[1], n = Math.hypot(dx, dy);
        return new double[]{dx / n, dy / n};
    }

    /** Leva cada ponto ao centroide da tinta na normal, so dentro do trecho continuo do traco. */
    static List<double[]> recentrar(List<double[]> pts) {
        List<double[]> r = new ArrayList<>();
        double passo = 0.25;
        for (int i = 0; i < pts.size(); i++) {
            double[] p = pts.get(i), t = direcao(pts, i, 3);
            double nx = -t[1], ny = t[0];
            int meio = 24;
            double[] v = new double[2 * meio + 1];
            for (int k = -meio; k <= meio; k++) v[k + meio] = tintaEm(p[0] + k * passo * nx, p[1] + k * passo * ny);
            int c = meio;
            for (int k = 0; k <= 12 && v[c] < 0.05; k++) {
                if (v[meio + k] >= 0.05) c = meio + k;
                else if (v[meio - k] >= 0.05) c = meio - k;
            }
            if (v[c] < 0.05) { r.add(p); continue; }
            int a = c, b = c;
            while (a > 0 && v[a - 1] > 0.03) a--;
            while (b < v.length - 1 && v[b + 1] > 0.03) b++;
            double soma = 0, momento = 0;
            for (int k = a; k <= b; k++) { soma += v[k]; momento += v[k] * (k - meio) * passo; }
            double o = momento / soma;
            r.add(new double[]{p[0] + o * nx, p[1] + o * ny});
        }
        return r;
    }

    /** Media gaussiana com janela simetrica, que encolhe perto das pontas para nao move-las. */
    static List<double[]> suavizar(List<double[]> pts, double sigma) {
        int n = pts.size(), raio = (int) Math.ceil(3 * sigma);
        List<double[]> r = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int w = Math.min(raio, Math.min(i, n - 1 - i));
            double sx = 0, sy = 0, sp = 0;
            for (int k = -w; k <= w; k++) {
                double peso = Math.exp(-k * k / (2 * sigma * sigma));
                sx += pts.get(i + k)[0] * peso;
                sy += pts.get(i + k)[1] * peso;
                sp += peso;
            }
            r.add(new double[]{sx / sp, sy / sp});
        }
        return r;
    }

    static List<double[]> reamostrar(List<double[]> pts, double passo) {
        List<double[]> r = new ArrayList<>();
        r.add(pts.get(0));
        double resto = passo;
        for (int i = 1; i < pts.size(); i++) {
            double[] a = pts.get(i - 1), b = pts.get(i);
            double seg = Math.hypot(b[0] - a[0], b[1] - a[1]), pos = 0;
            while (seg - pos >= resto) {
                pos += resto;
                double t = pos / seg;
                r.add(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
                resto = passo;
            }
            resto -= seg - pos;
        }
        double[] ultimo = pts.get(pts.size() - 1), fim = r.get(r.size() - 1);
        if (Math.hypot(ultimo[0] - fim[0], ultimo[1] - fim[1]) > passo * 0.3) r.add(ultimo);
        else r.set(r.size() - 1, ultimo);
        return r;
    }

    /** O esqueleto encurta as pontas; segue a tangente enquanto ainda ha tinta. */
    static List<double[]> estenderPontas(List<double[]> pts) {
        List<double[]> r = new ArrayList<>(pts);
        for (int lado = 0; lado < 2; lado++) {
            int i = lado == 0 ? 0 : r.size() - 1, j = lado == 0 ? 5 : r.size() - 6;
            double[] p = r.get(i), q = r.get(j);
            double dx = p[0] - q[0], dy = p[1] - q[1], n = Math.hypot(dx, dy);
            dx /= n; dy /= n;
            double k = 0;
            while (k < 8 && tintaEm(p[0] + (k + 0.25) * dx, p[1] + (k + 0.25) * dy) > 0.4) k += 0.25;
            if (k > 0) {
                double[] ponta = {p[0] + k * dx, p[1] + k * dy};
                if (lado == 0) r.add(0, ponta); else r.add(ponta);
            }
        }
        return r;
    }

    // ---------- ajuste de Bezier (Schneider) ----------

    static double[] tangente(double[][] d, int i, int sentido) {
        int j = Math.max(0, Math.min(d.length - 1, i + 3 * sentido));
        return unit(sub(d[j], d[i]));
    }

    static void ajustar(double[][] d, int ini, int fim, double[] t1, double[] t2, double erro2, List<double[][]> saida) {
        if (fim - ini == 1) {
            double dist = norm(sub(d[fim], d[ini])) / 3;
            saida.add(new double[][]{d[ini], add(d[ini], mul(t1, dist)), add(d[fim], mul(t2, dist)), d[fim]});
            return;
        }
        double[] u = parametrizar(d, ini, fim);
        double[][] bez = gerar(d, ini, fim, u, t1, t2);
        int[] corte = new int[1];
        double maxErro = erroMaximo(d, ini, fim, bez, u, corte);
        if (maxErro < erro2) { saida.add(bez); return; }
        if (maxErro < erro2 * 4) {
            for (int it = 0; it < 20; it++) {
                double[] u2 = reparametrizar(d, ini, fim, u, bez);
                bez = gerar(d, ini, fim, u2, t1, t2);
                maxErro = erroMaximo(d, ini, fim, bez, u2, corte);
                if (maxErro < erro2) { saida.add(bez); return; }
                u = u2;
            }
        }
        int c = corte[0];
        double[] tc = unit(sub(d[Math.max(ini, c - 2)], d[Math.min(fim, c + 2)]));
        ajustar(d, ini, c, t1, tc, erro2, saida);
        ajustar(d, c, fim, mul(tc, -1), t2, erro2, saida);
    }

    static double[][] gerar(double[][] d, int ini, int fim, double[] u, double[] t1, double[] t2) {
        int n = fim - ini + 1;
        double c00 = 0, c01 = 0, c11 = 0, x0 = 0, x1 = 0;
        for (int i = 0; i < n; i++) {
            double t = u[i], mt = 1 - t;
            double b0 = mt * mt * mt, b1 = 3 * t * mt * mt, b2 = 3 * t * t * mt, b3 = t * t * t;
            double[] a1 = mul(t1, b1), a2 = mul(t2, b2);
            c00 += dot(a1, a1);
            c01 += dot(a1, a2);
            c11 += dot(a2, a2);
            double[] tmp = sub(d[ini + i], add(mul(d[ini], b0 + b1), mul(d[fim], b2 + b3)));
            x0 += dot(a1, tmp);
            x1 += dot(a2, tmp);
        }
        double det = c00 * c11 - c01 * c01;
        double al = det == 0 ? 0 : (x0 * c11 - x1 * c01) / det;
        double ar = det == 0 ? 0 : (c00 * x1 - c01 * x0) / det;
        double seg = norm(sub(d[fim], d[ini])), eps = 1e-6 * seg;
        if (al < eps || ar < eps) { al = seg / 3; ar = seg / 3; }
        return new double[][]{d[ini], add(d[ini], mul(t1, al)), add(d[fim], mul(t2, ar)), d[fim]};
    }

    static double[] parametrizar(double[][] d, int ini, int fim) {
        double[] u = new double[fim - ini + 1];
        for (int i = ini + 1; i <= fim; i++) u[i - ini] = u[i - ini - 1] + norm(sub(d[i], d[i - 1]));
        for (int i = 1; i < u.length; i++) u[i] /= u[u.length - 1];
        return u;
    }

    static double[] reparametrizar(double[][] d, int ini, int fim, double[] u, double[][] bez) {
        double[] r = new double[u.length];
        for (int i = 0; i < u.length; i++) {
            double[] q = ponto(bez, u[i]), q1 = derivada1(bez, u[i]), q2 = derivada2(bez, u[i]);
            double[] dif = sub(q, d[ini + i]);
            double num = dot(dif, q1), den = dot(q1, q1) + dot(dif, q2);
            r[i] = den == 0 ? u[i] : Math.max(0, Math.min(1, u[i] - num / den));
        }
        return r;
    }

    static double erroMaximo(double[][] d, int ini, int fim, double[][] bez, double[] u, int[] corte) {
        double max = 0;
        corte[0] = (ini + fim + 1) / 2;
        for (int i = ini + 1; i < fim; i++) {
            double[] dif = sub(ponto(bez, u[i - ini]), d[i]);
            double e = dot(dif, dif);
            if (e >= max) { max = e; corte[0] = i; }
        }
        return max;
    }

    static double[] ponto(double[][] b, double t) {
        double mt = 1 - t;
        return add(add(mul(b[0], mt * mt * mt), mul(b[1], 3 * t * mt * mt)), add(mul(b[2], 3 * t * t * mt), mul(b[3], t * t * t)));
    }

    static double[] derivada1(double[][] b, double t) {
        double mt = 1 - t;
        return add(add(mul(sub(b[1], b[0]), 3 * mt * mt), mul(sub(b[2], b[1]), 6 * t * mt)), mul(sub(b[3], b[2]), 3 * t * t));
    }

    static double[] derivada2(double[][] b, double t) {
        double[] a = add(sub(b[2], mul(b[1], 2)), b[0]), c = add(sub(b[3], mul(b[2], 2)), b[1]);
        return add(mul(a, 6 * (1 - t)), mul(c, 6 * t));
    }

    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1]}; }
    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1]}; }
    static double[] mul(double[] a, double k) { return new double[]{a[0] * k, a[1] * k}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1]; }
    static double norm(double[] a) { return Math.hypot(a[0], a[1]); }
    static double[] unit(double[] a) { double n = norm(a); return new double[]{a[0] / n, a[1] / n}; }

    // ---------- conferencia ----------

    /** Desenho original com o vetor por cima (vermelho, 4x) e o traco final no fundo da abertura. */
    static void conferir(File pasta, BufferedImage img, List<double[][]> curvas, List<double[]> pts) throws Exception {
        pasta.mkdirs();
        int z = 4;
        BufferedImage sob = new BufferedImage(W * z, H * z, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sob.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, sob.getWidth(), sob.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, W * z, H * z, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(z, z);
        g.setColor(new Color(230, 0, 0));
        g.setStroke(new BasicStroke(0.4f));
        g.draw(caminho(curvas, 0, 0));
        g.setColor(new Color(0, 90, 255));
        for (double[][] c : curvas) g.fill(new java.awt.geom.Ellipse2D.Double(c[0][0] - 0.6, c[0][1] - 0.6, 1.2, 1.2));
        g.dispose();
        ImageIO.write(sob, "png", new File(pasta, "conferencia_sobreposto.png"));

        BufferedImage fundo = new BufferedImage(W * 2, H * 2, BufferedImage.TYPE_INT_RGB);
        g = fundo.createGraphics();
        g.setColor(new Color(0x0B0B0D));
        g.fillRect(0, 0, fundo.getWidth(), fundo.getHeight());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(2, 2);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0xFF6A00));
        g.draw(caminho(curvas, 0, 0));
        g.dispose();
        ImageIO.write(fundo, "png", new File(pasta, "conferencia_laranja.png"));
    }

    static Path2D caminho(List<double[][]> curvas, double dx, double dy) {
        Path2D.Double p = new Path2D.Double();
        p.moveTo(curvas.get(0)[0][0] + dx, curvas.get(0)[0][1] + dy);
        for (double[][] c : curvas) p.curveTo(c[1][0] + dx, c[1][1] + dy, c[2][0] + dx, c[2][1] + dy, c[3][0] + dx, c[3][1] + dy);
        return p;
    }
}
