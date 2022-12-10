/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.test.runtime;

import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.graal.python.runtime.sequence.storage.*;

public class SequenceStorageTests {

    private static Object[] getObjectValues() {
        return new Object[]{1, 2, 3, 4, 5, 6};
    }

    @Test
    public void objectsGetAndSet() {
        ObjectSequenceStorage store = new ObjectSequenceStorage(getObjectValues());
        assertEquals(4, store.getItemNormalized(3));
        store.setItemNormalized(5, 10);
        assertEquals(10, store.getItemNormalized(5));
    }

    @Test
    public void objectsGetSlice() {
        ObjectSequenceStorage store = new ObjectSequenceStorage(getObjectValues());
        ObjectSequenceStorage slice = store.getSliceInBound(1, 4, 1, 3);

        for (int i = 0; i < 3; i++) {
            assertEquals(i + 2, slice.getItemNormalized(i));
        }
    }

    @Test
    public void objectsInsert() {
        ObjectSequenceStorage store = new ObjectSequenceStorage(getObjectValues());
        store.insertItem(3, 42);
        assertEquals(42, store.getItemNormalized(3));
        assertEquals(6, store.getItemNormalized(6));
        assertEquals(7, store.length());
    }

    /**
     * IntSequenceStorage tests.
     */
    private static int[] getIntValues() {
        return new int[]{1, 2, 3, 4, 5, 6};
    }

    @Test
    public void intGetAndSet() throws SequenceStoreException {
        IntSequenceStorage store = new IntSequenceStorage(getIntValues());
        assertEquals(4, store.getItemNormalized(3));
        store.setItemNormalized(5, 10);
        assertEquals(10, store.getItemNormalized(5));
    }

    @Test
    public void intGetSlice() {
        IntSequenceStorage store = new IntSequenceStorage(getIntValues());
        IntSequenceStorage slice = store.getSliceInBound(1, 4, 1, 3);

        for (int i = 0; i < 3; i++) {
            assertEquals(i + 2, slice.getItemNormalized(i));
        }
    }

    @Test
    public void intInsert() throws SequenceStoreException {
        IntSequenceStorage store = new IntSequenceStorage(getIntValues());
        store.insertItem(3, 42);
        assertEquals(42, store.getItemNormalized(3));
        assertEquals(6, store.getItemNormalized(6));
        assertEquals(7, store.length());
    }
}
