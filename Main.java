import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class Main {
    enum Producto { SEQUIA, EXCESO_LLUVIA, HELADA }
    enum Zona { ALTA, MEDIA, BAJA }

    record Resultado(int pago, String detalle) {}
    record Comprobante(String numero, double comision, String texto) {}

    interface FuenteIndice { String nombre(); }
    interface ReglaDisparo { Resultado evaluar(double[] lluvia, double[] temperatura, LocalDate inicio); }
    interface GeneradorCertificado { String generar(); }
    interface FabricaProductoParametrico {
        FuenteIndice crearFuenteIndice();
        ReglaDisparo crearReglaDisparo();
        GeneradorCertificado crearGeneradorCertificado();
        double tasa();
    }

    static class FabricaSequia implements FabricaProductoParametrico {
        public FuenteIndice crearFuenteIndice() { return () -> "Accumulated rain for 30 days"; }
        public ReglaDisparo crearReglaDisparo() { return (lluvia, temp, fecha) -> {
            double suma = Arrays.stream(lluvia).sum(), historico = 112;
            double porcentaje = suma / historico * 100;
            int pago = porcentaje >= 60 ? 0 : porcentaje >= 40 ? 50 : 100;
            return new Resultado(pago, String.format(Locale.US, "accumulated %.1f mm | historical %.1f mm | %.1f %%", suma, historico, porcentaje));
        }; }
        public GeneradorCertificado crearGeneradorCertificado() { return () -> "CERT-SEQ (village historical average)"; }
        public double tasa() { return .075; }
    }

    static class FabricaExceso implements FabricaProductoParametrico {
        public FuenteIndice crearFuenteIndice() { return () -> "Maximum five-day moving rainfall"; }
        public ReglaDisparo crearReglaDisparo() { return (lluvia, temp, fecha) -> {
            double max = 0; int dia = 0;
            for (int i = 0; i <= lluvia.length - 5; i++) {
                double suma = 0; for (int j = i; j < i + 5; j++) suma += lluvia[j];
                if (suma > max) { max = suma; dia = i; }
            }
            int pago = max <= 150 ? 0 : max <= 250 ? 60 : 100;
            return new Resultado(pago, String.format(Locale.US, "five-day maximum %.1f mm (days %02d to %02d)", max, dia + 1, dia + 5));
        }; }
        public GeneradorCertificado crearGeneradorCertificado() { return () -> "CERT-EXC (critical window detected)"; }
        public double tasa() { return .052; }
    }

    static class FabricaHelada implements FabricaProductoParametrico {
        public FuenteIndice crearFuenteIndice() { return () -> "Daily minimum temperature"; }
        public ReglaDisparo crearReglaDisparo() { return (lluvia, temp, fecha) -> {
            List<String> dias = new ArrayList<>();
            for (int i = 0; i < temp.length; i++) if (temp[i] < 0) dias.add(fecha.plusDays(i).format(DateTimeFormatter.ofPattern("dd-MMM", Locale.US)));
            int pago = dias.size() == 0 || dias.size() == 1 ? 0 : dias.size() <= 3 ? 55 : 100;
            return new Resultado(pago, "days below 0 C: " + dias.size() + " " + dias);
        }; }
        public GeneradorCertificado crearGeneradorCertificado() { return () -> "CERT-HEL (frost days)"; }
        public double tasa() { return .09; }
    }

    static class Poliza {
        final String numero, documento, asegurado, cultivo, beneficiario;
        final double hectareas, valorHa;
        final Producto producto; final Zona zona;
        final LocalDate inicio, fin;
        final int descuento;
        final boolean renovacion;
        private Poliza(Builder b) {
            numero = b.numero; documento = b.documento; asegurado = b.asegurado; cultivo = b.cultivo;
            beneficiario = b.beneficiario; hectareas = b.hectareas; valorHa = b.valorHa; producto = b.producto;
            zona = b.zona; inicio = b.inicio; fin = b.fin; descuento = Math.min(20, b.descuento); renovacion = b.renovacion;
        }
        static class Builder {
            String numero, documento, asegurado, cultivo, beneficiario, anterior;
            double hectareas, valorHa; Producto producto; Zona zona; LocalDate inicio, fin;
            int descuento; boolean renovacion;
            Builder numero(String v) { numero = v; return this; }
            Builder asegurado(String documento, String nombre) { this.documento = documento; asegurado = nombre; return this; }
            Builder cultivo(String v) { cultivo = v; return this; }
            Builder hectareas(double v) { hectareas = v; return this; }
            Builder valorHa(double v) { valorHa = v; return this; }
            Builder producto(Producto v) { producto = v; return this; }
            Builder zona(Zona v) { zona = v; return this; }
            Builder vigencia(LocalDate i, LocalDate f) { inicio = i; fin = f; return this; }
            Builder descuentos(int v) { descuento += v; return this; }
            Builder renovacion(String anterior) { renovacion = true; this.anterior = anterior; descuento += 7; return this; }
            Poliza build() {
                if (numero == null || documento == null || asegurado == null || cultivo == null || producto == null || zona == null || inicio == null || fin == null)
                    throw new IllegalStateException("A required field is missing");
                if (hectareas < .5 || hectareas > 20) throw new IllegalStateException("Hectares outside the microinsurance range (0.5 to 20): " + hectareas);
                if (ChronoUnit.DAYS.between(inicio, fin) < 60) throw new IllegalStateException("Coverage must last at least 60 days");
                if (renovacion && (anterior == null || anterior.isBlank())) throw new IllegalStateException("Renewal discount requires a previous policy number");
                return new Poliza(this);
            }
        }
        double suma() { return hectareas * valorHa; }
        double prima(FabricaProductoParametrico f) {
            double factor = zona == Zona.ALTA ? 1.25 : zona == Zona.BAJA ? .85 : 1;
            return Math.round(suma() * f.tasa() * factor * (1 - descuento / 100.0));
        }
    }

    static abstract class CanalDeVenta {
        private int consecutivo = 1;
        public Comprobante emitir(Poliza p, FabricaProductoParametrico f) {
            double prima = p.prima(f), comision = prima * porcentaje();
            return crearComprobante(p, String.format("%06d", consecutivo++), comision);
        }
        protected abstract double porcentaje();
        protected abstract Comprobante crearComprobante(Poliza p, String numero, double comision);
    }
    static class CanalCooperativa extends CanalDeVenta {
        protected double porcentaje() { return .12; }
        protected Comprobante crearComprobante(Poliza p, String n, double c) { return new Comprobante("COOP-0417-" + n, c, "printed, manager signature and harvest deduction"); }
    }
    static class CanalAppMovil extends CanalDeVenta {
        protected double porcentaje() { return .03; }
        protected Comprobante crearComprobante(Poliza p, String n, double c) { return new Comprobante("APP-" + p.inicio.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + n, c, "digital with QR and electronic wallet"); }
    }
    static class CanalCorresponsal extends CanalDeVenta {
        protected double porcentaje() { return .08; }
        protected Comprobante crearComprobante(Poliza p, String n, double c) { return new Comprobante("CB-900123-" + n, c, "POS with 30-day bank collection code"); }
    }

    static FabricaProductoParametrico fabrica(Producto p) {
        return p == Producto.SEQUIA ? new FabricaSequia() : p == Producto.EXCESO_LLUVIA ? new FabricaExceso() : new FabricaHelada();
    }
    static String dinero(double n) { return String.format(Locale.US, "%,.0f", n).replace(',', '.'); }

    public static void main(String[] args) {
        System.out.println("=== PARAMETRIC AGRICULTURAL MICROINSURANCE - ISSUANCE ===");
        LocalDate inicio = LocalDate.of(2026, 6, 1), fin = inicio.plusDays(90);
        List<Poliza> cartera = new ArrayList<>();
        CanalDeVenta[] canales = { new CanalCooperativa(), new CanalAppMovil(), new CanalCorresponsal() };
        Producto[] productos = { Producto.HELADA, Producto.SEQUIA, Producto.EXCESO_LLUVIA, Producto.HELADA, Producto.SEQUIA, Producto.EXCESO_LLUVIA };
        String[] nombres = { "Rosa Elena Pabon", "Jairo Munoz", "Ana Torres", "Luis Gomez", "Marta Rojas", "Pedro Diaz" };
        for (int i = 0; i < 6; i++) {
            Poliza p = new Poliza.Builder().numero(String.format("POL-%06d", i + 1)).asegurado("DOC-" + (i + 1), nombres[i])
                .cultivo(i % 2 == 0 ? "PAPA" : "MAIZ").hectareas(i + 1).valorHa(8_000_000)
                .producto(productos[i]).zona(i % 3 == 0 ? Zona.ALTA : i % 3 == 1 ? Zona.MEDIA : Zona.BAJA).vigencia(inicio, fin)
                .descuentos(i == 2 ? 8 : 0).descuentos(i == 2 ? 7 : 0).descuentos(i == 2 ? 6 : 0).build();
            FabricaProductoParametrico f = fabrica(p.producto); Comprobante c = canales[i % 3].emitir(p, f); cartera.add(p);
            System.out.printf("%s | %-16s | %-13s | %.1f ha | zona %s%n", p.numero, p.asegurado, p.producto, p.hectareas, p.zona);
            System.out.printf("   Insured amount $ %s | premium $ %s | discount %d%%%n", dinero(p.suma()), dinero(p.prima(f)), p.descuento);
            System.out.printf("   Channel -> %s | commission $ %s | %s | %s%n", c.numero(), dinero(c.comision()), c.texto(), f.crearGeneradorCertificado().generar());
        }
        try { new Poliza.Builder().numero("ERROR").asegurado("1", "Invalido").cultivo("MAIZ").hectareas(45).valorHa(1).producto(Producto.SEQUIA).zona(Zona.MEDIA).vigencia(inicio, fin).build(); }
        catch (IllegalStateException e) { System.out.println("[CONTROLLED ERROR] " + e.getMessage()); }
        try { new Poliza.Builder().numero("ERROR").asegurado("1", "Invalido").cultivo("MAIZ").hectareas(1).valorHa(1).producto(Producto.SEQUIA).zona(Zona.MEDIA).vigencia(inicio, fin).renovacion(null).build(); }
        catch (IllegalStateException e) { System.out.println("[CONTROLLED ERROR] " + e.getMessage()); }

        double[] lluvia = { 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 60, 65, 70, 55, 20, 8, 7, 6, 5, 4, 3, 2, 1, 2, 3, 4, 5, 6, 7 };
        double[] temperatura = { 4, 3, 2, 1, 0, -2, -1, 2, 3, 4, 5, -3, 1, 2, 3, 4, 5, 6, 7, 8, 3, 2, 1, 4, 5, 6, 7, 8, 9, 10 };
        System.out.println("=== SEASON EVALUATION ===");
        double primas = 0, comisiones = 0, indemnizaciones = 0; Map<Poliza, Double> pagos = new HashMap<>();
        for (int i = 0; i < cartera.size(); i++) {
            Poliza p = cartera.get(i); FabricaProductoParametrico f = fabrica(p.producto); Resultado r = f.crearReglaDisparo().evaluar(lluvia, temperatura, inicio);
            double prima = p.prima(f), pago = p.suma() * r.pago() / 100; primas += prima; indemnizaciones += pago; comisiones += prima * new double[]{.12, .03, .08}[i % 3];
            if (pago > 0) pagos.put(p, pago);
            System.out.printf("%s %-13s -> %s -> PAGO %d%% | INDEMNIZACION $ %s%n", p.numero, p.producto, r.detalle(), r.pago(), dinero(pago));
        }
        System.out.println("=== PORTFOLIO SUMMARY ===");
        System.out.println("Issued premiums     $ " + dinero(primas));
        System.out.println("Paid commissions    $ " + dinero(comisiones));
        System.out.println("Indemnities         $ " + dinero(indemnizaciones));
        System.out.printf(Locale.US, "Loss ratio            %.2f %% %n", indemnizaciones / primas * 100);
        System.out.println("Technical result    $ " + dinero(primas - comisiones - indemnizaciones));
        System.out.println("INDEMNITY RANKING");
        List<Map.Entry<Poliza, Double>> ranking = new ArrayList<>(pagos.entrySet());
        ranking.sort((a, b) -> {
            int porMonto = Double.compare(b.getValue(), a.getValue());
            return porMonto != 0 ? porMonto : a.getKey().numero.compareTo(b.getKey().numero);
        });
        for (int i = 0; i < ranking.size(); i++) System.out.printf("%d. %s $ %s %s%n", i + 1, ranking.get(i).getKey().numero, dinero(ranking.get(i).getValue()), ranking.get(i).getKey().producto);
    }
}