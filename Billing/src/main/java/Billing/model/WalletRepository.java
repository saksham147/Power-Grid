package Billing.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface WalletRepository extends JpaRepository<Wallet, String> {

    /**
     * Reads a wallet and holds a row lock until the surrounding transaction ends -- for the shared
     * Grid Treasury, which several writers change at once: every customer bill credits it, every
     * plant purchase and maintenance charge debits it. A plain {@code findById} followed by a
     * change is a read-modify-write, so two of those overlapping would each start from the same
     * balance and the later commit would silently erase the earlier one. Must be called inside a
     * transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.zoneId = :zoneId")
    Optional<Wallet> findByIdForUpdate(String zoneId);
}
