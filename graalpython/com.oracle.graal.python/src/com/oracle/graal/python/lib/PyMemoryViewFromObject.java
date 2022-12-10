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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.util.BufferFormat.T_UINT_8_TYPE_CODE;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.memoryview.BufferLifecycleManager;
import com.oracle.graal.python.builtins.objects.memoryview.CExtPyBuffer;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewNodes;
import com.oracle.graal.python.builtins.objects.memoryview.NativeBufferLifecycleManager.NativeBufferLifecycleManagerFromSlot;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.BufferFormat;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class PyMemoryViewFromObject extends PNodeWithRaiseAndIndirectCall {
    public abstract PMemoryView execute(VirtualFrame frame, Object object);

    @Specialization
    PMemoryView fromMemoryView(PMemoryView object,
                    @Cached PythonObjectFactory factory) {
        object.checkReleased(this);
        return factory.createMemoryView(PythonContext.get(this), object.getLifecycleManager(), object.getBuffer(), object.getOwner(), object.getLength(),
                        object.isReadOnly(), object.getItemSize(), object.getFormat(), object.getFormatString(), object.getDimensions(),
                        object.getBufferPointer(), object.getOffset(), object.getBufferShape(), object.getBufferStrides(),
                        object.getBufferSuboffsets(), object.getFlags());
    }

    @Specialization
    PMemoryView fromNative(PythonNativeObject object,
                    @Cached CExtNodes.CreateMemoryViewFromNativeNode fromNativeNode) {
        return fromNativeNode.execute(object, BufferFlags.PyBUF_FULL_RO);
    }

    @Specialization
    PMemoryView fromMMap(PMMap object,
                    @Cached PythonObjectFactory factory,
                    @Cached TruffleString.CodePointLengthNode lengthNode,
                    @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
        return factory.createMemoryViewForManagedObject(object, 1, (int) object.getLength(), false, T_UINT_8_TYPE_CODE, lengthNode, atIndexNode);
    }

    @Specialization(guards = {"!isMemoryView(object)", "!isNativeObject(object)", "!isMMap(object)"}, limit = "3")
    PMemoryView fromManaged(VirtualFrame frame, Object object,
                    @CachedLibrary("object") PythonBufferAcquireLibrary bufferAcquireLib,
                    @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                    @Cached ConditionProfile hasSlotProfile,
                    @Cached GetClassNode getClassNode,
                    @Cached("createForceType()") ReadAttributeFromObjectNode readGetBufferNode,
                    @Cached("createForceType()") ReadAttributeFromObjectNode readReleaseBufferNode,
                    @Cached CallNode callNode,
                    @Cached PythonObjectFactory factory,
                    @Cached MemoryViewNodes.InitFlagsNode initFlagsNode,
                    @Cached TruffleString.CodePointLengthNode lengthNode,
                    @Cached TruffleString.CodePointAtIndexNode atIndexNode) {
        Object type = getClassNode.execute(object);
        Object getBufferAttr = readGetBufferNode.execute(type, TypeBuiltins.TYPE_GETBUFFER);
        if (hasSlotProfile.profile(getBufferAttr != PNone.NO_VALUE)) {
            // HPy object with buffer slot
            Object result = callNode.execute(getBufferAttr, object, BufferFlags.PyBUF_FULL_RO);
            if (!(result instanceof CExtPyBuffer)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere("The internal " + TypeBuiltins.TYPE_GETBUFFER + " is expected to return a CExtPyBuffer object.");
            }
            CExtPyBuffer cBuffer = (CExtPyBuffer) result;

            Object releaseBufferAttr = readReleaseBufferNode.execute(type, TypeBuiltins.TYPE_RELEASEBUFFER);
            BufferLifecycleManager bufferLifecycleManager = null;
            if (releaseBufferAttr != PNone.NO_VALUE) {
                bufferLifecycleManager = new NativeBufferLifecycleManagerFromSlot(cBuffer, object, releaseBufferAttr);
            }

            int[] shape = cBuffer.getShape();
            if (shape == null) {
                shape = new int[]{cBuffer.getLen() / cBuffer.getItemSize()};
            }
            int[] strides = cBuffer.getStrides();
            if (strides == null) {
                strides = PMemoryView.initStridesFromShape(cBuffer.getDims(), cBuffer.getItemSize(), shape);
            }
            int[] suboffsets = cBuffer.getSuboffsets();
            int flags = initFlagsNode.execute(cBuffer.getDims(), cBuffer.getItemSize(), shape, strides, suboffsets);
            // TODO when Sulong allows exposing pointers as interop buffer, we can get rid of this
            Object pythonBuffer = new NativeSequenceStorage(cBuffer.getBuf(), cBuffer.getLen(), cBuffer.getLen(), SequenceStorage.ListStorageType.Byte);
            TruffleString format = cBuffer.getFormat();
            return factory.createMemoryView(PythonContext.get(this), bufferLifecycleManager, pythonBuffer, cBuffer.getObj(), cBuffer.getLen(), cBuffer.isReadOnly(), cBuffer.getItemSize(),
                            BufferFormat.forMemoryView(format, lengthNode, atIndexNode),
                            format, cBuffer.getDims(), cBuffer.getBuf(), 0, shape, strides, suboffsets, flags);
        } else if (bufferAcquireLib.hasBuffer(object)) {
            // Managed object that implements PythonBufferAcquireLibrary
            Object buffer = bufferAcquireLib.acquireReadonly(object, frame, this);
            return factory.createMemoryViewForManagedObject(buffer, bufferLib.getOwner(buffer), bufferLib.getItemSize(buffer), bufferLib.getBufferLength(buffer), bufferLib.isReadonly(buffer),
                            bufferLib.getFormatString(buffer), lengthNode, atIndexNode);
        } else {
            throw raise(TypeError, ErrorMessages.MEMORYVIEW_A_BYTES_LIKE_OBJECT_REQUIRED_NOT_P, object);
        }
    }

    public static PyMemoryViewFromObject create() {
        return PyMemoryViewFromObjectNodeGen.create();
    }
}
