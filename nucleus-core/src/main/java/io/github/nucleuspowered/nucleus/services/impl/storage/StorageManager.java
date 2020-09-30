/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.services.impl.storage;

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.nucleuspowered.nucleus.core.config.CoreConfig;
import io.github.nucleuspowered.nucleus.guice.DataDirectory;
import io.github.nucleuspowered.nucleus.services.impl.storage.dataaccess.IConfigurateBackedDataTranslator;
import io.github.nucleuspowered.nucleus.services.impl.storage.dataobjects.modular.GeneralDataObject;
import io.github.nucleuspowered.nucleus.services.impl.storage.dataobjects.modular.IGeneralDataObject;
import io.github.nucleuspowered.nucleus.services.impl.storage.dataobjects.modular.IUserDataObject;
import io.github.nucleuspowered.nucleus.services.impl.storage.dataobjects.modular.IWorldDataObject;
import io.github.nucleuspowered.nucleus.services.impl.storage.dataobjects.modular.UserDataObject;
import io.github.nucleuspowered.nucleus.services.impl.storage.dataobjects.modular.WorldDataObject;
import io.github.nucleuspowered.nucleus.services.impl.storage.persistence.FlatFileStorageRepositoryFactory;
import io.github.nucleuspowered.nucleus.services.impl.storage.queryobjects.IUserQueryObject;
import io.github.nucleuspowered.nucleus.services.impl.storage.queryobjects.IWorldQueryObject;
import io.github.nucleuspowered.nucleus.services.impl.storage.services.SingleCachedService;
import io.github.nucleuspowered.nucleus.services.impl.storage.services.UserService;
import io.github.nucleuspowered.nucleus.services.impl.storage.services.WorldService;
import io.github.nucleuspowered.nucleus.services.interfaces.IConfigProvider;
import io.github.nucleuspowered.nucleus.services.interfaces.IConfigurateHelper;
import io.github.nucleuspowered.nucleus.services.interfaces.IStorageManager;
import io.github.nucleuspowered.storage.IStorageModule;
import io.github.nucleuspowered.storage.dataaccess.IDataTranslator;
import io.github.nucleuspowered.storage.dataobjects.IDataObject;
import io.github.nucleuspowered.storage.persistence.IStorageRepository;
import io.github.nucleuspowered.storage.persistence.IStorageRepositoryFactory;
import io.github.nucleuspowered.storage.services.IStorageService;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.ConfigurationOptions;
import ninja.leaping.configurate.SimpleConfigurationNode;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.plugin.PluginContainer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@Singleton
public final class StorageManager implements IStorageManager {

    private final FlatFileStorageRepositoryFactory flatFileStorageRepositoryFactory;
    private final IConfigurateHelper configurateHelper;
    private final IConfigProvider configProvider;
    private final IStorageService.SingleCached<IGeneralDataObject> generalService;
    private final UserService userService;
    private final WorldService worldService;

    private final Map<Class<? extends IDataObject>, IStorageModule<?, ?, ?, ?>> additionalStorageServices = new HashMap<>();

    @Inject
    public StorageManager(@DataDirectory final Supplier<Path> dataDirectory,
            final Logger logger,
            final IConfigurateHelper configurateHelper,
            final IConfigProvider configProvider,
            final PluginContainer pluginContainer) {
        this.flatFileStorageRepositoryFactory = new FlatFileStorageRepositoryFactory(dataDirectory, logger);
        this.configurateHelper = configurateHelper;
        this.configProvider = configProvider;
        this.userService = new UserService(this, pluginContainer);
        this.worldService = new WorldService(this, pluginContainer);
        this.generalService = new SingleCachedService<>(
                this::getGeneralRepository,
                this::getGeneralDataAccess,
                pluginContainer);
    }

    @Override
    public final IStorageRepositoryFactory getFlatFileRepositoryFactory() {
        return this.flatFileStorageRepositoryFactory;
    }

    // ugh
    @Override
    public <T extends IDataObject> void register(final Class<T> clazz, final IStorageModule<T, ? extends IStorageService<T>,
            ? extends IStorageRepository, ? extends IDataTranslator<T, JsonObject>> module) {
        if (this.additionalStorageServices.containsKey(clazz)) {
            throw new IllegalArgumentException("Class is already registered");
        }
        this.additionalStorageServices.put(clazz, module);
    }

    private IStorageRepository.@Nullable Keyed<UUID, IUserQueryObject, JsonObject> userRepository;

    private IStorageRepository.@Nullable Keyed<ResourceKey, IWorldQueryObject, JsonObject> worldRepository;

    private IStorageRepository.@Nullable Single<JsonObject> generalRepository;

