/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.ToNativeNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Emulates type {@code Py_complex}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public final class PyComplexWrapper extends PythonNativeWrapper {

    public static final String J_REAL = "real";
    public static final String J_IMAG = "imag";

    public PyComplexWrapper(PComplex delegate) {
        super(delegate);
    }

    public PComplex getPComplex(PythonNativeWrapperLibrary lib) {
        return (PComplex) lib.getDelegate(this);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PythonAbstractObject.Keys(new Object[]{J_REAL, J_IMAG});
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMemberReadable(String key) {
        return J_REAL.equals(key) || J_IMAG.equals(key);
    }

    @ExportMessage
    Object readMember(String key,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib) throws UnknownIdentifierException {
        switch (key) {
            case J_REAL:
                return getPComplex(lib).getReal();
            case J_IMAG:
                return getPComplex(lib).getImag();
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw UnknownIdentifierException.create(key);
    }

    @ExportMessage
    void toNative(
                    @Cached ToNativeNode toNativeNode) {
        toNativeNode.execute(this);
    }

    @ExportMessage
    boolean isPointer(
                    @Cached IsPointerNode pIsPointerNode) {
        return pIsPointerNode.execute(this);
    }

    @ExportMessage
    long asPointer(
                    @CachedLibrary(limit = "1") InteropLibrary interopLib,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib) throws UnsupportedMessageException {
        Object nativePointer = lib.getNativePointer(this);
        if (nativePointer instanceof Long) {
            return (long) nativePointer;
        }
        return interopLib.asPointer(nativePointer);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getNativeType(
                    @Cached GetLLVMType getLLVMType) {
        return getLLVMType.execute(LLVMType.Py_complex);
    }
}
