
class Producto {
	
    String sku;	
    String nombre;
    double precio;
    String url;
    String estado;

    Producto(String sku, String nombre, double precio, String url, String estado) {
    	
        this.sku = sku;
    	this.nombre = nombre;
        this.precio = precio;
        this.url = url;
        this.estado = estado;
    }
    
    public String getSKU() {
    	
    	return sku;
    	
    }
    
    public String getNombre() {
    	
    	return nombre;
    	
    }
    
    public double getPrecio() {
    	
    	return precio;
    	
    }
    
    public String getEstado() {
    	
    	return estado;
    	
    }
}
