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
package com.ocadotechnology.id;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class IdGeneratorTest {
    private static class A{}
    private static class B{}

    @AfterEach
    public void clear() {
        IdGenerator.clear();
    }

    @Test
    public void getIdReturnsNewId() {
        Id<A> idA = IdGenerator.getId(A.class);
        Id<B> idB = IdGenerator.getId(B.class);
        Assertions.assertNotSame(idA, idB);
        Assertions.assertSame(idA.id, idB.id);
    }

    @Test
    public void getCachedIdReturnsCachedIds() {
        Id<A> idA = IdGenerator.getCachedId(A.class);
        Id<B> idB = IdGenerator.getCachedId(B.class);
        Assertions.assertSame(idA, idB);
    }

    @Test
    public void rawIdGenerator() {
        AtomicLong rawIdGenerator = IdGenerator.getRawIdGenerator(A.class);
        Assertions.assertNotNull(rawIdGenerator);

        rawIdGenerator.set(100);
        Assertions.assertEquals(IdGenerator.getId(A.class).id, 100);
    }

    @Test
    public void initialiseIdCounter() {
        IdGenerator.initialiseIdCounter(A.class, 100);
        Assertions.assertEquals(IdGenerator.getId(A.class).id, 100);
    }

    @Test
    public void clearIdCounter() {
        AtomicLong rawIdGenerator = IdGenerator.getRawIdGenerator(A.class);
        rawIdGenerator.getAndSet(300);
        IdGenerator.initialiseIdCounter(B.class, 200);

        IdGenerator.clear();

        Assertions.assertEquals(rawIdGenerator.get(), 0);
        Assertions.assertEquals(IdGenerator.getId(A.class).id, 0);
        Assertions.assertEquals(IdGenerator.getId(B.class).id, 0);
    }

}
