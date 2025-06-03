package dev.booky.betterview.common.config.loading;
// Created by booky10 in BetterView (15:56 03.06.2025)

import io.leangen.geantyref.TypeToken;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ScopedConfigurationNode;
import org.spongepowered.configurate.loader.AbstractConfigurationLoader;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

@NullMarked
public final class ConfigurateLoader {

    private ConfigurateLoader() {
    }

    private static AbstractConfigurationLoader.Builder<?, ?> constructYamlLoader() {
        return YamlConfigurationLoader.builder().indent(2).nodeStyle(NodeStyle.BLOCK);
    }

    private static AbstractConfigurationLoader<?> buildLoader(
            AbstractConfigurationLoader.Builder<?, ?> loader,
            TypeSerializerCollection serializers, Path configPath
    ) {
        return loader.path(configPath)
                .defaultOptions(opts -> opts.serializers(
                        builder -> builder.registerAll(serializers)))
                .build();
    }

    public static <T> T loadYaml(
            TypeSerializerCollection serializers,
            Path configPath, TypeToken<T> configType, Supplier<T> constructor
    ) {
        return load(constructYamlLoader(), serializers, configPath, configType, constructor);
    }

    public static <T> T load(
            AbstractConfigurationLoader.Builder<?, ?> loader,
            TypeSerializerCollection serializers,
            Path configPath, TypeToken<T> configType, Supplier<T> constructor
    ) {
        AbstractConfigurationLoader<?> builtLoader = buildLoader(loader, serializers, configPath);
        try {
            ScopedConfigurationNode<?> node = builtLoader.load();
            return Objects.requireNonNullElseGet(node.get(configType), constructor);
        } catch (IOException exception) {
            throw new RuntimeException("Error while loading config " + configType.getType() + " from " + configPath, exception);
        }
    }

    public static <T> void saveYaml(
            TypeSerializerCollection serializers,
            Path configPath, TypeToken<T> configType, T config
    ) {
        save(constructYamlLoader(), serializers, configPath, configType, config);
    }

    public static <T> void save(
            AbstractConfigurationLoader.Builder<?, ?> loader,
            TypeSerializerCollection serializers,
            Path configPath, TypeToken<T> configType, T config
    ) {
        AbstractConfigurationLoader<?> builtLoader = buildLoader(loader, serializers, configPath);
        ScopedConfigurationNode<?> node = builtLoader.createNode();
        try {
            node.set(configType, config);
            builtLoader.save(node);
        } catch (IOException exception) {
            throw new RuntimeException("Error while saving config " + configType + " to " + configPath, exception);
        }
    }
}
