package Producer.api;

/** No storage unit with the requested id. Mapped to 404 by {@link ApiExceptionHandler}. */
public class StorageNotFoundException extends RuntimeException {

    public StorageNotFoundException(Long id) {
        super("No storage unit with id " + id);
    }
}
