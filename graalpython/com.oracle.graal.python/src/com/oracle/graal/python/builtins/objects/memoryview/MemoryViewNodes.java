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
package com.oracle.graal.python.builtins.objects.memoryview;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IndexError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.common.BufferStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.memoryview.NativeBufferLifecycleManager.NativeBufferLifecycleManagerFromSlot;
import com.oracle.graal.python.builtins.objects.mmap.MMapBuiltins;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public class MemoryViewNodes {
    static boolean isByteFormat(BufferFormat format) {
        return format == BufferFormat.UINT_8 || format == BufferFormat.INT_8 || format == BufferFormat.CHAR;
    }

    static void checkBufferBounds(Node node, PMemoryView self, PythonBufferAccessLibrary bufferLib, int offset, int length) {
        if (offset + length > bufferLib.getBufferLength(self.getBuffer())) {
            /*
             * This can only happen when the buffer gets resized while being exported. CPython makes
             * such resizing illegal in the first place, but we don't prevent it due to absence of
             * reference counting.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.raiseUncached(node, IndexError, ErrorMessages.INVALID_BUFFER_ACCESS);
        }
    }

    public abstract static class InitFlagsNode extends Node {
        public abstract int execute(int ndim, int itemsize, int[] shape, int[] strides, int[] suboffsets);

        @Specialization
        static int compute(int ndim, int itemsize, int[] shape, int[] strides, int[] suboffsets) {
            if (ndim == 0) {
                return PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN | PMemoryView.FLAG_SCALAR;
            } else if (suboffsets != null) {
                return PMemoryView.FLAG_PIL;
            } else {
                int flags = PMemoryView.FLAG_C | PMemoryView.FLAG_FORTRAN;
                int expectedStride = itemsize;
                for (int i = ndim - 1; i >= 0; i--) {
                    int dim = shape[i];
                    if (dim > 1 && strides[i] != expectedStride) {
                        flags &= ~PMemoryView.FLAG_C;
                        break;
                    }
                    expectedStride *= dim;
                }
                expectedStride = itemsize;
                for (int i = 0; i < ndim; i++) {
                    int dim = shape[i];
                    if (dim > 1 && strides[i] != expectedStride) {
                        flags &= ~PMemoryView.FLAG_FORTRAN;
                        break;
                    }
                    expectedStride *= dim;
                }
                return flags;
            }
        }
    }

    @ImportStatic(BufferFormat.class)
    public abstract static class UnpackValueNode extends PNodeWithRaise {
        public abstract Object execute(BufferFormat format, TruffleString formatStr, byte[] bytes, int offset);

        @Specialization(guards = "format != OTHER")
        static Object unpack(BufferFormat format, @SuppressWarnings("unused") TruffleString formatStr, byte[] bytes, int offset,
                        @Cached BufferStorageNodes.UnpackValueNode unpackValueNode) {
            return unpackValueNode.execute(format, bytes, offset);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object notImplemented(BufferFormat format, TruffleString formatStr, byte[] bytes, int offset) {
            throw raise(NotImplementedError, ErrorMessages.MEMORYVIEW_FORMAT_S_NOT_SUPPORTED, formatStr);
        }
    }

    @ImportStatic({BufferFormat.class, PGuards.class})
    public abstract static class PackValueNode extends PNodeWithRaise {
        @Child private IsBuiltinClassProfile isOverflowErrorProfile;

        public abstract void execute(VirtualFrame frame, BufferFormat format, TruffleString formatStr, Object object, byte[] bytes, int offset);

        @Specialization(guards = "format != OTHER")
        void pack(VirtualFrame frame, BufferFormat format, TruffleString formatStr, Object value, byte[] bytes, int offset,
                        @Cached BufferStorageNodes.PackValueNode packValueNode) {
            try {
                packValueNode.execute(frame, format, value, bytes, offset);
            } catch (PException e) {
                throw processException(e, formatStr);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        void notImplemented(BufferFormat format, TruffleString formatStr, Object object, byte[] bytes, int offset) {
            throw raise(NotImplementedError, ErrorMessages.MEMORYVIEW_FORMAT_S_NOT_SUPPORTED, formatStr);
        }

        private PException valueError(TruffleString formatStr) {
            throw raise(ValueError, ErrorMessages.MEMORYVIEW_INVALID_VALUE_FOR_FORMAT_S, formatStr);
        }

        private PException processException(PException e, TruffleString formatStr) {
            e.expect(OverflowError, getIsOverflowErrorProfile());
            throw valueError(formatStr);
        }

        private IsBuiltinClassProfile getIsOverflowErrorProfile() {
            if (isOverflowErrorProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isOverflowErrorProfile = insert(IsBuiltinClassProfile.create());
            }
            return isOverflowErrorProfile;
        }
    }

    @GenerateUncached
    abstract static class ReadBytesAtNode extends Node {
        public abstract void execute(byte[] dest, int destOffset, int len, PMemoryView self, Object ptr, int offset);

        @Specialization(guards = {"ptr != null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        static void doNativeCached(byte[] dest, int destOffset, @SuppressWarnings("unused") int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @Cached("len") int cachedLen,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < cachedLen; i++) {
                    dest[destOffset + i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static void doNativeGeneric(byte[] dest, int destOffset, int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < len; i++) {
                    dest[destOffset + i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = {"ptr == null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        void doManagedCached(byte[] dest, int destOffset, @SuppressWarnings("unused") int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Cached("len") int cachedLen) {
            checkBufferBounds(this, self, bufferLib, offset, cachedLen);
            bufferLib.readIntoByteArray(self.getBuffer(), offset, dest, destOffset, cachedLen);
        }

        @Specialization(guards = "ptr == null", replaces = "doManagedCached", limit = "3")
        void doManagedGeneric(byte[] dest, int destOffset, int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib) {
            checkBufferBounds(this, self, bufferLib, offset, len);
            bufferLib.readIntoByteArray(self.getBuffer(), offset, dest, destOffset, len);
        }
    }

    @GenerateUncached
    abstract static class WriteBytesAtNode extends Node {
        public abstract void execute(byte[] src, int srcOffset, int len, PMemoryView self, Object ptr, int offset);

        @Specialization(guards = {"ptr != null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        static void doNativeCached(byte[] src, int srcOffset, @SuppressWarnings("unused") int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @Cached("len") int cachedLen,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < cachedLen; i++) {
                    lib.writeArrayElement(ptr, offset + i, src[srcOffset + i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static void doNativeGeneric(byte[] src, int srcOffset, int len, @SuppressWarnings("unused") PMemoryView self, Object ptr, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary lib) {
            try {
                for (int i = 0; i < len; i++) {
                    lib.writeArrayElement(ptr, offset + i, src[srcOffset + i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = {"ptr == null", "cachedLen == len", "cachedLen <= 8"}, limit = "4")
        @ExplodeLoop
        void doManagedCached(byte[] src, int srcOffset, @SuppressWarnings("unused") int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Cached("len") int cachedLen) {
            checkBufferBounds(this, self, bufferLib, offset, cachedLen);
            bufferLib.writeFromByteArray(self.getBuffer(), offset, src, srcOffset, cachedLen);
        }

        @Specialization(guards = "ptr == null", replaces = "doManagedCached", limit = "3")
        void doManagedGeneric(byte[] src, int srcOffset, int len, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib) {
            checkBufferBounds(this, self, bufferLib, offset, len);
            bufferLib.writeFromByteArray(self.getBuffer(), offset, src, srcOffset, len);
        }
    }

    abstract static class ReadItemAtNode extends Node {
        public abstract Object execute(VirtualFrame frame, PMemoryView self, Object ptr, int offset);

        @Specialization(guards = {"ptr != null", "cachedItemSize == self.getItemSize()", "cachedItemSize <= 8"}, limit = "4")
        @ExplodeLoop
        static Object doNativeCached(PMemoryView self, Object ptr, int offset,
                        @Cached("self.getItemSize()") int cachedItemSize,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached UnpackValueNode unpackValueNode) {
            byte[] bytes = new byte[cachedItemSize];
            try {
                for (int i = 0; i < cachedItemSize; i++) {
                    bytes[i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
            return unpackValueNode.execute(self.getFormat(), self.getFormatString(), bytes, 0);
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static Object doNativeGeneric(PMemoryView self, Object ptr, int offset,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached UnpackValueNode unpackValueNode) {
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            try {
                for (int i = 0; i < itemSize; i++) {
                    bytes[i] = (byte) lib.readArrayElement(ptr, offset + i);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
            return unpackValueNode.execute(self.getFormat(), self.getFormatString(), bytes, 0);
        }

        @Specialization(guards = {"ptr == null", "cachedItemSize == self.getItemSize()", "cachedItemSize <= 8", "!isPMMap(self.getOwner())"}, limit = "4")
        @ExplodeLoop
        Object doManagedCached(PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Cached("self.getItemSize()") int cachedItemSize,
                        @Cached UnpackValueNode unpackValueNode) {
            byte[] bytes = new byte[cachedItemSize];
            checkBufferBounds(this, self, bufferLib, offset, cachedItemSize);
            bufferLib.readIntoByteArray(self.getBuffer(), offset, bytes, 0, cachedItemSize);
            return unpackValueNode.execute(self.getFormat(), self.getFormatString(), bytes, 0);
        }

        @Specialization(guards = {"ptr == null", "!isPMMap(self.getOwner())"}, replaces = "doManagedCached", limit = "3")
        Object doManagedGeneric(PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Cached UnpackValueNode unpackValueNode) {
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            checkBufferBounds(this, self, bufferLib, offset, itemSize);
            bufferLib.readIntoByteArray(self.getBuffer(), offset, bytes, 0, itemSize);
            return unpackValueNode.execute(self.getFormat(), self.getFormatString(), bytes, 0);
        }

        @Specialization(guards = {"ptr == null", "isPMMap(self.getOwner())"})
        static Object doManagedPMMap(VirtualFrame frame, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset,
                        @Cached MMapBuiltins.GetItemNode getItem,
                        @Cached("createCoerce()") CastToByteNode castToByteNode,
                        @Cached UnpackValueNode unpackValueNode) {
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            for (int i = 0; i < itemSize; i++) {
                bytes[i] = castToByteNode.execute(frame, getItem.execute(frame, self.getOwner(), offset + i));
            }
            Object ret = unpackValueNode.execute(self.getFormat(), self.getFormatString(), bytes, 0);
            return ret;
        }

        protected boolean isPMMap(Object owner) {
            return owner instanceof PMMap;
        }

        protected static CastToByteNode createCoerce() {
            return CastToByteNode.create(true);
        }
    }

    abstract static class WriteItemAtNode extends Node {
        public abstract void execute(VirtualFrame frame, PMemoryView self, Object ptr, int offset, Object object);

        @Specialization(guards = {"ptr != null", "cachedItemSize == self.getItemSize()", "cachedItemSize <= 8"}, limit = "4")
        @ExplodeLoop
        static void doNativeCached(VirtualFrame frame, PMemoryView self, Object ptr, int offset, Object object,
                        @Cached("self.getItemSize()") int cachedItemSize,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached PackValueNode packValueNode) {
            byte[] bytes = new byte[cachedItemSize];
            packValueNode.execute(frame, self.getFormat(), self.getFormatString(), object, bytes, 0);
            try {
                for (int i = 0; i < cachedItemSize; i++) {
                    lib.writeArrayElement(ptr, offset + i, bytes[i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = "ptr != null", replaces = "doNativeCached")
        static void doNativeGeneric(VirtualFrame frame, PMemoryView self, Object ptr, int offset, Object object,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached PackValueNode packValueNode) {
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            packValueNode.execute(frame, self.getFormat(), self.getFormatString(), object, bytes, 0);
            try {
                for (int i = 0; i < itemSize; i++) {
                    lib.writeArrayElement(ptr, offset + i, bytes[i]);
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
                throw CompilerDirectives.shouldNotReachHere("native buffer read failed");
            }
        }

        @Specialization(guards = {"ptr == null", "cachedItemSize == self.getItemSize()", "cachedItemSize <= 8", "!isPMMap(self.getOwner())"}, limit = "4")
        @ExplodeLoop
        void doManagedCached(VirtualFrame frame, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset, Object object,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Cached("self.getItemSize()") int cachedItemSize,
                        @Cached PackValueNode packValueNode) {
            byte[] bytes = new byte[cachedItemSize];
            packValueNode.execute(frame, self.getFormat(), self.getFormatString(), object, bytes, 0);
            checkBufferBounds(this, self, bufferLib, offset, cachedItemSize);
            bufferLib.writeFromByteArray(self.getBuffer(), offset, bytes, 0, cachedItemSize);
        }

        @Specialization(guards = {"ptr == null", "!isPMMap(self.getOwner())"}, replaces = "doManagedCached", limit = "3")
        void doManagedGeneric(VirtualFrame frame, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset, Object object,
                        @CachedLibrary("self.getBuffer()") PythonBufferAccessLibrary bufferLib,
                        @Cached PackValueNode packValueNode) {
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            packValueNode.execute(frame, self.getFormat(), self.getFormatString(), object, bytes, 0);
            checkBufferBounds(this, self, bufferLib, offset, itemSize);
            bufferLib.writeFromByteArray(self.getBuffer(), offset, bytes, 0, itemSize);
        }

        @Specialization(guards = {"ptr == null", "isPMMap(self.getOwner())"})
        static void doPMMapGeneric(VirtualFrame frame, PMemoryView self, @SuppressWarnings("unused") Object ptr, int offset, Object object,
                        @Cached PackValueNode packValueNode,
                        @Cached MMapBuiltins.SetItemNode setItemNode) {
            int itemSize = self.getItemSize();
            byte[] bytes = new byte[itemSize];
            packValueNode.execute(frame, self.getFormat(), self.getFormatString(), object, bytes, 0);
            for (int i = 0; i < itemSize; i++) {
                setItemNode.execute(frame, self.getOwner(), offset + i, bytes[i]);
            }
        }

        protected boolean isPMMap(Object owner) {
            return owner instanceof PMMap;
        }
    }

    @ValueType
    static class MemoryPointer {
        public Object ptr;
        public int offset;

        public MemoryPointer(Object ptr, int offset) {
            this.ptr = ptr;
            this.offset = offset;
        }
    }

    @ImportStatic(PGuards.class)
    abstract static class PointerLookupNode extends PNodeWithRaise {
        @Child private CExtNodes.PCallCapiFunction callCapiFunction;
        @Child private PyIndexCheckNode indexCheckNode;
        @Child private PyNumberAsSizeNode asSizeNode;
        @CompilationFinal private ConditionProfile hasSuboffsetsProfile;

        // index can be a tuple, int or int-convertible
        public abstract MemoryPointer execute(VirtualFrame frame, PMemoryView self, Object index);

        public abstract MemoryPointer execute(VirtualFrame frame, PMemoryView self, int index);

        private void lookupDimension(PMemoryView self, MemoryPointer ptr, int dim, int initialIndex) {
            int index = initialIndex;
            int[] shape = self.getBufferShape();
            int nitems = shape[dim];
            if (index < 0) {
                index += nitems;
            }
            if (index < 0 || index >= nitems) {
                throw raise(IndexError, ErrorMessages.INDEX_OUT_OF_BOUNDS_ON_DIMENSION_D, dim + 1);
            }

            ptr.offset += self.getBufferStrides()[dim] * index;

            int[] suboffsets = self.getBufferSuboffsets();
            if (getHasSuboffsetsProfile().profile(suboffsets != null) && suboffsets[dim] >= 0) {
                // The length may be out of bounds, but sulong shouldn't care if we don't
                // access the out-of-bound part
                ptr.ptr = getCallCapiFunction().call(NativeCAPISymbol.FUN_TRUFFLE_ADD_SUBOFFSET, ptr.ptr, ptr.offset, suboffsets[dim], self.getLength());
                ptr.offset = 0;
            }
        }

        @Specialization(guards = "self.getDimensions() == 1")
        MemoryPointer resolveInt(PMemoryView self, int index) {
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            lookupDimension(self, ptr, 0, index);
            return ptr;
        }

        @Specialization(guards = "self.getDimensions() != 1")
        MemoryPointer resolveIntError(PMemoryView self, @SuppressWarnings("unused") int index) {
            if (self.getDimensions() == 0) {
                throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
            }
            // CPython doesn't implement this either, as of 3.8
            throw raise(NotImplementedError, ErrorMessages.MULTI_DIMENSIONAL_SUB_VIEWS_NOT_IMPLEMENTED);
        }

        @Specialization(guards = {"cachedDimensions == self.getDimensions()", "cachedDimensions <= 8"})
        @ExplodeLoop
        MemoryPointer resolveTupleCached(VirtualFrame frame, PMemoryView self, PTuple indices,
                        @Cached("self.getDimensions()") int cachedDimensions,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(indices);
            checkTupleLength(indicesStorage, cachedDimensions);
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            for (int dim = 0; dim < cachedDimensions; dim++) {
                Object indexObj = getItemNode.execute(indicesStorage, dim);
                int index = convertIndex(frame, indexObj);
                lookupDimension(self, ptr, dim, index);
            }
            return ptr;
        }

        @Specialization(replaces = "resolveTupleCached")
        MemoryPointer resolveTupleGeneric(VirtualFrame frame, PMemoryView self, PTuple indices,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode) {
            SequenceStorage indicesStorage = getSequenceStorageNode.execute(indices);
            int ndim = self.getDimensions();
            checkTupleLength(indicesStorage, ndim);
            MemoryPointer ptr = new MemoryPointer(self.getBufferPointer(), self.getOffset());
            for (int dim = 0; dim < ndim; dim++) {
                Object indexObj = getItemNode.execute(indicesStorage, dim);
                int index = convertIndex(frame, indexObj);
                lookupDimension(self, ptr, dim, index);
            }
            return ptr;
        }

        @Specialization(guards = "!isPTuple(indexObj)")
        MemoryPointer resolveIntObj(VirtualFrame frame, PMemoryView self, Object indexObj) {
            final int index = convertIndex(frame, indexObj);
            if (self.getDimensions() == 1) {
                return resolveInt(self, index);
            } else {
                return resolveIntError(self, index);
            }
        }

        private void checkTupleLength(SequenceStorage indicesStorage, int ndim) {
            int length = indicesStorage.length();
            if (length == ndim) {
                return;
            }
            // Error cases
            if (ndim == 0) {
                throw raise(TypeError, ErrorMessages.INVALID_INDEXING_OF_0_DIM_MEMORY);
            } else if (length > ndim) {
                throw raise(TypeError, ErrorMessages.CANNOT_INDEX_D_DIMENSION_VIEW_WITH_D, ndim, length);
            } else {
                // CPython doesn't implement this either, as of 3.8
                throw raise(NotImplementedError, ErrorMessages.SUB_VIEWS_NOT_IMPLEMENTED);
            }
        }

        private int convertIndex(VirtualFrame frame, Object indexObj) {
            if (!getIndexCheckNode().execute(indexObj)) {
                throw raise(TypeError, ErrorMessages.MEMORYVIEW_INVALID_SLICE_KEY);
            }
            return getAsSizeNode().executeExact(frame, indexObj, IndexError);
        }

        private PyIndexCheckNode getIndexCheckNode() {
            if (indexCheckNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                indexCheckNode = insert(PyIndexCheckNode.create());
            }
            return indexCheckNode;
        }

        private PyNumberAsSizeNode getAsSizeNode() {
            if (asSizeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asSizeNode = insert(PyNumberAsSizeNode.create());
            }
            return asSizeNode;
        }

        private ConditionProfile getHasSuboffsetsProfile() {
            if (hasSuboffsetsProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasSuboffsetsProfile = ConditionProfile.create();
            }
            return hasSuboffsetsProfile;
        }

        private CExtNodes.PCallCapiFunction getCallCapiFunction() {
            if (callCapiFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callCapiFunction = insert(CExtNodes.PCallCapiFunction.create());
            }
            return callCapiFunction;
        }
    }

    @GenerateUncached
    public abstract static class ToJavaBytesNode extends Node {
        public abstract byte[] execute(PMemoryView self);

        @Specialization(guards = {"self.getDimensions() == cachedDimensions", "cachedDimensions < 8"})
        byte[] tobytesCached(PMemoryView self,
                        @Cached("self.getDimensions()") int cachedDimensions,
                        @Cached ReadBytesAtNode readBytesAtNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(raiseNode);
            byte[] bytes = new byte[self.getLength()];
            if (cachedDimensions == 0) {
                readBytesAtNode.execute(bytes, 0, self.getItemSize(), self, self.getBufferPointer(), self.getOffset());
            } else {
                convert(bytes, self, cachedDimensions, readBytesAtNode, callCapiFunction);
            }
            return bytes;
        }

        @Specialization(replaces = "tobytesCached")
        byte[] tobytesGeneric(PMemoryView self,
                        @Cached ReadBytesAtNode readBytesAtNode,
                        @Cached CExtNodes.PCallCapiFunction callCapiFunction,
                        @Cached PRaiseNode raiseNode) {
            self.checkReleased(raiseNode);
            byte[] bytes = new byte[self.getLength()];
            if (self.getDimensions() == 0) {
                readBytesAtNode.execute(bytes, 0, self.getItemSize(), self, self.getBufferPointer(), self.getOffset());
            } else {
                convertBoundary(bytes, self, self.getDimensions(), readBytesAtNode, callCapiFunction);
            }
            return bytes;
        }

        @TruffleBoundary
        private void convertBoundary(byte[] dest, PMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            convert(dest, self, ndim, readBytesAtNode, callCapiFunction);
        }

        protected void convert(byte[] dest, PMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            recursive(dest, 0, self, 0, ndim, self.getBufferPointer(), self.getOffset(), readBytesAtNode, callCapiFunction);
        }

        private static int recursive(byte[] dest, int initialDestOffset, PMemoryView self, int dim, int ndim, Object ptr, int initialOffset, ReadBytesAtNode readBytesAtNode,
                        CExtNodes.PCallCapiFunction callCapiFunction) {
            int offset = initialOffset;
            int destOffset = initialDestOffset;
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = callCapiFunction.call(NativeCAPISymbol.FUN_TRUFFLE_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim], self.getLength());
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    readBytesAtNode.execute(dest, destOffset, self.getItemSize(), self, xptr, xoffset);
                    destOffset += self.getItemSize();
                } else {
                    destOffset = recursive(dest, destOffset, self, dim + 1, ndim, xptr, xoffset, readBytesAtNode, callCapiFunction);
                }
                offset += self.getBufferStrides()[dim];
            }
            return destOffset;
        }

        public static ToJavaBytesNode create() {
            return MemoryViewNodesFactory.ToJavaBytesNodeGen.create();
        }
    }

    @GenerateUncached
    public abstract static class ToJavaBytesFortranOrderNode extends ToJavaBytesNode {
        @Override
        protected void convert(byte[] dest, PMemoryView self, int ndim, ReadBytesAtNode readBytesAtNode, CExtNodes.PCallCapiFunction callCapiFunction) {
            recursive(dest, 0, self.getItemSize(), self, 0, ndim, self.getBufferPointer(), self.getOffset(), readBytesAtNode, callCapiFunction);
        }

        private static void recursive(byte[] dest, int initialDestOffset, int destStride, PMemoryView self, int dim, int ndim, Object ptr, int initialOffset, ReadBytesAtNode readBytesAtNode,
                        CExtNodes.PCallCapiFunction callCapiFunction) {
            int offset = initialOffset;
            int destOffset = initialDestOffset;
            for (int i = 0; i < self.getBufferShape()[dim]; i++) {
                Object xptr = ptr;
                int xoffset = offset;
                if (self.getBufferSuboffsets() != null && self.getBufferSuboffsets()[dim] >= 0) {
                    xptr = callCapiFunction.call(NativeCAPISymbol.FUN_TRUFFLE_ADD_SUBOFFSET, ptr, offset, self.getBufferSuboffsets()[dim], self.getLength());
                    xoffset = 0;
                }
                if (dim == ndim - 1) {
                    readBytesAtNode.execute(dest, destOffset, self.getItemSize(), self, xptr, xoffset);
                } else {
                    recursive(dest, destOffset, destStride * self.getBufferShape()[dim], self, dim + 1, ndim, xptr, xoffset, readBytesAtNode, callCapiFunction);
                }
                destOffset += destStride;
                offset += self.getBufferStrides()[dim];
            }
        }

        public static ToJavaBytesFortranOrderNode create() {
            return MemoryViewNodesFactory.ToJavaBytesFortranOrderNodeGen.create();
        }
    }

    public abstract static class ReleaseNode extends PNodeWithRaiseAndIndirectCall {

        public final void execute(PMemoryView self) {
            execute(null, self);
        }

        public abstract void execute(VirtualFrame frame, PMemoryView self);

        @Specialization(guards = "self.getReference() == null")
        static void releaseSimple(PMemoryView self,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            self.checkExports(raiseNode);
            self.setReleased();
        }

        @Specialization(guards = {"self.getReference() != null"})
        void releaseNative(VirtualFrame frame, PMemoryView self,
                        @Cached ReleaseBufferNode releaseNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            self.checkExports(raiseNode);
            if (self.checkShouldReleaseBuffer()) {
                releaseNode.execute(frame, this, self.getLifecycleManager());
            }
            self.setReleased();
        }
    }

    @GenerateUncached
    public abstract static class ReleaseBufferNode extends Node {

        public abstract void execute(BufferLifecycleManager buffer);

        public final void execute(VirtualFrame frame, PNodeWithRaiseAndIndirectCall caller, BufferLifecycleManager buffer) {
            Object state = IndirectCallContext.enter(frame, caller);
            try {
                execute(buffer);
            } finally {
                IndirectCallContext.exit(frame, caller, state);
            }
        }

        @Specialization
        static void doCApiCached(NativeBufferLifecycleManager.NativeBufferLifecycleManagerFromType buffer,
                        @Cached PCallCapiFunction callReleaseNode) {
            callReleaseNode.call(NativeCAPISymbol.FUN_PY_TRUFFLE_RELEASE_BUFFER, buffer.bufferStructPointer);
        }

        @Specialization
        static void doCExtBuffer(NativeBufferLifecycleManagerFromSlot buffer,
                        @Cached CallNode callNode) {
            callNode.execute(buffer.releaseFunction, buffer.self, buffer.buffer);
        }

        @Fallback
        static void doManaged(@SuppressWarnings("unused") BufferLifecycleManager buffer) {
            // nothing to do
        }

        public static ReleaseBufferNode getUncached() {
            return MemoryViewNodesFactory.ReleaseBufferNodeGen.getUncached();
        }
    }
}
