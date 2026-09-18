package Producer.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageUnitRepository extends JpaRepository<StorageUnit, Long> {

    /** The one and only read per tick, mirroring {@code PowerPlantRepository#findByActiveTrue}. */
    List<StorageUnit> findByActiveTrue();
}
