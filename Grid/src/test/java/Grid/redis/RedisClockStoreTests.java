package Grid.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * The boot-time resume: a Redis that is only briefly unavailable must not cost the clock its
 * place. A restart from tick 0 makes Billing and Distributor ignore every tick they already hold,
 * so the read retries before it ever falls back to 0.
 */
class RedisClockStoreTests {

    private static RedisClockStore store(int attempts) {
        return spy(new RedisClockStore(mock(ReactiveStringRedisTemplate.class), attempts, Duration.ZERO));
    }

    private static RuntimeException down() {
        return new RedisConnectionFailureException("Redis is loading the dataset in memory");
    }

    @Test
    void aTransientFailureIsRetriedAndTheSavedTickIsStillFound() {
        RedisClockStore store = store(5);
        doThrow(down()).doThrow(down()).doReturn(18638L).when(store).readTick();

        assertThat(store.lastKnownTick()).isEqualTo(18638L);
        verify(store, times(3)).readTick();
    }

    @Test
    void aRedisThatStaysDownFallsBackToZeroOnlyAfterEveryAttempt() {
        RedisClockStore store = store(4);
        doThrow(down()).when(store).readTick();

        assertThat(store.lastKnownTick()).isZero();
        verify(store, times(4)).readTick();
    }

    @Test
    void anEmptyRedisIsAFreshInstallNotAFailureSoItIsNotRetried() {
        RedisClockStore store = store(5);
        doReturn(0L).when(store).readTick();

        assertThat(store.lastKnownTick()).isZero();
        verify(store, times(1)).readTick();
    }

    @Test
    void aSuccessfulFirstReadReturnsImmediately() {
        RedisClockStore store = store(5);
        doReturn(4465L).when(store).readTick();

        assertThat(store.lastKnownTick()).isEqualTo(4465L);
        verify(store, times(1)).readTick();
    }

    @Test
    void zeroConfiguredAttemptsStillMakesOneTry() {
        RedisClockStore store = store(0);
        doReturn(7L).when(store).readTick();

        assertThat(store.lastKnownTick()).isEqualTo(7L);
    }
}
