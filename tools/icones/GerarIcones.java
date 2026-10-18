import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gera os icones dos alfinetes do mapa (app/src/main/res/drawable-nodpi/map_icon_*.png).
 *
 * - svg/*.svg: icones desenhados para o Oikos (grade 24 x 24), renderizados aqui;
 * - desenhos/*.png: desenhos a traco do Luan, convertidos para o estilo do mapa:
 *   silhueta laranja cheia (tudo o que nao e fundo externo) e linhas internas escuras.
 *
 * Rodar na raiz do projeto:
 *   java tools/icones/GerarIcones.java
 */
public class GerarIcones {
    static final int SIZE = 144;
    static final int ORANGE = 0xFFFF6A00, DARK = 0xFF17181C;

    public static void main(String[] args) throws Exception {
        File base = new File("tools/icones");
        File out = new File("app/src/main/res/drawable-nodpi");
        out.mkdirs();
        String[][] svgs = {
            {"bird", "map_icon_bird"}, {"mammal", "map_icon_mammal"}, {"insect", "map_icon_insect"},
            {"arachnid", "map_icon_arachnid"}, {"fish", "map_icon_fish"}, {"camera_trap", "map_icon_camera_trap"},
            {"flora", "map_icon_flora"}, {"fungi", "map_icon_fungi"}
        };
        for (String[] s : svgs) {
            BufferedImage img = renderSvg(new File(base, "svg/" + s[0] + ".svg"), SIZE);
            ImageIO.write(img, "png", new File(out, s[1] + ".png"));
            System.out.println(s[1] + ".png <- svg/" + s[0] + ".svg");
        }
        // Parametros da versao "cheia" aprovada em 18/10/2026.
        Object[][] drawings = {
            {"anuro", "map_icon_frog", false},
            {"anuro_bromelia", "map_icon_frog_bromeliad", true},
            {"reptil", "map_icon_reptile", false},
            {"serpente", "map_icon_snake", false}
        };
        for (Object[] d : drawings) {
            BufferedImage icon = stylize(ImageIO.read(new File(base, "desenhos/" + d[0] + ".png")), 10, 2, 4, 2, (Boolean) d[2]);
            ImageIO.write(fit(icon, SIZE, 22.0 / 24.0), "png", new File(out, d[1] + ".png"));
            System.out.println(d[1] + ".png <- desenhos/" + d[0] + ".png");
        }
    }

    // ---------------------------------------------------------------- SVG

