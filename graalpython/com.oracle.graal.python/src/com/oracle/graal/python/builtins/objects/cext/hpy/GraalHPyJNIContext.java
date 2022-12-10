/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.GetHPyHandleForSingleton;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextMember;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.HPyContextNativePointer;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContextFactory.GetHPyHandleForSingletonNodeGen;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * This object is used to override specific native upcall pointers in the HPyContext. This is
 * queried for every member of HPyContext by {@code graal_hpy_context_to_native}, and overrides the
 * original values (which are NFI closures for functions in {@code hpy.c}, subsequently calling into
 * {@link GraalHPyContextFunctions}.
 */
@ExportLibrary(InteropLibrary.class)
final class GraalHPyJNIContext implements TruffleObject {

    GraalHPyJNIContext(@SuppressWarnings("unused") GraalHPyContext context) {
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return HPyContextMember.KEYS;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    boolean isMemberReadable(String key) {
        return HPyContextMember.getIndex(key) != -1;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    Object readMember(@SuppressWarnings("unused") String key) {
        return new HPyContextNativePointer(0L);
    }

    /**
     * Represents a native function pointer that will be called using an appropriate JNI trampoline
     * function depending on the {@link #signature} and the {@link #debug} flag.
     */
    @ExportLibrary(InteropLibrary.class)
    static final class GraalHPyJNIFunctionPointer implements TruffleObject {
        final long pointer;
        final LLVMType signature;

        /**
         * Function pointers created through {@code HPyModule_Create} or {@code HPyType_FromSpec}
         * remembers if the context that created it was in debug mode. Depending on this flag, we
         * decide which trampolines (universal or debug) we need to use. For reference: In CPython
         * this is implicitly given by the fact that the HPy context is stored in a C global
         * variable {@code _ctx_for_trampolines}.
         */
        final boolean debug;

        GraalHPyJNIFunctionPointer(long pointer, LLVMType signature, boolean debug) {
            this.pointer = pointer;
            this.signature = signature;
            this.debug = debug;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        static final class Execute {

            @Specialization(guards = "receiver.signature == cachedSignature")
            static Object doCached(GraalHPyJNIFunctionPointer receiver, Object[] arguments,
                            @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                            @Cached("receiver.signature") LLVMType cachedSignature,
                            @Cached(parameters = "receiver.signature") GraalHPyJNIConvertArgNode convertArgNode) {
                if (receiver.debug) {
                    return callDebug(receiver, cachedSignature, arguments, interopLibrary, convertArgNode);
                }
                return callUniversal(receiver, cachedSignature, arguments, interopLibrary, convertArgNode);
            }

            /**
             * Uses the appropriate trampoline to call the native function pointer. This method
             * merges all compatible signatures to reduce the number of necessary trampolines. For
             * example:
             *
             * <pre>
             *     typedef HPy (*HPyFunc_unaryfunc)(HPyContext *ctx, HPy);
             *     typedef HPy_ssize_t (*HPyFunc_lenfunc)(HPyContext *ctx, HPy);
             * </pre>
             *
             * use the same trampoline {@link GraalHPyContext#executePrimitive2(long, long, long)}
             * since all arguments and the return value can be represented as {@code jlong}.
             */
            private static long callUniversal(GraalHPyJNIFunctionPointer receiver, LLVMType signature, Object[] arguments,
                            InteropLibrary interopLibrary, GraalHPyJNIConvertArgNode convertArgNode) {
                switch (signature) {
                    case HPyModule_init:
                        return GraalHPyContext.executePrimitive1(receiver.pointer, convertHPyContext(arguments));
                    case HPyFunc_noargs:
                    case HPyFunc_unaryfunc:
                    case HPyFunc_getiterfunc:
                    case HPyFunc_iternextfunc:
                    case HPyFunc_reprfunc:
                    case HPyFunc_lenfunc:
                    case HPyFunc_hashfunc:
                        return GraalHPyContext.executePrimitive2(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1));
                    case HPyFunc_binaryfunc:
                    case HPyFunc_o:
                    case HPyFunc_getter:
                    case HPyFunc_getattrfunc:
                    case HPyFunc_getattrofunc:
                    case HPyFunc_ssizeargfunc:
                    case HPyFunc_traverseproc:
                        return GraalHPyContext.executePrimitive3(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                    case HPyFunc_varargs:
                    case HPyFunc_ternaryfunc:
                    case HPyFunc_descrgetfunc:
                    case HPyFunc_ssizessizeargfunc:
                        return GraalHPyContext.executePrimitive4(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                    case HPyFunc_keywords:
                        return GraalHPyContext.executePrimitive5(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3), convertArgNode.execute(arguments, 4));
                    case HPyFunc_inquiry:
                        return GraalHPyContext.executeInquiry(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1));
                    case HPyFunc_ssizeobjargproc:
                        return GraalHPyContext.executeSsizeobjargproc(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1), (long) arguments[2],
                                        convertArgNode.execute(arguments, 3));
                    case HPyFunc_initproc:
                        return GraalHPyContext.executeInitproc(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), (long) arguments[3], convertArgNode.execute(arguments, 4));
                    case HPyFunc_ssizessizeobjargproc:
                        return GraalHPyContext.executeSsizesizeobjargproc(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1), (long) arguments[2],
                                        (long) arguments[3], convertArgNode.execute(arguments, 4));
                    case HPyFunc_setter:
                    case HPyFunc_setattrfunc:
                    case HPyFunc_objobjargproc:
                    case HPyFunc_descrsetfunc:
                    case HPyFunc_setattrofunc:
                        return GraalHPyContext.executeObjobjargproc(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                    case HPyFunc_freefunc:
                        GraalHPyContext.executeFreefunc(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1));
                        return 0;
                    case HPyFunc_richcmpfunc:
                        return GraalHPyContext.executeRichcomparefunc(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), (int) arguments[3]);
                    case HPyFunc_objobjproc:
                        return GraalHPyContext.executeObjobjproc(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                    case HPyFunc_getbufferproc:
                        return GraalHPyContext.executeGetbufferproc(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), (int) arguments[3]);
                    case HPyFunc_releasebufferproc:
                        GraalHPyContext.executeReleasebufferproc(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                        return 0;
                    case HPyFunc_destroyfunc:
                        GraalHPyContext.hpyCallDestroyFunc(convertPointer(arguments[0], interopLibrary), receiver.pointer);
                        return 0;
                    case HPyFunc_destructor:
                        GraalHPyContext.executeDestructor(receiver.pointer, convertHPyContext(arguments), convertArgNode.execute(arguments, 1));
                        return 0;
                }
                throw CompilerDirectives.shouldNotReachHere();
            }

            /**
             * When we are in debug mode, we need to use different trampolines for calling the HPy
             * extension functions because object parameters (that will become handles) will be
             * wrapped in debug handles ({@code DHPy}) and, vice versa, object return values need to
             * be unwrapped. This un/-wrapping is done by the trampoline via calling
             * {@code DHPy_open} and {@code DHPy_unwrap}. Method
             * {@link #callUniversal(GraalHPyJNIFunctionPointer, LLVMType, Object[], InteropLibrary, GraalHPyJNIConvertArgNode)}
             * merges signatures as much as possible, for example:
             *
             * <pre>
             *     typedef HPy (*HPyFunc_unaryfunc)(HPyContext *ctx, HPy);
             *     typedef HPy_ssize_t (*HPyFunc_lenfunc)(HPyContext *ctx, HPy);
             * </pre>
             *
             * will both use the universal trampoline
             * {@link GraalHPyContext#executePrimitive2(long, long, long)} Considering the C
             * signature of them, we can represent both, {@code HPy} and {@code HPy_ssize_t}, as
             * {@code jlong} but we need to call {@code DHPy_unwrap} on the result of
             * {@code HPyFunc_unaryfunc} while we can just pass the {@code HPy_ssize_t} value
             * through as {@code jlong}.
             */
            private static long callDebug(GraalHPyJNIFunctionPointer receiver, LLVMType signature, Object[] arguments,
                            InteropLibrary interopLibrary, GraalHPyJNIConvertArgNode convertArgNode) {
                switch (signature) {
                    case HPyModule_init:
                        return GraalHPyContext.executeDebugModuleInit(receiver.pointer, convertHPyDebugContext(arguments));
                    case HPyFunc_noargs:
                    case HPyFunc_unaryfunc:
                    case HPyFunc_getiterfunc:
                    case HPyFunc_iternextfunc:
                    case HPyFunc_reprfunc:
                        return GraalHPyContext.executeDebugUnaryFunc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1));
                    case HPyFunc_lenfunc:
                    case HPyFunc_hashfunc:
                        // HPy_ssize_t (*HPyFunc_lenfunc)(HPyContext *ctx, HPy);
                        // HPy_hash_t (*HPyFunc_hashfunc)(HPyContext *ctx, HPy);
                        return GraalHPyContext.executeDebugLenFunc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1));
                    case HPyFunc_binaryfunc:
                    case HPyFunc_o:
                    case HPyFunc_getattrofunc:
                        return GraalHPyContext.executeDebugBinaryFunc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                    case HPyFunc_getattrfunc:
                    case HPyFunc_ssizeargfunc:
                    case HPyFunc_getter:
                        // HPy (*HPyFunc_getattrfunc) (HPyContext *ctx, HPy, char *);
                        // HPy (*HPyFunc_ssizeargfunc)(HPyContext *ctx, HPy, HPy_ssize_t);
                        // HPy (*HPyFunc_getter) (HPyContext *ctx, HPy, void *);
                        return GraalHPyContext.executeDebugGetattrFunc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                    case HPyFunc_traverseproc:
                        // int (*HPyFunc_traverseproc)(void *, HPyFunc_visitproc, void *);
                        return GraalHPyContext.executePrimitive3(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                    case HPyFunc_varargs:
                        // HPy (*HPyFunc_varargs)(HPyContext *, HPy, HPy *, HPy_ssize_t);
                        return GraalHPyContext.executeDebugVarargs(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                    case HPyFunc_ternaryfunc:
                    case HPyFunc_descrgetfunc:
                        // HPy (*HPyFunc_ternaryfunc)(HPyContext *, HPy, HPy, HPy)
                        // HPy (*HPyFunc_descrgetfunc)(HPyContext *, HPy, HPy, HPy)
                        return GraalHPyContext.executeDebugTernaryFunc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                    case HPyFunc_ssizessizeargfunc:
                        // HPy (*HPyFunc_ssizessizeargfunc)(HPyContext *, HPy, HPy_ssize_t,
                        // HPy_ssize_t);
                        return GraalHPyContext.executeDebugSsizeSsizeArgFunc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                    case HPyFunc_keywords:
                        // HPy (*HPyFunc_keywords)(HPyContext *, HPy, HPy *, HPy_ssize_t , HPy)
                        return GraalHPyContext.executeDebugKeywords(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3), convertArgNode.execute(arguments, 4));
                    case HPyFunc_inquiry:
                        return GraalHPyContext.executeDebugInquiry(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1));
                    case HPyFunc_ssizeobjargproc:
                        return GraalHPyContext.executeDebugSsizeobjargproc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1), (long) arguments[2],
                                        convertArgNode.execute(arguments, 3));
                    case HPyFunc_initproc:
                        return GraalHPyContext.executeDebugInitproc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), (long) arguments[3], convertArgNode.execute(arguments, 4));
                    case HPyFunc_ssizessizeobjargproc:
                        return GraalHPyContext.executeDebugSsizesizeobjargproc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        (long) arguments[2],
                                        (long) arguments[3], convertArgNode.execute(arguments, 4));
                    case HPyFunc_setter:
                        // int (*HPyFunc_setter)(HPyContext *ctx, HPy, HPy, void *);
                        return GraalHPyContext.executeDebugSetter(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                    case HPyFunc_setattrfunc:
                        // int (*HPyFunc_setattrfunc)(HPyContext *ctx, HPy, char *, HPy);
                        return GraalHPyContext.executeDebugSetattrFunc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                    case HPyFunc_objobjargproc:
                    case HPyFunc_descrsetfunc:
                    case HPyFunc_setattrofunc:
                        return GraalHPyContext.executeDebugObjobjargproc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), convertArgNode.execute(arguments, 3));
                    case HPyFunc_freefunc:
                        // no handles involved in freefunc; we can use the universal trampoline
                        GraalHPyContext.executeFreefunc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1));
                        return 0;
                    case HPyFunc_richcmpfunc:
                        // HPy (*HPyFunc_richcmpfunc)(HPyContext *ctx, HPy, HPy, HPy_RichCmpOp)
                        return GraalHPyContext.executeDebugRichcomparefunc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), (int) arguments[3]);
                    case HPyFunc_objobjproc:
                        return GraalHPyContext.executeDebugObjobjproc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                    case HPyFunc_getbufferproc:
                        return GraalHPyContext.executeDebugGetbufferproc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2), (int) arguments[3]);
                    case HPyFunc_releasebufferproc:
                        GraalHPyContext.executeDebugReleasebufferproc(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1),
                                        convertArgNode.execute(arguments, 2));
                        return 0;
                    case HPyFunc_destroyfunc:
                        // no handles involved in destroyfunc; we can use the universal trampoline
                        GraalHPyContext.hpyCallDestroyFunc(convertPointer(arguments[0], interopLibrary), receiver.pointer);
                        return 0;
                    case HPyFunc_destructor:
                        GraalHPyContext.executeDebugDestructor(receiver.pointer, convertHPyDebugContext(arguments), convertArgNode.execute(arguments, 1));
                        return 0;
                }
                throw CompilerDirectives.shouldNotReachHere();
            }

            private static long convertHPyContext(Object[] arguments) {
                GraalHPyContext hPyContext = GraalHPyJNIConvertArgNode.getHPyContext(arguments);
                if (!hPyContext.isPointer()) {
                    hPyContext.toNative();
                }
                try {
                    return hPyContext.asPointer();
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }

            private static long convertHPyDebugContext(Object[] arguments) {
                GraalHPyContext hPyContext = GraalHPyJNIConvertArgNode.getHPyContext(arguments);
                try {
                    return hPyContext.getHPyDebugContext();
                } catch (ApiInitException e) {
                    /*
                     * That's clearly an internal error because that should already be handled when
                     * loading a module in debug mode.
                     */
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }

            private static long convertPointer(Object argument, InteropLibrary interopLibrary) {
                if (!interopLibrary.isPointer(argument)) {
                    interopLibrary.toNative(argument);
                }
                try {
                    return interopLibrary.asPointer(argument);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        boolean isPointer() {
            return true;
        }

        @ExportMessage
        long asPointer() {
            return pointer;
        }
    }

    abstract static class GraalHPyJNIConvertArgNode extends Node {

        private static final GraalHPyJNIConvertArgUncachedNode UNCACHED = new GraalHPyJNIConvertArgUncachedNode();

        public static GraalHPyJNIConvertArgNode create(@SuppressWarnings("unused") LLVMType signature) {
            return new GraalHPyJNIConvertArgCachedNode();
        }

        public static GraalHPyJNIConvertArgNode getUncached(@SuppressWarnings("unused") LLVMType signature) {
            return UNCACHED;
        }

        public abstract long execute(Object[] arguments, int i);

        protected static GraalHPyContext getHPyContext(Object[] arguments) {
            Object ctx = arguments[0];
            if (ctx instanceof GraalHPyContext) {
                return (GraalHPyContext) ctx;
            }
            throw CompilerDirectives.shouldNotReachHere("first argument is expected to the HPy context");
        }

        static final class GraalHPyJNIConvertArgCachedNode extends GraalHPyJNIConvertArgNode {
            /**
             * Carefully picked limit. Expected possible argument object types are: LLVM native
             * pointer, LLVM managed pointer, {@link GraalHPyContext}, and {@link GraalHPyHandle}.
             */
            private static final int CACHE_LIMIT = 4;

            @Child private InteropLibrary interopLibrary;
            @CompilationFinal private ConditionProfile profile;
            @Child GetHPyHandleForSingleton getHPyHandleForSingleton;

            @Override
            public long execute(Object[] arguments, int i) {
                CompilerAsserts.partialEvaluationConstant(i);
                // TODO(fa): improved cached implementation; use state bits to remember types we've
                // seen per argument
                Object value = arguments[i];

                if (value instanceof GraalHPyHandle) {
                    GraalHPyHandle handle = (GraalHPyHandle) value;
                    Object delegate = handle.getDelegate();
                    if (GraalHPyBoxing.isBoxablePrimitive(delegate)) {
                        if (delegate instanceof Integer) {
                            return GraalHPyBoxing.boxInt((Integer) delegate);
                        }
                        assert delegate instanceof Double;
                        return GraalHPyBoxing.boxDouble((Double) delegate);
                    } else {
                        return handle.getId(getHPyContext(arguments), ensureProfile(), ensureHandleForSingletonNode());
                    }
                } else if (value instanceof Long) {
                    return (long) value;
                } else {
                    if (interopLibrary == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        interopLibrary = insert(InteropLibrary.getFactory().createDispatched(CACHE_LIMIT));
                    }
                    if (!interopLibrary.isPointer(value)) {
                        interopLibrary.toNative(value);
                    }
                    try {
                        return interopLibrary.asPointer(value);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
            }

            private ConditionProfile ensureProfile() {
                if (profile == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    profile = ConditionProfile.createBinaryProfile();
                }
                return profile;
            }

            private GetHPyHandleForSingleton ensureHandleForSingletonNode() {
                if (getHPyHandleForSingleton == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getHPyHandleForSingleton = insert(GetHPyHandleForSingletonNodeGen.create());
                }
                return getHPyHandleForSingleton;
            }
        }

        static final class GraalHPyJNIConvertArgUncachedNode extends GraalHPyJNIConvertArgNode {

            @Override
            public long execute(Object[] arguments, int i) {
                Object value = arguments[i];
                if (value instanceof GraalHPyHandle) {
                    GraalHPyHandle handle = (GraalHPyHandle) value;
                    Object delegate = handle.getDelegate();
                    if (GraalHPyBoxing.isBoxablePrimitive(delegate)) {
                        if (delegate instanceof Integer) {
                            return GraalHPyBoxing.boxInt((Integer) delegate);
                        }
                        assert delegate instanceof Double;
                        return GraalHPyBoxing.boxDouble((Double) delegate);
                    } else {
                        return handle.getIdUncached(getHPyContext(arguments));
                    }
                } else if (value instanceof Long) {
                    return (long) value;
                } else {
                    InteropLibrary interopLibrary = InteropLibrary.getUncached(value);
                    if (!interopLibrary.isPointer(value)) {
                        interopLibrary.toNative(value);
                    }
                    try {
                        return interopLibrary.asPointer(value);
                    } catch (UnsupportedMessageException e) {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
            }
        }
    }
}
