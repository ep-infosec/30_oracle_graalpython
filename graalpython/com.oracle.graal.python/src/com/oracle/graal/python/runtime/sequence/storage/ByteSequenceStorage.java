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
package com.oracle.graal.python.runtime.sequence.storage;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;

@ExportLibrary(PythonBufferAccessLibrary.class)
public final class ByteSequenceStorage extends TypedSequenceStorage {

    private byte[] values;

    public ByteSequenceStorage(byte[] elements) {
        this(elements, elements.length);
    }

    public ByteSequenceStorage(byte[] elements, int length) {
        this.values = elements;
        this.capacity = values.length;
        this.length = length;
    }

    public ByteSequenceStorage(int capacity) {
        this.values = new byte[capacity];
        this.capacity = capacity;
        this.length = 0;
    }

    @Override
    protected void increaseCapacityExactWithCopy(int newCapacity) {
        values = Arrays.copyOf(values, newCapacity);
        capacity = values.length;
    }

    @Override
    protected void increaseCapacityExact(int newCapacity) {
        values = new byte[newCapacity];
        capacity = values.length;
    }

    @Override
    public SequenceStorage copy() {
        return new ByteSequenceStorage(PythonUtils.arrayCopyOf(values, length));
    }

    @Override
    public SequenceStorage createEmpty(int newCapacity) {
        return new ByteSequenceStorage(newCapacity);
    }

    @Override
    public Object[] getInternalArray() {
        /**
         * Have to box and copy.
         */
        Object[] boxed = new Object[length];

        for (int i = 0; i < length; i++) {
            boxed[i] = values[i];
        }

        return boxed;
    }

    @TruffleBoundary(allowInlining = true)
    @Ignore
    public byte[] getInternalByteArray() {
        if (length != values.length) {
            assert length < values.length;
            return Arrays.copyOf(values, length);
        }
        return values;
    }

    @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
    public ByteBuffer getBufferView() {
        ByteBuffer view = ByteBuffer.wrap(values);
        view.limit(values.length);
        return view;
    }

    @Override
    public Object getItemNormalized(int idx) {
        return getIntItemNormalized(idx);
    }

    public final byte getByteItemNormalized(int idx) {
        return values[idx];
    }

    public int getIntItemNormalized(int idx) {
        return values[idx] & 0xFF;
    }

    @Override
    public void setItemNormalized(int idx, Object value) throws SequenceStoreException {
        if (value instanceof Byte) {
            setByteItemNormalized(idx, (byte) value);
        } else if (value instanceof Integer) {
            if ((int) value < 0 || (int) value >= 256) {
                throw PRaiseNode.raiseUncached(null, ValueError, ErrorMessages.BYTE_MUST_BE_IN_RANGE);
            }
            setByteItemNormalized(idx, ((Integer) value).byteValue());
        } else {
            throw PRaiseNode.raiseUncached(null, TypeError, ErrorMessages.INTEGER_REQUIRED);
        }
    }

    public void setByteItemNormalized(int idx, byte value) {
        values[idx] = value;
    }

    @Override
    public void insertItem(int idx, Object value) throws SequenceStoreException {
        if (value instanceof Byte) {
            insertByteItem(idx, (byte) value);
        } else if (value instanceof Integer) {
            insertByteItem(idx, ((Integer) value).byteValue());
        } else {
            throw new SequenceStoreException(value);
        }
    }

    public void insertByteItem(int idx, byte value) {
        ensureCapacity(length + 1);

        // shifting tail to the right by one slot
        for (int i = values.length - 1; i > idx; i--) {
            values[i] = values[i - 1];
        }

        values[idx] = value;
        length++;
    }

    @Override
    public void copyItem(int idxTo, int idxFrom) {
        values[idxTo] = values[idxFrom];
    }