    static BufferedImage renderSvg(File file, int size) throws Exception {
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 24.0, size / 24.0);
        draw(g, doc.getDocumentElement(), new Style());
        g.dispose();
        return img;
    }

    static class Style {
        String fill = "#000000", stroke = "none";
        float strokeWidth = 1, opacity = 1;
        int cap = BasicStroke.CAP_BUTT, join = BasicStroke.JOIN_MITER;

        Style derive(Element e) {
            Style s = new Style();
            s.fill = fill; s.stroke = stroke; s.strokeWidth = strokeWidth; s.opacity = opacity; s.cap = cap; s.join = join;
            if (e.hasAttribute("fill")) s.fill = e.getAttribute("fill");
            if (e.hasAttribute("stroke")) s.stroke = e.getAttribute("stroke");
            if (e.hasAttribute("stroke-width")) s.strokeWidth = Float.parseFloat(e.getAttribute("stroke-width"));
            if (e.hasAttribute("opacity")) s.opacity = opacity * Float.parseFloat(e.getAttribute("opacity"));
            if (e.hasAttribute("stroke-linecap")) s.cap = switch (e.getAttribute("stroke-linecap")) {
                case "round" -> BasicStroke.CAP_ROUND; case "square" -> BasicStroke.CAP_SQUARE; default -> BasicStroke.CAP_BUTT; };
            if (e.hasAttribute("stroke-linejoin")) s.join = switch (e.getAttribute("stroke-linejoin")) {
                case "round" -> BasicStroke.JOIN_ROUND; case "bevel" -> BasicStroke.JOIN_BEVEL; default -> BasicStroke.JOIN_MITER; };
            return s;
        }
    }

    static void draw(Graphics2D g, Element e, Style parent) {
        Style s = parent.derive(e);
        Graphics2D g2 = (Graphics2D) g.create();
        String tf = e.getAttribute("transform");
        Matcher rot = Pattern.compile("rotate\\(\\s*([-\\d.]+)(?:[ ,]+([-\\d.]+)[ ,]+([-\\d.]+))?\\s*\\)").matcher(tf);
        if (rot.find()) {
            double a = Math.toRadians(Double.parseDouble(rot.group(1)));
            if (rot.group(2) != null) g2.rotate(a, Double.parseDouble(rot.group(2)), Double.parseDouble(rot.group(3)));
            else g2.rotate(a);
        }
        Shape shape = switch (e.getTagName()) {
            case "path" -> path(e.getAttribute("d"));
            case "circle" -> { double cx = num(e, "cx"), cy = num(e, "cy"), r = num(e, "r"); yield new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r); }
            case "ellipse" -> { double cx = num(e, "cx"), cy = num(e, "cy"), rx = num(e, "rx"), ry = num(e, "ry"); yield new Ellipse2D.Double(cx - rx, cy - ry, 2 * rx, 2 * ry); }
            case "rect" -> { double rx = num(e, "rx"); yield new RoundRectangle2D.Double(num(e, "x"), num(e, "y"), num(e, "width"), num(e, "height"), 2 * rx, 2 * rx); }
            default -> null;
        };
        if (shape != null) {
            if (!"none".equals(s.fill)) { g2.setColor(color(s.fill, s.opacity)); g2.fill(shape); }
            if (!"none".equals(s.stroke)) {
                g2.setColor(color(s.stroke, s.opacity));
                g2.setStroke(new BasicStroke(s.strokeWidth, s.cap, s.join));
                g2.draw(shape);
            }
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element child) draw(g2, child, s);
        g2.dispose();
    }

    static double num(Element e, String a) { return e.hasAttribute(a) ? Double.parseDouble(e.getAttribute(a)) : 0; }

    static Color color(String hex, float opacity) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, Math.round(255 * opacity));
    }

    /** Caminho SVG com M L H V C Q Z (absolutos e relativos). */
    static Path2D path(String d) {
        Path2D p = new Path2D.Double();
        Matcher m = Pattern.compile("[MLHVCQZmlhvcqz]|-?\\d*\\.?\\d+(?:e-?\\d+)?").matcher(d);
        List<String> tokens = new ArrayList<>();
        while (m.find()) tokens.add(m.group());
        double x = 0, y = 0, sx = 0, sy = 0;
        char cmd = 'M';
        int i = 0;
        while (i < tokens.size()) {
            String t = tokens.get(i);
            if (Character.isLetter(t.charAt(0))) { cmd = t.charAt(0); i++; if (cmd == 'Z' || cmd == 'z') { p.closePath(); x = sx; y = sy; } continue; }
            boolean rel = Character.isLowerCase(cmd);
            double ox = rel ? x : 0, oy = rel ? y : 0;
            switch (Character.toUpperCase(cmd)) {
                case 'M' -> { x = ox + v(tokens, i); y = oy + v(tokens, i + 1); i += 2; p.moveTo(x, y); sx = x; sy = y; cmd = rel ? 'l' : 'L'; }
                case 'L' -> { x = ox + v(tokens, i); y = oy + v(tokens, i + 1); i += 2; p.lineTo(x, y); }
                case 'H' -> { x = ox + v(tokens, i); i += 1; p.lineTo(x, y); }
                case 'V' -> { y = oy + v(tokens, i); i += 1; p.lineTo(x, y); }
                case 'C' -> { p.curveTo(ox + v(tokens, i), oy + v(tokens, i + 1), ox + v(tokens, i + 2), oy + v(tokens, i + 3), ox + v(tokens, i + 4), oy + v(tokens, i + 5));
                    x = ox + v(tokens, i + 4); y = oy + v(tokens, i + 5); i += 6; }
                case 'Q' -> { p.quadTo(ox + v(tokens, i), oy + v(tokens, i + 1), ox + v(tokens, i + 2), oy + v(tokens, i + 3));
                    x = ox + v(tokens, i + 2); y = oy + v(tokens, i + 3); i += 4; }
                default -> throw new IllegalArgumentException("comando SVG nao suportado: " + cmd);
            }
        }
        return p;
    }

    static double v(List<String> t, int i) { return Double.parseDouble(t.get(i)); }

    // ---------------------------------------------------------------- desenhos

    /**
     * band: largura (px) da faixa do contorno, que fica laranja; gap: fecha
     * frestas do traco; solid: raio para preservar areas pintadas (pupilas);
     * detail: quanto engrossar as linhas internas; closeBottom: fecha a base
     * aberta (folhas da bromelia) so para o calculo.
     */
    static BufferedImage stylize(BufferedImage src, int band, int gap, int solid, int detail, boolean closeBottom) {
        int w = src.getWidth(), h = src.getHeight();
        boolean[] line = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int p = src.getRGB(x, y);
            int lum = ((p >> 16 & 255) * 299 + (p >> 8 & 255) * 587 + (p & 255) * 114) / 1000;
            if ((p >>> 24) < 128) lum = 255;
            line[y * w + x] = lum < 150;
        }
        boolean[] sealed = line.clone();
        if (closeBottom) {
            int yMax = 0;
            for (int i = 0; i < w * h; i++) if (line[i]) yMax = Math.max(yMax, i / w);
            int lo = w, hi = 0;
            for (int y = Math.max(0, yMax - 25); y <= yMax; y++) for (int x = 0; x < w; x++) if (line[y * w + x]) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
            for (int y = yMax - 4; y <= yMax - 1; y++) for (int x = lo; x <= hi; x++) sealed[y * w + x] = true;
        }
        boolean[] wall = dilate(sealed, w, h, gap);
        boolean[] outside = new boolean[w * h];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = 0; x < w; x++) { seed(q, outside, wall, x, 0, w); seed(q, outside, wall, x, h - 1, w); }
        for (int y = 0; y < h; y++) { seed(q, outside, wall, 0, y, w); seed(q, outside, wall, w - 1, y, w); }
        while (!q.isEmpty()) {
            int i = q.poll(), x = i % w, y = i / w;
            if (x > 0) seed(q, outside, wall, x - 1, y, w);
            if (x < w - 1) seed(q, outside, wall, x + 1, y, w);
            if (y > 0) seed(q, outside, wall, x, y - 1, w);
            if (y < h - 1) seed(q, outside, wall, x, y + 1, w);
        }
        boolean[] outsideGrown = dilate(outside, w, h, gap);
        boolean[] shape = new boolean[w * h];
        for (int i = 0; i < w * h; i++) shape[i] = (!outsideGrown[i] || sealed[i]) && !(outsideGrown[i] && !line[i]);
        boolean[] near = dilate(outside, w, h, band);
        boolean[] inner = new boolean[w * h];
        for (int i = 0; i < w * h; i++) inner[i] = line[i] && !near[i];
        inner = dilate(inner, w, h, detail);
        boolean[] opened = dilate(erode(line, w, h, solid), w, h, solid);
        for (int i = 0; i < w * h; i++) inner[i] = (inner[i] || opened[i]) && shape[i];
        int minX = w, minY = h, maxX = 0, maxY = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if (shape[y * w + x]) {
            minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
        }
        int side = Math.max(maxX - minX, maxY - minY) + 1;
        int ox = minX - (side - (maxX - minX + 1)) / 2, oy = minY - (side - (maxY - minY + 1)) / 2;
        BufferedImage icon = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++) {
            int sx = x + ox, sy = y + oy;
            if (sx < 0 || sy < 0 || sx >= w || sy >= h) continue;
            int i = sy * w + sx;
            if (shape[i]) icon.setRGB(x, y, inner[i] ? DARK : ORANGE);
        }
        return icon;
    }

    /** Reduz em etapas e centraliza ocupando [fill] do quadro. */
    static BufferedImage fit(BufferedImage icon, int size, double fill) {
        int inner = (int) Math.round(size * fill);
        BufferedImage cur = icon;
        int s = icon.getWidth();
        while (s / 2 >= inner) { s /= 2; cur = scale(cur, s); }
        cur = scale(cur, inner);
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(cur, (size - inner) / 2, (size - inner) / 2, null);
        g.dispose();
        return out;
    }

    static BufferedImage scale(BufferedImage img, int s) {
        BufferedImage o = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = o.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(img, 0, 0, s, s, null);
        g.dispose();
        return o;
    }

    static void seed(ArrayDeque<Integer> q, boolean[] seen, boolean[] wall, int x, int y, int w) {
        int i = y * w + x;
        if (seen[i] || wall[i]) return;
        seen[i] = true;
        q.add(i);
    }

    static boolean[] dilate(boolean[] m, int w, int h, int r) {
        boolean[] out = new boolean[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            if (!m[y * w + x]) continue;
            for (int dy = -r; dy <= r; dy++) for (int dx = -r; dx <= r; dx++) {
                if (dx * dx + dy * dy > r * r) continue;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && ny >= 0 && nx < w && ny < h) out[ny * w + nx] = true;
            }
        }
        return out;
    }

    static boolean[] erode(boolean[] m, int w, int h, int r) {
        boolean[] inv = new boolean[w * h];
        for (int i = 0; i < w * h; i++) inv[i] = !m[i];
        boolean[] d = dilate(inv, w, h, r);
        for (int i = 0; i < w * h; i++) d[i] = !d[i];
        return d;
    }
}
