/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.math.BigInteger;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;

/**
 * Casts a Python {@code int} to Java {@code long}. This method follows the semantics of CPython's
 * function {@code PyLong_AsUnsignedLong}:
 * <ul>
 * <li>raises {@code TypeError} if the argument is not an int</li>
 * <li>raises {@code OverflowError} if the argument is negative</li>
 * <li>raises {@code OverflowError} if the argument is greater than or equal to 2^64</li>
 * </ul>
 * Note that since Java {@code long} is signed, the values in the between 2^63 and 2^64-1 are
 * returned as negative numbers.
 */
@TypeSystemReference(PythonArithmeticTypes.class)
public abstract class CastToJavaUnsignedLongNode extends PNodeWithContext {

    @Child private PRaiseNode raiseNode;

    public static CastToJavaUnsignedLongNode create() {
        return CastToJavaUnsignedLongNodeGen.create();
    }

    public abstract long execute(int x);

    public abstract long execute(long x);

    public abstract long execute(Object x);

    @Specialization
    long toUnsignedLong(long x) {
        checkNegative(x < 0);
        return x;
    }

    @Specialization
    long toUnsignedLong(PInt x) {
        checkNegative(x.isNegative());
        return convertBigInt(x.getValue());
    }

    @Fallback
    long doUnsupported(@SuppressWarnings("unused") Object x) {
        throw getRaiseNode().raise(TypeError, ErrorMessages.INTEGER_REQUIRED);
    }

    private void checkNegative(boolean negative) {
        if (negative) {
            throw getRaiseNode().raise(OverflowError, ErrorMessages.CANNOT_CONVERT_NEGATIVE_VALUE_TO_UNSIGNED_INT);
        }
    }

    @TruffleBoundary
    private long convertBigInt(BigInteger bi) {
        if (bi.bitLength() > 64) {
            throw getRaiseNode().raise(OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "unsigned long");
        }
        return bi.longValue();
    }

    private PRaiseNode getRaiseNode() {
        if (raiseNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (isAdoptable()) {
                raiseNode = insert(PRaiseNode.create());
            } else {
                raiseNode = PRaiseNode.getUncached();
            }
        }
        return raiseNode;
    }
}
