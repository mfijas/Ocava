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
package com.ocadotechnology.indexedcache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collection;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;
import com.ocadotechnology.id.Id;
import com.ocadotechnology.id.SimpleLongIdentified;

@DisplayName("A ManyToOneIndex")
class ManyToOneIndexTest {

    @Nested
    class CacheTypeTests extends IndexTests {
        @Override
        ManyToOneIndex<Coordinate, TestState> addIndexToCache(IndexedImmutableObjectCache<TestState, TestState> cache) {
            return cache.addManyToOneIndex(TestState::getLocations);
        }
    }

    @Nested
    class CacheSubTypeTests extends IndexTests {
        @Override
        ManyToOneIndex<Coordinate, TestState> addIndexToCache(IndexedImmutableObjectCache<TestState, TestState> cache) {
            // IMPORTANT:
            // DO NOT inline indexFunction, as that will not fail to compile should addManyToOneIndex() require a type of
            // Function<TestState, Collection<Coordinate>> instead of Function<? super TestState, Collection<Coordinate>>,
            // due to automatic type coercion of the lambda.
            Function<LocationState, Collection<Coordinate>> indexFunction = LocationState::getLocations;
            return cache.addManyToOneIndex(indexFunction);
        }
    }

    private abstract static class IndexTests {
        
        private IndexedImmutableObjectCache<TestState, TestState> cache;
        private ManyToOneIndex<Coordinate, TestState> index;

        abstract ManyToOneIndex<Coordinate, TestState> addIndexToCache(IndexedImmutableObjectCache<TestState, TestState> cache);
        
        @BeforeEach
        void init() {
            cache = IndexedImmutableObjectCache.createHashMapBackedCache();
            index = addIndexToCache(cache);
        }

        @Test
        void putOrUpdate_whenFunctionReturnsNull_thenExceptionThrownOnAdd() {
            TestState stateOne = new TestState(Id.create(1), null);

            assertThatThrownBy(() -> cache.add(stateOne))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void putOrUpdate_whenMultipleTestStatesMapToTheSameCoordinate_thenExceptionThrownOnAdd() {
            TestState stateOne = new TestState(Id.create(1), ImmutableSet.of(Coordinate.create(0, 0)));
            TestState stateTwo = new TestState(Id.create(2), ImmutableSet.of(Coordinate.create(0, 0)));

            assertThatCode(() -> cache.add(stateOne)).doesNotThrowAnyException();
            assertThatThrownBy(() -> cache.add(stateTwo)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void putOrUpdateAll_whenMultipleTestStatesMapToTheSameCoordinate_thenExceptionThrownOnAdd() {
            TestState stateOne = new TestState(Id.create(1), ImmutableSet.of(Coordinate.create(0, 0)));
            TestState stateTwo = new TestState(Id.create(2), ImmutableSet.of(Coordinate.create(0, 0)));

            assertThatThrownBy(() -> cache.addAll(ImmutableSet.of(stateOne, stateTwo)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void putOrUpdateAll_whenTestStateMapsToCoordinateMappedToBySomeThingElse_thenExceptionThrownOnAdd() {
            TestState stateOne = new TestState(Id.create(1), ImmutableSet.of(Coordinate.create(0, 0)));
            TestState stateTwo = new TestState(Id.create(2), ImmutableSet.of(Coordinate.create(0, 1)));
            TestState stateThree = new TestState(Id.create(3), ImmutableSet.of(Coordinate.create(0, 0)));

            assertThatCode(() -> cache.add(stateOne)).doesNotThrowAnyException();
            assertThatThrownBy(() -> cache.addAll(ImmutableSet.of(stateTwo, stateThree)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void putOrUpdateAll_whenLocationsAreSwappedTheyAreSwappedInIndex() {
            TestState stateOne = new TestState(Id.create(1), ImmutableSet.of(Coordinate.create(0, 1)));
            TestState stateTwo = new TestState(Id.create(2), ImmutableSet.of(Coordinate.create(1, 0)));

            cache.addAll(ImmutableSet.of(stateOne, stateTwo));

            Change<TestState> updateOne = Change.update(stateOne, new TestState(Id.create(1), ImmutableSet.of(Coordinate.create(1, 0))));
            Change<TestState> updateTwo = Change.update(stateTwo, new TestState(Id.create(2), ImmutableSet.of(Coordinate.create(0, 1))));

            cache.updateAll(ImmutableSet.of(updateOne, updateTwo));

            assertThat(index.getOrNull(Coordinate.create(1, 0))).isEqualTo(stateOne);
            assertThat(index.getOrNull(Coordinate.create(0, 1))).isEqualTo(stateTwo);
        }

        @Test
        void putOrUpdateAll_whenManyKeysAreUsed() {
            TestState stateOne = new TestState(Id.create(1), ImmutableSet.of(Coordinate.create(0, 1), Coordinate.create(0, 2), Coordinate.create(0, 3)));
            TestState stateTwo = new TestState(Id.create(2), ImmutableSet.of(Coordinate.create(1, 0), Coordinate.create(2, 0), Coordinate.create(3, 0)));

            cache.addAll(ImmutableSet.of(stateOne, stateTwo));

            assertThat(index.getOrNull(Coordinate.create(0, 1))).isEqualTo(stateOne);
            assertThat(index.getOrNull(Coordinate.create(0, 2))).isEqualTo(stateOne);
            assertThat(index.getOrNull(Coordinate.create(0, 3))).isEqualTo(stateOne);
            assertThat(index.getOrNull(Coordinate.create(1, 0))).isEqualTo(stateTwo);
            assertThat(index.getOrNull(Coordinate.create(2, 0))).isEqualTo(stateTwo);
            assertThat(index.getOrNull(Coordinate.create(3, 0))).isEqualTo(stateTwo);
        }
    }

    private interface LocationState {
        ImmutableSet<Coordinate> getLocations();
    }

    private static class TestState extends SimpleLongIdentified<TestState> implements LocationState {
        private final ImmutableSet<Coordinate> locations;

        private TestState(Id<TestState> id, ImmutableSet<Coordinate> locations) {
            super(id);
            this.locations = locations;
        }

        @Override
        public ImmutableSet<Coordinate> getLocations() {
            return locations;
        }
    }
}
