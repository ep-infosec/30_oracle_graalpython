/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
// skip GIL
package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;

/**
 * Represents the value of a native pointer as Python int.<br/>
 * Such objects are created using C API function {@code PyLong_FromVoidPtr} and semantics are best
 * explained by looking at how CPython constructs the value:
 * {@code PyLong_FromUnsignedLong((unsigned long)(uintptr_t)p)}. CPython casts the {@code (void *)}
 * to an integer and this will be the value for the Python int. In our case, to get a numeric
 * representation of a pointer, we would need to send a to-native message. However, we try to avoid
 * this eager transformation using this wrapper.
 */
public class PythonNativeVoidPtr extends PythonAbstractObject {
    private final Object object;
    private final long nativePointerValue;
    private final boolean hasNativePointer;

    public PythonNativeVoidPtr(Object object) {
        this.object = object;
        this.nativePointerValue = 0;
        this.hasNativePointer = false;
    }

    public PythonNativeVoidPtr(Object object, long nativePointerValue) {
        this.object = object;
        this.nativePointerValue = nativePointerValue;
        this.hasNativePointer = true;
    }

    public Object getPointerObject() {
        return object;
    }

    public long getNativePointer() {
        return nativePointerValue;
    }

    public boolean isNativePointer() {
        return hasNativePointer;
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return PythonUtils.formatJString("PythonNativeVoidPtr(%s)", object);
    }
}
