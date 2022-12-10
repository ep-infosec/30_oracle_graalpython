/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.zlib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ZlibDecompress;
import static com.oracle.graal.python.builtins.modules.zlib.ZLibModuleBuiltins.DEF_BUF_SIZE;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_OK;
import static com.oracle.graal.python.builtins.modules.zlib.ZlibNodes.Z_STREAM_ERROR;
import static com.oracle.graal.python.nodes.ErrorMessages.ERROR_D_S_S;
import static com.oracle.graal.python.nodes.ErrorMessages.INCONSISTENT_STREAM_STATE;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_BE_GREATER_THAN_ZERO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___COPY__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ZLibError;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.NFIZlibSupport;
import com.oracle.graal.python.runtime.NativeLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = ZlibDecompress)
public class ZlibDecompressBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZlibDecompressBuiltinsFactory.getFactories();
    }

    @Builtin(name = "decompress", minNumOfPositionalArgs = 1, parameterNames = {"$self", "", "max_length"})
    @ArgumentClinic(name = "max_length", conversionClass = ZLibModuleBuiltins.ExpectIntNode.class, defaultValue = "0", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class DecompressNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZlibDecompressBuiltinsClinicProviders.DecompressNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"maxLength >= 0", "self.isInitialized()"})
        PBytes doNativeBytes(ZLibCompObject.NativeZlibCompObject self, PBytesLike data, int maxLength,
                        @Cached SequenceStorageNodes.GetInternalByteArrayNode toBytes,
                        @Shared("dobj") @Cached ZlibNodes.ZlibNativeDecompressObj decompressObj) {
            synchronized (self) {
                assert self.isInitialized();
                byte[] bytes = toBytes.execute(data.getSequenceStorage());
                int len = data.getSequenceStorage().length();
                return factory().createBytes(decompressObj.execute(self, PythonContext.get(this), bytes, len, maxLength));
            }
        }

        @Specialization(guards = {"maxLength >= 0", "self.isInitialized()", "!isBytes(data)"})
        PBytes doNativeObject(VirtualFrame frame, ZLibCompObject.NativeZlibCompObject self, Object data, int maxLength,
                        @Shared("bb") @Cached BytesNodes.ToBytesNode toBytes,
                        @Shared("dobj") @Cached ZlibNodes.ZlibNativeDecompressObj decompressObj) {
            synchronized (self) {
                assert self.isInitialized();
                byte[] bytes = toBytes.execute(frame, data);
                int len = bytes.length;
                return factory().createBytes(decompressObj.execute(self, PythonContext.get(this), bytes, len, maxLength));
            }
        }

        @Specialization(guards = {"maxLength >= 0", "self.isInitialized()"})
        PBytes doit(VirtualFrame frame, ZLibCompObject.JavaZlibCompObject self, Object data, int maxLength,
                        @Cached ZlibNodes.JavaDecompressNode decompressNode,
                        @Shared("bb") @Cached BytesNodes.ToBytesNode toBytes) {
            byte[] bytes = toBytes.execute(frame, data);
            byte[] res = decompressNode.execute(self, bytes, maxLength, DEF_BUF_SIZE, factory());
            return factory().createBytes(res);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"maxLength >= 0", "!self.isInitialized()"})
        PBytes error(ZLibCompObject self, Object data, int maxLength) {
            throw raise(ZLibError, ERROR_D_S_S, Z_STREAM_ERROR, "while decompressing data", "inconsistent stream state");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "maxLength < 0")
        PNone err(ZLibCompObject self, Object data, int maxLength) {
            throw raise(ValueError, S_MUST_BE_GREATER_THAN_ZERO, "max_length");
        }
    }

    abstract static class BaseCopyNode extends PNodeWithContext {

        public abstract Object execute(ZLibCompObject self, PythonContext ctxt, PythonObjectFactory factory);

        @Specialization(guards = "self.isInitialized()")
        static Object doNative(ZLibCompObject.NativeZlibCompObject self, PythonContext ctxt, PythonObjectFactory factory,
                        @Cached NativeLibrary.InvokeNativeFunction createCompObject,
                        @Cached NativeLibrary.InvokeNativeFunction decompressObjCopy,
                        @Cached NativeLibrary.InvokeNativeFunction deallocateStream,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            synchronized (self) {
                assert self.isInitialized();
                NFIZlibSupport zlibSupport = ctxt.getNFIZlibSupport();
                Object zstNewCopy = zlibSupport.createCompObject(createCompObject);
                int err = zlibSupport.decompressObjCopy(self.getZst(), zstNewCopy, decompressObjCopy);
                if (err != Z_OK) {
                    zlibSupport.deallocateStream(zstNewCopy, deallocateStream);
                    errorHandling.execute(self.getZst(), err, zlibSupport, false);
                }
                ZLibCompObject copy = factory.createNativeZLibCompObject(ZlibDecompress, zstNewCopy, zlibSupport);
                copy.setEof(self.isEof());
                return copy;
            }
        }

        @Specialization(guards = {"self.isInitialized()", "self.canCopy()"})
        Object doJava(ZLibCompObject.JavaZlibCompObject self, @SuppressWarnings("unused") PythonContext ctxt, PythonObjectFactory factory) {
            return self.copyDecompressObj(factory, this);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isInitialized()", "!self.canCopy()"})
        static PNone error(ZLibCompObject.JavaZlibCompObject self, PythonContext ctxt, PythonObjectFactory factory,
                        @Cached.Shared("r") @Cached PRaiseNode raise) {
            throw raise.raise(NotImplementedError, toTruffleStringUncached("JDK based zlib doesn't support copying"));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!self.isInitialized()")
        static PNone error(ZLibCompObject self, PythonContext ctxt, PythonObjectFactory factory,
                        @Cached.Shared("r") @Cached PRaiseNode raise) {
            throw raise.raise(ValueError, INCONSISTENT_STREAM_STATE);
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doit(ZLibCompObject self,
                        @Cached BaseCopyNode copyNode) {
            return copyNode.execute(self, PythonContext.get(this), factory());
        }
    }

    @Builtin(name = J___COPY__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UndescoreCopyNode extends CopyNode {
    }

    @Builtin(name = "__deepcopy__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DeepCopyNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doit(ZLibCompObject self, @SuppressWarnings("unused") Object memo,
                        @Cached BaseCopyNode copyNode) {
            return copyNode.execute(self, PythonContext.get(this), factory());
        }
    }

    @Builtin(name = "flush", minNumOfPositionalArgs = 1, parameterNames = {"$self", "length"})
    @ArgumentClinic(name = "length", conversionClass = ZLibModuleBuiltins.ExpectIntNode.class, defaultValue = "ZLibModuleBuiltins.DEF_BUF_SIZE", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class FlushNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ZlibDecompressBuiltinsClinicProviders.FlushNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"length > 0", "!self.isEof()", "self.isInitialized()"})
        PBytes doit(ZLibCompObject.NativeZlibCompObject self, int length,
                        @Cached NativeLibrary.InvokeNativeFunction decompressObjFlush,
                        @Cached ZlibNodes.GetNativeBufferNode getBuffer,
                        @Cached NativeLibrary.InvokeNativeFunction getIsInitialised,
                        @Cached ZlibNodes.NativeDeallocation processDeallocation,
                        @Cached ZlibNodes.ZlibNativeErrorHandling errorHandling) {
            synchronized (self) {
                PythonContext ctxt = PythonContext.get(this);
                assert self.isInitialized();
                NFIZlibSupport zlibSupport = ctxt.getNFIZlibSupport();
                int err = zlibSupport.decompressObjFlush(self.getZst(), length, decompressObjFlush);
                if (err != Z_OK) {
                    errorHandling.execute(self.getZst(), err, zlibSupport, false);
                }
                byte[] resultArray = getBuffer.getOutputBuffer(self.getZst(), ctxt);
                if (zlibSupport.getIsInitialised(self.getZst(), getIsInitialised) == 0) {
                    processDeallocation.execute(self, ctxt, factory(), false);
                }
                return factory().createBytes(resultArray);
            }
        }

        @Specialization(guards = {"length > 0", "!self.isEof()", "self.isInitialized()"})
        PBytes doit(ZLibCompObject.JavaZlibCompObject self, int length,
                        @Cached ZlibNodes.JavaDecompressNode decompressNode,
                        @Cached BytesNodes.ToBytesNode toBytes) {
            byte[] res;
            try {
                res = decompressNode.execute(self, toBytes.execute(self.getUnconsumedTail()), 0, length, factory());
            } catch (PException e) {
                // CPython ignores errors here
                res = PythonUtils.EMPTY_BYTE_ARRAY;
            }
            self.setUninitialized();
            return factory().createBytes(res);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"length > 0", "self.isEof() || !self.isInitialized()"})
        PBytes empty(ZLibCompObject.JavaZlibCompObject self, int length) {
            self.setUninitialized();
            return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"length > 0", "self.isEof() || !self.isInitialized()"})
        PBytes empty(ZLibCompObject.NativeZlibCompObject self, int length) {
            return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "length <= 0")
        PBytes error(ZLibCompObject self, int length) {
            throw raise(ValueError, S_MUST_BE_GREATER_THAN_ZERO, "length");
        }
    }

    @Builtin(name = "unused_data", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class UnusedDataNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.isInitialized()")
        PBytes doit(ZLibCompObject.NativeZlibCompObject self,
                        @Cached ZlibNodes.GetNativeBufferNode getBuffer) {
            synchronized (self) {
                assert self.isInitialized();
                return factory().createBytes(getBuffer.getUnusedDataBuffer(self.getZst(), PythonContext.get(this)));
            }
        }

        @Specialization(guards = "!self.isInitialized()")
        static PBytes doeof(ZLibCompObject.NativeZlibCompObject self) {
            return self.getUnusedData();
        }

        @Specialization
        static PBytes doit(ZLibCompObject.JavaZlibCompObject self) {
            return self.getUnusedData();
        }
    }

    @Builtin(name = "unconsumed_tail", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class UnconsumedTailNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.isInitialized()")
        PBytes doit(ZLibCompObject.NativeZlibCompObject self,
                        @Cached ZlibNodes.GetNativeBufferNode getBuffer) {
            synchronized (self) {
                assert self.isInitialized();
                return factory().createBytes(getBuffer.getUnconsumedTailBuffer(self.getZst(), PythonContext.get(this)));
            }
        }

        @Specialization(guards = "!self.isInitialized()")
        static PBytes doeof(ZLibCompObject.NativeZlibCompObject self) {
            return self.getUnconsumedTail();
        }

        @Specialization
        static PBytes doit(ZLibCompObject.JavaZlibCompObject self) {
            return self.getUnconsumedTail();
        }
    }

    @Builtin(name = "eof", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EOFNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.isEof() || !self.isInitialized()")
        static boolean doit(ZLibCompObject.NativeZlibCompObject self) {
            return self.isEof();
        }

        @Specialization(guards = {"!self.isEof()", "self.isInitialized()"})
        boolean getit(ZLibCompObject.NativeZlibCompObject self,
                        @Cached NativeLibrary.InvokeNativeFunction getEOF) {
            synchronized (self) {
                assert self.isInitialized();
                NFIZlibSupport zlibSupport = PythonContext.get(this).getNFIZlibSupport();
                self.setEof(zlibSupport.getEOF(self.getZst(), getEOF) == 1);
                return self.isEof();
            }
        }

        @Specialization
        static boolean doit(ZLibCompObject.JavaZlibCompObject self) {
            return self.isEof();
        }
    }
}
