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
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PAsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.ToPyObjectNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wrappers for methods used by native code.
 */
public abstract class ManagedMethodWrappers {

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
    public abstract static class MethodWrapper extends PythonNativeWrapper {

        private final Object typeid;

        public MethodWrapper(Object method, Object typeid) {
            super(method);
            this.typeid = typeid;
        }

        @ExportMessage
        public boolean isPointer(
                        @Exclusive @Cached IsPointerNode pIsPointerNode) {
            return pIsPointerNode.execute(this);
        }

        @ExportMessage
        public long asPointer(
                        @Exclusive @Cached PAsPointerNode pAsPointerNode) {
            return pAsPointerNode.execute(this);
        }

        @ExportMessage
        public void toNative(
                        @Exclusive @Cached ToPyObjectNode toPyObjectNode,
                        @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            invalidateNode.execute();
            setNativePointer(toPyObjectNode.execute(this));
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean hasNativeType() {
            return typeid != null;
        }

        @ExportMessage
        public Object getNativeType() {
            return typeid;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class MethKeywords extends MethodWrapper {

        public MethKeywords(Object method, Object typeid) {
            super(method, typeid);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached ToJavaNode toJavaNode,
                        @Exclusive @Cached CExtNodes.ToNewRefNode toSulongNode,
                        @Exclusive @Cached CallNode callNode,
                        @Exclusive @Cached ExecutePositionalStarargsNode posStarargsNode,
                        @Exclusive @Cached ExpandKeywordStarargsNode expandKwargsNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                if (arguments.length != 3) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(3, 3, arguments.length);
                }

                // convert args
                Object receiver = toJavaNode.execute(arguments[0]);
                Object starArgs = toJavaNode.execute(arguments[1]);
                Object kwArgs = toJavaNode.execute(arguments[2]);

                Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                Object[] pArgs = PythonUtils.prependArgument(receiver, starArgsArray);
                PKeyword[] kwArgsArray = expandKwargsNode.execute(kwArgs);

                // execute
                return toSulongNode.execute(callNode.execute(null, lib.getDelegate(this), pArgs, kwArgsArray));
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class MethVarargs extends MethodWrapper {

        public MethVarargs(Object method) {
            super(method, null);
        }

        @ExportMessage
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached ToJavaNode toJavaNode,
                        @Exclusive @Cached CExtNodes.ToNewRefNode toSulongNode,
                        @Exclusive @Cached PythonAbstractObject.PExecuteNode executeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException, UnsupportedMessageException {
            boolean mustRelease = gil.acquire();
            try {
                if (arguments.length != 1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(1, 1, arguments.length);
                }

                // convert args
                Object varArgs = toJavaNode.execute(arguments[0]);
                return toSulongNode.execute(executeNode.execute(lib.getDelegate(this), new Object[]{varArgs}));
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    /**
     * Creates a wrapper for signature {@code meth(*args, **kwargs)}.
     */
    public static MethodWrapper createKeywords(Object method, Object typeid) {
        return new MethKeywords(method, typeid);
    }

    /**
     * Creates a wrapper for signature {@code meth(*args)}.
     */
    public static MethodWrapper createVarargs(Object method) {
        return new MethVarargs(method);
    }

}
