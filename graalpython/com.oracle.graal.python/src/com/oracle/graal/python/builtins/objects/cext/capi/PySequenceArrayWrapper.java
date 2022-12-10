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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_BYTE_ARRAY_TYPE_ID;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_GET_PTR_ARRAY_TYPE_ID;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_NATIVE_HANDLE_FOR_ARRAY;

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropSubscriptAssignNode;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.IsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToJavaStealingNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ListGeneralizationNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.NoGeneralizationNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.EmptySequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wraps a sequence object (like a list) such that it behaves like a bare C array.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public final class PySequenceArrayWrapper extends PythonNativeWrapper {

    /** Number of bytes that constitute a single element. */
    private final int elementAccessSize;

    public PySequenceArrayWrapper(Object delegate, int elementAccessSize) {
        super(delegate);
        this.elementAccessSize = elementAccessSize;
    }

    public int getElementAccessSize() {
        return elementAccessSize;
    }

    @Override
    public int hashCode() {
        CompilerAsserts.neverPartOfCompilation();
        final int prime = 31;
        int result = 1;
        result = prime * result + PythonNativeWrapperLibrary.getUncached().getDelegate(this).hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        // n.b.: (tfel) This is hopefully fine here, since if we get to this
        // code path, we don't speculate that either of those objects is
        // constant anymore, so any caching on them won't happen anyway
        PythonNativeWrapperLibrary lib = PythonNativeWrapperLibrary.getUncached();
        return lib.getDelegate(this) == lib.getDelegate(obj);
    }

    @ExportMessage
    final long getArraySize(
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Shared("sizeNode") @Cached PyObjectSizeNode sizeNode) {
        return sizeNode.execute(null, lib.getDelegate(this));
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    final Object readArrayElement(long index,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Exclusive @Cached ReadArrayItemNode readArrayItemNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return readArrayItemNode.execute(lib.getDelegate(this), index);
        } finally {
            gil.release(mustRelease);
        }
    }

    static int getCallSiteInlineCacheMaxDepth() {
        return PythonOptions.getCallSiteInlineCacheMaxDepth();
    }

    @ExportMessage
    final boolean isArrayElementReadable(long identifier,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Shared("sizeNode") @Cached PyObjectSizeNode sizeNode) {
        // also include the implicit null-terminator
        return 0 <= identifier && identifier <= getArraySize(lib, sizeNode);
    }

    @ImportStatic({SpecialMethodNames.class, PySequenceArrayWrapper.class})
    @TypeSystemReference(PythonTypes.class)
    @GenerateUncached
    abstract static class ReadArrayItemNode extends Node {

        public abstract Object execute(Object arrayObject, Object idx);

        @Specialization(guards = "idx == cachedIdx", limit = "3")
        static Object doTupleCachedIdx(PTuple tuple, @SuppressWarnings("unused") long idx,
                        @Cached("idx") long cachedIdx,
                        @Exclusive @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Exclusive @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getItemNode.execute(tuple.getSequenceStorage(), cachedIdx));
        }

        @Specialization(replaces = "doTupleCachedIdx")
        static Object doTuple(PTuple tuple, long idx,
                        @Exclusive @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getItemNode.execute(tuple.getSequenceStorage(), idx));
        }

        @Specialization(guards = "idx == cachedIdx", limit = "3")
        static Object doListCachedIdx(PList list, @SuppressWarnings("unused") long idx,
                        @Cached("idx") long cachedIdx,
                        @Exclusive @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Exclusive @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getItemNode.execute(list.getSequenceStorage(), cachedIdx));
        }

        @Specialization(replaces = "doListCachedIdx")
        static Object doList(PList list, long idx,
                        @Exclusive @Cached SequenceStorageNodes.GetItemDynamicNode getItemNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(getItemNode.execute(list.getSequenceStorage(), idx));
        }

        /**
         * The sequence array wrapper of a {@code bytes} object represents {@code ob_sval}. We type
         * it as {@code uint8_t*} and therefore we get a byte index. However, we return
         * {@code uint64_t} since we do not know how many bytes are requested.
         */
        @Specialization(limit = "2")
        @ExplodeLoop
        static byte doBytesI64(PBytesLike bytesLike, long byteIdx,
                        @CachedLibrary("bytesLike") PythonBufferAccessLibrary bufferLib) {
            int len = bufferLib.getBufferLength(bytesLike);
            // simulate sentinel value
            if (byteIdx >= len) {
                assert byteIdx < len + 8;
                return 0;
            }
            return bufferLib.readByte(bytesLike, (int) byteIdx);
        }

        @Specialization
        @ExplodeLoop
        static long doPMmapI64(PMMap mmap, long byteIdx,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupGetItemNode,
                        @Exclusive @Cached CallNode callGetItemNode,
                        @Cached PyLongAsLongNode asLongNode) {

            long len = mmap.getLength();
            Object attrGetItem = lookupGetItemNode.execute(mmap, SpecialMethodNames.T___GETITEM__);

            int i = (int) byteIdx;
            long result = 0;
            for (int j = 0; j < Long.BYTES; j++) {
                if (i + j < len) {
                    long shift = Byte.SIZE * j;
                    long mask = 0xFFL << shift;
                    result |= (asLongNode.execute(null, callGetItemNode.execute(attrGetItem, mmap, byteIdx)) << shift) & mask;
                }
            }
            return result;
        }

        @Specialization(guards = {"!isTuple(object)", "!isList(object)", "!hasByteArrayContent(object)"})
        static Object doGeneric(Object object, long idx,
                        @Exclusive @Cached LookupInheritedAttributeNode.Dynamic lookupGetItemNode,
                        @Exclusive @Cached CallNode callGetItemNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            Object attrGetItem = lookupGetItemNode.execute(object, SpecialMethodNames.T___GETITEM__);
            return toSulongNode.execute(callGetItemNode.execute(attrGetItem, object, idx));
        }

        protected static boolean isTuple(Object object) {
            return object instanceof PTuple;
        }

        protected static boolean isList(Object object) {
            return object instanceof PList;
        }
    }

    @ExportMessage
    public void writeArrayElement(long index, Object value,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached WriteArrayItemNode writeArrayItemNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            writeArrayItemNode.execute(lib.getDelegate(this), index, value);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void removeArrayElement(@SuppressWarnings("unused") long index) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    public boolean isArrayElementModifiable(long index,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Shared("sizeNode") @Cached PyObjectSizeNode sizeNode) {
        return 0 <= index && index <= getArraySize(lib, sizeNode);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isArrayElementInsertable(@SuppressWarnings("unused") long index) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isArrayElementRemovable(@SuppressWarnings("unused") long index) {
        return false;
    }

    @GenerateUncached
    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class WriteArrayItemNode extends Node {
        public abstract void execute(Object arrayObject, Object idx, Object value) throws UnsupportedMessageException;

        @Specialization
        void doBytes(PBytesLike s, long idx, byte value,
                        @Shared("setByteItemNode") @Cached SequenceStorageNodes.SetItemDynamicNode setByteItemNode) {
            setByteItemNode.execute(null, NoGeneralizationNode.DEFAULT, s.getSequenceStorage(), idx, value);
        }

        @Specialization
        @ExplodeLoop
        void doBytes(PBytesLike s, long idx, short value,
                        @Shared("setByteItemNode") @Cached SequenceStorageNodes.SetItemDynamicNode setByteItemNode) {
            for (int offset = 0; offset < Short.BYTES; offset++) {
                setByteItemNode.execute(null, NoGeneralizationNode.DEFAULT, s.getSequenceStorage(), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
            }
        }

        @Specialization
        @ExplodeLoop
        void doBytes(PBytesLike s, long idx, int value,
                        @Shared("setByteItemNode") @Cached SequenceStorageNodes.SetItemDynamicNode setByteItemNode) {
            for (int offset = 0; offset < Integer.BYTES; offset++) {
                setByteItemNode.execute(null, NoGeneralizationNode.DEFAULT, s.getSequenceStorage(), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
            }
        }

        @Specialization
        @ExplodeLoop
        void doBytes(PBytesLike s, long idx, long value,
                        @Shared("setByteItemNode") @Cached SequenceStorageNodes.SetItemDynamicNode setByteItemNode) {
            for (int offset = 0; offset < Long.BYTES; offset++) {
                setByteItemNode.execute(null, NoGeneralizationNode.DEFAULT, s.getSequenceStorage(), idx + offset, (byte) (value >> (8 * offset)) & 0xFF);
            }
        }

        @Specialization
        void doList(PList s, long idx, Object value,
                        @Shared("toJavaNode") @Cached ToJavaStealingNode toJavaNode,
                        @Cached SequenceStorageNodes.SetItemDynamicNode setListItemNode,
                        @Cached ConditionProfile updateStorageProfile) {
            SequenceStorage storage = s.getSequenceStorage();
            SequenceStorage updatedStorage = setListItemNode.execute(null, ListGeneralizationNode.SUPPLIER, storage, idx, toJavaNode.execute(value));
            if (updateStorageProfile.profile(storage != updatedStorage)) {
                s.setSequenceStorage(updatedStorage);
            }
        }

        @Specialization
        void doTuple(PTuple s, long idx, Object value,
                        @Shared("toJavaNode") @Cached ToJavaStealingNode toJavaNode,
                        @Cached SequenceStorageNodes.SetItemDynamicNode setListItemNode) {
            setListItemNode.execute(null, NoGeneralizationNode.DEFAULT, s.getSequenceStorage(), idx, toJavaNode.execute(value));
        }

        @Specialization
        void doGeneric(PythonAbstractObject sequence, Object idx, Object value,
                        @Shared("toJavaNode") @Cached ToJavaStealingNode toJavaNode,
                        @Cached PInteropSubscriptAssignNode setItemNode) throws UnsupportedMessageException {
            setItemNode.execute(sequence, idx, toJavaNode.execute(value));
        }
    }

    @ExportMessage
    public void toNative(
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Exclusive @Cached ToNativeArrayNode toPyObjectNode,
                    @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
        invalidateNode.execute();
        if (!lib.isNative(this)) {
            setNativePointer(toPyObjectNode.execute(this));
        }
    }

    @GenerateUncached
    abstract static class ToNativeArrayNode extends Node {
        public abstract Object execute(PySequenceArrayWrapper object);

        @Specialization(guards = "isPSequence(lib.getDelegate(object))")
        static Object doPSequence(PySequenceArrayWrapper object,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceNodes.SetSequenceStorageNode setStorage,
                        @CachedLibrary(limit = "3") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached ToNativeStorageNode toNativeStorageNode) {
            PSequence sequence = (PSequence) lib.getDelegate(object);
            NativeSequenceStorage nativeStorage = toNativeStorageNode.execute(getStorage.execute(sequence), sequence instanceof PBytesLike);
            if (nativeStorage == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("could not allocate native storage");
            }
            // switch to native storage
            setStorage.execute(sequence, nativeStorage);
            return nativeStorage.getPtr();
        }

        @Specialization(guards = "!isPSequence(lib.getDelegate(object))")
        static Object doGeneric(PySequenceArrayWrapper object,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached PCallCapiFunction callNativeHandleForArrayNode) {
            // TODO correct element size
            return callNativeHandleForArrayNode.call(FUN_NATIVE_HANDLE_FOR_ARRAY, object, 8L);
        }

        protected static boolean isPSequence(Object obj) {
            return obj instanceof PSequence;
        }
    }

    @GenerateUncached
    abstract static class ToNativeStorageNode extends Node {

        public abstract NativeSequenceStorage execute(SequenceStorage object, boolean isBytesLike);

        public static boolean isEmptySequenceStorage(SequenceStorage s) {
            return s instanceof EmptySequenceStorage;
        }

        @Specialization(guards = {"!isNative(s)", "!isEmptySequenceStorage(s)"})
        static NativeSequenceStorage doManaged(SequenceStorage s, @SuppressWarnings("unused") boolean isBytesLike,
                        @Cached ConditionProfile isObjectArrayProfile,
                        @Shared("storageToNativeNode") @Cached SequenceStorageNodes.StorageToNativeNode storageToNativeNode,
                        @Cached SequenceStorageNodes.GetInternalArrayNode getInternalArrayNode) {
            Object array = getInternalArrayNode.execute(s);
            if (isBytesLike) {
                assert array instanceof byte[];
            } else if (!isObjectArrayProfile.profile(array instanceof Object[])) {
                array = generalize(s);
            }
            return storageToNativeNode.execute(array);
        }

        @TruffleBoundary
        private static Object generalize(SequenceStorage s) {
            return s.getInternalArray();
        }

        @Specialization
        static NativeSequenceStorage doNative(NativeSequenceStorage s, @SuppressWarnings("unused") boolean isBytesLike) {
            return s;
        }

        @Specialization
        static NativeSequenceStorage doEmptyStorage(@SuppressWarnings("unused") EmptySequenceStorage s, @SuppressWarnings("unused") boolean isBytesLike,
                        @Shared("storageToNativeNode") @Cached SequenceStorageNodes.StorageToNativeNode storageToNativeNode) {
            // TODO(fa): not sure if that completely reflects semantics
            return storageToNativeNode.execute(PythonUtils.EMPTY_BYTE_ARRAY);
        }

        protected static boolean isNative(SequenceStorage s) {
            return s instanceof NativeSequenceStorage;
        }
    }

    @ExportMessage
    public boolean isPointer(
                    @Cached IsPointerNode pIsPointerNode) {
        return pIsPointerNode.execute(this);
    }

    @ExportMessage
    public long asPointer(
                    @CachedLibrary(limit = "1") InteropLibrary interopLibrary,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib) throws UnsupportedMessageException {
        Object nativePointer = lib.getNativePointer(this);
        if (nativePointer instanceof Long) {
            return (long) nativePointer;
        }
        return interopLibrary.asPointer(nativePointer);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    Object getNativeType(
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Exclusive @Cached GetTypeIDNode getTypeIDNode) {
        return getTypeIDNode.execute(lib.getDelegate(this));
    }

    @GenerateUncached
    @ImportStatic({SpecialMethodNames.class, PySequenceArrayWrapper.class})
    abstract static class GetTypeIDNode extends PNodeWithContext {

        public abstract Object execute(Object delegate);

        protected static Object callGetByteArrayTypeIDUncached() {
            return PCallCapiFunction.getUncached().call(FUN_GET_BYTE_ARRAY_TYPE_ID, 0);
        }

        protected static Object callGetPtrArrayTypeIDUncached() {
            return PCallCapiFunction.getUncached().call(FUN_GET_PTR_ARRAY_TYPE_ID, 0);
        }

        @Specialization(guards = {"isSingleContext()", "hasByteArrayContent(object)"})
        Object doByteArray(@SuppressWarnings("unused") Object object,
                        @Exclusive @Cached("callGetByteArrayTypeIDUncached()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(guards = {"isSingleContext()", "!hasByteArrayContent(object)"})
        Object doPtrArray(@SuppressWarnings("unused") Object object,
                        @Exclusive @Cached("callGetPtrArrayTypeIDUncached()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(guards = "hasByteArrayContent(object)", replaces = "doByteArray")
        Object doByteArrayMultiCtx(@SuppressWarnings("unused") Object object,
                        @Shared("callUnaryNode") @Cached PCallCapiFunction callUnaryNode) {
            return callUnaryNode.call(FUN_GET_BYTE_ARRAY_TYPE_ID, 0);
        }

        @Specialization(guards = "!hasByteArrayContent(object)", replaces = "doPtrArray")
        Object doPtrArrayMultiCtx(@SuppressWarnings("unused") Object object,
                        @Shared("callUnaryNode") @Cached PCallCapiFunction callUnaryNode) {
            return callUnaryNode.call(FUN_GET_PTR_ARRAY_TYPE_ID, 0);
        }
    }

    protected static boolean hasByteArrayContent(Object object) {
        return object instanceof PBytesLike || object instanceof PMMap;
    }

}
