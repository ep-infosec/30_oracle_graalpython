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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PAsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.ToPyObjectNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallTernaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public abstract class PyProcsWrapper extends PythonNativeWrapper {

    public PyProcsWrapper(Object delegate) {
        super(delegate);
    }

    @ExportMessage
    protected boolean isExecutable() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    protected Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("should not reach");
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        // TODO implement native type
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getNativeType() {
        // TODO implement native type
        return null;
    }

    @ExportMessage
    protected boolean isPointer(
                    @Cached IsPointerNode isPointerNode) {
        return isPointerNode.execute(this);
    }

    @ExportMessage
    protected long asPointer(
                    @Exclusive @Cached PAsPointerNode pAsPointerNode) {
        return pAsPointerNode.execute(this);
    }

    @ExportMessage
    protected void toNative(
                    @Exclusive @Cached ToPyObjectNode toPyObjectNode,
                    @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
        invalidateNode.execute();
        setNativePointer(toPyObjectNode.execute(this));
    }

    @ExportLibrary(InteropLibrary.class)
    static final class GetAttrWrapper extends PyProcsWrapper {

        public GetAttrWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        protected Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached CallBinaryMethodNode executeNode,
                        @Cached ToJavaNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                try {
                    return toNewRefNode.execute(executeNode.executeObject(null, lib.getDelegate(this), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1])));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(null, e);
                    return PythonContext.get(gil).getNativeNull().getPtr();
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class BinaryFuncWrapper extends PyProcsWrapper {

        public BinaryFuncWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        protected Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached CallBinaryMethodNode executeNode,
                        @Cached ToJavaNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                try {
                    return toNewRefNode.execute(executeNode.executeObject(null, lib.getDelegate(this), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1])));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(null, e);
                    return PythonContext.get(gil).getNativeNull().getPtr();
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class UnaryFuncWrapper extends PyProcsWrapper {

        public UnaryFuncWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        protected Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached CallUnaryMethodNode executeNode,
                        @Cached ToJavaNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                /*
                 * Accept a second argumenthere, since these functions are sometimes called using
                 * METH_O with a "NULL" value.
                 */
                if (arguments.length > 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(1, 2, arguments.length);
                }
                try {
                    return toNewRefNode.execute(executeNode.executeObject(null, lib.getDelegate(this), toJavaNode.execute(arguments[0])));
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(null, e);
                    return PythonContext.get(gil).getNativeNull().getPtr();
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class SetAttrWrapper extends PyProcsWrapper {

        public SetAttrWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        protected int execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached CallTernaryMethodNode callTernaryMethodNode,
                        @Cached ToJavaNode toJavaNode,
                        @Cached ConditionProfile arityProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                if (arityProfile.profile(arguments.length != 3)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(3, 3, arguments.length);
                }
                try {
                    callTernaryMethodNode.execute(null, lib.getDelegate(this), toJavaNode.execute(arguments[0]), toJavaNode.execute(arguments[1]), toJavaNode.execute(arguments[2]));
                    return 0;
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(null, e);
                    return -1;
                }
            } finally {
                gil.release(mustRelease);
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class InitWrapper extends PyProcsWrapper {

        public InitWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage(name = "execute")
        static class Execute {

            @Specialization(guards = "arguments.length == 3")
            static int init(InitWrapper self, Object[] arguments,
                            @CachedLibrary("self") PythonNativeWrapperLibrary lib,
                            @Cached ExecutePositionalStarargsNode posStarargsNode,
                            @Cached ExpandKeywordStarargsNode expandKwargsNode,
                            @Cached CallVarargsMethodNode callNode,
                            @Cached ToJavaNode toJavaNode,
                            @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                            @Exclusive @Cached GilNode gil) {
                boolean mustRelease = gil.acquire();
                try {
                    try {
                        // convert args
                        Object receiver = toJavaNode.execute(arguments[0]);
                        Object starArgs = toJavaNode.execute(arguments[1]);
                        Object kwArgs = toJavaNode.execute(arguments[2]);

                        Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                        Object[] pArgs = PythonUtils.prependArgument(receiver, starArgsArray);
                        PKeyword[] kwArgsArray = expandKwargsNode.execute(kwArgs);
                        callNode.execute(null, lib.getDelegate(self), pArgs, kwArgsArray);
                        return 0;
                    } catch (PException e) {
                        transformExceptionToNativeNode.execute(null, e);
                        return -1;
                    }
                } finally {
                    gil.release(mustRelease);
                }
            }

            @Specialization(guards = "arguments.length != 3")
            static int error(@SuppressWarnings("unused") InitWrapper self, Object[] arguments) throws ArityException {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, 3, arguments.length);
            }

        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class VarargWrapper extends PyProcsWrapper {

        public VarargWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage(name = "execute")
        static class Execute {

            @Specialization(guards = "arguments.length == 2")
            static Object init(VarargWrapper self, Object[] arguments,
                            @CachedLibrary("self") PythonNativeWrapperLibrary lib,
                            @Cached ToNewRefNode toNewRefNode,
                            @Cached ExecutePositionalStarargsNode posStarargsNode,
                            @Cached CallVarargsMethodNode callNode,
                            @Cached ToJavaNode toJavaNode,
                            @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                            @Exclusive @Cached GilNode gil) {
                boolean mustRelease = gil.acquire();
                try {
                    try {
                        // convert args
                        Object receiver = toJavaNode.execute(arguments[0]);
                        Object starArgs = toJavaNode.execute(arguments[1]);

                        Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                        Object[] pArgs = PythonUtils.prependArgument(receiver, starArgsArray);
                        return toNewRefNode.execute(callNode.execute(null, lib.getDelegate(self), pArgs, PKeyword.EMPTY_KEYWORDS));
                    } catch (PException e) {
                        transformExceptionToNativeNode.execute(null, e);
                        return PythonContext.get(gil).getNativeNull().getPtr();
                    }
                } finally {
                    gil.release(mustRelease);
                }
            }

            @Specialization(guards = "arguments.length != 2")
            static int error(@SuppressWarnings("unused") VarargWrapper self, Object[] arguments) throws ArityException {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(2, 2, arguments.length);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class VarargKeywordWrapper extends PyProcsWrapper {

        public VarargKeywordWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage(name = "execute")
        static class Execute {

            @Specialization(guards = "arguments.length == 3")
            static Object init(VarargKeywordWrapper self, Object[] arguments,
                            @CachedLibrary("self") PythonNativeWrapperLibrary lib,
                            @Cached ToNewRefNode toNewRefNode,
                            @Cached ExecutePositionalStarargsNode posStarargsNode,
                            @Cached ExpandKeywordStarargsNode expandKwargsNode,
                            @Cached CallVarargsMethodNode callNode,
                            @Cached ToJavaNode toJavaNode,
                            @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                            @Exclusive @Cached GilNode gil) {
                boolean mustRelease = gil.acquire();
                try {
                    try {
                        // convert args
                        Object receiver = toJavaNode.execute(arguments[0]);
                        Object starArgs = toJavaNode.execute(arguments[1]);
                        Object kwArgs = toJavaNode.execute(arguments[2]);

                        Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                        Object[] pArgs = PythonUtils.prependArgument(receiver, starArgsArray);
                        PKeyword[] kwArgsArray = expandKwargsNode.execute(kwArgs);
                        return toNewRefNode.execute(callNode.execute(null, lib.getDelegate(self), pArgs, kwArgsArray));
                    } catch (PException e) {
                        transformExceptionToNativeNode.execute(null, e);
                        return PythonContext.get(gil).getNativeNull().getPtr();
                    }
                } finally {
                    gil.release(mustRelease);
                }
            }

            @Specialization(guards = "arguments.length != 3")
            static int error(@SuppressWarnings("unused") VarargKeywordWrapper self, Object[] arguments) throws ArityException {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, 3, arguments.length);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class TernaryFunctionWrapper extends PyProcsWrapper {

        public TernaryFunctionWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage(name = "execute")
        static class Execute {

            @Specialization(guards = "arguments.length == 3")
            static Object call(TernaryFunctionWrapper self, Object[] arguments,
                            @CachedLibrary("self") PythonNativeWrapperLibrary lib,
                            @Cached ExecutePositionalStarargsNode posStarargsNode,
                            @Cached ExpandKeywordStarargsNode expandKwargsNode,
                            @Cached CallVarargsMethodNode callNode,
                            @Cached ToJavaNode toJavaNode,
                            @Cached ToNewRefNode toNewRefNode,
                            @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                            @Exclusive @Cached GilNode gil) {
                boolean mustRelease = gil.acquire();
                try {
                    try {
                        // convert args
                        Object receiver = toJavaNode.execute(arguments[0]);
                        Object starArgs = toJavaNode.execute(arguments[1]);
                        Object kwArgs = toJavaNode.execute(arguments[2]);

                        Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                        Object[] pArgs = PythonUtils.prependArgument(receiver, starArgsArray);
                        PKeyword[] kwArgsArray = expandKwargsNode.execute(kwArgs);
                        Object result = callNode.execute(null, lib.getDelegate(self), pArgs, kwArgsArray);
                        return toNewRefNode.execute(result);
                    } catch (PException e) {
                        transformExceptionToNativeNode.execute(null, e);
                        return PythonContext.get(gil).getNativeNull().getPtr();
                    }
                } finally {
                    gil.release(mustRelease);
                }
            }

            @Specialization(guards = "arguments.length != 3")
            static Object error(@SuppressWarnings("unused") TernaryFunctionWrapper self, Object[] arguments) throws ArityException {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw ArityException.create(3, 3, arguments.length);
            }

        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class SsizeargfuncWrapper extends PyProcsWrapper {

        private final boolean newRef;

        public SsizeargfuncWrapper(Object delegate, boolean newRef) {
            super(delegate);
            this.newRef = newRef;
        }

        @ExportMessage
        protected Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached ToSulongNode toSulongNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached CallBinaryMethodNode executeNode,
                        @Cached ToJavaNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                assert arguments[1] instanceof Number;
                try {
                    Object result = executeNode.executeObject(null, lib.getDelegate(this), toJavaNode.execute(arguments[0]), arguments[1]);
                    return newRef ? toNewRefNode.execute(result) : toSulongNode.execute(result);
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(null, e);
                    return PythonContext.get(toJavaNode).getNativeNull().getPtr();
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class LenfuncWrapper extends PyProcsWrapper {

        public LenfuncWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        protected Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached CallUnaryMethodNode executeNode,
                        @Cached ToJavaNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached CastToJavaIntLossyNode castLossy,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PRaiseNode raiseNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                if (arguments.length != 1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(1, 1, arguments.length);
                }
                try {
                    Object result = executeNode.executeObject(null, lib.getDelegate(this), toJavaNode.execute(arguments[0]));
                    int len = PyObjectSizeNode.convertAndCheckLen(null, result, indexNode, castLossy, asSizeNode, raiseNode);
                    return (long) len;
                } catch (PException e) {
                    transformExceptionToNativeNode.execute(null, e);
                    return PythonContext.get(toJavaNode).getNativeNull().getPtr();
                }
            } finally {
                gil.release(mustRelease);
            }
        }
    }

    public static GetAttrWrapper createGetAttrWrapper(Object method) {
        assert !(method instanceof PNone) && !(method instanceof PNotImplemented);
        return new GetAttrWrapper(method);
    }

    public static UnaryFuncWrapper createUnaryFuncWrapper(Object method) {
        assert !(method instanceof PNone) && !(method instanceof PNotImplemented);
        return new UnaryFuncWrapper(method);
    }

    public static BinaryFuncWrapper createBinaryFuncWrapper(Object method) {
        assert !(method instanceof PNone) && !(method instanceof PNotImplemented);
        return new BinaryFuncWrapper(method);
    }

    public static SetAttrWrapper createSetAttrWrapper(Object setAttrMethod) {
        assert !(setAttrMethod instanceof PNone) && !(setAttrMethod instanceof PNotImplemented);
        return new SetAttrWrapper(setAttrMethod);
    }

    public static InitWrapper createInitWrapper(Object setInitMethod) {
        assert !(setInitMethod instanceof PNone) && !(setInitMethod instanceof PNotImplemented);
        return new InitWrapper(setInitMethod);
    }

    public static VarargWrapper createVarargWrapper(Object method) {
        assert !(method instanceof PNone) && !(method instanceof PNotImplemented);
        return new VarargWrapper(method);
    }

    public static VarargKeywordWrapper createVarargKeywordWrapper(Object method) {
        assert !(method instanceof PNone) && !(method instanceof PNotImplemented);
        return new VarargKeywordWrapper(method);
    }

    /**
     * Wraps CPython's {@code ternaryfunc} slots.
     */
    public static TernaryFunctionWrapper createTernaryFunctionWrapper(Object method) {
        assert !(method instanceof PNone) && !(method instanceof PNotImplemented);
        return new TernaryFunctionWrapper(method);
    }

    public static SsizeargfuncWrapper createSsizeargfuncWrapper(Object method, boolean newRef) {
        assert !(method instanceof PNone) && !(method instanceof PNotImplemented);
        return new SsizeargfuncWrapper(method, newRef);
    }

    public static LenfuncWrapper createLenfuncWrapper(Object method) {
        assert !(method instanceof PNone) && !(method instanceof PNotImplemented);
        return new LenfuncWrapper(method);
    }
}
