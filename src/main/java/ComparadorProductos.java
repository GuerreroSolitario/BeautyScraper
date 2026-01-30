import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.NumberFormat;
import java.util.Locale;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ComparadorProductos {

    public static void compararSnapshots(String archivoA, String archivoB) throws Exception {

        Gson gson = new Gson();

        // Acumuladores separados
        StringBuilder subidas = new StringBuilder();
        StringBuilder bajadas = new StringBuilder();
        StringBuilder eliminados = new StringBuilder();
        StringBuilder outOfStock = new StringBuilder();
        StringBuilder enStock = new StringBuilder();
        StringBuilder nuevos = new StringBuilder();
        StringBuilder reporteFinal = new StringBuilder();

        // Leer JSON A
        List<Producto> productosA = gson.fromJson(new FileReader(archivoA), new TypeToken<List<Producto>>() {}.getType());

        // Leer JSON B
        List<Producto> productosB = gson.fromJson(new FileReader(archivoB), new TypeToken<List<Producto>>() {}.getType());

        Map<String, Producto> mapaA = new HashMap<>();
        for (Producto p : productosA) {
            mapaA.put(p.getSKU(), p);
        }

        Map<String, Producto> mapaB = new HashMap<>();
        for (Producto p : productosB) {
            mapaB.put(p.getSKU(), p);
        }

        // Comparar productos
        for (String sku : mapaB.keySet()) {
            Producto nuevo = mapaB.get(sku);
            Producto viejo = mapaA.get(sku);

            if (viejo == null) {
                // NUEVOS PRODUCTOS: ahora también muestran estado
                nuevos.append("🆕 Nuevo Producto: ").append(nuevo.getNombre())
                      .append(" | SKU: ").append(nuevo.getSKU())
                      .append(" | Precio: ").append(formatearPrecio(nuevo.getPrecio()))
                      .append(" | Estado: ").append(nuevo.getEstado())
                      .append("\n\n");
            } else {
                if (nuevo.getPrecio() > viejo.getPrecio()) {
                    double diferencia = nuevo.getPrecio() - viejo.getPrecio();
                    double porcentaje = (diferencia / viejo.getPrecio()) * 100;

                    subidas.append("⬆️ Subida de precio en: ").append(nuevo.getNombre())
                           .append(" | SKU: ").append(nuevo.getSKU())
                           .append(": ").append(formatearPrecio(viejo.getPrecio()))
                           .append(" -> ").append(formatearPrecio(nuevo.getPrecio()))
                           .append(" (+" + formatearPrecio(diferencia) + ", " + String.format("%.2f", porcentaje) + "%)\n\n");
                } else if (nuevo.getPrecio() < viejo.getPrecio()) {
                    double diferencia = viejo.getPrecio() - nuevo.getPrecio();
                    double porcentaje = (diferencia / viejo.getPrecio()) * 100;

                    bajadas.append("⬇️ Bajada de precio en: ").append(nuevo.getNombre())
                           .append(" | SKU: ").append(nuevo.getSKU())
                           .append(": ").append(formatearPrecio(viejo.getPrecio()))
                           .append(" -> ").append(formatearPrecio(nuevo.getPrecio()))
                           .append(" (-" + formatearPrecio(diferencia) + ", -" + String.format("%.2f", porcentaje) + "%)\n\n");
                }

                // CAMBIOS DE STOCK: ahora también muestran precio
                if (!nuevo.getEstado().equals(viejo.getEstado())) {
                    if ("OUT OF STOCK".equalsIgnoreCase(nuevo.getEstado())) {
                        outOfStock.append("🔄 Cambio de stock en ").append(nuevo.getNombre())
                                  .append(" | SKU: ").append(nuevo.getSKU())
                                  .append(": ").append(viejo.getEstado()).append(" -> ").append(nuevo.getEstado())
                                  .append(" | Precio: ").append(formatearPrecio(nuevo.getPrecio()))
                                  .append("\n\n");
                    } else if ("IN STOCK".equalsIgnoreCase(nuevo.getEstado())) {
                        enStock.append("🔄 Cambio de stock en ").append(nuevo.getNombre())
                               .append(" | SKU: ").append(nuevo.getSKU())
                               .append(": ").append(viejo.getEstado()).append(" -> ").append(nuevo.getEstado())
                               .append(" | Precio: ").append(formatearPrecio(nuevo.getPrecio()))
                               .append("\n\n");
                    }
                }
            }
        }

        // Detectar productos eliminados
        for (String sku : mapaA.keySet()) {
            if (!mapaB.containsKey(sku)) {
                Producto eliminado = mapaA.get(sku);
                eliminados.append("❌ Producto eliminado: ").append(eliminado.getNombre())
                          .append(" | SKU: ").append(eliminado.getSKU())
                          .append(" | Precio: ").append(formatearPrecio(eliminado.getPrecio()))
                          .append("\n\n");
            }
        }

        // Guardar cada reporte en archivo separado
        guardarArchivo("./data/subidas.txt", subidas.toString());
        guardarArchivo("./data/bajadas.txt", bajadas.toString());
        guardarArchivo("./data/eliminados.txt", eliminados.toString());
        guardarArchivo("./data/outofstock.txt", outOfStock.toString());
        guardarArchivo("./data/enstock.txt", enStock.toString());
        guardarArchivo("./data/nuevos.txt", nuevos.toString());

        System.out.println("Reportes guardados en ./data/");
        
        // Construir reporte final con salto de línea después de cada encabezado
        if (nuevos.length() > 0) {
            reporteFinal.append("📦 Nuevos Productos:\n\n").append(nuevos).append("\n");
        }
        if (subidas.length() > 0) {
            reporteFinal.append("💹 Subidas de Precio:\n\n").append(subidas).append("\n");
        }
        if (bajadas.length() > 0) {
            reporteFinal.append("💸 Bajadas de Precio:\n\n").append(bajadas).append("\n");
        }
        if (outOfStock.length() > 0) {
            reporteFinal.append("📊 OUT OF STOCK:\n\n").append(outOfStock).append("\n");
        }
        if (enStock.length() > 0) {
            reporteFinal.append("📊 IN STOCK:\n\n").append(enStock).append("\n");
        }
        if (eliminados.length() > 0) {
            reporteFinal.append("🗑️ Eliminados:\n\n").append(eliminados).append("\n");
        }

        // Si no hubo cambios en ningún builder
        if (reporteFinal.length() == 0) {
            reporteFinal.append("✅ No hubo cambios entre los snapshots.\n");
        }

        // Guardar archivo combinado
        guardarArchivo("./data/reporte.txt", reporteFinal.toString());

    }

    private static void guardarArchivo(String ruta, String contenido) {
        try (FileWriter writer = new FileWriter(ruta)) {
            writer.write(contenido);
        } catch (Exception e) {
            System.out.println("Error al guardar " + ruta + ": " + e.getMessage());
        }
    }

    // Método para formatear precios en MXN con separador de miles
    private static String formatearPrecio(double precio) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("es", "MX"));
        String valor = nf.format(precio); // Esto genera $479.00
        return valor + " MXN";            // Le agregamos el sufijo MXN
    }

}
