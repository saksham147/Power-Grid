package Producer.model;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PowerPlantRepository extends JpaRepository<PowerPlant, Long> {

    /** The one and only read per tick. */
    List<PowerPlant> findByActiveTrue();
}
