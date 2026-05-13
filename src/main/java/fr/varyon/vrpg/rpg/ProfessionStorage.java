package fr.varyon.vrpg.rpg;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ProfessionStorage {

    CompletableFuture<Void> initialize();

    CompletableFuture<PlayerAccount> loadPlayer(@Nonnull UUID uuid);

    CompletableFuture<Void> savePlayer(@Nonnull UUID uuid, @Nonnull PlayerAccount account);

    CompletableFuture<Void> saveAll(@Nonnull Map<UUID, PlayerAccount> dirty);

    void saveAllSync(@Nonnull Map<UUID, PlayerAccount> dirty);

    CompletableFuture<Boolean> playerExists(@Nonnull UUID uuid);

    CompletableFuture<Void> deletePlayer(@Nonnull UUID uuid);

    CompletableFuture<List<UUID>> findUuidsByName(@Nonnull String exactNameCaseInsensitive);

    CompletableFuture<String> getSavedName(@Nonnull UUID uuid);

    @Nullable
    String getName();

    CompletableFuture<Void> shutdown();
}
