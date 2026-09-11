package Producer.api;

/**
 * No plant with the requested id. Mapped to 404 by {@link ApiExceptionHandler}.
 */
public class PlantNotFoundException extends RuntimeException {

    public PlantNotFoundException(Long id) {
        super("No plant with id " + id);
    }
}
