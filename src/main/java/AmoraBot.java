import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class AmoraBot {
    
    public static Document doc; 
    public static int globalIndex = 1;

    // --- Método auxiliar con reintentos ---
    public static Document fetchWithRetries(String url, int maxRetries) throws Exception {
        int attempts = 0;
        while (attempts < maxRetries) {
            try {
                Connection.Response response = Jsoup.connect(url).timeout(10000).execute();
                if (response.statusCode() == 200) {
                    return response.parse();
                } else {
                    throw new Exception("HTTP status: " + response.statusCode());
                }
            } catch (Exception e) {
                attempts++;
                System.out.println("Error al conectar (" + attempts + "/" + maxRetries + "): " + e.getMessage());
                if (attempts >= maxRetries) {
                    throw new Exception("Fallo tras " + maxRetries + " intentos: " + e.getMessage());
                }
            }
        }
        return null;
    }

    public static void main(String[] args) {
        
        String baseURL = "https://amoramarket.com.mx/fragancias.html?product_list_limit=48&p=";

        int step = 1;
        int lastValid = 0;

        // --- Búsqueda exponencial ---
        while (true) {
            String url = baseURL + step;
            System.out.println("Probando página: " + step);

            try {
                Document doc = fetchWithRetries(url, 3);
                Elements items = doc.select("a.product-item-link");

                if (items.size() > 0) {
                    lastValid = step;
                    step *= 2; 
                } else {
                    break; 
                }
            } catch (Exception e) {
                System.out.println("Error crítico en búsqueda exponencial: " + e.getMessage());
                System.exit(1); // termina el programa
            }
        }

        // --- Búsqueda binaria ---
        int low = lastValid + 1;
        int high = step - 1;
        int maxPage = lastValid;

        while (low <= high) {
            int mid = (low + high) / 2;
            String url = baseURL + mid;

            try {
                Document doc = fetchWithRetries(url, 3);
                Elements items = doc.select("a.product-item-link");

                if (items.size() > 0) {
                    maxPage = mid;   
                    low = mid + 1;   
                } else {
                    high = mid - 1;  
                }
            } catch (Exception e) {
                System.out.println("Error crítico en búsqueda binaria: " + e.getMessage());
                System.exit(1);
            }
        }

        System.out.println("La última página con productos es: " + maxPage);

        List<Producto> productos = new ArrayList<>();
                        
        // --- Recorrido de páginas ---
        for(int p = 1; p <= maxPage; p++) {
            
            String url = baseURL + p;
            
            try {
                Document doc = fetchWithRetries(url, 3);

                // --- Extraer SKUs ---
                Element scriptTag = doc.selectFirst("script:containsData(\"items\")");
                List<String> skus = new ArrayList<>();
                if(scriptTag != null) {
                    String scriptContent = scriptTag.html();
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
                    java.util.regex.Matcher matcher = pattern.matcher(scriptContent);
                    while(matcher.find()) {
                        skus.add(matcher.group(1).toUpperCase());
                    }
                }
                
                // --- Extraer datos de productos ---
                Elements items = doc.select("a.product-item-link");
                Elements precioPerfume = doc.select("span.special-price span.price");
                Elements urlPerfume = doc.select("div.product-item-photo a");
                Elements availability = doc.select("li.product-item");
                
                for(int i = 0; i < items.size(); i++) {
                    Element stock = availability.get(i).selectFirst("div.actions-primary form[data-role=tocart-form]");
                    Element outOfStock = availability.get(i).selectFirst("div.stock.unavailable");
                    
                    String estado;
                    if(stock != null) {
                        estado = "IN STOCK";
                    } else if(outOfStock != null) {
                        estado = "OUT OF STOCK";
                    } else {
                        estado = "Estado Desconocido";
                    }
                    
                    String precioTexto = precioPerfume.get(i).text().replace("$", "").replace(",", "");
                    double precio = Double.parseDouble(precioTexto);
                    
                    Producto producto = new Producto(
                        skus.get(i),
                        items.get(i).text(),
                        precio,
                        urlPerfume.get(i).attr("href"),
                        estado
                    );
                    
                    productos.add(producto);
                    
                    System.out.println(globalIndex + ". " 
                        + items.get(i).text() + " - "
                        + precioPerfume.get(i).text() + " - "
                        + urlPerfume.get(i).attr("href") + " - "
                        + skus.get(i) + " - "
                        + estado);
                    
                    globalIndex++;
                }
            } catch(Exception e) {
                System.out.println("Error crítico en recorrido de páginas: " + e.getMessage());
                System.exit(1);
            }
        }
        
        // --- Guardar JSON ---
        Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
        String json = gson.toJson(productos);
        
        try (FileWriter writer = new FileWriter("./data/productos_B.json")) {
            writer.write(json);
            System.out.println("Archivo JSON guardado");
        } catch (Exception e) {
            System.out.println("Error al guardar el archivo: " + e.getMessage());
            System.exit(1);
        }
        
        // --- Comparar snapshots ---
        try {
            ComparadorProductos.compararSnapshots("./data/productos_A.json", "./data/productos_B.json");

            // --- Enviar reporte a Telegram ---
            String reporte = new String(
                Files.readAllBytes(Paths.get("./data/reporte.txt")),
                StandardCharsets.UTF_8
            );

            String token = System.getenv("TELEGRAM_TOKEN");
            String chatId = System.getenv("TELEGRAM_CHAT_ID");

            TelegramUtils.sendTelegramMessageSplit(reporte, token, chatId);

        } catch (Exception e) {
            System.out.println("Error al comparar snapshots o enviar reporte: " + e.getMessage());
            System.exit(1);
        }
        
     // --- Actualizar snapshot A con B ---
        try {
            java.nio.file.Path pathA = Paths.get("./data/productos_A.json");
            java.nio.file.Path pathB = Paths.get("./data/productos_B.json");

            // Sobrescribir A con el contenido de B
            Files.copy(pathB, pathA, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // Eliminar B si no quieres que quede guardado
            Files.delete(pathB);

            System.out.println("Snapshot actualizado: productos_A.json reemplazado con productos_B.json");
        } catch (Exception e) {
            System.out.println("Error al actualizar snapshot: " + e.getMessage());
        }

    }
}