    private final IConfigurateBackedDataTranslator<IUserDataObject> userDataAccess = new IConfigurateBackedDataTranslator<IUserDataObject>() {
        @Override public ConfigurationNode createNewNode() {
            return SimpleConfigurationNode.root(StorageManager.this.configurateHelper.setOptions(ConfigurationOptions.defaults()));
        }

        @Override public IUserDataObject createNew() {
            final UserDataObject d = new UserDataObject();
            d.setBackingNode(StorageManager.this.configurateHelper.createNode());
            return d;
        }
    };
    private final IConfigurateBackedDataTranslator<IWorldDataObject> worldDataAccess = new IConfigurateBackedDataTranslator<IWorldDataObject>() {
        @Override public ConfigurationNode createNewNode() {
            return SimpleConfigurationNode.root(StorageManager.this.configurateHelper.setOptions(ConfigurationOptions.defaults()));
        }

        @Override public IWorldDataObject createNew() {
            final WorldDataObject d = new WorldDataObject();
            d.setBackingNode(StorageManager.this.configurateHelper.createNode());
            return d;
        }
    };
    private final IConfigurateBackedDataTranslator<IGeneralDataObject> generalDataAccess = new IConfigurateBackedDataTranslator<IGeneralDataObject>() {
        @Override public ConfigurationNode createNewNode() {
            return SimpleConfigurationNode.root(StorageManager.this.configurateHelper.setOptions(ConfigurationOptions.defaults()));
        }

        @Override public IGeneralDataObject createNew() {
            final GeneralDataObject d = new GeneralDataObject();
            d.setBackingNode(StorageManager.this.configurateHelper.createNode());
            return d;
        }
    };

    @Override
    public IStorageService.SingleCached<IGeneralDataObject> getGeneralService() {
        return this.generalService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IDataObject> Optional<IStorageService<T>> getAdditionalStorageServiceForDataObject(final Class<T> clazz) {
        return Optional.ofNullable(this.additionalStorageServices.get(clazz)).map(x -> (IStorageService<T>) x.getService());
    }

    @Override
    public UserService getUserService() {
        return this.userService;
    }

    @Override
    public WorldService getWorldService() {
        return this.worldService;
    }

    @Override public IDataTranslator<IUserDataObject, JsonObject> getUserDataAccess() {
        return this.userDataAccess;
    }

    @Override public IDataTranslator<IWorldDataObject, JsonObject> getWorldDataAccess() {
        return this.worldDataAccess;
    }

    @Override public IDataTranslator<IGeneralDataObject, JsonObject> getGeneralDataAccess() {
        return this.generalDataAccess;
    }

    @Override
    public IStorageRepository.Keyed<UUID, IUserQueryObject, JsonObject> getUserRepository() {
        if (this.userRepository == null) {
            // fallback to flat file
            this.userRepository = this.flatFileStorageRepositoryFactory.userRepository();
        }
        return this.userRepository;
    }

    @Override
    public IStorageRepository.Keyed<ResourceKey, IWorldQueryObject, JsonObject> getWorldRepository() {
        if (this.worldRepository== null) {
            // fallback to flat file
            this.worldRepository = this.flatFileStorageRepositoryFactory.worldRepository();
        }
        return this.worldRepository;
    }

    @Override
    public IStorageRepository.Single<JsonObject> getGeneralRepository() {
        if (this.generalRepository == null) {
            // fallback to flat file
            this.generalRepository = this.flatFileStorageRepositoryFactory.generalRepository();
        }
        return this.generalRepository;
    }

    @Override
    public CompletableFuture<Void> saveAndInvalidateAllCaches() {
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        futures.add(this.generalService.ensureSaved().whenComplete((cv, t) -> this.generalService.clearCache()));
        futures.add(this.userService.ensureSaved().whenComplete((cv, t) -> this.userService.clearCache()));
        futures.add(this.worldService.ensureSaved().whenComplete((cv, t) -> this.worldService.clearCache()));
        this.additionalStorageServices.values().forEach(x -> futures.add(x.getService().ensureSaved().whenComplete((cv, t) -> x.getService().clearCache())));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public CompletableFuture<Void> saveAll() {
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        futures.add(this.generalService.ensureSaved());
        futures.add(this.userService.ensureSaved());
        futures.add(this.worldService.ensureSaved());
        this.additionalStorageServices.values().forEach(x -> futures.add(x.getService().ensureSaved()));
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    @Override
    public void attachAll() {
        this.detachAll();
        // TODO: attach based on core config
        final CoreConfig config = this.configProvider.getCoreConfig();
    }

    @Override
    public void detachAll() {
        // TODO: Data registry
        if (this.generalRepository != null) {
            this.generalRepository.shutdown();
        }

        this.generalRepository = null;

        if (this.worldRepository != null) {
            this.worldRepository.shutdown();
        }

        this.worldRepository = null;

        if (this.userRepository != null) {
            this.userRepository.shutdown();
        }

        this.userRepository = null;

        this.additionalStorageServices.values().forEach(IStorageModule::detach);
    }



}