    @Override
    public ByteSequenceStorage getSliceInBound(int start, int stop, int step, int sliceLength) {
        byte[] newArray = new byte[sliceLength];

        if (step == 1) {
            PythonUtils.arraycopy(values, start, newArray, 0, sliceLength);
            return new ByteSequenceStorage(newArray);
        }

        for (int i = start, j = 0; j < sliceLength; i += step, j++) {
            newArray[j] = values[i];
        }

        return new ByteSequenceStorage(newArray);
    }

    public int indexOfByte(byte value) {
        for (int i = 0; i < length; i++) {
            if (values[i] == value) {
                return i;
            }
        }

        return -1;
    }

    public int indexOfInt(int value) {
        for (int i = 0; i < length; i++) {
            if ((values[i] & 0xFF) == value) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void reverse() {
        if (length > 0) {
            int head = 0;
            int tail = length - 1;
            int middle = (length - 1) / 2;

            for (; head <= middle; head++, tail--) {
                byte temp = values[head];
                values[head] = values[tail];
                values[tail] = temp;
            }
        }
    }

    @Override
    public Object getIndicativeValue() {
        return 0;
    }

    @Override
    public boolean equals(SequenceStorage other) {
        if (other.length() != length() || !(other instanceof ByteSequenceStorage)) {
            return false;
        }

        byte[] otherArray = ((ByteSequenceStorage) other).getInternalByteArray();
        for (int i = 0; i < length(); i++) {
            if (values[i] != otherArray[i]) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Object getInternalArrayObject() {
        return values;
    }

    @Override
    public Object getCopyOfInternalArrayObject() {
        return Arrays.copyOf(values, length);
    }

    @Override
    public Object[] getCopyOfInternalArray() {
        return getInternalArray();
    }

    @Override
    public void setInternalArrayObject(Object arrayObject) {
        this.values = (byte[]) arrayObject;
    }

    @Override
    public ListStorageType getElementType() {
        return ListStorageType.Byte;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isBuffer() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isReadonly() {
        return false;
    }

    @ExportMessage
    int getBufferLength() {
        return length;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasInternalByteArray() {
        return true;
    }

    @ExportMessage(name = "getInternalByteArray")
    byte[] getInternalByteArrayMessage() {
        return values;
    }

    @ExportMessage
    byte readByte(int byteOffset) {
        return values[byteOffset];
    }

    @ExportMessage
    void writeByte(int byteOffset, byte value) {
        values[byteOffset] = value;
    }

    @ExportMessage
    short readShort(int byteOffset) {
        return PythonUtils.arrayAccessor.getShort(values, byteOffset);
    }

    @ExportMessage
    void writeShort(int byteOffset, short value) {
        PythonUtils.arrayAccessor.putShort(values, byteOffset, value);
    }

    @ExportMessage
    int readInt(int byteOffset) {
        return PythonUtils.arrayAccessor.getInt(values, byteOffset);
    }

    @ExportMessage
    void writeInt(int byteOffset, int value) {
        PythonUtils.arrayAccessor.putInt(values, byteOffset, value);
    }

    @ExportMessage
    long readLong(int byteOffset) {
        return PythonUtils.arrayAccessor.getLong(values, byteOffset);
    }

    @ExportMessage
    void writeLong(int byteOffset, long value) {
        PythonUtils.arrayAccessor.putLong(values, byteOffset, value);
    }

    @ExportMessage
    float readFloat(int byteOffset) {
        return PythonUtils.arrayAccessor.getFloat(values, byteOffset);
    }

    @ExportMessage
    void writeFloat(int byteOffset, float value) {
        PythonUtils.arrayAccessor.putFloat(values, byteOffset, value);
    }

    @ExportMessage
    double readDouble(int byteOffset) {
        return PythonUtils.arrayAccessor.getDouble(values, byteOffset);
    }

    @ExportMessage
    void writeDouble(int byteOffset, double value) {
        PythonUtils.arrayAccessor.putDouble(values, byteOffset, value);
    }
}
