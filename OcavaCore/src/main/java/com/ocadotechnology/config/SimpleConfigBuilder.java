/*
 * Copyright © 2017 Ocado (Ocava)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ocadotechnology.config;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.ocadotechnology.physics.units.LengthUnit;

/**
 * Utility for building a single config object from a set of {@code String:String} key:value pairs.  Intended
 * for use with tests and small utilities with hard-coded config.
 */
public class SimpleConfigBuilder<E extends Enum<E>> {
    private Class<E> configType;
    private Map<String, String> configMap; // Not used ImmutableMap.Builder to allow entries to be changed
    private TimeUnit timeUnit;
    private LengthUnit lengthUnit;

    private SimpleConfigBuilder(Class<E> configType) {
        this.configType = configType;
        this.configMap = new HashMap<>();
    }

    public static <E extends Enum<E>> SimpleConfigBuilder<E> create(Class<E> configType) {
        return new SimpleConfigBuilder<>(configType);
    }

    /**
     * Adds a config value to the builder.
     * @param key A string representation of the enum key.  This must be qualified from the level of the configType enum
     *            provided to the constructor (see {@link ConfigManager}).
     * @param value A String representation of the config value.  This should adhere to the appropriate syntax expected
     *              by the accessor.  If it does not, the accessor method will throw a runtime exception.
     * @return {@code this}, to facilitate fluent call chains.
     */
    public SimpleConfigBuilder<E> put(String key, String value) {
        configMap.put(key, value);
        return this;
    }

    /**
     * Adds a config value to the builder.
     * @param key An enum key.
     * @param value A String representation of the config value.  This should adhere to the appropriate syntax expected by
     *              the accessor.
     * @return {@code this}, to facilitate fluent call chains.
     */
    public SimpleConfigBuilder<E> put(Enum<?> key, String value) {
        return put(getKeyName(key), value);
    }

    /**
     * Adds a config value to the builder.
     * @param key An enum key.
     * @param value A config value.  This will be converted to a String using its {@code toString()} method.
     * @return {@code this}, to facilitate fluent call chains.
     */
    public SimpleConfigBuilder<E> put(Enum<?> key, Object value) {
        return put(getKeyName(key), value.toString());
    }

    /**
     * Adds a config value to the builder.
     * @param key An enum key.
     * @param values A collection of objects to be stored as one config value.  This will be converted to a String using
     *               the {@code toString()} method of each element and then comma-separating the elements.
     * @return {@code this}, to facilitate fluent call chains.
     */
    public SimpleConfigBuilder<E> put(Enum<?> key, Collection<Object> values) {
        return put(getKeyName(key), Joiner.on(",").join(values));
    }

    /**
     * Adds a time config value to the builder.
     * @param key An enum key.
     * @param value A numeric value to be stored as a config value.
     * @param unit The time unit associated with the above value.
     * @return {@code this}, to facilitate fluent call chains.
     */
    public SimpleConfigBuilder<E> put(Enum<?> key, double value, TimeUnit unit) {
        return put(getKeyName(key), value + "," + unit);
    }

    /**
     * Adds a length config value to the builder.
     * @param key An enum key.
     * @param value A numeric value to be stored as a config value.
     * @param unit The length unit associated with the above value.
     * @return {@code this}, to facilitate fluent call chains.
     */
    public SimpleConfigBuilder<E> put(Enum<?> key, double value, LengthUnit unit) {
        return put(getKeyName(key), value + "," + unit);
    }

    /**
     * Adds a speed or acceleration config value to the builder.
     * @param key An enum key.
     * @param value A numeric value to be stored as a config value.
     * @param timeUnit The time unit associated with the above value.
     * @param lengthUnit The length unit associated with the above value.
     * @return {@code this}, to facilitate fluent call chains.
     */
    public SimpleConfigBuilder<E> put(Enum<?> key, double value, LengthUnit lengthUnit, TimeUnit timeUnit) {
        return put(getKeyName(key), value + "," + lengthUnit + "," + timeUnit);
    }

    /**
     * Defines the TimeUnit to be used by this application as its default value.  This method must be called if any of
     * the supplied config is to be accessed as a Time, Speed or Acceleration value.
     */
    public SimpleConfigBuilder<E> setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
        return this;
    }

    /**
     * Defines the TimeUnit to be used by this application as its default value.  This method must be called if any of
     * the supplied config is to be accessed as a Length, Speed or Acceleration value.
     */
    public SimpleConfigBuilder<E> setLengthUnit(LengthUnit lengthUnit) {
        this.lengthUnit = lengthUnit;
        return this;
    }

    /**
     * Non-destructively constructs a Config object from this {@link SimpleConfigBuilder}, leaving the internal state of
     * the builder unchanged.
     *
     * @throws ConfigKeysNotRecognisedException if any of the config keys given in the {@code put} methods above are not
     *              recognised within the specified {@code configType} class.
     */
    public Config<E> build() throws ConfigKeysNotRecognisedException {
        ConfigManager.Builder configManagerBuilder = new ConfigManager.Builder(new String[]{});
        configManagerBuilder.loadConfigFromMap(ImmutableMap.copyOf(configMap), ImmutableSet.of(configType));
        configManagerBuilder.setTimeUnit(timeUnit);
        configManagerBuilder.setLengthUnit(lengthUnit);
        return configManagerBuilder.build().getConfig(configType);
    }

    /**
     * Non-destructively constructs a Config object from this {@link SimpleConfigBuilder}, leaving the internal state of
     * the builder unchanged.  This version of the build method is intended for use cases where the user is confident
     * that all of the input data is valid (eg in a unit test), or otherwise wants to avoid writing handling code.  It
     * should be used with care.
     *
     * @throws RuntimeException if any of the config keys given in the {@code put} methods above are not
     *              recognised within the specified {@code configType} class.
     */
    public Config<E> buildWrapped() {
        try {
            return build();
        } catch (ConfigKeysNotRecognisedException e) {
            throw new RuntimeException("Error caught building Config object.", e);
        }
    }

    private static String getKeyName(Enum<?> key) {
        Class<?> clazz = key.getClass();
        return clazz.getCanonicalName().replace(clazz.getPackage().getName() + ".", "") +
                "." +
                key.name();
    }
}
